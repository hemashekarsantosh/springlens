"use client";

import React from "react";
import { Search, SlidersHorizontal } from "lucide-react";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Slider } from "@/components/ui/slider";
import { formatMs } from "@/lib/utils";

export interface BeanFilterState {
  minDurationMs: number;
  packagePrefix: string;
  beanNameSearch: string;
}

interface BeanFilterProps {
  value: BeanFilterState;
  onChange: (value: BeanFilterState) => void;
  maxDurationMs: number;
}

export function BeanFilter({ value, onChange, maxDurationMs }: BeanFilterProps) {
  return (
    <div className="flex flex-wrap items-end gap-4 rounded-lg border bg-white p-4">
      <div className="flex items-center gap-2">
        <SlidersHorizontal className="h-4 w-4 text-gray-500" aria-hidden="true" />
        <span className="text-sm font-medium text-gray-700">Filters</span>
      </div>

      {/* Min duration slider */}
      <div className="flex min-w-[200px] flex-col gap-2">
        <Label htmlFor="min-duration" className="text-xs text-gray-600">
          Min Duration: {formatMs(value.minDurationMs)}
        </Label>
        <Slider
          id="min-duration"
          min={0}
          max={maxDurationMs}
          step={10}
          value={[value.minDurationMs]}
          onValueChange={([v]) =>
            onChange({ ...value, minDurationMs: v ?? 0 })
          }
          aria-label="Minimum bean duration filter"
          className="w-48"
        />
      </div>

      {/* Package prefix */}
      <div className="flex flex-col gap-1.5">
        <Label htmlFor="package-prefix" className="text-xs text-gray-600">
          Package Prefix
        </Label>
        <Input
          id="package-prefix"
          placeholder="com.example"
          value={value.packagePrefix}
          onChange={(e) => onChange({ ...value, packagePrefix: e.target.value })}
          className="h-8 w-48 text-sm"
          aria-label="Filter by Java package prefix"
        />
      </div>

      {/* Bean name search */}
      <div className="flex flex-col gap-1.5">
        <Label htmlFor="bean-search" className="text-xs text-gray-600">
          Bean Name
        </Label>
        <div className="relative">
          <Search
            className="absolute left-2 top-1/2 h-3.5 w-3.5 -translate-y-1/2 text-gray-400"
            aria-hidden="true"
          />
          <Input
            id="bean-search"
            placeholder="Search beans..."
            value={value.beanNameSearch}
            onChange={(e) => onChange({ ...value, beanNameSearch: e.target.value })}
            className="h-8 w-48 pl-7 text-sm"
            aria-label="Search bean by name"
          />
        </div>
      </div>
    </div>
  );
}
