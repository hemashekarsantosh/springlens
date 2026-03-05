"use client";

import React, { useState } from "react";
import { useParams } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { getTimeline, listSnapshots, analysisKeys } from "@/lib/api/analysis";
import { StartupTimeline } from "@/components/timeline/StartupTimeline";
import { PhaseBreakdown } from "@/components/timeline/PhaseBreakdown";
import { BeanFilter, type BeanFilterState } from "@/components/timeline/BeanFilter";
import { Skeleton } from "@/components/ui/skeleton";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Label } from "@/components/ui/label";
import { Badge } from "@/components/ui/badge";
import { formatMs, formatDate } from "@/lib/utils";
import type { BeanTiming } from "@/types/api";

const PLACEHOLDER_PROJECT_ID = "00000000-0000-0000-0000-000000000001";

export default function TimelinePage() {
  const params = useParams<{ workspaceSlug: string; projectSlug: string }>();

  const [selectedSnapshotId, setSelectedSnapshotId] = useState<string>("");
  const [filters, setFilters] = useState<BeanFilterState>({
    minDurationMs: 0,
    packagePrefix: "",
    beanNameSearch: "",
  });

  // Load snapshots for selector
  const snapshotsQuery = useQuery({
    queryKey: analysisKeys.snapshots(PLACEHOLDER_PROJECT_ID),
    queryFn: () => listSnapshots(PLACEHOLDER_PROJECT_ID, { limit: 50 }),
  });

  const snapshotId = selectedSnapshotId || snapshotsQuery.data?.data[0]?.snapshot_id || "";

  // Load timeline
  const timelineQuery = useQuery({
    queryKey: analysisKeys.timeline(PLACEHOLDER_PROJECT_ID, snapshotId, {
      min_duration_ms: filters.minDurationMs,
      package_prefix: filters.packagePrefix,
    }),
    queryFn: () =>
      getTimeline(PLACEHOLDER_PROJECT_ID, snapshotId, {
        min_duration_ms: filters.minDurationMs || undefined,
        package_prefix: filters.packagePrefix || undefined,
      }),
    enabled: !!snapshotId,
  });

  // Client-side filter for bean name search
  const filteredBeans: BeanTiming[] = (timelineQuery.data?.beans ?? []).filter((b) => {
    if (!filters.beanNameSearch) return true;
    return b.bean_name.toLowerCase().includes(filters.beanNameSearch.toLowerCase());
  });

  const maxBeanDuration = Math.max(...filteredBeans.map((b) => b.duration_ms), 0);

  return (
    <div className="space-y-4">
      <div>
        <h2 className="text-2xl font-bold text-gray-900">Startup Timeline</h2>
        <p className="mt-1 text-sm text-gray-500">
          Gantt chart of bean initialization — scroll to zoom, drag to pan
        </p>
      </div>

      {/* Snapshot selector */}
      <div className="flex items-end gap-4 rounded-lg border bg-white p-4">
        <div className="flex flex-col gap-1.5">
          <Label htmlFor="snapshot-select" className="text-xs text-gray-600">Snapshot</Label>
          <Select
            value={snapshotId}
            onValueChange={setSelectedSnapshotId}
          >
            <SelectTrigger id="snapshot-select" className="w-72 text-sm" aria-label="Select snapshot">
              <SelectValue placeholder="Select a snapshot..." />
            </SelectTrigger>
            <SelectContent>
              {snapshotsQuery.data?.data.map((s) => (
                <SelectItem key={s.snapshot_id} value={s.snapshot_id}>
                  {s.git_commit_sha.slice(0, 8)} — {formatDate(s.captured_at)} ({formatMs(s.total_startup_ms)})
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>

        {timelineQuery.data && (
          <div className="flex gap-3 text-sm">
            <Badge variant="secondary">{timelineQuery.data.beans.length} beans total</Badge>
            <Badge variant="info">{filteredBeans.length} shown</Badge>
            <Badge variant="warning">
              {timelineQuery.data.beans.filter((b) => b.is_bottleneck).length} bottlenecks
            </Badge>
          </div>
        )}
      </div>

      {/* Filters */}
      <BeanFilter
        value={filters}
        onChange={setFilters}
        maxDurationMs={maxBeanDuration}
      />

      {/* Phase breakdown */}
      {timelineQuery.data?.phases && timelineQuery.data.phases.length > 0 && (
        <PhaseBreakdown phases={timelineQuery.data.phases} />
      )}

      {/* Timeline chart */}
      <div className="rounded-lg border bg-white p-4">
        {timelineQuery.isLoading ? (
          <div className="space-y-2" aria-busy="true" aria-label="Loading timeline">
            {Array.from({ length: 8 }).map((_, i) => (
              <Skeleton key={i} className="h-5 w-full rounded" />
            ))}
          </div>
        ) : timelineQuery.isError ? (
          <div className="p-6 text-center text-red-600" role="alert">
            Failed to load timeline data
          </div>
        ) : filteredBeans.length === 0 ? (
          <div className="p-8 text-center text-gray-500">
            No beans match the current filters
          </div>
        ) : (
          <StartupTimeline
            beans={filteredBeans}
            totalStartupMs={timelineQuery.data?.total_startup_ms ?? 0}
          />
        )}
      </div>
    </div>
  );
}
