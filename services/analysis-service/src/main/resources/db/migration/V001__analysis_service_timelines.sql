-- SpringLens Analysis Service — Timeline Schema
-- V001: startup_timelines table

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE IF NOT EXISTS startup_timelines (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    snapshot_id         UUID NOT NULL,
    workspace_id        UUID NOT NULL,
    project_id          UUID NOT NULL,
    environment_name    TEXT NOT NULL,
    git_commit_sha      TEXT NOT NULL,
    total_startup_ms    INT NOT NULL CHECK (total_startup_ms >= 0),
    bottleneck_count    INT NOT NULL DEFAULT 0,
    bean_count          INT NOT NULL DEFAULT 0,
    timeline_data       JSONB NOT NULL DEFAULT '{}',
    bean_graph_data     JSONB NOT NULL DEFAULT '{}',
    analyzed_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT startup_timelines_snapshot_unique UNIQUE (snapshot_id)
);

ALTER TABLE startup_timelines ENABLE ROW LEVEL SECURITY;
CREATE POLICY timelines_tenant_isolation ON startup_timelines
    USING (workspace_id = current_setting('app.current_workspace_id', true)::uuid);

CREATE INDEX IF NOT EXISTS idx_timelines_project_env
    ON startup_timelines (project_id, environment_name, analyzed_at DESC);

CREATE INDEX IF NOT EXISTS idx_timelines_workspace
    ON startup_timelines (workspace_id, analyzed_at DESC);

CREATE INDEX IF NOT EXISTS idx_timelines_snapshot
    ON startup_timelines (snapshot_id);
