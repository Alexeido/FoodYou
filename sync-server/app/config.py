from __future__ import annotations

from functools import lru_cache
from pathlib import Path

from pydantic_settings import BaseSettings, SettingsConfigDict

SERVER_ROOT = Path(__file__).resolve().parent.parent


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=str(SERVER_ROOT / ".env"), env_file_encoding="utf-8", extra="ignore"
    )

    db_path: str = "data/sync.db"

    # Primer administrador del panel, si no hay ninguno. La contraseña es provisional: el
    # panel avisa hasta que se cambia.
    admin_username: str = "admin"
    admin_initial_password: str = "admin"
    admin_session_hours: int = 12
    display_timezone: str = "Europe/Madrid"

    # La dirección pública de este servidor: es el emisor de OAuth, y el MCP cuelga de /mcp
    # (Cloudflare manda esa ruta al contenedor del MCP).
    public_url: str = "https://sync.alexeido.com"
    # Cuánto vale un token dado por OAuth antes de que el cliente lo renueve.
    oauth_access_hours: int = 24

    @property
    def mcp_public_url(self) -> str:
        return self.public_url.rstrip("/") + "/mcp"

    # Avisos en vivo: cada cuánto se manda un latido para que nadie corte la conexión.
    events_keepalive_seconds: float = 25.0

    # Límites del protocolo (docs/sync/protocol.md)
    max_changes_per_request: int = 500
    max_documents_per_response: int = 500
    max_document_bytes: int = 256 * 1024
    max_body_bytes: int = 5 * 1024 * 1024
    # Un reloj adelantado no puede ganar todos los conflictos para siempre.
    max_clock_skew_ms: int = 5 * 60 * 1000

    @property
    def db_full_path(self) -> Path:
        path = Path(self.db_path)
        if not path.is_absolute():
            path = SERVER_ROOT / path
        path.parent.mkdir(parents=True, exist_ok=True)
        return path


@lru_cache
def get_settings() -> Settings:
    return Settings()
