"use client";

import React, { useState } from "react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import {
  LayoutDashboard,
  Clock,
  Share2,
  Lightbulb,
  GitCompare,
  Settings,
  ChevronDown,
  ChevronRight,
  Zap,
} from "lucide-react";
import { cn } from "@/lib/utils";

interface NavItem {
  label: string;
  href: string;
  icon: React.ComponentType<{ className?: string }>;
}

interface SidebarProps {
  workspaceSlug: string;
  projectSlug?: string;
  workspaceName: string;
  projects: Array<{ slug: string; name: string }>;
}

const projectNavItems = (workspaceSlug: string, projectSlug: string): NavItem[] => [
  {
    label: "Overview",
    href: `/${workspaceSlug}/${projectSlug}`,
    icon: LayoutDashboard,
  },
  {
    label: "Startup Timeline",
    href: `/${workspaceSlug}/${projectSlug}/timeline`,
    icon: Clock,
  },
  {
    label: "Bean Graph",
    href: `/${workspaceSlug}/${projectSlug}/bean-graph`,
    icon: Share2,
  },
  {
    label: "Recommendations",
    href: `/${workspaceSlug}/${projectSlug}/recommendations`,
    icon: Lightbulb,
  },
  {
    label: "Compare",
    href: `/${workspaceSlug}/${projectSlug}/compare`,
    icon: GitCompare,
  },
  {
    label: "Settings",
    href: `/${workspaceSlug}/${projectSlug}/settings`,
    icon: Settings,
  },
];

export function Sidebar({ workspaceSlug, projectSlug, workspaceName, projects }: SidebarProps) {
  const pathname = usePathname();
  const [expandedProject, setExpandedProject] = useState<string | null>(projectSlug ?? null);

  return (
    <aside className="flex h-full w-64 flex-col border-r bg-gray-50" aria-label="Sidebar navigation">
      {/* Logo */}
      <div className="flex h-16 items-center gap-2 border-b px-4">
        <Zap className="h-6 w-6 text-blue-600" aria-hidden="true" />
        <span className="text-lg font-bold text-gray-900">SpringLens</span>
      </div>

      {/* Workspace */}
      <div className="border-b px-4 py-3">
        <Link
          href={`/${workspaceSlug}`}
          className={cn(
            "flex items-center gap-2 rounded-md px-2 py-1.5 text-sm font-medium transition-colors",
            pathname === `/${workspaceSlug}`
              ? "bg-blue-100 text-blue-700"
              : "text-gray-700 hover:bg-gray-200"
          )}
          aria-current={pathname === `/${workspaceSlug}` ? "page" : undefined}
        >
          <LayoutDashboard className="h-4 w-4" aria-hidden="true" />
          {workspaceName}
        </Link>
      </div>

      {/* Projects nav */}
      <nav className="flex-1 overflow-y-auto p-3" aria-label="Projects">
        <p className="mb-2 px-2 text-xs font-semibold uppercase tracking-wider text-gray-500">
          Projects
        </p>
        {projects.map((project) => {
          const isExpanded = expandedProject === project.slug;
          const navItems = projectNavItems(workspaceSlug, project.slug);

          return (
            <div key={project.slug} className="mb-1">
              <button
                onClick={() => setExpandedProject(isExpanded ? null : project.slug)}
                className={cn(
                  "flex w-full items-center justify-between rounded-md px-2 py-1.5 text-sm font-medium transition-colors",
                  project.slug === projectSlug
                    ? "text-blue-700"
                    : "text-gray-700 hover:bg-gray-200"
                )}
                aria-expanded={isExpanded}
                aria-controls={`project-nav-${project.slug}`}
              >
                <span className="truncate">{project.name}</span>
                {isExpanded ? (
                  <ChevronDown className="h-3 w-3 flex-shrink-0" aria-hidden="true" />
                ) : (
                  <ChevronRight className="h-3 w-3 flex-shrink-0" aria-hidden="true" />
                )}
              </button>

              {isExpanded && (
                <ul
                  id={`project-nav-${project.slug}`}
                  className="ml-2 mt-1 space-y-0.5 border-l border-gray-200 pl-3"
                >
                  {navItems.map((item) => {
                    const Icon = item.icon;
                    const isActive = pathname === item.href;
                    return (
                      <li key={item.href}>
                        <Link
                          href={item.href}
                          className={cn(
                            "flex items-center gap-2 rounded-md px-2 py-1.5 text-sm transition-colors",
                            isActive
                              ? "bg-blue-100 font-medium text-blue-700"
                              : "text-gray-600 hover:bg-gray-200 hover:text-gray-900"
                          )}
                          aria-current={isActive ? "page" : undefined}
                        >
                          <Icon className="h-4 w-4" aria-hidden="true" />
                          {item.label}
                        </Link>
                      </li>
                    );
                  })}
                </ul>
              )}
            </div>
          );
        })}
      </nav>

      {/* Workspace settings */}
      <div className="border-t p-3">
        <Link
          href={`/${workspaceSlug}/settings`}
          className={cn(
            "flex items-center gap-2 rounded-md px-2 py-1.5 text-sm font-medium transition-colors",
            pathname === `/${workspaceSlug}/settings`
              ? "bg-blue-100 text-blue-700"
              : "text-gray-700 hover:bg-gray-200"
          )}
        >
          <Settings className="h-4 w-4" aria-hidden="true" />
          Workspace Settings
        </Link>
      </div>
    </aside>
  );
}
