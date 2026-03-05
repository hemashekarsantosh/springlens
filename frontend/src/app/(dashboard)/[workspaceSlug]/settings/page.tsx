import React from "react";
import type { Metadata } from "next";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Badge } from "@/components/ui/badge";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";

export const metadata: Metadata = { title: "Workspace Settings" };

interface PageProps {
  params: Promise<{ workspaceSlug: string }>;
}

export default async function WorkspaceSettingsPage({ params }: PageProps) {
  const { workspaceSlug } = await params;

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-2xl font-bold text-gray-900">Workspace Settings</h2>
        <p className="mt-1 text-sm text-gray-500">
          Manage your workspace, members, and billing.
        </p>
      </div>

      <Tabs defaultValue="general">
        <TabsList>
          <TabsTrigger value="general">General</TabsTrigger>
          <TabsTrigger value="members">Members</TabsTrigger>
          <TabsTrigger value="billing">Billing</TabsTrigger>
          <TabsTrigger value="api-keys">API Keys</TabsTrigger>
        </TabsList>

        {/* General */}
        <TabsContent value="general" className="mt-4 space-y-4">
          <Card>
            <CardHeader>
              <CardTitle>Workspace Details</CardTitle>
              <CardDescription>Update your workspace name and settings.</CardDescription>
            </CardHeader>
            <CardContent className="space-y-4">
              <div className="space-y-1.5">
                <Label htmlFor="ws-name">Workspace Name</Label>
                <Input
                  id="ws-name"
                  defaultValue={workspaceSlug
                    .split("-")
                    .map((w) => w.charAt(0).toUpperCase() + w.slice(1))
                    .join(" ")}
                  className="max-w-sm"
                  aria-label="Workspace name"
                />
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="ws-slug">Workspace Slug</Label>
                <Input
                  id="ws-slug"
                  defaultValue={workspaceSlug}
                  className="max-w-sm font-mono text-sm"
                  aria-label="Workspace URL slug"
                />
                <p className="text-xs text-gray-500">Used in your dashboard URL</p>
              </div>
              <Button>Save Changes</Button>
            </CardContent>
          </Card>

          <Card className="border-red-200">
            <CardHeader>
              <CardTitle className="text-red-700">Danger Zone</CardTitle>
              <CardDescription>Irreversible actions for this workspace.</CardDescription>
            </CardHeader>
            <CardContent>
              <Button variant="destructive" aria-label="Delete workspace permanently">
                Delete Workspace
              </Button>
            </CardContent>
          </Card>
        </TabsContent>

        {/* Members */}
        <TabsContent value="members" className="mt-4">
          <Card>
            <CardHeader>
              <CardTitle>Team Members</CardTitle>
              <CardDescription>Manage access to your workspace.</CardDescription>
            </CardHeader>
            <CardContent className="space-y-4">
              <div className="flex gap-2">
                <Input
                  placeholder="colleague@example.com"
                  className="flex-1"
                  aria-label="Invite member email address"
                />
                <Button>Invite</Button>
              </div>

              <div className="rounded-lg border">
                <table className="w-full text-sm" role="table">
                  <thead>
                    <tr className="border-b bg-gray-50 text-left text-xs font-semibold text-gray-600">
                      <th className="px-4 py-3" scope="col">Member</th>
                      <th className="px-4 py-3" scope="col">Role</th>
                      <th className="px-4 py-3" scope="col">Actions</th>
                    </tr>
                  </thead>
                  <tbody>
                    {[
                      { name: "Alex Developer", email: "alex@acme.io", role: "Admin" },
                      { name: "Jordan DevOps", email: "jordan@acme.io", role: "Developer" },
                    ].map((member) => (
                      <tr key={member.email} className="border-b last:border-0">
                        <td className="px-4 py-3">
                          <p className="font-medium text-gray-900">{member.name}</p>
                          <p className="text-xs text-gray-500">{member.email}</p>
                        </td>
                        <td className="px-4 py-3">
                          <Badge variant="secondary">{member.role}</Badge>
                        </td>
                        <td className="px-4 py-3">
                          <Button variant="ghost" size="sm" className="text-red-600 hover:text-red-700">
                            Remove
                          </Button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </CardContent>
          </Card>
        </TabsContent>

        {/* Billing */}
        <TabsContent value="billing" className="mt-4">
          <Card>
            <CardHeader>
              <CardTitle>Subscription</CardTitle>
              <CardDescription>Manage your SpringLens plan and billing.</CardDescription>
            </CardHeader>
            <CardContent className="space-y-4">
              <div className="flex items-center justify-between rounded-lg border p-4">
                <div>
                  <p className="font-semibold text-gray-900">Free Plan</p>
                  <p className="text-sm text-gray-500">1 project · 90-day history · 1 member</p>
                </div>
                <Button>Upgrade to Pro</Button>
              </div>
              <div className="grid grid-cols-3 gap-4 text-sm">
                {[
                  { plan: "Free", price: "$0/mo", projects: "1", members: "1", history: "90 days" },
                  { plan: "Pro", price: "$49/mo", projects: "10", members: "10", history: "1 year" },
                  { plan: "Enterprise", price: "Custom", projects: "Unlimited", members: "Unlimited", history: "Unlimited" },
                ].map((tier) => (
                  <div key={tier.plan} className="rounded-lg border p-4">
                    <p className="font-bold text-gray-900">{tier.plan}</p>
                    <p className="text-lg font-semibold text-blue-600">{tier.price}</p>
                    <ul className="mt-2 space-y-1 text-xs text-gray-500">
                      <li>{tier.projects} projects</li>
                      <li>{tier.members} members</li>
                      <li>{tier.history} history</li>
                    </ul>
                  </div>
                ))}
              </div>
            </CardContent>
          </Card>
        </TabsContent>

        {/* API Keys */}
        <TabsContent value="api-keys" className="mt-4">
          <Card>
            <CardHeader>
              <CardTitle>API Keys</CardTitle>
              <CardDescription>
                API keys authenticate the SpringLens JVM agent and CI/CD integrations.
              </CardDescription>
            </CardHeader>
            <CardContent className="space-y-4">
              <Button aria-label="Create new API key">Create New Key</Button>
              <div className="rounded-lg border">
                <table className="w-full text-sm" role="table">
                  <thead>
                    <tr className="border-b bg-gray-50 text-left text-xs font-semibold text-gray-600">
                      <th className="px-4 py-3" scope="col">Name</th>
                      <th className="px-4 py-3" scope="col">Key</th>
                      <th className="px-4 py-3" scope="col">Created</th>
                      <th className="px-4 py-3" scope="col">Actions</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr className="border-b last:border-0">
                      <td className="px-4 py-3 font-medium text-gray-900">CI Pipeline</td>
                      <td className="px-4 py-3 font-mono text-xs text-gray-500">sl_proj_••••••••••••</td>
                      <td className="px-4 py-3 text-gray-500">Mar 1, 2026</td>
                      <td className="px-4 py-3">
                        <Button variant="ghost" size="sm" className="text-red-600 hover:text-red-700">
                          Revoke
                        </Button>
                      </td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </CardContent>
          </Card>
        </TabsContent>
      </Tabs>
    </div>
  );
}
