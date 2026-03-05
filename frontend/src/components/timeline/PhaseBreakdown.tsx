"use client";

import React from "react";
import {
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
  Cell,
} from "recharts";
import type { PhaseBreakdown as PhaseBreakdownType } from "@/types/api";
import { formatMs } from "@/lib/utils";

interface PhaseBreakdownProps {
  phases: PhaseBreakdownType[];
}

const PHASE_COLORS: Record<string, string> = {
  context_refresh: "#3b82f6",
  bean_post_processors: "#f97316",
  application_listeners: "#8b5cf6",
  context_loaded: "#10b981",
  started: "#6b7280",
};

function getPhaseColor(phaseName: string): string {
  return PHASE_COLORS[phaseName] ?? "#94a3b8";
}

function formatPhaseName(name: string): string {
  return name.replace(/_/g, " ").replace(/\b\w/g, (c) => c.toUpperCase());
}

interface CustomTooltipProps {
  active?: boolean;
  payload?: Array<{ value: number; payload: PhaseBreakdownType }>;
  label?: string;
}

function CustomTooltip({ active, payload }: CustomTooltipProps) {
  if (!active || !payload?.length) return null;
  const data = payload[0]?.payload;
  if (!data) return null;
  return (
    <div className="rounded-lg border bg-white p-3 shadow-lg">
      <p className="font-semibold text-gray-900">{formatPhaseName(data.phase_name)}</p>
      <p className="text-sm text-gray-600">Duration: {formatMs(data.duration_ms)}</p>
      <p className="text-sm text-gray-600">
        {data.percentage_of_total.toFixed(1)}% of total startup
      </p>
    </div>
  );
}

export function PhaseBreakdown({ phases }: PhaseBreakdownProps) {
  const chartData = phases.map((p) => ({
    ...p,
    name: formatPhaseName(p.phase_name),
  }));

  return (
    <div className="rounded-lg border bg-white p-4">
      <h3 className="mb-4 text-sm font-semibold text-gray-700">Startup Phase Breakdown</h3>
      <ResponsiveContainer width="100%" height={200}>
        <BarChart data={chartData} layout="vertical" margin={{ left: 140, right: 40 }}>
          <CartesianGrid strokeDasharray="3 3" horizontal={false} />
          <XAxis
            type="number"
            tickFormatter={(v: number) => formatMs(v)}
            tick={{ fontSize: 11 }}
          />
          <YAxis
            type="category"
            dataKey="name"
            width={130}
            tick={{ fontSize: 11 }}
          />
          <Tooltip content={<CustomTooltip />} />
          <Bar dataKey="duration_ms" radius={[0, 4, 4, 0]}>
            {chartData.map((entry, index) => (
              <Cell key={index} fill={getPhaseColor(entry.phase_name)} />
            ))}
          </Bar>
        </BarChart>
      </ResponsiveContainer>

      {/* Legend */}
      <div className="mt-3 flex flex-wrap gap-3">
        {phases.map((p) => (
          <div key={p.phase_name} className="flex items-center gap-1.5">
            <div
              className="h-3 w-3 rounded-sm"
              style={{ backgroundColor: getPhaseColor(p.phase_name) }}
              aria-hidden="true"
            />
            <span className="text-xs text-gray-600">
              {formatPhaseName(p.phase_name)} ({formatMs(p.duration_ms)})
            </span>
          </div>
        ))}
      </div>
    </div>
  );
}
