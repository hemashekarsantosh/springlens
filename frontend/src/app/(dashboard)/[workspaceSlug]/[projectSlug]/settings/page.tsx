"use client";

import React, { useState } from "react";
import { useParams } from "next/navigation";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { PlusCircle, Trash2 } from "lucide-react";
import { listCiBudgets, upsertCiBudget, recommendationKeys } from "@/lib/api/recommendations";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Badge } from "@/components/ui/badge";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Skeleton } from "@/components/ui/skeleton";
import { formatMs, formatDateTime } from "@/lib/utils";
import type { CiBudgetRequest } from "@/types/api";

const PLACEHOLDER_PROJECT_ID = "00000000-0000-0000-0000-000000000001";

function CiBudgetsSection() {
  const queryClient = useQueryClient();
  const [showForm, setShowForm] = useState(false);
  const [form, setForm] = useState<Partial<CiBudgetRequest>>({
    environment: "ci",
    budget_ms: 10000,
    enabled: true,
  });

  const { data, isLoading } = useQuery({
    queryKey: recommendationKeys.ciBudgets(PLACEHOLDER_PROJECT_ID),
    queryFn: () => listCiBudgets(PLACEHOLDER_PROJECT_ID),
  });

  const mutation = useMutation({
    mutationFn: (body: CiBudgetRequest) => upsertCiBudget(PLACEHOLDER_PROJECT_ID, body),
    onSuccess: () => {
      void queryClient.invalidateQueries({
        queryKey: recommendationKeys.ciBudgets(PLACEHOLDER_PROJECT_ID),
      });
      setShowForm(false);
    },
  });

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!form.environment || !form.budget_ms) return;
    mutation.mutate({
      environment: form.environment as CiBudgetRequest["environment"],
      budget_ms: form.budget_ms,
      alert_threshold_ms: form.alert_threshold_ms ?? null,
      enabled: form.enabled ?? true,
    });
  };

  return (
    <Card>
      <CardHeader>
        <div className="flex items-center justify-between">
          <div>
            <CardTitle>CI/CD Startup Budgets</CardTitle>
            <CardDescription>
              Fail CI when startup exceeds the configured budget. Requires Admin role for production.
            </CardDescription>
          </div>
          <Button
            size="sm"
            onClick={() => setShowForm(!showForm)}
            aria-label="Add new CI budget"
          >
            <PlusCircle className="mr-1.5 h-4 w-4" aria-hidden="true" />
            Add Budget
          </Button>
        </div>
      </CardHeader>
      <CardContent className="space-y-4">
        {showForm && (
          <form
            onSubmit={handleSubmit}
            className="rounded-lg border bg-gray-50 p-4 space-y-3"
            aria-label="New CI budget form"
          >
            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-1.5">
                <Label htmlFor="budget-env">Environment</Label>
                <Select
                  value={form.environment}
                  onValueChange={(v) =>
                    setForm((f) => ({
                      ...f,
                      environment: v as CiBudgetRequest["environment"],
                    }))
                  }
                >
                  <SelectTrigger id="budget-env" aria-label="Budget environment">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="dev">Development</SelectItem>
                    <SelectItem value="staging">Staging</SelectItem>
                    <SelectItem value="production">Production</SelectItem>
                    <SelectItem value="ci">CI</SelectItem>
                  </SelectContent>
                </Select>
              </div>

              <div className="space-y-1.5">
                <Label htmlFor="budget-ms">Budget (ms)</Label>
                <Input
                  id="budget-ms"
                  type="number"
                  min={100}
                  max={300000}
                  value={form.budget_ms ?? ""}
                  onChange={(e) => setForm((f) => ({ ...f, budget_ms: Number(e.target.value) }))}
                  aria-label="Startup budget in milliseconds"
                />
              </div>

              <div className="space-y-1.5">
                <Label htmlFor="alert-threshold">Alert Threshold (ms, optional)</Label>
                <Input
                  id="alert-threshold"
                  type="number"
                  min={100}
                  max={300000}
                  value={form.alert_threshold_ms ?? ""}
                  onChange={(e) =>
                    setForm((f) => ({
                      ...f,
                      alert_threshold_ms: e.target.value ? Number(e.target.value) : undefined,
                    }))
                  }
                  placeholder="Warn at this threshold"
                  aria-label="Alert threshold in milliseconds"
                />
              </div>
            </div>

            <div className="flex gap-2">
              <Button type="submit" size="sm" disabled={mutation.isPending}>
                {mutation.isPending ? "Saving..." : "Save Budget"}
              </Button>
              <Button
                type="button"
                variant="ghost"
                size="sm"
                onClick={() => setShowForm(false)}
              >
                Cancel
              </Button>
            </div>
          </form>
        )}

        {isLoading ? (
          <Skeleton className="h-24" />
        ) : data?.budgets.length === 0 ? (
          <p className="text-sm text-gray-500">No CI/CD budgets configured.</p>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm" role="table">
              <thead>
                <tr className="border-b bg-gray-50 text-left text-xs font-semibold text-gray-600">
                  <th className="px-4 py-2" scope="col">Environment</th>
                  <th className="px-4 py-2" scope="col">Budget</th>
                  <th className="px-4 py-2" scope="col">Alert At</th>
                  <th className="px-4 py-2" scope="col">Status</th>
                  <th className="px-4 py-2" scope="col">Updated</th>
                </tr>
              </thead>
              <tbody>
                {data?.budgets.map((b) => (
                  <tr key={b.budget_id} className="border-b last:border-0">
                    <td className="px-4 py-2">
                      <Badge variant="secondary">{b.environment}</Badge>
                    </td>
                    <td className="px-4 py-2 font-semibold">{formatMs(b.budget_ms)}</td>
                    <td className="px-4 py-2 text-gray-500">{formatMs(b.alert_threshold_ms)}</td>
                    <td className="px-4 py-2">
                      <Badge variant={b.enabled ? "success" : "secondary"}>
                        {b.enabled ? "Active" : "Disabled"}
                      </Badge>
                    </td>
                    <td className="px-4 py-2 text-xs text-gray-400">
                      {formatDateTime(b.updated_at)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </CardContent>
    </Card>
  );
}

export default function ProjectSettingsPage() {
  const params = useParams<{ workspaceSlug: string; projectSlug: string }>();

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-2xl font-bold text-gray-900">Project Settings</h2>
        <p className="mt-1 text-sm text-gray-500">
          Configure CI/CD budgets, webhooks, and project details.
        </p>
      </div>

      <Tabs defaultValue="general">
        <TabsList>
          <TabsTrigger value="general">General</TabsTrigger>
          <TabsTrigger value="ci-budgets">CI/CD Budgets</TabsTrigger>
          <TabsTrigger value="webhooks">Webhooks</TabsTrigger>
        </TabsList>

        <TabsContent value="general" className="mt-4">
          <Card>
            <CardHeader>
              <CardTitle>Project Details</CardTitle>
              <CardDescription>Update project name and configuration.</CardDescription>
            </CardHeader>
            <CardContent className="space-y-4">
              <div className="space-y-1.5">
                <Label htmlFor="project-name">Project Name</Label>
                <Input
                  id="project-name"
                  defaultValue={params.projectSlug.replace(/-/g, " ")}
                  className="max-w-sm"
                  aria-label="Project name"
                />
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="project-id-display">Project ID</Label>
                <Input
                  id="project-id-display"
                  value={PLACEHOLDER_PROJECT_ID}
                  readOnly
                  className="max-w-sm font-mono text-xs text-gray-500"
                  aria-label="Project ID (read-only)"
                />
                <p className="text-xs text-gray-500">
                  Use this in SPRINGLENS_PROJECT environment variable
                </p>
              </div>
              <Button>Save Changes</Button>
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="ci-budgets" className="mt-4">
          <CiBudgetsSection />
        </TabsContent>

        <TabsContent value="webhooks" className="mt-4">
          <Card>
            <CardHeader>
              <CardTitle>Webhook Notifications</CardTitle>
              <CardDescription>
                Send startup regression alerts to Slack or custom endpoints.
              </CardDescription>
            </CardHeader>
            <CardContent className="space-y-4">
              <div className="space-y-1.5">
                <Label htmlFor="slack-url">Slack Webhook URL</Label>
                <Input
                  id="slack-url"
                  type="url"
                  placeholder="https://hooks.slack.com/services/..."
                  className="max-w-lg"
                  aria-label="Slack incoming webhook URL"
                />
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="custom-webhook">Custom Webhook URL</Label>
                <Input
                  id="custom-webhook"
                  type="url"
                  placeholder="https://your-endpoint.example.com/webhook"
                  className="max-w-lg"
                  aria-label="Custom webhook endpoint URL"
                />
              </div>
              <Button>Save Webhooks</Button>
            </CardContent>
          </Card>
        </TabsContent>
      </Tabs>
    </div>
  );
}
