ALTER TABLE tokens DROP CONSTRAINT IF EXISTS tokens_user_id_key;

CREATE INDEX IF NOT EXISTS idx_tokens_user ON tokens(user_id);