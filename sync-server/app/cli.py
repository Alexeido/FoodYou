"""Gestión de cuentas desde la terminal.

    python -m app.cli add NOMBRE            (pide la contraseña)
    python -m app.cli passwd NOMBRE
    python -m app.cli disable NOMBRE
    python -m app.cli enable NOMBRE
    python -m app.cli list

En Docker: docker exec -it foodyou-sync python -m app.cli add NOMBRE
"""

from __future__ import annotations

import getpass
import sys

from app import accounts
from app.db import init_schema, transaction


def _password(args: list[str]) -> str:
    if "--password" in args:
        return args[args.index("--password") + 1]
    first = getpass.getpass("Contraseña: ")
    if first != getpass.getpass("Repítela: "):
        sys.exit("No coinciden")
    return first


def main(argv: list[str]) -> None:
    init_schema()
    if not argv:
        sys.exit(__doc__)
    command, rest = argv[0], argv[1:]
    with transaction() as conn:
        if command == "list":
            for r in conn.execute(
                "SELECT a.username, a.is_active, a.seq, COUNT(d.id) docs FROM accounts a "
                "LEFT JOIN documents d ON d.account_id = a.id GROUP BY a.id ORDER BY a.username"
            ):
                state = "activa" if r["is_active"] else "desactivada"
                print(f"{r['username']}: {state}, {r['docs']} documentos, seq {r['seq']}")
            return
        if not rest:
            sys.exit(__doc__)
        name = rest[0]
        try:
            if command == "add":
                accounts.create(conn, name, _password(rest))
                print(f"Cuenta {name} creada")
            elif command == "passwd":
                accounts.set_password(conn, name, _password(rest))
                print("Contraseña cambiada")
            elif command == "disable":
                accounts.set_active(conn, name, False)
                print(f"Cuenta {name} desactivada")
            elif command == "enable":
                accounts.set_active(conn, name, True)
                print(f"Cuenta {name} activada")
            else:
                sys.exit(__doc__)
        except accounts.AccountError as exc:
            sys.exit(str(exc))


if __name__ == "__main__":
    main(sys.argv[1:])
