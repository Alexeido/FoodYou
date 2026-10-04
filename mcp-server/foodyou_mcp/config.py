"""Configuración por variables de entorno (en Docker, el .env del servicio)."""

from __future__ import annotations

import os
from dataclasses import dataclass, field
from pathlib import Path


@dataclass(frozen=True)
class Config:
    # El servidor de sincronización: es quien guarda el diario y quien decide si un token
    # tiene acceso MCP. Dentro de la red de Docker, por su nombre de servicio.
    sync_url: str = field(default_factory=lambda: os.environ.get("SYNC_URL", "http://sync:8000"))
    # El servidor de alimentos, para buscar qué añadir. Opcional: sin él, solo se puede
    # añadir lo que ya está en el historial del diario o entradas rápidas por macros.
    food_url: str = field(default_factory=lambda: os.environ.get("FOOD_URL", ""))
    food_user: str = field(default_factory=lambda: os.environ.get("FOOD_USER", ""))
    food_password: str = field(default_factory=lambda: os.environ.get("FOOD_PASSWORD", ""))
    # Buscar también en Open Food Facts cuando la base de datos propia no tiene algo.
    open_food_facts: bool = field(default_factory=lambda: os.environ.get("OPEN_FOOD_FACTS", "1") not in ("0", "false", ""))
    timezone: str = field(default_factory=lambda: os.environ.get("TIMEZONE", "Europe/Madrid"))
    data_dir: Path = field(default_factory=lambda: Path(os.environ.get("DATA_DIR", "data")))
    # Dirección pública del servidor de sincronización, que es quien autoriza (OAuth): se
    # anuncia en el 401 para que claude.ai sepa dónde iniciar sesión.
    public_url: str = field(default_factory=lambda: os.environ.get("PUBLIC_URL", "https://sync.alexeido.com"))
    # Nombres con los que se llega al servidor (protección contra DNS rebinding del SDK).
    allowed_hosts: tuple[str, ...] = field(
        default_factory=lambda: tuple(
            h.strip()
            for h in os.environ.get("ALLOWED_HOSTS", "sync.alexeido.com,localhost,127.0.0.1").split(",")
            if h.strip()
        )
    )


def load() -> Config:
    return Config()
