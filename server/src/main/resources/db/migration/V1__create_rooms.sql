-- Salas sincronizadas. É a tabela que o servidor já criava sozinho antes de existirem migrações;
-- num banco que já a tem, o Flyway marca esta migração como aplicada sem rodá-la (ver Database.kt).
--
-- state: o RoomState inteiro em JSON (o mesmo formato do WebSocket). version repete a versão do
-- estado numa coluna para a gravação condicional. last_active_at decide a expiração (1 dia).
CREATE TABLE IF NOT EXISTS rooms (
    code           TEXT        PRIMARY KEY,
    state          JSONB       NOT NULL,
    version        BIGINT      NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL,
    last_active_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS rooms_last_active_at ON rooms (last_active_at);
