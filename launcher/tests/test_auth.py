import threading
import urllib.request
from urllib.parse import parse_qs, urlencode, urlparse

import pytest
import requests

from hollow_launcher import auth
from hollow_launcher.appconfig import AppConfig
from hollow_launcher.auth import LoginError, NOT_APPROVED_MESSAGE
from hollow_launcher.token_store import MemoryBackend, TokenStore

CLIENT_ID = "1a2b3c4d-1111-2222-3333-444455556666"
PROFILE = {"id": "0123456789abcdef0123456789abcdef", "name": "Steve",
           "skins": [{"state": "INACTIVE", "url": "http://old"}, {"state": "ACTIVE", "url": "http://textures.minecraft.net/texture/abc"}]}


@pytest.fixture
def good_chain(monkeypatch):
    """Every Xbox/Minecraft step succeeds."""
    monkeypatch.setattr(auth.ms, "authenticate_with_xbl", lambda t: {"Token": "xbl", "DisplayClaims": {"xui": [{"uhs": "hash"}]}})
    monkeypatch.setattr(auth.ms, "authenticate_with_xsts", lambda t: {"Token": "xsts"})
    monkeypatch.setattr(auth.ms, "authenticate_with_minecraft", lambda uhs, t: {"access_token": "mc-token"})
    monkeypatch.setattr(auth.ms, "get_profile", lambda t: dict(PROFILE))


# ------------------------------------------------------------------------------------------------ pure helpers

def test_redirect_uri_gets_the_local_port():
    assert auth.build_redirect_uri("http://localhost", 51234) == "http://localhost:51234"
    assert auth.build_redirect_uri("http://localhost/", 51234) == "http://localhost:51234"
    assert auth.build_redirect_uri("http://localhost:25585", 51234) == "http://localhost:25585"
    assert auth.redirect_port("http://localhost") is None
    assert auth.redirect_port("http://localhost:25585") == 25585


def test_xsts_errors_are_explained():
    assert auth.xsts_error({"Token": "x"}) is None
    e = auth.xsts_error({"XErr": 2148916233})
    assert e.kind == "xbox" and "Xbox profile" in e.message
    assert "child account" in auth.xsts_error({"XErr": 2148916238}).message
    assert "error 123" in auth.xsts_error({"XErr": 123}).message


def test_minecraft_login_without_token_means_not_approved():
    e = auth.minecraft_login_error({"path": "/authentication/login_with_xbox",
                                     "errorMessage": "Invalid app registration, see https://aka.ms/AppRegInfo for more information"})
    assert e.kind == "not_approved"
    assert e.message == NOT_APPROVED_MESSAGE
    assert auth.minecraft_login_error({}).kind == "not_approved"
    assert auth.minecraft_login_error({"access_token": "x"}) is None


def test_token_errors():
    assert auth.token_error({"access_token": "a", "refresh_token": "r"}, refreshing=False) is None
    assert auth.token_error({"error": "invalid_grant"}, refreshing=True).kind == "expired"
    assert auth.token_error({"error": "invalid_client", "error_description": "AADSTS700016: app not found"}, False).kind == "config"
    assert "redirect URI" in auth.token_error({"error": "invalid_request", "error_description": "AADSTS50011: mismatch"}, False).message


def test_account_from_profile():
    a = auth.account_from(dict(PROFILE), "mc", "refresh")
    assert a.name == "Steve" and a.skin_url.endswith("/abc")
    assert a.public() == {"uuid": PROFILE["id"], "name": "Steve", "skin_url": a.skin_url}
    assert "token" not in str(a.public())
    with pytest.raises(LoginError) as e:
        auth.account_from({"error": "NOT_FOUND"}, "mc", "r")
    assert e.value.kind == "no_minecraft"


# ------------------------------------------------------------------------------------------------ the chain

def test_finish_login_success(good_chain):
    a = auth.finish_login("ms-token", "refresh")
    assert (a.name, a.access_token, a.refresh_token) == ("Steve", "mc-token", "refresh")


def test_not_approved_app_gives_the_friendly_message(good_chain, monkeypatch):
    monkeypatch.setattr(auth.ms, "authenticate_with_minecraft", lambda uhs, t: {
        "path": "/authentication/login_with_xbox", "errorMessage": "Invalid app registration, see https://aka.ms/AppRegInfo"})
    with pytest.raises(LoginError) as e:
        auth.finish_login("ms-token", "refresh")
    assert e.value.kind == "not_approved"
    assert str(e.value) == NOT_APPROVED_MESSAGE


def test_no_xbox_profile(good_chain, monkeypatch):
    monkeypatch.setattr(auth.ms, "authenticate_with_xsts", lambda t: {"Identity": "0", "XErr": 2148916233})
    with pytest.raises(LoginError) as e:
        auth.finish_login("ms-token", "refresh")
    assert e.value.kind == "xbox"


def test_account_without_minecraft(good_chain, monkeypatch):
    monkeypatch.setattr(auth.ms, "get_profile", lambda t: {"error": "NOT_FOUND", "path": "/minecraft/profile"})
    with pytest.raises(LoginError) as e:
        auth.finish_login("ms-token", "refresh")
    assert e.value.kind == "no_minecraft"


