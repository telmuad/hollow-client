"""Microsoft account login: the official OAuth 2.0 authorization-code flow with PKCE, then Xbox Live -> XSTS ->
Minecraft services, using minecraft-launcher-lib's step functions so each failure can be explained in plain words.

There is no offline or cracked mode: without a working Microsoft login the launcher can install and update, but
not start the game.
"""

from __future__ import annotations

import html
import socket
import threading
import time
import webbrowser
from dataclasses import dataclass
from http.server import BaseHTTPRequestHandler, HTTPServer
from typing import Any, Callable
from urllib.parse import parse_qs, urlparse

import requests
from minecraft_launcher_lib import microsoft_account as ms

from .appconfig import AppConfig

NOT_APPROVED_MESSAGE = ("Your app ID hasn't been approved by Mojang yet. You can still install and update everything, "
                        "and launch from the official launcher in the meantime.")

XERR_MESSAGES = {
    2148916227: "This Microsoft account is banned from Xbox Live.",
    2148916229: "This account needs a parent's permission to play online. A parent can change this at account.microsoft.com/family.",
    2148916233: "This Microsoft account doesn't have an Xbox profile yet. Sign in once at minecraft.net or xbox.com to create one, then try again.",
    2148916234: "You need to accept the Xbox Live terms first. Sign in at xbox.com once, then try again.",
    2148916235: "Xbox Live isn't available in your country, so Microsoft won't let this account sign in.",
    2148916236: "This account needs adult verification on the Xbox website before it can sign in.",
    2148916237: "This account needs adult verification on the Xbox website before it can sign in.",
    2148916238: "This is a child account. An adult has to add it to a Microsoft family before it can play.",
}


class LoginError(Exception):
    """kind: not_approved, xbox, no_minecraft, cancelled, timeout, network, config, expired, unknown"""

    def __init__(self, kind: str, message: str, detail: str = ""):
        super().__init__(message)
        self.kind = kind
        self.message = message
        self.detail = detail


@dataclass
class Account:
    uuid: str
    name: str
    access_token: str
    refresh_token: str
    skin_url: str | None
    obtained_at: float

    def public(self) -> dict[str, Any]:
        """What may be stored on disk (no tokens)."""
        return {"uuid": self.uuid, "name": self.name, "skin_url": self.skin_url}


# ------------------------------------------------------------------------------------------------ pure helpers

def redirect_port(base: str) -> int | None:
    """The fixed port in the configured redirect URI, or None if it has none (then any free port is used)."""
    return urlparse(base).port


def build_redirect_uri(base: str, port: int) -> str:
    """http://localhost + the port the local server got. Microsoft accepts any port on loopback for desktop apps."""
    parsed = urlparse(base)
    host = parsed.hostname or "localhost"
    if parsed.port:
        return f"http://{host}:{parsed.port}"
    return f"http://{host}:{port}"


def xsts_error(response: dict[str, Any]) -> LoginError | None:
    xerr = response.get("XErr")
    if xerr is None:
        return None
    try:
        code = int(xerr)
    except (TypeError, ValueError):
        code = 0
    message = XERR_MESSAGES.get(code, f"Xbox Live refused the sign-in (error {xerr}).")
    return LoginError("xbox", message, str(response))


def minecraft_login_error(response: dict[str, Any]) -> LoginError | None:
    """login_with_xbox gives no token when the Azure app isn't approved for the Minecraft API (HTTP 403,
    "Invalid app registration"). That is by far the most common reason, so any missing token is reported that way."""
    if isinstance(response, dict) and response.get("access_token"):
        return None
    return LoginError("not_approved", NOT_APPROVED_MESSAGE, str(response))


