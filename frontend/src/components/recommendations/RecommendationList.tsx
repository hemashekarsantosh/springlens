"use client";

import React, { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { AlertTriangle, TrendingDown } from "lucide-react";
import { getRecommendations, recommendationKeys } from "@/lib/api/recommendations";
import type { RecommendationCategory, RecommendationStatus } from "@/types/api";
import { RecommendationCard } from "./RecommendationCard";
import { Skeleton } from "@/components/ui/skeleton";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Label } from "@/components/ui/label";
import { Badge } from "@/components/ui/badge";
import { formatMs, formatDateTime } from "@/lib/utils";

interface RecommendationListProps {
  projectId: string;
  environment?: string;
}

export function RecommendationList({ projectId, environment }: RecommendationListProps) {
  const [categoryFilter, setCategoryFilter] = useState<RecommendationCategory | "all">("all");
  const [statusFilter, setStatusFilter] = useState<RecommendationStatus | "all">("all");

  const params = {
    environment,
    category: categoryFilter !== "all" ? categoryFilter : undefined,
    status: statusFilter !== "all" ? statusFilter : undefined,
  };

  const { data, isLoading, isError, error } = useQuery({
    queryKey: recommendationKeys.list(projectId, params),
    queryFn: () => getRecommendations(projectId, params),
  });

  if (isLoading) {
    return (
      <div className="space-y-4" aria-busy="true" aria-label="Loading recommendations">
        {Array.from({ length: 3 }).map((_, i) => (
          <Skeleton key={i} className="h-40 w-full rounded-lg" />
        ))}
      </div>
    );
  }

  if (isError) {
    return (
      <div className="rounded-lg border border-red-200 bg-red-50 p-6 text-center" role="alert">
        <p className="font-semibold text-red-800">Failed to load recommendations</p>
        <p className="mt-1 text-sm text-red-600">{(error as Error).message}</p>
      </div>
    );
  }

  if (!data) return null;

  const filtered = data.recommendations.filter((r) => {
    if (categoryFilter !== "all" && r.category !== categoryFilter) return false;
    if (statusFilter !== "all" && r.status !== statusFilter) return false;
    return true;
  });

  return (
    <div className="space-y-4">
      {/* Header stats */}
      <div className="flex flex-wrap items-center justify-between gap-3 rounded-lg border bg-white p-4">
        <div className="flex items-center gap-3">
          <TrendingDown className="h-5 w-5 text-green-600" aria-hidden="true" />
          <div>
            <p className="text-sm font-medium text-gray-900">
              {data.recommendations.length} recommendations
            </p>
            <p className="text-xs text-gray-500">
              Up to {formatMs(data.total_potential_savings_ms)} potential savings
            </p>
          </div>
        </div>

        {data.is_stale && (
          <div className="flex items-center gap-1.5 rounded-md bg-yellow-50 px-3 py-1.5 text-xs text-yellow-800" role="alert">
            <AlertTriangle className="h-3.5 w-3.5" aria-hidden="true" />
            Recommendations based on data older than 24h
          </div>
        )}

        <p className="text-xs text-gray-400">
          Generated {formatDateTime(data.generated_at)}
        </p>
      </div>

      {/* Filters */}
      <div className="flex flex-wrap gap-4 rounded-lg border bg-white p-4">
        <div className="flex flex-col gap-1.5">
          <Label htmlFor="cat-filter" className="text-xs text-gray-600">Category</Label>
          <Select
            value={categoryFilter}
            onValueChange={(v) => setCategoryFilter(v as RecommendationCategory | "all")}
          >
            <SelectTrigger id="cat-filter" className="h-8 w-48 text-sm" aria-label="Filter by category">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="all">All Categories</SelectItem>
              <SelectItem value="lazy_loading">Lazy Loading</SelectItem>
              <SelectItem value="aot_compilation">AOT Compilation</SelectItem>
              <SelectItem value="graalvm_native">GraalVM Native</SelectItem>
              <SelectItem value="classpath_optimization">Classpath</SelectItem>
              <SelectItem value="dependency_removal">Dependency Removal</SelectItem>
            </SelectContent>
          </Select>
        </div>

        <div className="flex flex-col gap-1.5">
          <Label htmlFor="status-filter" className="text-xs text-gray-600">Status</Label>
          <Select
            value={statusFilter}
            onValueChange={(v) => setStatusFilter(v as RecommendationStatus | "all")}
          >
            <SelectTrigger id="status-filter" className="h-8 w-40 text-sm" aria-label="Filter by status">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="all">All Statuses</SelectItem>
              <SelectItem value="active">Active</SelectItem>
              <SelectItem value="applied">Applied</SelectItem>
              <SelectItem value="wont_fix">Won&apos;t Fix</SelectItem>
            </SelectContent>
          </Select>
        </div>

        <div className="ml-auto self-end">
          <Badge variant="secondary">{filtered.length} shown</Badge>
        </div>
      </div>

      {/* List */}
      {filtered.length === 0 ? (
        <div className="rounded-lg border bg-white p-8 text-center text-gray-500">
          <p className="font-medium">No recommendations match the current filters</p>
        </div>
      ) : (
        <div className="space-y-3">
          {filtered.map((rec) => (
            <RecommendationCard key={rec.recommendation_id} recommendation={rec} projectId={projectId} />
          ))}
        </div>
      )}
    </div>
  );
}