def test_network_errors_are_reported(good_chain, monkeypatch):
    def boom(t):
        raise requests.ConnectionError("no route")

    monkeypatch.setattr(auth.ms, "authenticate_with_xbl", boom)
    with pytest.raises(LoginError) as e:
        auth.finish_login("ms-token", "refresh")
    assert e.value.kind == "network"


def test_refresh(good_chain, monkeypatch):
    cfg = AppConfig(azure_client_id=CLIENT_ID)
    monkeypatch.setattr(auth.ms, "refresh_authorization_token", lambda *a: {"access_token": "ms2", "refresh_token": "new-refresh"})
    a = auth.refresh(cfg, "old-refresh")
    assert a.refresh_token == "new-refresh"
    monkeypatch.setattr(auth.ms, "refresh_authorization_token", lambda *a: {"error": "invalid_grant"})
    with pytest.raises(LoginError) as e:
        auth.refresh(cfg, "old-refresh")
    assert e.value.kind == "expired"


def test_login_needs_a_client_id():
    with pytest.raises(LoginError) as e:
        auth.login(AppConfig(), open_browser=lambda url: None, timeout=1)
    assert e.value.kind == "config"


# ------------------------------------------------------------------------------------------------ browser round trip

def fake_browser(reply):
    """A 'browser' that follows the login URL straight back to our redirect URI with the given reply."""
    seen = {}

    def open_browser(url):
        q = parse_qs(urlparse(url).query)
        seen["url"] = url
        redirect = q["redirect_uri"][0]
        params = reply(q)

        def go():
            with urllib.request.urlopen(f"{redirect}/?{urlencode(params)}", timeout=5) as r:
                seen["page"] = r.read().decode("utf-8")

        threading.Thread(target=go, daemon=True).start()

    return open_browser, seen


def test_full_login_round_trip(good_chain, monkeypatch):
    captured = {}

    def token(client_id, secret, redirect, code, verifier):
        captured.update(client_id=client_id, redirect=redirect, code=code, verifier=verifier)
        return {"access_token": "ms", "refresh_token": "refresh"}

    monkeypatch.setattr(auth.ms, "get_authorization_token", token)
    browser, seen = fake_browser(lambda q: {"code": "the-code", "state": q["state"][0]})
    a = auth.login(AppConfig(azure_client_id=CLIENT_ID), open_browser=browser, timeout=10)
    assert a.name == "Steve"
    q = parse_qs(urlparse(seen["url"]).query)
    assert q["code_challenge_method"] == ["S256"], "PKCE is used"
    assert q["client_id"] == [CLIENT_ID]
    assert captured["code"] == "the-code"
    assert captured["redirect"] == q["redirect_uri"][0] and captured["redirect"].startswith("http://localhost:")
    assert captured["verifier"]


def test_cancel_in_browser(good_chain):
    browser, _ = fake_browser(lambda q: {"error": "access_denied", "state": q["state"][0]})
    with pytest.raises(LoginError) as e:
        auth.login(AppConfig(azure_client_id=CLIENT_ID), open_browser=browser, timeout=10)
    assert e.value.kind == "cancelled"


def test_wrong_state_is_rejected(good_chain, monkeypatch):
    monkeypatch.setattr(auth.ms, "get_authorization_token", lambda *a: pytest.fail("must not exchange the code"))
    browser, _ = fake_browser(lambda q: {"code": "x", "state": "someone-else"})
    with pytest.raises(LoginError) as e:
        auth.login(AppConfig(azure_client_id=CLIENT_ID), open_browser=browser, timeout=10)
    assert "didn't match" in e.value.message


def test_cancel_button_stops_waiting():
    cancelled = threading.Event()
    cancelled.set()
    with pytest.raises(LoginError) as e:
        auth.login(AppConfig(azure_client_id=CLIENT_ID), open_browser=lambda url: None, timeout=5, cancelled=cancelled)
    assert e.value.kind == "cancelled"


def test_timeout():
    with pytest.raises(LoginError) as e:
        auth.login(AppConfig(azure_client_id=CLIENT_ID), open_browser=lambda url: None, timeout=0.5)
    assert e.value.kind == "timeout"


# ------------------------------------------------------------------------------------------------ token store

def test_token_round_trip_with_chunks():
    backend = MemoryBackend(max_len=1280)
    store = TokenStore(backend)
    token = "M.C5" + "x" * 3500
    store.save_refresh_token(token)
    assert store.load_refresh_token() == token
    assert backend.get_password("HollowClient", "refresh_token.count") == "4"


def test_token_clear_and_overwrite():
    backend = MemoryBackend()
    store = TokenStore(backend)
    store.save_refresh_token("a" * 2500)
    store.save_refresh_token("short")
    assert store.load_refresh_token() == "short"
    assert ("HollowClient", "refresh_token.2") not in backend.data, "old chunks are removed"
    store.clear()
    assert store.load_refresh_token() is None
    assert backend.data == {}


def test_partial_token_is_ignored():
    backend = MemoryBackend()
    store = TokenStore(backend)
    store.save_refresh_token("b" * 2500)
    del backend.data[("HollowClient", "refresh_token.1")]
    assert store.load_refresh_token() is None
    backend.data[("HollowClient", "refresh_token.count")] = "lots"
    assert store.load_refresh_token() is None


def test_store_survives_a_failing_backend():
    class Broken(MemoryBackend):
        def get_password(self, service, username):
            raise RuntimeError("locked")

    assert TokenStore(Broken()).load_refresh_token() is None
