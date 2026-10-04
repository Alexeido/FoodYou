# Food You sync server

Reference implementation of the [Food You sync protocol](../docs/sync/protocol.md): it keeps a
person's diary in step across their devices. It is independent from the custom food database;
the app configures each one separately (Settings → Sync).

The server stores documents and merges them field by field, keeping the newest change. It does
not know what a meal is, which is what lets other clients (a watch app, an MCP server for an
assistant) read and write the same diary.

## Run it

```bash
python -m venv .venv
.venv/bin/pip install -r requirements.txt     # Windows: .venv\Scripts\pip
.venv/bin/python -m app.cli add <user>        # asks for a password
.venv/bin/uvicorn app.main:app --host 0.0.0.0 --port 8000
```

With Docker:

```bash
docker build -t foodyou-sync .
docker run -d --name foodyou-sync -p 8000:8000 -v "$PWD/data:/data" foodyou-sync
docker exec -it foodyou-sync python -m app.cli add <user>
```

Put it behind HTTPS (a reverse proxy or a tunnel): the app sends the password with every request.

## Accounts

```bash
python -m app.cli add <user>
python -m app.cli passwd <user>
python -m app.cli disable <user>    # the app gets 403; enable undoes it
python -m app.cli list
```

## Tests

```bash
pip install -r requirements-dev.txt
python -m pytest tests
```

The app has a contract test that runs its real client against this server; see
`app/src/androidUnitTest/.../sync/SyncContractTest.kt`.
