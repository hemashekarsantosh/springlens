import React from "react";
import { redirect } from "next/navigation";
import { auth } from "@/lib/auth";
import { Header } from "@/components/layout/Header";

export default async function DashboardRootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  const session = await auth();
  if (!session) redirect("/login");

  return (
    <div className="flex min-h-screen flex-col bg-gray-50">
      {/*
        The nested [workspaceSlug] layout renders the sidebar.
        This root layout only provides auth guard and the header shell
        for pages that don't have a full workspace context yet
        (e.g., /dashboard redirect page).
      */}
      {children}
    </div>
  );
}
