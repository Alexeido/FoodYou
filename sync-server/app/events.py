"""Avisos en vivo: cuando una cuenta cambia, cada conexión abierta de esa cuenta se entera
al momento y la app sincroniza, en vez de esperar a su siguiente vuelta.

Vive en memoria del proceso (un solo worker, como el resto del servidor). Si el servidor
se reinicia, las apps se reconectan solas y siguen sincronizando por su cuenta.
"""

from __future__ import annotations

import asyncio
from collections import defaultdict


class Notifier:
    def __init__(self) -> None:
        self._versions: dict[int, int] = defaultdict(int)
        self._condition: asyncio.Condition | None = None

    def _cond(self) -> asyncio.Condition:
        if self._condition is None:
            self._condition = asyncio.Condition()
        return self._condition

    def version(self, account_id: int) -> int:
        return self._versions[account_id]

    async def notify(self, account_id: int) -> None:
        cond = self._cond()
        async with cond:
            self._versions[account_id] += 1
            cond.notify_all()

    async def wait(self, account_id: int, seen: int, timeout: float) -> bool:
        """True si la cuenta cambió después de `seen`; False si pasó el tiempo."""
        cond = self._cond()
        async with cond:
            try:
                await asyncio.wait_for(
                    cond.wait_for(lambda: self._versions[account_id] != seen), timeout
                )
                return True
            except asyncio.TimeoutError:
                return False


notifier = Notifier()
