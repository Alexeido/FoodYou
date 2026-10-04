# Food You MCP server

An [MCP](https://modelcontextprotocol.io) server that gives an AI assistant (Claude, or any MCP
client) access to a person's Food You diary. It can do what the in-app assistant does:

- read days, totals and goals;
- search for foods and add, change or delete entries;
- plan a day in a draft;
- create and log recipes;
- remember things about the person;
- analyse what they eat.

Changes reach the person's phone and watch within seconds.

It talks to the [sync server](../sync-server/): it is just another device on the account, using the
same [protocol](../docs/sync/protocol.md). The sync server decides who may use it. Each account
needs MCP access turned on in the sync admin panel.

## Connecting

- **Claude Code.** Create an MCP token in the sync admin panel (open the account, then "Crear token
  MCP"), then run:
  ```bash
  claude mcp add --transport http foodyou https://<your-sync-host>/mcp \
      --header "Authorization: Bearer fy_mcp_..."
  ```
- **Claude apps (web, desktop, mobile).** Go to Settings → Connectors → Add custom connector and
  enter `https://<your-sync-host>/mcp`, then sign in with the sync account. The sync server acts as
  the OAuth authorization server (dynamic client registration, PKCE, refresh tokens).

The server is meant to be served under the sync host's `/mcp` path, with a reverse proxy sending
`/mcp` here and everything else to the sync server. That way the OAuth discovery URLs and the MCP
endpoint share one origin.

## Tools

| Area | Tools |
|---|---|
| Diary | `get_meals`, `get_day`, `get_goals`, `get_summary`, `get_totals` |
| Analysis | `top_foods`, `top_brands`, `nutrient_sources`, `meal_times`, `find_in_diary` |
| Adding | `search_food`, `add_food`, `add_foods`, `add_quick_entry` |
| Changing | `update_entry`, `set_eaten`, `delete_entries`, `undo_last`, `redo`, `recent_changes` |
| Planning | `plan_start`, `plan_add`, `plan_remove`, `plan_show`, `plan_commit`, `plan_discard` |
| Recipes | `get_recipes`, `get_recipe`, `create_recipe`, `delete_recipe` |
| Memory | `get_memory`, `remember`, `forget` (shared with the in-app assistant) |

`search_food` looks in the person's history first, then their recipes, then an optional custom food
database (`FOOD_URL`), and then Open Food Facts.

## Running

Configuration comes from environment variables; see [`.env.example`](.env.example):

- `SYNC_URL`: where the sync server is.
- `FOOD_URL`, `FOOD_USER`, `FOOD_PASSWORD`: the custom food database (optional).
- `ALLOWED_HOSTS`: the public host names, for DNS-rebinding protection.
- `PUBLIC_URL`: the public URL of the sync host.
- `TIMEZONE`.
- `OPEN_FOOD_FACTS`: set it to `0` to turn Open Food Facts off.

```bash
docker build -t foodyou-mcp .
docker run -d --name foodyou-mcp --env-file .env -v "$PWD/data:/app/data" foodyou-mcp
```

`data/` keeps the undo journal.

## Tests

```bash
pip install -r requirements-dev.txt
python -m pytest
```

The tests run the real sync server in-process (`../sync-server`), with a fake food database.
