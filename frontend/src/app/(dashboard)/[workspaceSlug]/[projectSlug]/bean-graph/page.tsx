"use client";

import React, { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { getBeanGraph, listSnapshots, analysisKeys } from "@/lib/api/analysis";
import { BeanDependencyGraph } from "@/components/bean-graph/BeanDependencyGraph";
import { Skeleton } from "@/components/ui/skeleton";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Label } from "@/components/ui/label";
import { Badge } from "@/components/ui/badge";
import { formatMs, formatDate } from "@/lib/utils";

const PLACEHOLDER_PROJECT_ID = "00000000-0000-0000-0000-000000000001";

export default function BeanGraphPage() {
  const [selectedSnapshotId, setSelectedSnapshotId] = useState<string>("");

  const snapshotsQuery = useQuery({
    queryKey: analysisKeys.snapshots(PLACEHOLDER_PROJECT_ID),
    queryFn: () => listSnapshots(PLACEHOLDER_PROJECT_ID, { limit: 50 }),
  });

  const snapshotId = selectedSnapshotId || snapshotsQuery.data?.data[0]?.snapshot_id || "";

  const graphQuery = useQuery({
    queryKey: analysisKeys.beanGraph(PLACEHOLDER_PROJECT_ID, snapshotId),
    queryFn: () => getBeanGraph(PLACEHOLDER_PROJECT_ID, snapshotId),
    enabled: !!snapshotId,
  });

  return (
    <div className="space-y-4">
      <div>
        <h2 className="text-2xl font-bold text-gray-900">Bean Dependency Graph</h2>
        <p className="mt-1 text-sm text-gray-500">
          Force-directed DAG of bean dependencies. Click a node to inspect details.
        </p>
      </div>

      {/* Snapshot selector */}
      <div className="flex items-end gap-4 rounded-lg border bg-white p-4">
        <div className="flex flex-col gap-1.5">
          <Label htmlFor="bg-snapshot-select" className="text-xs text-gray-600">Snapshot</Label>
          <Select value={snapshotId} onValueChange={setSelectedSnapshotId}>
            <SelectTrigger id="bg-snapshot-select" className="w-72 text-sm" aria-label="Select snapshot">
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

        {graphQuery.data && (
          <div className="flex gap-3">
            <Badge variant="secondary">{graphQuery.data.nodes.length} nodes</Badge>
            <Badge variant="secondary">{graphQuery.data.edges.length} edges</Badge>
            <Badge variant="danger">
              {graphQuery.data.nodes.filter((n) => n.is_bottleneck).length} bottlenecks
            </Badge>
          </div>
        )}
      </div>

      {/* Graph */}
      <div className="rounded-lg border bg-white" style={{ height: 600 }}>
        {graphQuery.isLoading ? (
          <div className="flex h-full items-center justify-center" aria-busy="true" aria-label="Loading graph">
            <Skeleton className="h-full w-full rounded-lg" />
          </div>
        ) : graphQuery.isError ? (
          <div className="flex h-full items-center justify-center text-red-600" role="alert">
            Failed to load bean graph
          </div>
        ) : !graphQuery.data ? (
          <div className="flex h-full items-center justify-center text-gray-400">
            Select a snapshot to view the bean dependency graph
          </div>
        ) : (
          <BeanDependencyGraph nodes={graphQuery.data.nodes} edges={graphQuery.data.edges} />
        )}
      </div>
    </div>
  );
}
