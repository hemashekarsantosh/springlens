"use client";

import React from "react";
import { useQuery } from "@tanstack/react-query";
import { listSnapshots, analysisKeys } from "@/lib/api/analysis";
import { SnapshotDiff } from "@/components/compare/SnapshotDiff";
import { Skeleton } from "@/components/ui/skeleton";

const PLACEHOLDER_PROJECT_ID = "00000000-0000-0000-0000-000000000001";

export default function ComparePage() {
  const { data, isLoading, isError } = useQuery({
    queryKey: analysisKeys.snapshots(PLACEHOLDER_PROJECT_ID, { limit: 50 }),
    queryFn: () => listSnapshots(PLACEHOLDER_PROJECT_ID, { limit: 50 }),
  });

  return (
    <div className="space-y-4">
      <div>
        <h2 className="text-2xl font-bold text-gray-900">Compare Snapshots</h2>
        <p className="mt-1 text-sm text-gray-500">
          Select two snapshots to see a detailed diff of startup performance changes.
        </p>
      </div>

      {isLoading && (
        <div className="space-y-4" aria-busy="true">
          <Skeleton className="h-24 rounded-lg" />
          <Skeleton className="h-64 rounded-lg" />
        </div>
      )}

      {isError && (
        <div className="rounded-lg border border-red-200 bg-red-50 p-6 text-center" role="alert">
          <p className="font-semibold text-red-800">Failed to load snapshots</p>
        </div>
      )}

      {data && data.data.length < 2 && (
        <div className="rounded-lg border bg-gray-50 p-8 text-center text-gray-500">
          <p className="font-medium">Not enough snapshots to compare</p>
          <p className="mt-1 text-sm">At least 2 startup snapshots are required for comparison.</p>
        </div>
      )}

      {data && data.data.length >= 2 && (
        <SnapshotDiff projectId={PLACEHOLDER_PROJECT_ID} snapshots={data.data} />
      )}
    </div>
  );
}
