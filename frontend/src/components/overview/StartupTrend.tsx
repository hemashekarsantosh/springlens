"use client";

import React from "react";
import {
  LineChart,
  Line,
  XAxis,
  YAxis,
  Tooltip,
  ResponsiveContainer,
} from "recharts";
import type { SnapshotSummary } from "@/types/api";
import { formatMs, formatDate } from "@/lib/utils";

interface StartupTrendProps {
  snapshots: SnapshotSummary[];
  height?: number;
}

interface CustomTooltipProps {
  active?: boolean;
  payload?: Array<{ value: number; payload: SnapshotSummary }>;
}

function CustomTooltip({ active, payload }: CustomTooltipProps) {
  if (!active || !payload?.length) return null;
  const data = payload[0]?.payload;
  if (!data) return null;
  return (
    <div className="rounded-lg border bg-white p-2 shadow-lg text-xs">
      <p className="font-semibold text-gray-900">{formatMs(data.total_startup_ms)}</p>
      <p className="text-gray-500">{formatDate(data.captured_at)}</p>
      <p className="text-gray-400 font-mono">{data.git_commit_sha.slice(0, 8)}</p>
    </div>
  );
}

export function StartupTrend({ snapshots, height = 80 }: StartupTrendProps) {
  const sorted = [...snapshots].sort(
    (a, b) => new Date(a.captured_at).getTime() - new Date(b.captured_at).getTime()
  );

  const data = sorted.map((s) => ({
    ...s,
    date: formatDate(s.captured_at),
  }));

  if (data.length < 2) {
    return (
      <div className="flex items-center justify-center text-xs text-gray-400" style={{ height }}>
        Insufficient data for trend
      </div>
    );
  }

  const values = data.map((d) => d.total_startup_ms);
  const min = Math.min(...values);
  const max = Math.max(...values);
  const padding = (max - min) * 0.1 || 100;

  return (
    <ResponsiveContainer width="100%" height={height}>
      <LineChart data={data} margin={{ top: 4, right: 4, bottom: 4, left: 4 }}>
        <XAxis dataKey="date" hide />
        <YAxis domain={[min - padding, max + padding]} hide />
        <Tooltip content={<CustomTooltip />} />
        <Line
          type="monotone"
          dataKey="total_startup_ms"
          stroke="#3b82f6"
          strokeWidth={2}
          dot={false}
          activeDot={{ r: 4 }}
        />
      </LineChart>
    </ResponsiveContainer>
  );
}
