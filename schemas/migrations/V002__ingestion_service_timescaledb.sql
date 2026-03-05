-- SpringLens Ingestion Service — TimescaleDB Schema
-- V002: startup_snapshots, bean_events, phase_events, autoconfiguration_events, idempotency_keys
-- Requires TimescaleDB extension

CREATE EXTENSION IF NOT EXISTS timescaledb;
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ── STARTUP SNAPSHOTS (TimescaleDB hypertable) ───────────────────────────────
CREATE TABLE IF NOT EXISTS startup_snapshots (
    id                  UUID NOT NULL DEFAULT gen_random_uuid(),
    workspace_id        UUID NOT NULL,      -- RLS column
    project_id          UUID NOT NULL,
    environment_id      UUID NOT NULL,
    environment_name    TEXT NOT NULL,
    git_commit_sha      TEXT NOT NULL CHECK (git_commit_sha ~ '^[0-9a-f]{40}$'),
    total_startup_ms    INT NOT NULL CHECK (total_startup_ms >= 0),
    spring_boot_version TEXT NOT NULL,
    java_version        TEXT NOT NULL,
    agent_version       TEXT NOT NULL,
    hostname            TEXT,
    status              TEXT NOT NULL DEFAULT 'queued'
                            CHECK (status IN ('queued', 'processing', 'complete', 'failed')),
    captured_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    processed_at        TIMESTAMPTZ,
    deleted_at          TIMESTAMPTZ,

    PRIMARY KEY (id, captured_at)   -- Partition key must be in PK for TimescaleDB
);

-- Convert to TimescaleDB hypertable partitioned by captured_at (weekly chunks)
SELECT create_hypertable('startup_snapshots', 'captured_at',
    chunk_time_interval => INTERVAL '1 week',
    if_not_exists => TRUE
);

-- Enable compression (chunks older than 7 days)
ALTER TABLE startup_snapshots SET (
    timescaledb.compress,
    timescaledb.compress_segmentby = 'workspace_id,project_id'
);
SELECT add_compression_policy('startup_snapshots', INTERVAL '7 days', if_not_exists => TRUE);

-- Row-Level Security
ALTER TABLE startup_snapshots ENABLE ROW LEVEL SECURITY;
CREATE POLICY snapshots_tenant_isolation ON startup_snapshots
    USING (workspace_id = current_setting('app.current_workspace_id', true)::uuid);

CREATE INDEX IF NOT EXISTS idx_snapshots_project_env ON startup_snapshots (project_id, environment_name, captured_at DESC)
    WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_snapshots_commit ON startup_snapshots (project_id, git_commit_sha, environment_name);

-- ── BEAN EVENTS (TimescaleDB hypertable) ────────────────────────────────────
CREATE TABLE IF NOT EXISTS bean_events (
    id              UUID NOT NULL DEFAULT gen_random_uuid(),
    snapshot_id     UUID NOT NULL,
    workspace_id    UUID NOT NULL,      -- RLS column
    bean_name       TEXT NOT NULL,
    class_name      TEXT NOT NULL,
    duration_ms     INT NOT NULL CHECK (duration_ms >= 0),
    start_ms        INT NOT NULL CHECK (start_ms >= 0),
    context_id      TEXT NOT NULL DEFAULT 'default',
    dependency_names JSONB NOT NULL DEFAULT '[]',
    is_bottleneck   BOOLEAN NOT NULL DEFAULT FALSE,
    captured_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    PRIMARY KEY (id, captured_at)
);

SELECT create_hypertable('bean_events', 'captured_at',
    chunk_time_interval => INTERVAL '1 week',
    if_not_exists => TRUE
);

ALTER TABLE bean_events SET (
    timescaledb.compress,
    timescaledb.compress_segmentby = 'workspace_id,snapshot_id'
);
SELECT add_compression_policy('bean_events', INTERVAL '7 days', if_not_exists => TRUE);

ALTER TABLE bean_events ENABLE ROW LEVEL SECURITY;
CREATE POLICY bean_events_tenant_isolation ON bean_events
    USING (workspace_id = current_setting('app.current_workspace_id', true)::uuid);

CREATE INDEX IF NOT EXISTS idx_bean_events_snapshot ON bean_events (snapshot_id, duration_ms DESC);
CREATE INDEX IF NOT EXISTS idx_bean_events_bottleneck ON bean_events (snapshot_id) WHERE is_bottleneck = TRUE;

-- ── PHASE EVENTS ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS phase_events (
    id                  UUID NOT NULL DEFAULT gen_random_uuid(),
    snapshot_id         UUID NOT NULL,
    workspace_id        UUID NOT NULL,
    phase_name          TEXT NOT NULL CHECK (phase_name IN (
                            'context_refresh', 'bean_post_processors',
                            'application_listeners', 'context_loaded', 'started')),
    duration_ms         INT NOT NULL CHECK (duration_ms >= 0),
    start_ms            INT NOT NULL CHECK (start_ms >= 0),
    percentage_of_total FLOAT NOT NULL CHECK (percentage_of_total BETWEEN 0 AND 100),
    captured_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    PRIMARY KEY (id, captured_at)
);

SELECT create_hypertable('phase_events', 'captured_at',
    chunk_time_interval => INTERVAL '1 week',
    if_not_exists => TRUE
);

ALTER TABLE phase_events ENABLE ROW LEVEL SECURITY;
CREATE POLICY phase_events_tenant_isolation ON phase_events
    USING (workspace_id = current_setting('app.current_workspace_id', true)::uuid);

CREATE INDEX IF NOT EXISTS idx_phase_events_snapshot ON phase_events (snapshot_id);

-- ── AUTOCONFIGURATION EVENTS ─────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS autoconfiguration_events (
    id                      UUID NOT NULL DEFAULT gen_random_uuid(),
    snapshot_id             UUID NOT NULL,
    workspace_id            UUID NOT NULL,
    class_name              TEXT NOT NULL,
    matched                 BOOLEAN NOT NULL,
    duration_ms             INT NOT NULL DEFAULT 0,
    condition_evaluation_ms INT NOT NULL DEFAULT 0,
    captured_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    PRIMARY KEY (id, captured_at)
);

SELECT create_hypertable('autoconfiguration_events', 'captured_at',
    chunk_time_interval => INTERVAL '1 week',
    if_not_exists => TRUE
);

ALTER TABLE autoconfiguration_events ENABLE ROW LEVEL SECURITY;
CREATE POLICY autoconfig_tenant_isolation ON autoconfiguration_events
    USING (workspace_id = current_setting('app.current_workspace_id', true)::uuid);

CREATE INDEX IF NOT EXISTS idx_autoconfig_snapshot ON autoconfiguration_events (snapshot_id, matched);

-- ── IDEMPOTENCY KEYS (standard table — short-lived, not time-series) ──────────
CREATE TABLE IF NOT EXISTS idempotency_keys (
    key         TEXT PRIMARY KEY,       -- "project_id:env:commit_sha"
    snapshot_id UUID NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL DEFAULT NOW() + INTERVAL '60 seconds'
);

CREATE INDEX IF NOT EXISTS idx_idempotency_expires ON idempotency_keys (expires_at);

-- Auto-cleanup expired keys (pg_cron or application-level cleanup)
-- Application deletes expired keys on read (lazy) and via scheduled job hourly
