// ─── Shared ───────────────────────────────────────────────────────────────────

export interface ErrorResponse {
  code: string;
  message: string;
  trace_id: string;
  details?: Record<string, unknown>;
}

// ─── Snapshots ────────────────────────────────────────────────────────────────

export type Environment = "dev" | "staging" | "production" | "ci" | "local";
export type SnapshotTrend = "faster" | "slower" | "stable" | "baseline";

export interface SnapshotSummary {
  snapshot_id: string;
  environment: Environment;
  total_startup_ms: number;
  bean_count: number;
  bottleneck_count: number;
  git_commit_sha: string;
  captured_at: string;
  trend: SnapshotTrend;
}

export interface SnapshotListResponse {
  data: SnapshotSummary[];
  cursor: string | null;
  total: number;
}

// ─── Timeline ────────────────────────────────────────────────────────────────

export interface PhaseBreakdown {
  phase_name: string;
  duration_ms: number;
  start_ms: number;
  percentage_of_total: number;
}

export interface BeanTiming {
  bean_name: string;
  class_name: string;
  duration_ms: number;
  start_ms: number;
  is_bottleneck: boolean;
  dependencies: string[];
}

export interface AutoconfigSummary {
  class_name: string;
  matched: boolean;
  duration_ms: number;
  recommendation: string | null;
}

export interface TimelineResponse {
  snapshot_id: string;
  total_startup_ms: number;
  phases: PhaseBreakdown[];
  beans: BeanTiming[];
  autoconfigurations: AutoconfigSummary[];
}

// ─── Bean Graph ───────────────────────────────────────────────────────────────

export interface BeanGraphNode {
  id: string;
  label: string;
  class_name: string;
  duration_ms: number;
  is_bottleneck: boolean;
}

export interface BeanGraphEdge {
  source: string;
  target: string;
}

export interface BeanGraphResponse {
  nodes: BeanGraphNode[];
  edges: BeanGraphEdge[];
}

// ─── Comparison ──────────────────────────────────────────────────────────────

export interface ChangedBean {
  bean_name: string;
  baseline_ms: number;
  target_ms: number;
  delta_ms: number;
  delta_percent: number;
}

export interface SnapshotComparisonResponse {
  baseline_snapshot_id: string;
  target_snapshot_id: string;
  total_delta_ms: number;
  added_beans: BeanTiming[];
  removed_beans: BeanTiming[];
  changed_beans: ChangedBean[];
}

// ─── Workspace Overview ───────────────────────────────────────────────────────

export type ProjectTrend7d = "improving" | "degrading" | "stable" | "no_data";

export interface ProjectOverview {
  project_id: string;
  project_name: string;
  latest_startup_ms: number;
  trend_7d: ProjectTrend7d;
  last_seen: string;
}

export interface WorkspaceOverviewResponse {
  workspace_id: string;
  project_count: number;
  projects: ProjectOverview[];
}

// ─── Recommendations ─────────────────────────────────────────────────────────

export type RecommendationCategory =
  | "lazy_loading"
  | "aot_compilation"
  | "graalvm_native"
  | "classpath_optimization"
  | "dependency_removal";

export type RecommendationEffort = "low" | "medium" | "high";
export type RecommendationStatus = "active" | "applied" | "wont_fix";

export interface GraalvmFeasibility {
  feasible: boolean;
  blockers: string[];
  estimated_native_startup_ms: number | null;
}

export interface Recommendation {
  recommendation_id: string;
  rank: number;
  category: RecommendationCategory;
  title: string;
  description: string;
  estimated_savings_ms: number;
  estimated_savings_percent: number;
  effort: RecommendationEffort;
  status: RecommendationStatus;
  code_snippet: string | null;
  config_snippet: string | null;
  warnings: string[];
  affected_beans: string[];
  graalvm_feasibility: GraalvmFeasibility | null;
  applied_at: string | null;
}

export interface RecommendationListResponse {
  snapshot_id: string;
  generated_at: string;
  is_stale: boolean;
  total_potential_savings_ms: number;
  recommendations: Recommendation[];
}

export interface UpdateRecommendationStatusRequest {
  status: RecommendationStatus;
  note?: string;
}

// ─── CI Budgets ──────────────────────────────────────────────────────────────

export interface CiBudget {
  budget_id: string;
  environment: string;
  budget_ms: number;
  alert_threshold_ms: number;
  enabled: boolean;
  created_by: string;
  updated_at: string;
}

export interface CiBudgetRequest {
  environment: "dev" | "staging" | "production" | "ci";
  budget_ms: number;
  alert_threshold_ms?: number | null;
  enabled: boolean;
}

export interface CiBudgetListResponse {
  budgets: CiBudget[];
}

// ─── Ingestion (Status/Budget Check) ─────────────────────────────────────────

export type SnapshotAnalysisStatus = "queued" | "processing" | "complete" | "failed";

export interface SnapshotStatusResponse {
  snapshot_id: string;
  status: SnapshotAnalysisStatus;
  total_startup_ms: number;
  bottleneck_count: number;
  recommendation_count: number;
  completed_at: string;
}

export interface BudgetCheckResponse {
  within_budget: boolean;
  actual_ms: number;
  budget_ms: number;
}

export interface BudgetExceededResponse extends ErrorResponse {
  actual_ms: number;
  budget_ms: number;
  excess_ms: number;
  top_bottlenecks: Array<{ bean_name: string; duration_ms: number }>;
}
