-- Create audit_logs table for immutable forensics trail
-- ✅ SECURITY: Provides compliance-required audit trail for all sensitive operations

CREATE TABLE IF NOT EXISTS audit_logs (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    action VARCHAR(50) NOT NULL,
    resource_type VARCHAR(50) NOT NULL,
    resource_id UUID,
    changes TEXT,
    ip_address VARCHAR(45),
    user_agent VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    result VARCHAR(20) NOT NULL DEFAULT 'SUCCESS',
    error_message VARCHAR(500),
    CONSTRAINT chk_result CHECK (result IN ('SUCCESS', 'FAILURE'))
);

-- Indexes for audit trail queries
CREATE INDEX idx_audit_workspace_id ON audit_logs(workspace_id);
CREATE INDEX idx_audit_user_id ON audit_logs(user_id);
CREATE INDEX idx_audit_resource ON audit_logs(resource_type, resource_id);
CREATE INDEX idx_audit_action ON audit_logs(action);
CREATE INDEX idx_audit_created_at ON audit_logs(created_at DESC);

-- Composite index for common audit trail queries (workspace + date)
CREATE INDEX idx_audit_workspace_date ON audit_logs(workspace_id, created_at DESC);

-- Partition for large audit logs (optional, remove if not supported)
-- PARTITION BY RANGE (YEAR(created_at));

COMMENT ON TABLE audit_logs IS 'Immutable audit trail for forensics, compliance, and breach investigation';
COMMENT ON COLUMN audit_logs.workspace_id IS 'Multi-tenant isolation: every audit log belongs to a workspace';
COMMENT ON COLUMN audit_logs.result IS 'SUCCESS or FAILURE: allows filtering security incidents';
COMMENT ON COLUMN audit_logs.ip_address IS 'Client IP for forensic attribution (from X-Forwarded-For or remote_addr)';
