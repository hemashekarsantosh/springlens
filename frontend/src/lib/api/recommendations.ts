import { apiClient } from "./client";
import type {
  RecommendationListResponse,
  Recommendation,
  UpdateRecommendationStatusRequest,
  RecommendationCategory,
  RecommendationStatus,
  CiBudgetListResponse,
  CiBudget,
  CiBudgetRequest,
} from "@/types/api";

// ─── Query key factories ──────────────────────────────────────────────────────

export const recommendationKeys = {
  list: (
    projectId: string,
    params?: { environment?: string; category?: RecommendationCategory; status?: RecommendationStatus }
  ) => ["recommendations", projectId, params] as const,
  ciBudgets: (projectId: string) => ["ci-budgets", projectId] as const,
};

// ─── API functions ────────────────────────────────────────────────────────────

export interface GetRecommendationsParams {
  environment?: string;
  category?: RecommendationCategory;
  status?: RecommendationStatus;
}

export async function getRecommendations(
  projectId: string,
  params?: GetRecommendationsParams
): Promise<RecommendationListResponse> {
  const response = await apiClient.get<RecommendationListResponse>(
    `/projects/${projectId}/recommendations`,
    { params }
  );
  return response.data;
}

export async function updateRecommendationStatus(
  projectId: string,
  recommendationId: string,
  body: UpdateRecommendationStatusRequest
): Promise<Recommendation> {
  const response = await apiClient.patch<Recommendation>(
    `/projects/${projectId}/recommendations/${recommendationId}/status`,
    body
  );
  return response.data;
}

export async function listCiBudgets(projectId: string): Promise<CiBudgetListResponse> {
  const response = await apiClient.get<CiBudgetListResponse>(
    `/projects/${projectId}/ci-budgets`
  );
  return response.data;
}

export async function upsertCiBudget(
  projectId: string,
  body: CiBudgetRequest
): Promise<CiBudget> {
  const response = await apiClient.put<CiBudget>(
    `/projects/${projectId}/ci-budgets`,
    body
  );
  return response.data;
}
