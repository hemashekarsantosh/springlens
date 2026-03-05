"use client";

import React from "react";
import Link from "next/link";
import { TrendingDown, TrendingUp, Minus, Activity } from "lucide-react";
import type { ProjectOverview } from "@/types/api";
import { formatMs, formatDate, startupHealthBadgeClass, cn } from "@/lib/utils";

interface WorkspaceHealthProps {
  projects: ProjectOverview[];
  workspaceSlug: string;
}

function TrendIcon({ trend }: { trend: ProjectOverview["trend_7d"] }) {
  switch (trend) {
    case "improving":
      return <TrendingDown className="h-4 w-4 text-green-600" aria-label="Improving" />;
    case "degrading":
      return <TrendingUp className="h-4 w-4 text-red-600" aria-label="Degrading" />;
    case "stable":
      return <Minus className="h-4 w-4 text-gray-400" aria-label="Stable" />;
    case "no_data":
      return <Activity className="h-4 w-4 text-gray-300" aria-label="No data" />;
  }
}

function trendLabel(trend: ProjectOverview["trend_7d"]): string {
  switch (trend) {
    case "improving": return "Improving";
    case "degrading": return "Degrading";
    case "stable": return "Stable";
    case "no_data": return "No data";
  }
}

function healthLabel(ms: number): string {
  if (ms < 5000) return "Healthy";
  if (ms < 10000) return "Warning";
  return "Critical";
}

export function WorkspaceHealth({ projects, workspaceSlug }: WorkspaceHealthProps) {
  if (projects.length === 0) {
    return (
      <div className="rounded-lg border bg-gray-50 p-8 text-center text-gray-500">
        <p className="font-medium">No projects in this workspace yet</p>
        <p className="mt-1 text-sm">Add a project to start tracking startup performance.</p>
      </div>
    );
  }

  return (
    <div
      className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3"
      role="list"
      aria-label="Project health cards"
    >
      {projects.map((project) => {
        const projectSlug = project.project_name.toLowerCase().replace(/\s+/g, "-");
        const badgeClass = startupHealthBadgeClass(project.latest_startup_ms);

        return (
          <Link
            key={project.project_id}
            href={`/${workspaceSlug}/${projectSlug}`}
            className={cn(
              "group block rounded-lg border bg-white p-5 transition-all",
              "hover:shadow-md hover:border-blue-200 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2"
            )}
            role="listitem"
            aria-label={`${project.project_name}: ${formatMs(project.latest_startup_ms)} startup time, ${trendLabel(project.trend_7d)} trend`}
          >
            {/* Project name */}
            <div className="flex items-start justify-between">
              <h3 className="font-semibold text-gray-900 group-hover:text-blue-600 transition-colors">
                {project.project_name}
              </h3>
              <TrendIcon trend={project.trend_7d} />
            </div>

            {/* Startup time */}
            <div className="mt-3 flex items-end gap-2">
              <p className="text-3xl font-bold text-gray-900">
                {formatMs(project.latest_startup_ms)}
              </p>
              <span
                className={cn(
                  "mb-1 inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium",
                  badgeClass
                )}
              >
                {healthLabel(project.latest_startup_ms)}
              </span>
            </div>

            {/* Trend & last seen */}
            <div className="mt-3 flex items-center justify-between text-xs text-gray-500">
              <span className="flex items-center gap-1">
                <TrendIcon trend={project.trend_7d} />
                {trendLabel(project.trend_7d)} (7d)
              </span>
              <span>Last seen {formatDate(project.last_seen)}</span>
            </div>
          </Link>
        );
      })}
    </div>
  );
}
