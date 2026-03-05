"use client";

import React, { useState } from "react";
import { ChevronDown, ChevronUp, AlertTriangle, CheckCircle2, XCircle, RotateCcw } from "lucide-react";
import SyntaxHighlighter from "react-syntax-highlighter";
import { githubGist } from "react-syntax-highlighter/dist/esm/styles/hljs";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import type { Recommendation, RecommendationStatus } from "@/types/api";
import { updateRecommendationStatus } from "@/lib/api/recommendations";
import { recommendationKeys } from "@/lib/api/recommendations";
import { CategoryBadge } from "./CategoryBadge";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { formatMs, cn } from "@/lib/utils";

interface RecommendationCardProps {
  recommendation: Recommendation;
  projectId: string;
}

const EFFORT_CONFIG: Record<
  Recommendation["effort"],
  { label: string; className: string }
> = {
  low: { label: "Low Effort", className: "bg-green-100 text-green-800" },
  medium: { label: "Medium Effort", className: "bg-yellow-100 text-yellow-800" },
  high: { label: "High Effort", className: "bg-red-100 text-red-800" },
};

function detectLanguage(snippet: string): string {
  if (snippet.trim().startsWith("<") || snippet.includes("xmlns")) return "xml";
  if (snippet.includes("spring.") || snippet.includes("=")) return "properties";
  return "java";
}

