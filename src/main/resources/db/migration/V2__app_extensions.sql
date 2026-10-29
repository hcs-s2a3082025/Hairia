ALTER TABLE hairdressers ADD COLUMN gender VARCHAR(20), ADD COLUMN avatar_uri TEXT;
ALTER TABLE customers
    ADD COLUMN gender VARCHAR(20),
    ADD COLUMN avatar_uri TEXT,
    ADD COLUMN hair_note VARCHAR(100),
    ADD COLUMN consent_share BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN consent_ai BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN consent_external BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE treatment_histories ADD COLUMN product_note TEXT;
ALTER TABLE charts ADD COLUMN internal_memo TEXT;

CREATE TABLE auth_sessions (
    token_hash VARCHAR(64) PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users (user_id),
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_auth_sessions_user_id ON auth_sessions (user_id);

CREATE TABLE audit_logs (
    audit_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users (user_id),
    action_text TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_audit_logs_user_created ON audit_logs (user_id, audit_id DESC);

INSERT INTO questionnaires (salon_id, question_text, question_type) VALUES
    (NULL, '色落ちが気になりますか', 'FADE'),
    (NULL, '広がりが気になりますか', 'VOLUME'),
    (NULL, '手触りはどうですか', 'TEXTURE'),
    (NULL, 'ダメージの自覚（1〜5）', 'DAMAGE'),
    (NULL, 'ホームケアの状況', 'HOMECARE'),
    (NULL, '次回の希望', 'WISH');