def token_error(response: dict[str, Any], refreshing: bool) -> LoginError | None:
    if isinstance(response, dict) and response.get("access_token") and response.get("refresh_token"):
        return None
    code = str(response.get("error", "")) if isinstance(response, dict) else ""
    desc = str(response.get("error_description", "")) if isinstance(response, dict) else ""
    if refreshing:
        return LoginError("expired", "Your sign-in has expired. Please log in again.", f"{code} {desc}")
    if code in ("invalid_client", "unauthorized_client") or "AADSTS700016" in desc or "AADSTS7000218" in desc:
        return LoginError("config", "Microsoft didn't accept the app ID in config.json. Check the ID, and that the Azure app "
                                    "allows personal accounts and has http://localhost as a mobile/desktop redirect URI.",
                          f"{code} {desc}")
    if "AADSTS50011" in desc:
        return LoginError("config", "The redirect URI doesn't match your Azure app. Add http://localhost as a "
                                    "\"Mobile and desktop applications\" redirect URI.", f"{code} {desc}")
    return LoginError("unknown", "Microsoft sign-in failed. Please try again.", f"{code} {desc}")


def account_from(profile: dict[str, Any], access_token: str, refresh_token: str) -> Account:
    if profile.get("error") == "NOT_FOUND":
        raise LoginError("no_minecraft", "This Microsoft account doesn't own Minecraft: Java Edition.", str(profile))
    if not profile.get("id") or not profile.get("name"):
        raise LoginError("unknown", "Minecraft didn't send your profile. Please try again.", str(profile))
    skin = None
    for s in profile.get("skins") or []:
        if isinstance(s, dict) and s.get("state") == "ACTIVE" and isinstance(s.get("url"), str):
            skin = s["url"]
            break
    return Account(str(profile["id"]), str(profile["name"]), access_token, refresh_token, skin, time.time())


# ------------------------------------------------------------------------------------------------ the chain

def _step(fn: Callable[..., dict[str, Any]], *args: Any) -> dict[str, Any]:
    try:
        result = fn(*args)
    except requests.RequestException as e:
        raise LoginError("network", "Couldn't reach Microsoft/Xbox servers. Check your internet connection.", str(e)) from e
    except ValueError as e:  # the server sent something that isn't JSON
        raise LoginError("unknown", "A sign-in server sent an unexpected reply. Please try again.", str(e)) from e
    return result if isinstance(result, dict) else {}


def finish_login(ms_access_token: str, refresh_token: str) -> Account:
    """Microsoft access token -> Xbox Live -> XSTS -> Minecraft access token -> profile."""
    xbl = _step(ms.authenticate_with_xbl, ms_access_token)
    try:
        xbl_token = xbl["Token"]
        userhash = xbl["DisplayClaims"]["xui"][0]["uhs"]
    except (KeyError, IndexError, TypeError):
        raise LoginError("xbox", "Xbox Live didn't accept this account. Sign in at xbox.com once, then try again.", str(xbl))
    xsts = _step(ms.authenticate_with_xsts, xbl_token)
    err = xsts_error(xsts)
    if err:
        raise err
    if "Token" not in xsts:
        raise LoginError("xbox", "Xbox Live didn't accept this account. Please try again.", str(xsts))
    mc = _step(ms.authenticate_with_minecraft, userhash, xsts["Token"])
    err = minecraft_login_error(mc)
    if err:
        raise err
    profile = _step(ms.get_profile, mc["access_token"])
    return account_from(profile, mc["access_token"], refresh_token)


def refresh(config: AppConfig, refresh_token: str) -> Account:
    """Silent sign-in with the stored refresh token."""
    if not config.has_client_id:
        raise LoginError("config", config.status_text())
    tokens = _step(ms.refresh_authorization_token, config.azure_client_id, None, None, refresh_token)
    err = token_error(tokens, refreshing=True)
    if err:
        raise err
    return finish_login(tokens["access_token"], tokens["refresh_token"])


# ------------------------------------------------------------------------------------------------ browser login

_PAGE = """<!doctype html><html><head><meta charset="utf-8"><title>Hollow Client</title>
<style>body{{margin:0;height:100vh;display:flex;align-items:center;justify-content:center;background:#0f1015;
color:#ecedf2;font-family:Segoe UI,system-ui,sans-serif}}.c{{text-align:center;padding:32px 40px;background:#171920;
border:1px solid #2a2d38;border-radius:14px}}h1{{margin:0 0 8px;font-size:22px;color:{color}}}p{{margin:0;color:#9ea3b0}}
</style></head><body><div class="c"><h1>{title}</h1><p>{text}</p></div></body></html>"""


