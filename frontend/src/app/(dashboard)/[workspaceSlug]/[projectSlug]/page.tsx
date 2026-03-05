"use client";

import React from "react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { Clock, GitBranch, AlertTriangle, Lightbulb, ArrowRight } from "lucide-react";
import { listSnapshots, analysisKeys } from "@/lib/api/analysis";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import { StartupTrend } from "@/components/overview/StartupTrend";
import { formatMs, formatDateTime, startupHealthBadgeClass, cn } from "@/lib/utils";

// Placeholder project ID — production resolves from slug via API
const PLACEHOLDER_PROJECT_ID = "00000000-0000-0000-0000-000000000001";

export default function ProjectOverviewPage() {
  const params = useParams<{ workspaceSlug: string; projectSlug: string }>();

  const { data, isLoading, isError } = useQuery({
    queryKey: analysisKeys.snapshots(PLACEHOLDER_PROJECT_ID, { limit: 20 }),
    queryFn: () => listSnapshots(PLACEHOLDER_PROJECT_ID, { limit: 20 }),
  });

  if (isLoading) {
    return (
      <div className="space-y-6" aria-busy="true">
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
          {Array.from({ length: 4 }).map((_, i) => (
            <Skeleton key={i} className="h-28 rounded-xl" />
          ))}
        </div>
        <Skeleton className="h-48 rounded-xl" />
        <Skeleton className="h-64 rounded-xl" />
      </div>
    );
  }

  if (isError || !data) {
    return (
      <div className="rounded-lg border border-red-200 bg-red-50 p-8 text-center" role="alert">
        <p className="font-semibold text-red-800">Failed to load project data</p>
      </div>
    );
  }

  const latest = data.data[0];
  const badgeClass = latest ? startupHealthBadgeClass(latest.total_startup_ms) : "";
  const base = `/${params.workspaceSlug}/${params.projectSlug}`;

  return (
    <div className="space-y-6">
      <div className="flex items-start justify-between">
        <div>
          <h2 className="text-2xl font-bold text-gray-900 capitalize">
            {params.projectSlug.replace(/-/g, " ")}
          </h2>
          <p className="mt-1 text-sm text-gray-500">
            {data.total} snapshots recorded
          </p>
        </div>
        {latest && (
          <span className={cn("rounded-full px-3 py-1 text-sm font-semibold", badgeClass)}>
            {formatMs(latest.total_startup_ms)}
          </span>
        )}
      </div>

      {/* Latest snapshot metrics */}
      {latest && (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
          <Card>
            <CardHeader className="pb-2">
              <CardTitle className="text-xs font-medium text-gray-500 flex items-center gap-1.5">
                <Clock className="h-3.5 w-3.5" aria-hidden="true" />
                Latest Startup
              </CardTitle>
            </CardHeader>
            <CardContent>
              <p className="text-2xl font-bold text-gray-900">{formatMs(latest.total_startup_ms)}</p>
              <p className="mt-0.5 text-xs text-gray-400">{formatDateTime(latest.captured_at)}</p>
            </CardContent>
          </Card>

          <Card>
            <CardHeader className="pb-2">
              <CardTitle className="text-xs font-medium text-gray-500 flex items-center gap-1.5">
                <GitBranch className="h-3.5 w-3.5" aria-hidden="true" />
                Commit
              </CardTitle>
            </CardHeader>
            <CardContent>
              <p className="font-mono text-lg font-bold text-gray-900">
                {latest.git_commit_sha.slice(0, 8)}
              </p>
              <Badge
                variant={
                  latest.trend === "faster"
                    ? "success"
                    : latest.trend === "slower"
                    ? "danger"
                    : "secondary"
                }
                className="mt-1"
              >
                {latest.trend}
              </Badge>
            </CardContent>
          </Card>

          <Card>
            <CardHeader className="pb-2">
              <CardTitle className="text-xs font-medium text-gray-500 flex items-center gap-1.5">
                <AlertTriangle className="h-3.5 w-3.5" aria-hidden="true" />
                Bottlenecks
              </CardTitle>
            </CardHeader>
            <CardContent>
              <p className="text-2xl font-bold text-gray-900">{latest.bottleneck_count}</p>
              <p className="mt-0.5 text-xs text-gray-400">beans exceeding threshold</p>
            </CardContent>
          </Card>

          <Card>
            <CardHeader className="pb-2">
              <CardTitle className="text-xs font-medium text-gray-500 flex items-center gap-1.5">
                <Lightbulb className="h-3.5 w-3.5" aria-hidden="true" />
                Beans
              </CardTitle>
            </CardHeader>
            <CardContent>
              <p className="text-2xl font-bold text-gray-900">{latest.bean_count}</p>
              <p className="mt-0.5 text-xs text-gray-400">initialized at startup</p>
            </CardContent>
          </Card>
        </div>
      )}

      {/* Startup trend chart */}
      <Card>
        <CardHeader>
          <CardTitle className="text-sm font-semibold text-gray-700">
            Startup Time Trend
          </CardTitle>
        </CardHeader>
        <CardContent>
          <StartupTrend snapshots={data.data} height={140} />
        </CardContent>
      </Card>

      {/* Quick links */}
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {[
          {
            href: `${base}/timeline`,
            title: "Startup Timeline",
            desc: "Gantt chart of bean initialization",
            icon: Clock,
          },
          {
            href: `${base}/bean-graph`,
            title: "Bean Dependency Graph",
            desc: "Interactive DAG of bean dependencies",
            icon: GitBranch,
          },
          {
            href: `${base}/recommendations`,
            title: "Recommendations",
            desc: "Ranked optimization suggestions",
            icon: Lightbulb,
          },
        ].map(({ href, title, desc, icon: Icon }) => (
          <Link
            key={href}
            href={href}
            className="group flex items-center justify-between rounded-lg border bg-white p-4 transition-all hover:border-blue-200 hover:shadow-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2"
          >
            <div className="flex items-center gap-3">
              <div className="rounded-md bg-blue-50 p-2">
                <Icon className="h-5 w-5 text-blue-600" aria-hidden="true" />
              </div>
              <div>
                <p className="font-medium text-gray-900 group-hover:text-blue-600 transition-colors">
                  {title}
                </p>
                <p className="text-xs text-gray-500">{desc}</p>
              </div>
            </div>
            <ArrowRight className="h-4 w-4 text-gray-400 group-hover:text-blue-400 transition-colors" aria-hidden="true" />
          </Link>
        ))}
      </div>

      {/* Recent snapshots */}
      <Card>
        <CardHeader>
          <CardTitle className="text-sm font-semibold text-gray-700">Recent Snapshots</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="overflow-x-auto">
            <table className="w-full text-sm" role="table">
              <thead>
                <tr className="border-b text-left text-xs font-semibold text-gray-600">
                  <th className="pb-2 pr-4" scope="col">Commit</th>
                  <th className="pb-2 pr-4" scope="col">Environment</th>
                  <th className="pb-2 pr-4" scope="col">Startup Time</th>
                  <th className="pb-2 pr-4" scope="col">Beans</th>
                  <th className="pb-2 pr-4" scope="col">Trend</th>
                  <th className="pb-2" scope="col">Captured</th>
                </tr>
              </thead>
              <tbody>
                {data.data.slice(0, 8).map((snapshot) => (
                  <tr key={snapshot.snapshot_id} className="border-b last:border-0 hover:bg-gray-50">
                    <td className="py-2 pr-4 font-mono text-xs text-gray-700">
                      {snapshot.git_commit_sha.slice(0, 8)}
                    </td>
                    <td className="py-2 pr-4">
                      <Badge variant="secondary" className="text-xs">{snapshot.environment}</Badge>
                    </td>
                    <td className={cn("py-2 pr-4 font-semibold text-xs", startupHealthBadgeClass(snapshot.total_startup_ms))}>
                      {formatMs(snapshot.total_startup_ms)}
                    </td>
                    <td className="py-2 pr-4 text-gray-600">{snapshot.bean_count}</td>
                    <td className="py-2 pr-4">
                      <Badge
                        variant={
                          snapshot.trend === "faster"
                            ? "success"
                            : snapshot.trend === "slower"
                            ? "danger"
                            : "secondary"
                        }
                        className="text-xs"
                      >
                        {snapshot.trend}
                      </Badge>
                    </td>
                    <td className="py-2 text-xs text-gray-500">
                      {formatDateTime(snapshot.captured_at)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
