-- Servidor de sincronización de Food You. Ver docs/sync/protocol.md.

PRAGMA journal_mode = WAL;

CREATE TABLE IF NOT EXISTS accounts (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    username      TEXT NOT NULL UNIQUE COLLATE NOCASE,
    password_hash TEXT NOT NULL,
    is_active     INTEGER NOT NULL DEFAULT 1,
    -- Último seq asignado en esta cuenta: cada cambio de un documento suma uno.
    seq           INTEGER NOT NULL DEFAULT 0,
    created_at    TEXT NOT NULL
);

-- Un documento por (cuenta, tipo, id). `fields` es JSON:
--   {"campo": {"value": ..., "clock": 1759484921337, "device": "a91f..."}}
CREATE TABLE IF NOT EXISTS documents (
    account_id  INTEGER NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    kind        TEXT NOT NULL,
    id          TEXT NOT NULL,
    fields      TEXT NOT NULL,
    deleted     INTEGER NOT NULL DEFAULT 0,
    seq         INTEGER NOT NULL,
    updated_at  TEXT NOT NULL,
    PRIMARY KEY (account_id, kind, id)
);

CREATE INDEX IF NOT EXISTS idx_documents_seq ON documents(account_id, seq);

-- Tokens de acceso sin contraseña: los de un dispositivo emparejado (el reloj) y los del
-- MCP. Se guarda el hash, nunca el token.
CREATE TABLE IF NOT EXISTS tokens (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    account_id    INTEGER NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    kind          TEXT NOT NULL,              -- 'device' | 'mcp'
    name          TEXT NOT NULL,
    token_hash    TEXT NOT NULL UNIQUE,
    created_at    TEXT NOT NULL,
    last_used_at  TEXT,
    revoked       INTEGER NOT NULL DEFAULT 0
);

-- Códigos de 6 cifras para emparejar un dispositivo desde la app. Caducan en minutos.
CREATE TABLE IF NOT EXISTS pairing_codes (
    code_hash   TEXT PRIMARY KEY,
    account_id  INTEGER NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    expires_at  TEXT NOT NULL,
    used        INTEGER NOT NULL DEFAULT 0
);

-- Qué dispositivos sincronizan cada cuenta, para el panel.
CREATE TABLE IF NOT EXISTS devices (
    account_id  INTEGER NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    device      TEXT NOT NULL,
    user_agent  TEXT,
    first_seen  TEXT NOT NULL,
    last_seen   TEXT NOT NULL,
    syncs       INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (account_id, device)
);

-- Una fila por sincronización: cuántos cambios mandó y cuántos documentos recibió.
CREATE TABLE IF NOT EXISTS sync_log (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    account_id  INTEGER NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    at          TEXT NOT NULL,
    device      TEXT,
    sent        INTEGER NOT NULL,
    received    INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_sync_log ON sync_log(account_id, at);

-- Sesiones del panel web.
CREATE TABLE IF NOT EXISTS admin_sessions (
    token_hash  TEXT PRIMARY KEY,
    account_id  INTEGER NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    expires_at  TEXT NOT NULL
);

-- OAuth para conectar el MCP desde claude.ai: los clientes se registran solos (RFC 7591)
-- y cada autorización deja un token MCP en `tokens` (con caducidad y token de refresco).
CREATE TABLE IF NOT EXISTS oauth_clients (
    client_id      TEXT PRIMARY KEY,
    secret_hash    TEXT,
    name           TEXT NOT NULL,
    redirect_uris  TEXT NOT NULL,             -- JSON
    created_at     TEXT NOT NULL
);

-- Códigos de autorización: valen una vez y unos minutos, atados a su PKCE.
CREATE TABLE IF NOT EXISTS oauth_codes (
    code_hash       TEXT PRIMARY KEY,
    client_id       TEXT NOT NULL,
    account_id      INTEGER NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    redirect_uri    TEXT NOT NULL,
    code_challenge  TEXT NOT NULL,
    expires_at      TEXT NOT NULL,
    used            INTEGER NOT NULL DEFAULT 0
);