class _Handler(BaseHTTPRequestHandler):
    def do_GET(self) -> None:  # noqa: N802 (http.server naming)
        parsed = urlparse(self.path)
        if parsed.path not in ("", "/"):
            self.send_response(404)
            self.end_headers()
            return
        qs = parse_qs(parsed.query)
        ok = "code" in qs
        title = "You're signed in" if ok else "Sign-in didn't finish"
        text = "You can close this tab and go back to Hollow Client." if ok else \
            html.escape(qs.get("error_description", ["You can close this tab and try again in Hollow Client."])[0])
        body = _PAGE.format(title=title, text=text, color="#8b6cff" if ok else "#f2706b").encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", "text/html; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)
        self.server.hollow_box.put(self.path)  # type: ignore[attr-defined]

    def log_message(self, format: str, *args: Any) -> None:  # silence the default console logging
        pass


class _V6Server(HTTPServer):
    address_family = socket.AF_INET6


class _Box:
    def __init__(self) -> None:
        self.value: str | None = None
        self.event = threading.Event()

    def put(self, value: str) -> None:
        if self.value is None:
            self.value = value
            self.event.set()


class LoopbackServer:
    """Listens on 127.0.0.1 (and ::1 on the same port when possible) for Microsoft's redirect back to the app."""

    def __init__(self, port: int | None = None):
        self.box = _Box()
        self.servers: list[HTTPServer] = []
        v4 = HTTPServer(("127.0.0.1", port or 0), _Handler)
        self.port = v4.server_address[1]
        self.servers.append(v4)
        try:
            self.servers.append(_V6Server(("::1", self.port), _Handler))
        except OSError:
            pass
        for s in self.servers:
            s.hollow_box = self.box  # type: ignore[attr-defined]
            threading.Thread(target=s.serve_forever, kwargs={"poll_interval": 0.2}, daemon=True).start()

    def wait(self, timeout: float, cancelled: threading.Event | None = None) -> str | None:
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            if self.box.event.wait(0.25):
                return self.box.value
            if cancelled is not None and cancelled.is_set():
                return None
        return None

    def close(self) -> None:
        for s in self.servers:
            s.shutdown()
            s.server_close()


def login(config: AppConfig, open_browser: Callable[[str], Any] = webbrowser.open, timeout: float = 300,
          cancelled: threading.Event | None = None) -> Account:
    """Open the Microsoft sign-in page in the default browser and finish the login when it redirects back."""
    if config.problem or not config.has_client_id:
        raise LoginError("config", config.status_text())
    try:
        server = LoopbackServer(redirect_port(config.redirect_uri))
    except OSError as e:
        raise LoginError("config", "Couldn't open the local port for the Microsoft redirect. If you set a port in "
                                   "redirect_uri, pick another one.", str(e)) from e
    try:
        redirect = build_redirect_uri(config.redirect_uri, server.port)
        url, state, verifier = ms.get_secure_login_data(config.azure_client_id, redirect)
        open_browser(url)
        path = server.wait(timeout, cancelled)
    finally:
        server.close()
    if path is None:
        if cancelled is not None and cancelled.is_set():
            raise LoginError("cancelled", "Sign-in cancelled.")
        raise LoginError("timeout", "The sign-in page wasn't finished within 5 minutes. Please try again.")
    qs = parse_qs(urlparse(path).query)
    if "error" in qs:
        code = qs["error"][0]
        if code == "access_denied":
            raise LoginError("cancelled", "Sign-in was cancelled in the browser.")
        raise LoginError("unknown", "Microsoft sign-in failed: " + qs.get("error_description", [code])[0])
    if qs.get("state", [None])[0] != state or "code" not in qs:
        raise LoginError("unknown", "The sign-in reply didn't match this request, so it was ignored. Please try again.")
    tokens = _step(ms.get_authorization_token, config.azure_client_id, None, redirect, qs["code"][0], verifier)
    err = token_error(tokens, refreshing=False)
    if err:
        raise err
    return finish_login(tokens["access_token"], tokens["refresh_token"])
