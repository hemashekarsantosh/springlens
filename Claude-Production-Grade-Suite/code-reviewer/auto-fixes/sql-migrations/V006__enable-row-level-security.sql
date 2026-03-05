-- HIGH-001 Remediation: Add PostgreSQL Row-Level Security Policies
-- This migration enables RLS on all tenant-scoped tables for defense-in-depth

-- ============================================================================
-- INGESTION SERVICE TABLES
-- ============================================================================

-- Enable RLS on startup_snapshots
ALTER TABLE startup_snapshots ENABLE ROW LEVEL SECURITY;

-- Create policy for tenant isolation on snapshots
CREATE POLICY tenant_isolation_snapshots ON startup_snapshots
  USING (workspace_id = current_setting('app.current_workspace_id')::uuid);

CREATE POLICY tenant_isolation_snapshots_insert ON startup_snapshots
  WITH CHECK (workspace_id = current_setting('app.current_workspace_id')::uuid);

-- ============================================================================
-- ANALYSIS SERVICE TABLES
-- ============================================================================

-- Enable RLS on startup_timelines
ALTER TABLE startup_timelines ENABLE ROW LEVEL SECURITY;

-- Create policy for tenant isolation on timelines
CREATE POLICY tenant_isolation_timelines ON startup_timelines
  USING (workspace_id = current_setting('app.current_workspace_id')::uuid);

CREATE POLICY tenant_isolation_timelines_insert ON startup_timelines
  WITH CHECK (workspace_id = current_setting('app.current_workspace_id')::uuid);

-- ============================================================================
-- RECOMMENDATION SERVICE TABLES
-- ============================================================================

-- Enable RLS on recommendations
ALTER TABLE recommendations ENABLE ROW LEVEL SECURITY;

-- Create policy for tenant isolation on recommendations
CREATE POLICY tenant_isolation_recommendations ON recommendations
  USING (workspace_id = current_setting('app.current_workspace_id')::uuid);

CREATE POLICY tenant_isolation_recommendations_insert ON recommendations
  WITH CHECK (workspace_id = current_setting('app.current_workspace_id')::uuid);

-- Enable RLS on ci_budgets
ALTER TABLE ci_budgets ENABLE ROW LEVEL SECURITY;

-- Create policy for tenant isolation on CI budgets
CREATE POLICY tenant_isolation_ci_budgets ON ci_budgets
  USING (workspace_id = current_setting('app.current_workspace_id')::uuid);

CREATE POLICY tenant_isolation_ci_budgets_insert ON ci_budgets
  WITH CHECK (workspace_id = current_setting('app.current_workspace_id')::uuid);

-- ============================================================================
-- AUTH SERVICE TABLES
-- ============================================================================

-- Enable RLS on workspaces
ALTER TABLE workspaces ENABLE ROW LEVEL SECURITY;

-- Create policy for workspace isolation (users can only see their workspace)
CREATE POLICY tenant_isolation_workspaces ON workspaces
  USING (id = current_setting('app.current_workspace_id')::uuid);

-- Enable RLS on workspace_members
ALTER TABLE workspace_members ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_workspace_members ON workspace_members
  USING (workspace_id = current_setting('app.current_workspace_id')::uuid);

-- Enable RLS on projects
ALTER TABLE projects ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_projects ON projects
  USING (workspace_id = current_setting('app.current_workspace_id')::uuid);

CREATE POLICY tenant_isolation_projects_insert ON projects
  WITH CHECK (workspace_id = current_setting('app.current_workspace_id')::uuid);

-- ============================================================================
-- NOTIFICATION SERVICE TABLES
-- ============================================================================

-- Enable RLS on webhook_configs
ALTER TABLE webhook_configs ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_webhook_configs ON webhook_configs
  USING (workspace_id = current_setting('app.current_workspace_id')::uuid);

CREATE POLICY tenant_isolation_webhook_configs_insert ON webhook_configs
  WITH CHECK (workspace_id = current_setting('app.current_workspace_id')::uuid);

-- Enable RLS on delivery_logs
ALTER TABLE delivery_logs ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_delivery_logs ON delivery_logs
  USING (workspace_id = current_setting('app.current_workspace_id')::uuid);

-- ============================================================================
-- DEFENSE-IN-DEPTH NOTES
-- ============================================================================
--
-- These RLS policies provide a database-level defense layer against:
-- 1. Application-layer bugs (missing workspace_id filter in service code)
-- 2. SQL injection attacks that bypass parameterized queries
-- 3. Accidental data leakage via ad-hoc database queries
--
-- The policies enforce that every query on these tables is automatically
-- filtered by workspace_id, even if the application forgets to check it.
--
-- APPLICATION LAYER REQUIREMENT:
-- Before any database query, the Spring HandlerInterceptor MUST set:
--   connection.createStatement().execute(
--     "SET app.current_workspace_id = '" + workspaceId + "'"
--   );
--
-- See: services/ingestion-service/src/main/java/io/springlens/ingestion/config/TenantContextInterceptor.java
--
