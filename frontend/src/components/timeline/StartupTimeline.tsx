"use client";

import React, { useEffect, useRef, useState, useCallback } from "react";
import * as d3 from "d3";
import type { BeanTiming } from "@/types/api";
import { formatMs, shortClassName, cn } from "@/lib/utils";

interface StartupTimelineProps {
  beans: BeanTiming[];
  totalStartupMs: number;
}

interface TooltipData {
  bean: BeanTiming;
  x: number;
  y: number;
}

const ROW_HEIGHT = 22;
const MARGIN = { top: 40, right: 20, bottom: 40, left: 220 };
const MIN_BAR_WIDTH = 2;

function getBeanColor(bean: BeanTiming): string {
  if (bean.is_bottleneck) return "#ef4444"; // red
  const name = bean.bean_name.toLowerCase();
  if (name.includes("postprocessor") || name.includes("post_processor")) return "#f97316"; // orange
  return "#3b82f6"; // blue
}

export function StartupTimeline({ beans, totalStartupMs }: StartupTimelineProps) {
  const svgRef = useRef<SVGSVGElement>(null);
  const containerRef = useRef<HTMLDivElement>(null);
  const [tooltip, setTooltip] = useState<TooltipData | null>(null);
  const [width, setWidth] = useState(900);

  // Observe container width for responsiveness
  useEffect(() => {
    const container = containerRef.current;
    if (!container) return;
    const observer = new ResizeObserver((entries) => {
      const entry = entries[0];
      if (entry) setWidth(entry.contentRect.width);
    });
    observer.observe(container);
    setWidth(container.clientWidth);
    return () => observer.disconnect();
  }, []);

  const drawChart = useCallback(() => {
    const svg = svgRef.current;
    if (!svg || beans.length === 0) return;

    const innerWidth = width - MARGIN.left - MARGIN.right;
    const innerHeight = beans.length * ROW_HEIGHT;
    const totalHeight = innerHeight + MARGIN.top + MARGIN.bottom;

    // Clear previous render
    d3.select(svg).selectAll("*").remove();
    svg.setAttribute("height", String(totalHeight));
    svg.setAttribute("width", String(width));

    const root = d3
      .select(svg)
      .append("g")
      .attr("transform", `translate(${MARGIN.left},${MARGIN.top})`);

    // Scales
    const xScale = d3.scaleLinear().domain([0, totalStartupMs]).range([0, innerWidth]);

    // Sorted beans by start_ms
    const sorted = [...beans].sort((a, b) => a.start_ms - b.start_ms);

    // Zoom behavior
    const zoom = d3
      .zoom<SVGSVGElement, unknown>()
      .scaleExtent([1, 20])
      .translateExtent([
        [0, 0],
        [width, totalHeight],
      ])
      .on("zoom", (event: d3.D3ZoomEvent<SVGSVGElement, unknown>) => {
        const newX = event.transform.rescaleX(xScale);
        xAxisGroup.call(
          d3
            .axisBottom(newX)
            .tickFormat((d) => formatMs(d as number))
            .ticks(6)
        );
        barsGroup
          .selectAll<SVGRectElement, BeanTiming>("rect")
          .attr("x", (d) => newX(d.start_ms))
          .attr("width", (d) =>
            Math.max(MIN_BAR_WIDTH, newX(d.start_ms + d.duration_ms) - newX(d.start_ms))
          );
      });

    d3.select(svg).call(zoom);

    // X axis
    const xAxisGroup = root
      .append("g")
      .attr("transform", `translate(0,${innerHeight})`)
      .call(
        d3
          .axisBottom(xScale)
          .tickFormat((d) => formatMs(d as number))
          .ticks(6)
      );
    xAxisGroup.select(".domain").attr("stroke", "#e5e7eb");
    xAxisGroup.selectAll("line").attr("stroke", "#e5e7eb");
    xAxisGroup.selectAll("text").attr("fill", "#6b7280").attr("font-size", "11px");

    // Gridlines
    root
      .append("g")
      .attr("class", "grid")
      .call(
        d3
          .axisBottom(xScale)
          .ticks(6)
          .tickSize(-innerHeight)
          .tickFormat(() => "")
      )
      .attr("transform", `translate(0,${innerHeight})`)
      .select(".domain")
      .remove();
    root.selectAll(".grid line").attr("stroke", "#f3f4f6").attr("stroke-dasharray", "3,3");

    // Y axis labels
    root
      .selectAll<SVGTextElement, BeanTiming>(".y-label")
      .data(sorted)
      .enter()
      .append("text")
      .attr("class", "y-label")
      .attr("x", -8)
      .attr("y", (_, i) => i * ROW_HEIGHT + ROW_HEIGHT / 2 + 4)
      .attr("text-anchor", "end")
      .attr("font-size", "11px")
      .attr("fill", (d) => (d.is_bottleneck ? "#dc2626" : "#374151"))
      .text((d) => shortClassName(d.bean_name).slice(0, 28));

    // Bars
    const barsGroup = root.append("g").attr("class", "bars");

    barsGroup
      .selectAll<SVGRectElement, BeanTiming>("rect")
      .data(sorted)
      .enter()
      .append("rect")
      .attr("x", (d) => xScale(d.start_ms))
      .attr("y", (_, i) => i * ROW_HEIGHT + 2)
      .attr("width", (d) =>
        Math.max(MIN_BAR_WIDTH, xScale(d.start_ms + d.duration_ms) - xScale(d.start_ms))
      )
      .attr("height", ROW_HEIGHT - 4)
      .attr("rx", 2)
      .attr("fill", (d) => getBeanColor(d))
      .attr("opacity", 0.85)
      .attr("cursor", "pointer")
      .attr("role", "img")
      .attr("aria-label", (d) => `${d.bean_name}: ${formatMs(d.duration_ms)}`)
      .on("mouseenter", function (event: MouseEvent, d: BeanTiming) {
        d3.select(this).attr("opacity", 1).attr("stroke", "#1e40af").attr("stroke-width", 1.5);
        const rect = svgRef.current?.getBoundingClientRect();
        if (rect) {
          setTooltip({
            bean: d,
            x: event.clientX - rect.left,
            y: event.clientY - rect.top,
          });
        }
      })
      .on("mouseleave", function () {
        d3.select(this).attr("opacity", 0.85).attr("stroke", null);
        setTooltip(null);
      });
  }, [beans, totalStartupMs, width]);

  useEffect(() => {
    drawChart();
  }, [drawChart]);

  const innerHeight = beans.length * ROW_HEIGHT;
  const totalHeight = innerHeight + MARGIN.top + MARGIN.bottom;

  return (
    <div ref={containerRef} className="relative w-full overflow-x-auto">
      {/* Legend */}
      <div className="mb-3 flex flex-wrap gap-4">
        {[
          { color: "#ef4444", label: "Bottleneck" },
          { color: "#f97316", label: "Post-processor" },
          { color: "#3b82f6", label: "Normal" },
        ].map(({ color, label }) => (
          <div key={label} className="flex items-center gap-1.5">
            <div className="h-3 w-3 rounded-sm" style={{ backgroundColor: color }} aria-hidden="true" />
            <span className="text-xs text-gray-600">{label}</span>
          </div>
        ))}
        <span className="ml-auto text-xs text-gray-500">Scroll to zoom, drag to pan</span>
      </div>

      <svg
        ref={svgRef}
        width={width}
        height={totalHeight}
        role="img"
        aria-label={`Startup timeline for ${beans.length} beans over ${formatMs(totalStartupMs)}`}
        className="block"
      />

      {/* Hover tooltip */}
      {tooltip && (
        <div
          className="pointer-events-none absolute z-10 max-w-xs rounded-lg border bg-white p-3 shadow-xl text-sm"
          style={{
            left: Math.min(tooltip.x + 12, width - 260),
            top: tooltip.y - 10,
          }}
          role="tooltip"
        >
          <p className="font-semibold text-gray-900 break-all">{tooltip.bean.bean_name}</p>
          <p className="mt-1 text-xs text-gray-500 break-all">{tooltip.bean.class_name}</p>
          <div className="mt-2 grid grid-cols-2 gap-x-4 gap-y-1 text-xs">
            <span className="text-gray-500">Duration</span>
            <span className={cn("font-medium", tooltip.bean.is_bottleneck ? "text-red-600" : "text-gray-900")}>
              {formatMs(tooltip.bean.duration_ms)}
            </span>
            <span className="text-gray-500">Start</span>
            <span className="font-medium text-gray-900">{formatMs(tooltip.bean.start_ms)}</span>
          </div>
          {tooltip.bean.dependencies.length > 0 && (
            <div className="mt-2">
              <p className="text-xs font-medium text-gray-700">Dependencies ({tooltip.bean.dependencies.length})</p>
              <ul className="mt-1 text-xs text-gray-500">
                {tooltip.bean.dependencies.slice(0, 4).map((dep) => (
                  <li key={dep} className="truncate">• {dep}</li>
                ))}
                {tooltip.bean.dependencies.length > 4 && (
                  <li className="text-gray-400">+{tooltip.bean.dependencies.length - 4} more</li>
                )}
              </ul>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
