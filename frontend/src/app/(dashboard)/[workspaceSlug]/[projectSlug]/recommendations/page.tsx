"use client";

import React from "react";
import { RecommendationList } from "@/components/recommendations/RecommendationList";

const PLACEHOLDER_PROJECT_ID = "00000000-0000-0000-0000-000000000001";

export default function RecommendationsPage() {
  return (
    <div className="space-y-4">
      <div>
        <h2 className="text-2xl font-bold text-gray-900">Recommendations</h2>
        <p className="mt-1 text-sm text-gray-500">
          Ranked optimization suggestions based on your latest startup snapshot.
        </p>
      </div>
      <RecommendationList projectId={PLACEHOLDER_PROJECT_ID} />
    </div>
  );
}
