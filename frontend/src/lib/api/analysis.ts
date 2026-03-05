import { apiClient } from "./client";
import type {
  SnapshotListResponse,
  TimelineResponse,
  BeanGraphResponse,
  SnapshotComparisonResponse,
  WorkspaceOverviewResponse,
  Environment,
} from "@/types/api";

// ─── Query key factories ──────────────────────────────────────────────────────

export const analysisKeys = {
  snapshots: (projectId: string, params?: Record<string, string | number>) =>
    ["snapshots", projectId, params] as const,
  timeline: (projectId: string, snapshotId: string, params?: Record<string, string | number>) =>
    ["timeline", projectId, snapshotId, params] as const,
  beanGraph: (projectId: string, snapshotId: string) =>
    ["bean-graph", projectId, snapshotId] as const,
  compare: (projectId: string, baselineId: string, targetId: string) =>
    ["compare", projectId, baselineId, targetId] as const,
  workspaceOverview: (workspaceId: string) =>
    ["workspace-overview", workspaceId] as const,
};

// ─── API functions ────────────────────────────────────────────────────────────

export interface ListSnapshotsParams {
  environment?: Environment;
  from?: string;
  to?: string;
  cursor?: string;
  limit?: number;
}

export async function listSnapshots(
  projectId: string,
  params?: ListSnapshotsParams
): Promise<SnapshotListResponse> {
  const response = await apiClient.get<SnapshotListResponse>(
    `/projects/${projectId}/snapshots`,
    { params }
  );
  return response.data;
}

export interface GetTimelineParams {
  min_duration_ms?: number;
  package_prefix?: string;
}

export async function getTimeline(
  projectId: string,
  snapshotId: string,
  params?: GetTimelineParams
): Promise<TimelineResponse> {
  const response = await apiClient.get<TimelineResponse>(
    `/projects/${projectId}/snapshots/${snapshotId}/timeline`,
    { params }
  );
  return response.data;
}

export async function getBeanGraph(
  projectId: string,
  snapshotId: string
): Promise<BeanGraphResponse> {
  const response = await apiClient.get<BeanGraphResponse>(
    `/projects/${projectId}/snapshots/${snapshotId}/bean-graph`
  );
  return response.data;
}

export async function compareSnapshots(
  projectId: string,
  baseline: string,
  target: string
): Promise<SnapshotComparisonResponse> {
  const response = await apiClient.get<SnapshotComparisonResponse>(
    `/projects/${projectId}/compare`,
    { params: { baseline, target } }
  );
  return response.data;
}

export async function getWorkspaceOverview(
  workspaceId: string
): Promise<WorkspaceOverviewResponse> {
  const response = await apiClient.get<WorkspaceOverviewResponse>(
    `/workspaces/${workspaceId}/overview`
  );
  return response.data;
}
