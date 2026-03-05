"use client";

import React, { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { ArrowDown, ArrowUp, Minus, Plus, TrendingDown, TrendingUp } from "lucide-react";
import { compareSnapshots, analysisKeys } from "@/lib/api/analysis";
import type { SnapshotSummary } from "@/types/api";
import { Skeleton } from "@/components/ui/skeleton";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Label } from "@/components/ui/label";
import { Badge } from "@/components/ui/badge";
import { formatMs, formatDate, cn } from "@/lib/utils";

interface SnapshotDiffProps {
  projectId: string;
  snapshots: SnapshotSummary[];
}

export function SnapshotDiff({ projectId, snapshots }: SnapshotDiffProps) {
  const [baselineId, setBaselineId] = useState<string>(snapshots[1]?.snapshot_id ?? "");
  const [targetId, setTargetId] = useState<string>(snapshots[0]?.snapshot_id ?? "");

  const canCompare = baselineId && targetId && baselineId !== targetId;

  const { data, isLoading, isError } = useQuery({
    queryKey: analysisKeys.compare(projectId, baselineId, targetId),
    queryFn: () => compareSnapshots(projectId, baselineId, targetId),
    enabled: !!canCompare,
  });

  const isRegression = (data?.total_delta_ms ?? 0) > 0;

  return (
    <div className="space-y-4">
      {/* Snapshot selectors */}
      <div className="flex flex-wrap items-end gap-6 rounded-lg border bg-white p-4">
        <div className="flex flex-col gap-1.5">
          <Label htmlFor="baseline-select" className="text-xs font-medium text-gray-600">
            Baseline (Before)
          </Label>
          <Select value={baselineId} onValueChange={setBaselineId}>
            <SelectTrigger id="baseline-select" className="w-64 text-sm" aria-label="Select baseline snapshot">
              <SelectValue placeholder="Select baseline..." />
            </SelectTrigger>
            <SelectContent>
              {snapshots.map((s) => (
                <SelectItem key={s.snapshot_id} value={s.snapshot_id} disabled={s.snapshot_id === targetId}>
                  {s.git_commit_sha.slice(0, 8)} — {formatDate(s.captured_at)} ({formatMs(s.total_startup_ms)})
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>

        <div className="flex items-center text-gray-400">
          <ArrowDown className="h-5 w-5 rotate-[-90deg]" aria-hidden="true" />
        </div>

        <div className="flex flex-col gap-1.5">
          <Label htmlFor="target-select" className="text-xs font-medium text-gray-600">
            Target (After)
          </Label>
          <Select value={targetId} onValueChange={setTargetId}>
            <SelectTrigger id="target-select" className="w-64 text-sm" aria-label="Select target snapshot">
              <SelectValue placeholder="Select target..." />
            </SelectTrigger>
            <SelectContent>
              {snapshots.map((s) => (
                <SelectItem key={s.snapshot_id} value={s.snapshot_id} disabled={s.snapshot_id === baselineId}>
                  {s.git_commit_sha.slice(0, 8)} — {formatDate(s.captured_at)} ({formatMs(s.total_startup_ms)})
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
      </div>

      {!canCompare && (
        <div className="rounded-lg border bg-gray-50 p-8 text-center text-gray-500">
          <p>Select two different snapshots to compare</p>
        </div>
      )}

      {canCompare && isLoading && (
        <div className="space-y-3" aria-busy="true" aria-label="Loading comparison">
          <Skeleton className="h-16 w-full rounded-lg" />
          <Skeleton className="h-64 w-full rounded-lg" />
        </div>
      )}

      {canCompare && isError && (
        <div className="rounded-lg border border-red-200 bg-red-50 p-6 text-center" role="alert">
          <p className="font-semibold text-red-800">Failed to load comparison</p>
        </div>
      )}

      {data && (
        <div className="space-y-4">
          {/* Total delta banner */}
          <div
            className={cn(
              "flex items-center gap-3 rounded-lg border p-4",
              isRegression
                ? "border-red-200 bg-red-50"
                : "border-green-200 bg-green-50"
            )}
            role="status"
          >
            {isRegression ? (
              <TrendingUp className="h-6 w-6 text-red-600" aria-hidden="true" />
            ) : (
              <TrendingDown className="h-6 w-6 text-green-600" aria-hidden="true" />
            )}
            <div>
              <p className={cn("text-lg font-bold", isRegression ? "text-red-700" : "text-green-700")}>
                {isRegression ? "+" : ""}{formatMs(data.total_delta_ms)} total delta
              </p>
              <p className="text-sm text-gray-600">
                {isRegression ? "Regression detected" : "Improvement detected"} across {data.changed_beans.length} changed beans
              </p>
            </div>
            <div className="ml-auto flex gap-3 text-sm">
              {data.added_beans.length > 0 && (
                <Badge variant="danger">
                  <Plus className="mr-1 h-3 w-3" aria-hidden="true" />
                  {data.added_beans.length} added
                </Badge>
              )}
              {data.removed_beans.length > 0 && (
                <Badge variant="success">
                  <Minus className="mr-1 h-3 w-3" aria-hidden="true" />
                  {data.removed_beans.length} removed
                </Badge>
              )}
            </div>
          </div>

          {/* Changed beans table */}
          {data.changed_beans.length > 0 && (
            <div className="rounded-lg border bg-white overflow-hidden">
              <div className="border-b bg-gray-50 px-4 py-3">
                <h3 className="text-sm font-semibold text-gray-700">
                  Changed Beans ({data.changed_beans.length}) — sorted by largest regression first
                </h3>
              </div>
              <div className="overflow-x-auto">
                <table className="w-full text-sm" role="table">
                  <thead>
                    <tr className="border-b bg-gray-50 text-left text-xs font-semibold text-gray-600">
                      <th className="px-4 py-3" scope="col">Bean Name</th>
                      <th className="px-4 py-3 text-right" scope="col">Baseline</th>
                      <th className="px-4 py-3 text-right" scope="col">Target</th>
                      <th className="px-4 py-3 text-right" scope="col">Delta</th>
                      <th className="px-4 py-3 text-right" scope="col">% Change</th>
                    </tr>
                  </thead>
                  <tbody>
                    {[...data.changed_beans]
                      .sort((a, b) => b.delta_ms - a.delta_ms)
                      .map((bean) => (
                        <tr
                          key={bean.bean_name}
                          className="border-b last:border-0 hover:bg-gray-50"
                        >
                          <td className="px-4 py-3 font-mono text-xs text-gray-900">
                            {bean.bean_name}
                          </td>
                          <td className="px-4 py-3 text-right text-gray-600">
                            {formatMs(bean.baseline_ms)}
                          </td>
                          <td className="px-4 py-3 text-right text-gray-600">
                            {formatMs(bean.target_ms)}
                          </td>
                          <td className={cn(
                            "px-4 py-3 text-right font-semibold",
                            bean.delta_ms > 0 ? "text-red-600" : "text-green-600"
                          )}>
                            <span className="flex items-center justify-end gap-1">
                              {bean.delta_ms > 0 ? (
                                <ArrowUp className="h-3.5 w-3.5" aria-hidden="true" />
                              ) : (
                                <ArrowDown className="h-3.5 w-3.5" aria-hidden="true" />
                              )}
                              {bean.delta_ms > 0 ? "+" : ""}{formatMs(bean.delta_ms)}
                            </span>
                          </td>
                          <td className={cn(
                            "px-4 py-3 text-right font-medium",
                            bean.delta_percent > 0 ? "text-red-600" : "text-green-600"
                          )}>
                            {bean.delta_percent > 0 ? "+" : ""}{bean.delta_percent.toFixed(1)}%
                          </td>
                        </tr>
                      ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}

          {/* Added beans */}
          {data.added_beans.length > 0 && (
            <div className="rounded-lg border border-red-200 bg-white overflow-hidden">
              <div className="border-b bg-red-50 px-4 py-3">
                <h3 className="text-sm font-semibold text-red-700">
                  Added Beans ({data.added_beans.length}) — new in target
                </h3>
              </div>
              <div className="overflow-x-auto">
                <table className="w-full text-sm" role="table">
                  <thead>
                    <tr className="border-b bg-gray-50 text-left text-xs font-semibold text-gray-600">
                      <th className="px-4 py-3" scope="col">Bean Name</th>
                      <th className="px-4 py-3" scope="col">Class</th>
                      <th className="px-4 py-3 text-right" scope="col">Duration</th>
                    </tr>
                  </thead>
                  <tbody>
                    {data.added_beans.map((bean) => (
                      <tr key={bean.bean_name} className="border-b last:border-0 bg-red-50/30">
                        <td className="px-4 py-3 font-mono text-xs text-gray-900">{bean.bean_name}</td>
                        <td className="px-4 py-3 text-xs text-gray-500 truncate max-w-xs">{bean.class_name}</td>
                        <td className="px-4 py-3 text-right font-medium text-red-600">
                          +{formatMs(bean.duration_ms)}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}

          {/* Removed beans */}
          {data.removed_beans.length > 0 && (
            <div className="rounded-lg border border-green-200 bg-white overflow-hidden">
              <div className="border-b bg-green-50 px-4 py-3">
                <h3 className="text-sm font-semibold text-green-700">
                  Removed Beans ({data.removed_beans.length}) — not in target
                </h3>
              </div>
              <div className="overflow-x-auto">
                <table className="w-full text-sm" role="table">
                  <thead>
                    <tr className="border-b bg-gray-50 text-left text-xs font-semibold text-gray-600">
                      <th className="px-4 py-3" scope="col">Bean Name</th>
                      <th className="px-4 py-3" scope="col">Class</th>
                      <th className="px-4 py-3 text-right" scope="col">Was</th>
                    </tr>
                  </thead>
                  <tbody>
                    {data.removed_beans.map((bean) => (
                      <tr key={bean.bean_name} className="border-b last:border-0 bg-green-50/30">
                        <td className="px-4 py-3 font-mono text-xs text-gray-500 line-through">{bean.bean_name}</td>
                        <td className="px-4 py-3 text-xs text-gray-400 truncate max-w-xs">{bean.class_name}</td>
                        <td className="px-4 py-3 text-right font-medium text-green-600">
                          -{formatMs(bean.duration_ms)}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
