-- SpringLens Recommendation Service Schema
-- V003: recommendations, ci_budgets, webhook_configs, delivery_log

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ── RECOMMENDATIONS ──────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS recommendations (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    snapshot_id                 UUID NOT NULL,
    workspace_id                UUID NOT NULL,      -- RLS column
    project_id                  UUID NOT NULL,
    environment_name            TEXT NOT NULL,
    rank                        INT NOT NULL CHECK (rank > 0),
    category                    TEXT NOT NULL CHECK (category IN (
                                    'lazy_loading', 'aot_compilation', 'graalvm_native',
                                    'classpath_optimization', 'dependency_removal')),
    title                       TEXT NOT NULL,
    description                 TEXT NOT NULL,
    estimated_savings_ms        INT NOT NULL CHECK (estimated_savings_ms >= 0),
    estimated_savings_percent   FLOAT NOT NULL CHECK (estimated_savings_percent BETWEEN 0 AND 100),
    effort                      TEXT NOT NULL CHECK (effort IN ('low', 'medium', 'high')),
    status                      TEXT NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'applied', 'wont_fix')),
    code_snippet                TEXT,
    config_snippet              TEXT,
    warnings                    JSONB NOT NULL DEFAULT '[]',
    affected_beans              JSONB NOT NULL DEFAULT '[]',
    graalvm_feasibility         JSONB,
    applied_note                TEXT,
    applied_at                  TIMESTAMPTZ,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT recommendations_snapshot_rank_unique UNIQUE (snapshot_id, rank)
);

ALTER TABLE recommendations ENABLE ROW LEVEL SECURITY;
CREATE POLICY recommendations_tenant_isolation ON recommendations
    USING (workspace_id = current_setting('app.current_workspace_id', true)::uuid);

CREATE INDEX IF NOT EXISTS idx_recs_project_env_status
    ON recommendations (project_id, environment_name, status, rank)
    WHERE status = 'active';

CREATE INDEX IF NOT EXISTS idx_recs_snapshot ON recommendations (snapshot_id);

-- ── CI BUDGETS ────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS ci_budgets (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id          UUID NOT NULL,
    workspace_id        UUID NOT NULL,
    environment         TEXT NOT NULL CHECK (environment IN ('dev', 'staging', 'production', 'ci', 'local')),
    budget_ms           INT NOT NULL CHECK (budget_ms BETWEEN 100 AND 300000),
    alert_threshold_ms  INT CHECK (alert_threshold_ms BETWEEN 100 AND 300000),
    enabled             BOOLEAN NOT NULL DEFAULT TRUE,
    created_by          UUID NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT ci_budgets_project_env_unique UNIQUE (project_id, environment),
    CONSTRAINT ci_budgets_alert_lt_budget CHECK (
        alert_threshold_ms IS NULL OR alert_threshold_ms < budget_ms
    )
);

ALTER TABLE ci_budgets ENABLE ROW LEVEL SECURITY;
CREATE POLICY ci_budgets_tenant_isolation ON ci_budgets
    USING (workspace_id = current_setting('app.current_workspace_id', true)::uuid);

CREATE INDEX IF NOT EXISTS idx_ci_budgets_project ON ci_budgets (project_id) WHERE enabled = TRUE;
