-- SpringLens Notification Service Schema
-- V001: webhook_configs, delivery_log

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE IF NOT EXISTS webhook_configs (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id    UUID NOT NULL,
    project_id      UUID,
    type            TEXT NOT NULL CHECK (type IN ('slack', 'pagerduty', 'github_pr')),
    url_encrypted   TEXT NOT NULL,
    filter_config   JSONB NOT NULL DEFAULT '{}',
    enabled         BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE webhook_configs ENABLE ROW LEVEL SECURITY;
CREATE POLICY webhook_configs_tenant_isolation ON webhook_configs
    USING (workspace_id = current_setting('app.current_workspace_id', true)::uuid);

CREATE TABLE IF NOT EXISTS delivery_log (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    webhook_config_id   UUID NOT NULL REFERENCES webhook_configs(id) ON DELETE CASCADE,
    snapshot_id         UUID NOT NULL,
    workspace_id        UUID NOT NULL,
    http_status         INT,
    error_message       TEXT,
    attempt_count       INT NOT NULL DEFAULT 1,
    next_retry_at       TIMESTAMPTZ,
    delivered_at        TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE delivery_log ENABLE ROW LEVEL SECURITY;
CREATE POLICY delivery_log_tenant_isolation ON delivery_log
    USING (workspace_id = current_setting('app.current_workspace_id', true)::uuid);

CREATE INDEX IF NOT EXISTS idx_delivery_log_retry
    ON delivery_log (next_retry_at)
    WHERE delivered_at IS NULL AND next_retry_at IS NOT NULL;
