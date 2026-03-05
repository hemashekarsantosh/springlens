import React from "react";
import { redirect } from "next/navigation";
import { auth } from "@/lib/auth";
import { Sidebar } from "@/components/layout/Sidebar";
import { Header } from "@/components/layout/Header";

interface WorkspaceLayoutProps {
  children: React.ReactNode;
  params: { workspaceSlug: string };
}

/**
 * Workspace shell layout: sidebar + header.
 *
 * In production, workspaceName and projects would be fetched from the API
 * using the workspaceSlug and the user's access token from the session.
 * For this scaffold, we derive display values from the slug and use
 * placeholder project data so the shell renders without backend dependency.
 */
export default async function WorkspaceLayout({ children, params }: WorkspaceLayoutProps) {
  const session = await auth();
  if (!session) redirect("/login");

  const { workspaceSlug } = params;

  // Derive workspace name from slug for display
  const workspaceName = workspaceSlug
    .split("-")
    .map((w) => w.charAt(0).toUpperCase() + w.slice(1))
    .join(" ");

  // Placeholder projects — replaced by an API call in production
  const projects = [
    { slug: "api-service", name: "API Service" },
    { slug: "auth-service", name: "Auth Service" },
    { slug: "notification-service", name: "Notification Service" },
  ];

  return (
    <div className="flex h-screen overflow-hidden">
      <Sidebar
        workspaceSlug={workspaceSlug}
        workspaceName={workspaceName}
        projects={projects}
      />
      <div className="flex flex-1 flex-col overflow-hidden">
        <Header
          userName={session.user?.name ?? null}
          userEmail={session.user?.email ?? null}
          userImage={session.user?.image ?? null}
          pageTitle={workspaceName}
        />
        <main
          className="flex-1 overflow-y-auto p-6"
          id="main-content"
          tabIndex={-1}
        >
          {children}
        </main>
      </div>
    </div>
  );
}
