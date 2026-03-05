import React from "react";
import { Badge } from "@/components/ui/badge";
import type { RecommendationCategory } from "@/types/api";

interface CategoryBadgeProps {
  category: RecommendationCategory;
}

const CATEGORY_CONFIG: Record<
  RecommendationCategory,
  { label: string; variant: "info" | "warning" | "purple" | "success" | "danger" }
> = {
  lazy_loading: { label: "Lazy Loading", variant: "info" },
  aot_compilation: { label: "AOT Compilation", variant: "warning" },
  graalvm_native: { label: "GraalVM Native", variant: "purple" },
  classpath_optimization: { label: "Classpath", variant: "success" },
  dependency_removal: { label: "Dependency Removal", variant: "danger" },
};

export function CategoryBadge({ category }: CategoryBadgeProps) {
  const config = CATEGORY_CONFIG[category];
  return <Badge variant={config.variant}>{config.label}</Badge>;
}