export function RecommendationCard({ recommendation: rec, projectId }: RecommendationCardProps) {
  const [expanded, setExpanded] = useState(false);
  const queryClient = useQueryClient();

  const mutation = useMutation({
    mutationFn: (status: RecommendationStatus) =>
      updateRecommendationStatus(projectId, rec.recommendation_id, { status }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: recommendationKeys.list(projectId) });
    },
  });

  const effortConfig = EFFORT_CONFIG[rec.effort];
  const snippet = rec.code_snippet ?? rec.config_snippet;

  return (
    <article
      className={cn(
        "rounded-lg border bg-white p-5 transition-shadow hover:shadow-md",
        rec.status === "applied" && "border-green-200 bg-green-50/30",
        rec.status === "wont_fix" && "opacity-60"
      )}
      aria-label={`Recommendation ${rec.rank}: ${rec.title}`}
    >
      {/* Header row */}
      <div className="flex items-start gap-3">
        {/* Rank */}
        <div className="flex h-7 w-7 flex-shrink-0 items-center justify-center rounded-full bg-blue-100 text-xs font-bold text-blue-700">
          {rec.rank}
        </div>

        {/* Title & badges */}
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-2">
            <CategoryBadge category={rec.category} />
            <span
              className={cn(
                "inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium",
                effortConfig.className
              )}
            >
              {effortConfig.label}
            </span>
            {rec.status === "applied" && (
              <Badge variant="success" className="gap-1">
                <CheckCircle2 className="h-3 w-3" aria-hidden="true" />
                Applied
              </Badge>
            )}
            {rec.status === "wont_fix" && (
              <Badge variant="secondary">Won&apos;t Fix</Badge>
            )}
          </div>
          <h3 className="mt-1.5 text-base font-semibold text-gray-900">{rec.title}</h3>
        </div>

        {/* Savings */}
        <div className="flex-shrink-0 text-right">
          <p className="text-lg font-bold text-green-600">-{formatMs(rec.estimated_savings_ms)}</p>
          <p className="text-xs text-gray-500">{rec.estimated_savings_percent.toFixed(1)}% faster</p>
        </div>
      </div>

      {/* Description */}
      <p className="mt-3 text-sm text-gray-600">{rec.description}</p>

      {/* Affected beans */}
      {rec.affected_beans.length > 0 && (
        <div className="mt-2 flex flex-wrap gap-1">
          {rec.affected_beans.slice(0, 5).map((bean) => (
            <span key={bean} className="rounded bg-gray-100 px-1.5 py-0.5 text-xs text-gray-600">
              {bean}
            </span>
          ))}
          {rec.affected_beans.length > 5 && (
            <span className="text-xs text-gray-400">+{rec.affected_beans.length - 5} more</span>
          )}
        </div>
      )}

      {/* Warnings */}
      {rec.warnings.length > 0 && (
        <div className="mt-3 rounded-md border border-yellow-200 bg-yellow-50 p-3">
          <div className="flex items-center gap-1.5 text-yellow-800">
            <AlertTriangle className="h-4 w-4 flex-shrink-0" aria-hidden="true" />
            <span className="text-xs font-semibold">Compatibility Warnings</span>
          </div>
          <ul className="mt-1.5 space-y-0.5">
            {rec.warnings.map((warning, i) => (
              <li key={i} className="text-xs text-yellow-700">
                {warning}
              </li>
            ))}
          </ul>
        </div>
      )}

      {/* GraalVM feasibility */}
      {rec.graalvm_feasibility && (
        <div className={cn(
          "mt-3 rounded-md border p-3 text-xs",
          rec.graalvm_feasibility.feasible
            ? "border-green-200 bg-green-50 text-green-800"
            : "border-red-200 bg-red-50 text-red-800"
        )}>
          <p className="font-semibold">
            GraalVM Native: {rec.graalvm_feasibility.feasible ? "Feasible" : "Not Feasible"}
          </p>
          {rec.graalvm_feasibility.estimated_native_startup_ms !== null && (
            <p>Estimated native startup: {formatMs(rec.graalvm_feasibility.estimated_native_startup_ms)}</p>
          )}
          {rec.graalvm_feasibility.blockers.length > 0 && (
            <ul className="mt-1 space-y-0.5">
              {rec.graalvm_feasibility.blockers.map((b, i) => (
                <li key={i}>• {b}</li>
              ))}
            </ul>
          )}
        </div>
      )}

      {/* Expandable code snippet */}
      {snippet && (
        <div className="mt-3">
          <button
            onClick={() => setExpanded(!expanded)}
            className="flex items-center gap-1 text-xs font-medium text-blue-600 hover:text-blue-800"
            aria-expanded={expanded}
            aria-controls={`snippet-${rec.recommendation_id}`}
          >
            {expanded ? (
              <ChevronUp className="h-3.5 w-3.5" aria-hidden="true" />
            ) : (
              <ChevronDown className="h-3.5 w-3.5" aria-hidden="true" />
            )}
            {expanded ? "Hide" : "Show"} code snippet
          </button>

          {expanded && (
            <div
              id={`snippet-${rec.recommendation_id}`}
              className="mt-2 overflow-hidden rounded-md border"
            >
              <SyntaxHighlighter
                language={detectLanguage(snippet)}
                style={githubGist}
                customStyle={{ margin: 0, fontSize: "12px", maxHeight: "300px" }}
                showLineNumbers
              >
                {snippet}
              </SyntaxHighlighter>
            </div>
          )}
        </div>
      )}

      {/* Action buttons */}
      {rec.status === "active" && (
        <div className="mt-4 flex gap-2">
          <Button
            size="sm"
            variant="outline"
            className="border-green-300 text-green-700 hover:bg-green-50"
            onClick={() => mutation.mutate("applied")}
            disabled={mutation.isPending}
            aria-label={`Mark recommendation "${rec.title}" as applied`}
          >
            <CheckCircle2 className="mr-1.5 h-4 w-4" aria-hidden="true" />
            Mark as Applied
          </Button>
          <Button
            size="sm"
            variant="ghost"
            className="text-gray-500 hover:text-gray-700"
            onClick={() => mutation.mutate("wont_fix")}
            disabled={mutation.isPending}
            aria-label={`Mark recommendation "${rec.title}" as won't fix`}
          >
            <XCircle className="mr-1.5 h-4 w-4" aria-hidden="true" />
            Won&apos;t Fix
          </Button>
        </div>
      )}

      {(rec.status === "applied" || rec.status === "wont_fix") && (
        <div className="mt-4">
          <Button
            size="sm"
            variant="ghost"
            className="text-gray-500 hover:text-gray-700"
            onClick={() => mutation.mutate("active")}
            disabled={mutation.isPending}
            aria-label={`Reactivate recommendation "${rec.title}"`}
          >
            <RotateCcw className="mr-1.5 h-4 w-4" aria-hidden="true" />
            Reactivate
          </Button>
        </div>
      )}
    </article>
  );
}
