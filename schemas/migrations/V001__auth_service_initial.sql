-- SpringLens Auth Service — Initial Schema
-- V001: Users, Workspaces, Members, Projects, Environments, API Keys, Subscriptions
-- Flyway migration — idempotent by design (CREATE TABLE IF NOT EXISTS)

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- Enable Row-Level Security helper function
CREATE OR REPLACE FUNCTION current_workspace_id() RETURNS uuid AS $$
  SELECT current_setting('app.current_workspace_id', true)::uuid
$$ LANGUAGE sql STABLE;

-- ── USERS ────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS users (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email               TEXT NOT NULL,
    password_hash       TEXT,           -- NULL for OAuth-only accounts
    display_name        TEXT NOT NULL,
    avatar_url          TEXT,
    github_id           TEXT,
    email_verified_at   TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at          TIMESTAMPTZ,

    CONSTRAINT users_email_unique UNIQUE (email),
    CONSTRAINT users_github_id_unique UNIQUE (github_id)
);

CREATE INDEX IF NOT EXISTS idx_users_email ON users (email) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_users_github_id ON users (github_id) WHERE github_id IS NOT NULL;

-- ── WORKSPACES ───────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS workspaces (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name                    TEXT NOT NULL,
    slug                    TEXT NOT NULL,
    plan                    TEXT NOT NULL DEFAULT 'free' CHECK (plan IN ('free', 'pro', 'enterprise')),
    stripe_customer_id      TEXT,
    stripe_subscription_id  TEXT,
    plan_project_limit      INT NOT NULL DEFAULT 1,
    plan_member_limit       INT NOT NULL DEFAULT 1,
    plan_environment_limit  INT NOT NULL DEFAULT 1,
    plan_history_days       INT NOT NULL DEFAULT 90,
    sso_enabled             BOOLEAN NOT NULL DEFAULT FALSE,
    saml_metadata_url       TEXT,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at              TIMESTAMPTZ,

    CONSTRAINT workspaces_slug_unique UNIQUE (slug)
);

CREATE INDEX IF NOT EXISTS idx_workspaces_stripe_customer ON workspaces (stripe_customer_id)
    WHERE stripe_customer_id IS NOT NULL;

-- ── WORKSPACE MEMBERS ────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS workspace_members (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id    UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role            TEXT NOT NULL DEFAULT 'developer' CHECK (role IN ('admin', 'developer', 'viewer')),
    joined_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ,

    CONSTRAINT workspace_members_unique UNIQUE (workspace_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_wm_workspace ON workspace_members (workspace_id) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_wm_user ON workspace_members (user_id) WHERE deleted_at IS NULL;

-- ── PROJECTS ─────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS projects (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id    UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    name            TEXT NOT NULL,
    slug            TEXT NOT NULL,
    description     TEXT,
    repo_url        TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ,

    CONSTRAINT projects_workspace_slug_unique UNIQUE (workspace_id, slug)
);

-- Row-Level Security on projects
ALTER TABLE projects ENABLE ROW LEVEL SECURITY;
CREATE POLICY projects_tenant_isolation ON projects
    USING (workspace_id = current_workspace_id());

CREATE INDEX IF NOT EXISTS idx_projects_workspace ON projects (workspace_id) WHERE deleted_at IS NULL;

-- ── ENVIRONMENTS ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS environments (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id      UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    workspace_id    UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    name            TEXT NOT NULL CHECK (name IN ('dev', 'staging', 'production', 'ci', 'local')),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT environments_project_name_unique UNIQUE (project_id, name)
);

ALTER TABLE environments ENABLE ROW LEVEL SECURITY;
CREATE POLICY environments_tenant_isolation ON environments
    USING (workspace_id = current_workspace_id());

-- ── API KEYS ─────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS api_keys (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id    UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    project_id      UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    name            TEXT NOT NULL,
    key_hash        TEXT NOT NULL,          -- bcrypt hash of full key
    key_prefix      TEXT NOT NULL,          -- first 8 chars (display only)
    last_used_at    TIMESTAMPTZ,
    expires_at      TIMESTAMPTZ,
    revoked_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by      UUID NOT NULL REFERENCES users(id)
);

ALTER TABLE api_keys ENABLE ROW LEVEL SECURITY;
CREATE POLICY api_keys_tenant_isolation ON api_keys
    USING (workspace_id = current_workspace_id());

CREATE INDEX IF NOT EXISTS idx_api_keys_project ON api_keys (project_id)
    WHERE revoked_at IS NULL AND (expires_at IS NULL OR expires_at > NOW());

-- ── SUBSCRIPTIONS ────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS subscriptions (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id            UUID NOT NULL REFERENCES workspaces(id),
    stripe_subscription_id  TEXT NOT NULL,
    status                  TEXT NOT NULL CHECK (status IN ('active', 'past_due', 'canceled', 'trialing')),
    plan                    TEXT NOT NULL CHECK (plan IN ('free', 'pro', 'enterprise')),
    amount_cents            INT NOT NULL DEFAULT 0,
    current_period_start    TIMESTAMPTZ NOT NULL,
    current_period_end      TIMESTAMPTZ NOT NULL,
    canceled_at             TIMESTAMPTZ,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT subscriptions_stripe_id_unique UNIQUE (stripe_subscription_id)
);

CREATE INDEX IF NOT EXISTS idx_subscriptions_workspace ON subscriptions (workspace_id);
