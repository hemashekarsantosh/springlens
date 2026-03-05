"use client";

import React from "react";
import { useParams } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { Activity, Boxes, TrendingDown } from "lucide-react";
import { getWorkspaceOverview, analysisKeys } from "@/lib/api/analysis";
import { WorkspaceHealth } from "@/components/overview/WorkspaceHealth";
import { Skeleton } from "@/components/ui/skeleton";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { formatMs } from "@/lib/utils";

// Placeholder workspace ID — in production this is looked up from the API
// using the workspaceSlug. A real implementation would pass this via server
// component data fetching.
const PLACEHOLDER_WORKSPACE_ID = "00000000-0000-0000-0000-000000000000";

export default function WorkspaceOverviewPage() {
  const params = useParams<{ workspaceSlug: string }>();

  const { data, isLoading, isError } = useQuery({
    queryKey: analysisKeys.workspaceOverview(PLACEHOLDER_WORKSPACE_ID),
    queryFn: () => getWorkspaceOverview(PLACEHOLDER_WORKSPACE_ID),
  });

  if (isLoading) {
    return (
      <div className="space-y-6" aria-busy="true" aria-label="Loading workspace overview">
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
          {Array.from({ length: 3 }).map((_, i) => (
            <Skeleton key={i} className="h-28 rounded-xl" />
          ))}
        </div>
        <Skeleton className="h-64 rounded-xl" />
      </div>
    );
  }

  if (isError || !data) {
    return (
      <div className="rounded-lg border border-red-200 bg-red-50 p-8 text-center" role="alert">
        <p className="font-semibold text-red-800">Failed to load workspace overview</p>
        <p className="mt-1 text-sm text-red-600">
          Check your connection and ensure your API key is valid.
        </p>
      </div>
    );
  }

  const avgStartupMs =
    data.projects.length > 0
      ? Math.round(data.projects.reduce((sum, p) => sum + p.latest_startup_ms, 0) / data.projects.length)
      : 0;

  const improvingCount = data.projects.filter((p) => p.trend_7d === "improving").length;
  const degradingCount = data.projects.filter((p) => p.trend_7d === "degrading").length;

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-2xl font-bold text-gray-900">Workspace Overview</h2>
        <p className="mt-1 text-sm text-gray-500">
          Organization-wide startup health across {data.project_count} projects
        </p>
      </div>

      {/* Summary stats */}
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
        <Card>
          <CardHeader className="pb-2">
            <CardTitle className="text-sm font-medium text-gray-500 flex items-center gap-2">
              <Boxes className="h-4 w-4" aria-hidden="true" />
              Total Projects
            </CardTitle>
          </CardHeader>
          <CardContent>
            <p className="text-3xl font-bold text-gray-900">{data.project_count}</p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="pb-2">
            <CardTitle className="text-sm font-medium text-gray-500 flex items-center gap-2">
              <Activity className="h-4 w-4" aria-hidden="true" />
              Avg Startup Time
            </CardTitle>
          </CardHeader>
          <CardContent>
            <p className="text-3xl font-bold text-gray-900">{formatMs(avgStartupMs)}</p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="pb-2">
            <CardTitle className="text-sm font-medium text-gray-500 flex items-center gap-2">
              <TrendingDown className="h-4 w-4" aria-hidden="true" />
              7-Day Trend
            </CardTitle>
          </CardHeader>
          <CardContent>
            <p className="text-sm">
              <span className="font-semibold text-green-600">{improvingCount} improving</span>
              {" · "}
              <span className="font-semibold text-red-600">{degradingCount} degrading</span>
            </p>
          </CardContent>
        </Card>
      </div>

      {/* Project health grid */}
      <section aria-labelledby="projects-heading">
        <h3 id="projects-heading" className="mb-4 text-lg font-semibold text-gray-900">
          Projects
        </h3>
        <WorkspaceHealth projects={data.projects} workspaceSlug={params.workspaceSlug} />
      </section>
    </div>
  );
}
