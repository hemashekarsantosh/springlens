"use client";

import React, { useEffect, useRef, useState, useCallback } from "react";
import * as d3 from "d3";
import type { BeanGraphNode, BeanGraphEdge } from "@/types/api";
import { formatMs, shortClassName } from "@/lib/utils";
import { ZoomIn, ZoomOut, Maximize2 } from "lucide-react";
import { Button } from "@/components/ui/button";

interface BeanDependencyGraphProps {
  nodes: BeanGraphNode[];
  edges: BeanGraphEdge[];
}

interface SelectedNode extends BeanGraphNode {
  dependencyCount: number;
}

interface SimNode extends d3.SimulationNodeDatum, BeanGraphNode {
  x?: number;
  y?: number;
}

interface SimLink extends d3.SimulationLinkDatum<SimNode> {
  source: SimNode | string;
  target: SimNode | string;
}

function nodeRadius(durationMs: number, maxDuration: number): number {
  const minR = 6;
  const maxR = 28;
  if (maxDuration === 0) return minR;
  return minR + ((durationMs / maxDuration) * (maxR - minR));
}

function nodeColor(node: BeanGraphNode): string {
  return node.is_bottleneck ? "#ef4444" : "#3b82f6";
}

export function BeanDependencyGraph({ nodes, edges }: BeanDependencyGraphProps) {
  const svgRef = useRef<SVGSVGElement>(null);
  const containerRef = useRef<HTMLDivElement>(null);
  const simRef = useRef<d3.Simulation<SimNode, SimLink> | null>(null);
  const [selectedNode, setSelectedNode] = useState<SelectedNode | null>(null);
  const [dimensions, setDimensions] = useState({ width: 800, height: 600 });
  const zoomRef = useRef<d3.ZoomBehavior<SVGSVGElement, unknown> | null>(null);

  useEffect(() => {
    const container = containerRef.current;
    if (!container) return;
    const observer = new ResizeObserver((entries) => {
      const entry = entries[0];
      if (entry) {
        setDimensions({
          width: entry.contentRect.width,
          height: Math.max(500, entry.contentRect.height),
        });
      }
    });
    observer.observe(container);
    setDimensions({ width: container.clientWidth, height: Math.max(500, container.clientHeight) });
    return () => observer.disconnect();
  }, []);

  const buildGraph = useCallback(() => {
    const svg = svgRef.current;
    if (!svg || nodes.length === 0) return;

    const { width, height } = dimensions;
    const maxDuration = Math.max(...nodes.map((n) => n.duration_ms), 1);

    d3.select(svg).selectAll("*").remove();

    const zoom = d3
      .zoom<SVGSVGElement, unknown>()
      .scaleExtent([0.1, 5])
      .on("zoom", (event: d3.D3ZoomEvent<SVGSVGElement, unknown>) => {
        g.attr("transform", event.transform.toString());
      });
    zoomRef.current = zoom;
    d3.select(svg).call(zoom);

    const g = d3.select(svg).append("g");

    // Arrow marker
    d3.select(svg)
      .append("defs")
      .append("marker")
      .attr("id", "arrow")
      .attr("viewBox", "0 -5 10 10")
      .attr("refX", 20)
      .attr("refY", 0)
      .attr("markerWidth", 6)
      .attr("markerHeight", 6)
      .attr("orient", "auto")
      .append("path")
      .attr("d", "M0,-5L10,0L0,5")
      .attr("fill", "#9ca3af");

    const simNodes: SimNode[] = nodes.map((n) => ({ ...n }));
    const nodeById = new Map(simNodes.map((n) => [n.id, n]));

    const simLinks: SimLink[] = edges
      .filter((e) => nodeById.has(e.source) && nodeById.has(e.target))
      .map((e) => ({
        source: nodeById.get(e.source)!,
        target: nodeById.get(e.target)!,
      }));

    const simulation = d3
      .forceSimulation<SimNode>(simNodes)
      .force(
        "link",
        d3
          .forceLink<SimNode, SimLink>(simLinks)
          .id((d) => d.id)
          .distance(80)
      )
      .force("charge", d3.forceManyBody().strength(-200))
      .force("center", d3.forceCenter(width / 2, height / 2))
      .force("collision", d3.forceCollide<SimNode>().radius((d) => nodeRadius(d.duration_ms, maxDuration) + 4));

    simRef.current = simulation;

    // Links
    const link = g
      .append("g")
      .attr("class", "links")
      .selectAll<SVGLineElement, SimLink>("line")
      .data(simLinks)
      .enter()
      .append("line")
      .attr("stroke", "#d1d5db")
      .attr("stroke-width", 1.5)
      .attr("marker-end", "url(#arrow)");

    // Nodes
    const node = g
      .append("g")
      .attr("class", "nodes")
      .selectAll<SVGCircleElement, SimNode>("circle")
      .data(simNodes)
      .enter()
      .append("circle")
      .attr("r", (d) => nodeRadius(d.duration_ms, maxDuration))
      .attr("fill", (d) => nodeColor(d))
      .attr("fill-opacity", 0.85)
      .attr("stroke", "#fff")
      .attr("stroke-width", 2)
      .attr("cursor", "pointer")
      .attr("role", "button")
      .attr("aria-label", (d) => `Bean: ${d.label}, ${formatMs(d.duration_ms)}`)
      .call(
        d3
          .drag<SVGCircleElement, SimNode>()
          .on("start", (event, d) => {
            if (!event.active) simulation.alphaTarget(0.3).restart();
            d.fx = d.x;
            d.fy = d.y;
          })
          .on("drag", (event, d) => {
            d.fx = event.x;
            d.fy = event.y;
          })
          .on("end", (event, d) => {
            if (!event.active) simulation.alphaTarget(0);
            d.fx = null;
            d.fy = null;
          })
      )
      .on("click", (_event, d) => {
        const depCount = simLinks.filter(
          (l) => (l.source as SimNode).id === d.id || (l.target as SimNode).id === d.id
        ).length;
        setSelectedNode({ ...d, dependencyCount: depCount });
      });

    // Labels
    const labels = g
      .append("g")
      .attr("class", "labels")
      .selectAll<SVGTextElement, SimNode>("text")
      .data(simNodes)
      .enter()
      .append("text")
      .attr("text-anchor", "middle")
      .attr("dy", (d) => nodeRadius(d.duration_ms, maxDuration) + 12)
      .attr("font-size", "9px")
      .attr("fill", "#374151")
      .attr("pointer-events", "none")
      .text((d) => shortClassName(d.label).slice(0, 16));

    simulation.on("tick", () => {
      link
        .attr("x1", (d) => (d.source as SimNode).x ?? 0)
        .attr("y1", (d) => (d.source as SimNode).y ?? 0)
        .attr("x2", (d) => (d.target as SimNode).x ?? 0)
        .attr("y2", (d) => (d.target as SimNode).y ?? 0);

      node.attr("cx", (d) => d.x ?? 0).attr("cy", (d) => d.y ?? 0);
      labels.attr("x", (d) => d.x ?? 0).attr("y", (d) => d.y ?? 0);
    });

    return () => {
      simulation.stop();
    };
  }, [nodes, edges, dimensions]);

  useEffect(() => {
    return buildGraph();
  }, [buildGraph]);

  const handleZoomIn = () => {
    if (svgRef.current && zoomRef.current) {
      d3.select(svgRef.current).transition().call(zoomRef.current.scaleBy, 1.5);
    }
  };

  const handleZoomOut = () => {
    if (svgRef.current && zoomRef.current) {
      d3.select(svgRef.current).transition().call(zoomRef.current.scaleBy, 0.67);
    }
  };

  const handleReset = () => {
    if (svgRef.current && zoomRef.current) {
      d3.select(svgRef.current).transition().call(zoomRef.current.transform, d3.zoomIdentity);
    }
  };

  return (
    <div className="flex gap-4">
      {/* Graph */}
      <div ref={containerRef} className="relative flex-1 overflow-hidden rounded-lg border bg-white" style={{ minHeight: 500 }}>
        {/* Zoom controls */}
        <div className="absolute right-3 top-3 z-10 flex flex-col gap-1">
          <Button variant="outline" size="icon" onClick={handleZoomIn} aria-label="Zoom in" className="h-8 w-8">
            <ZoomIn className="h-4 w-4" />
          </Button>
          <Button variant="outline" size="icon" onClick={handleZoomOut} aria-label="Zoom out" className="h-8 w-8">
            <ZoomOut className="h-4 w-4" />
          </Button>
          <Button variant="outline" size="icon" onClick={handleReset} aria-label="Reset zoom" className="h-8 w-8">
            <Maximize2 className="h-4 w-4" />
          </Button>
        </div>

        {/* Legend */}
        <div className="absolute left-3 top-3 z-10 flex gap-3 rounded-md border bg-white/90 px-3 py-2 backdrop-blur-sm">
          {[
            { color: "#ef4444", label: "Bottleneck" },
            { color: "#3b82f6", label: "Normal" },
          ].map(({ color, label }) => (
            <div key={label} className="flex items-center gap-1.5">
              <div className="h-3 w-3 rounded-full" style={{ backgroundColor: color }} aria-hidden="true" />
              <span className="text-xs text-gray-600">{label}</span>
            </div>
          ))}
          <span className="text-xs text-gray-400">Node size = duration</span>
        </div>

        <svg
          ref={svgRef}
          width={dimensions.width}
          height={dimensions.height}
          role="img"
          aria-label={`Bean dependency graph with ${nodes.length} nodes and ${edges.length} edges`}
          className="block"
        />
      </div>

      {/* Detail panel */}
      {selectedNode && (
        <div className="w-72 flex-shrink-0 rounded-lg border bg-white p-4">
          <div className="mb-3 flex items-start justify-between">
            <h3 className="text-sm font-semibold text-gray-900 break-all">{selectedNode.label}</h3>
            <button
              onClick={() => setSelectedNode(null)}
              className="ml-2 text-gray-400 hover:text-gray-600"
              aria-label="Close details panel"
            >
              ×
            </button>
          </div>
          <dl className="space-y-2 text-sm">
            <div>
              <dt className="text-xs font-medium text-gray-500">Class</dt>
              <dd className="mt-0.5 break-all text-gray-900 text-xs">{selectedNode.class_name}</dd>
            </div>
            <div>
              <dt className="text-xs font-medium text-gray-500">Duration</dt>
              <dd className={`mt-0.5 font-semibold ${selectedNode.is_bottleneck ? "text-red-600" : "text-gray-900"}`}>
                {formatMs(selectedNode.duration_ms)}
              </dd>
            </div>
            <div>
              <dt className="text-xs font-medium text-gray-500">Connections</dt>
              <dd className="mt-0.5 text-gray-900">{selectedNode.dependencyCount}</dd>
            </div>
            <div>
              <dt className="text-xs font-medium text-gray-500">Status</dt>
              <dd className={`mt-0.5 font-medium ${selectedNode.is_bottleneck ? "text-red-600" : "text-green-600"}`}>
                {selectedNode.is_bottleneck ? "Bottleneck" : "Normal"}
              </dd>
            </div>
          </dl>
        </div>
      )}
    </div>
  );
}
