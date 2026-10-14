"""Keeps the Microsoft refresh token in Windows Credential Manager (through `keyring`), never in a plain file.

Credential Manager limits how big one entry can be (2560 bytes, and keyring stores text as UTF-16), and Microsoft
refresh tokens can get long, so the token is split over a few numbered entries.
"""

from __future__ import annotations

import json
import sys
from typing import Any, Protocol

SERVICE = "HollowClient"
CHUNK = 1000
MAX_CHUNKS = 32


class Backend(Protocol):
    def get_password(self, service: str, username: str) -> str | None: ...

    def set_password(self, service: str, username: str, password: str) -> None: ...

    def delete_password(self, service: str, username: str) -> None: ...


def default_backend() -> Any:
    """keyring with its Windows backend chosen explicitly (backend discovery can fail inside a packaged exe)."""
    import keyring

    if sys.platform == "win32":
        try:
            from keyring.backends.Windows import WinVaultKeyring

            keyring.set_keyring(WinVaultKeyring())
        except Exception:  # pragma: no cover - only if the Windows backend can't load
            pass
    return keyring


class TokenStore:
    def __init__(self, backend: Any = None, service: str = SERVICE):
        self.backend = backend if backend is not None else default_backend()
        self.service = service

    def _get(self, key: str) -> str | None:
        try:
            return self.backend.get_password(self.service, key)
        except Exception:
            return None

    def _delete(self, key: str) -> None:
        try:
            self.backend.delete_password(self.service, key)
        except Exception:
            pass

    def save_refresh_token(self, token: str) -> None:
        chunks = [token[i:i + CHUNK] for i in range(0, len(token), CHUNK)] or [""]
        if len(chunks) > MAX_CHUNKS:
            raise ValueError("refresh token is unexpectedly long")
        self.clear()
        for i, part in enumerate(chunks):
            self.backend.set_password(self.service, f"refresh_token.{i}", part)
        # The count goes in last, so a half-written token never looks complete.
        self.backend.set_password(self.service, "refresh_token.count", str(len(chunks)))

    def load_refresh_token(self) -> str | None:
        count_text = self._get("refresh_token.count")
        if not count_text or not count_text.isdigit():
            return None
        count = int(count_text)
        if not 0 < count <= MAX_CHUNKS:
            return None
        parts = []
        for i in range(count):
            part = self._get(f"refresh_token.{i}")
            if part is None:
                return None
            parts.append(part)
        token = "".join(parts)
        return token or None

    def clear(self) -> None:
        count_text = self._get("refresh_token.count")
        self._delete("refresh_token.count")
        count = int(count_text) if count_text and count_text.isdigit() else MAX_CHUNKS
        for i in range(min(count, MAX_CHUNKS)):
            self._delete(f"refresh_token.{i}")


class MemoryBackend:
    """In-memory keyring for tests."""

    def __init__(self, max_len: int = 1280):
        self.data: dict[tuple[str, str], str] = {}
        self.max_len = max_len

    def get_password(self, service: str, username: str) -> str | None:
        return self.data.get((service, username))

    def set_password(self, service: str, username: str, password: str) -> None:
        if len(password) > self.max_len:
            raise ValueError("too long for one credential")
        self.data[(service, username)] = password

    def delete_password(self, service: str, username: str) -> None:
        if (service, username) not in self.data:
            raise KeyError(username)
        del self.data[(service, username)]

    def dump(self) -> str:
        return json.dumps({f"{k[0]}/{k[1]}": v for k, v in self.data.items()})
