# SpringLens Frontend — Implementation Summary

**Engineer Role:** Frontend Engineer
**Date:** 2026-03-05
**Stack:** Next.js 14 (App Router), TypeScript, Tailwind CSS, shadcn/ui, TanStack Query v5, next-auth v5, D3.js, Recharts

---

## Files Created

### Root Configuration

| File | Purpose |
|------|---------|
| `frontend/package.json` | All dependencies including Next.js 14.2.x, D3 v7, Recharts v2, TanStack Query v5, next-auth v5, react-syntax-highlighter, Radix UI primitives, Vitest |
| `frontend/next.config.ts` | Next.js config: strict mode, image domains for GitHub avatars |
| `frontend/tsconfig.json` | TypeScript strict mode, path alias `@/*` → `src/*` |
| `frontend/tailwind.config.ts` | Tailwind with shadcn/ui CSS variables, shimmer animation for skeletons |
| `frontend/postcss.config.js` | PostCSS with Tailwind + autoprefixer |
| `frontend/Dockerfile` | Multi-stage Node 20 Alpine build |
| `frontend/.env.local.example` | Template: `NEXT_PUBLIC_API_URL`, `AUTH_SECRET`, `AUTH_GITHUB_ID/SECRET`, `NEXTAUTH_URL` |

---

### Source: Types (`src/types/`)

| File | Purpose |
|------|---------|
| `src/types/api.ts` | Complete TypeScript types matching all three OpenAPI specs (analysis, recommendation, ingestion services). Covers: `SnapshotSummary`, `TimelineResponse`, `BeanTiming`, `PhaseBreakdown`, `BeanGraphResponse`, `SnapshotComparisonResponse`, `WorkspaceOverviewResponse`, `Recommendation`, `GraalvmFeasibility`, `CiBudget`, etc. |
| `src/types/next-auth.d.ts` | Module augmentation: adds `accessToken` to `Session`, augments `JWT` with `accessToken` and `githubAccessToken` |

---

### Source: Library (`src/lib/`)

| File | Purpose |
|------|---------|
| `src/lib/utils.ts` | `cn()`, `formatMs()`, `formatDate()`, `formatDateTime()`, `truncate()`, `shortClassName()`, `startupHealthColor()`, `startupHealthBadgeClass()` |
| `src/lib/auth.ts` | next-auth v5 config with GitHub provider. JWT session strategy. `jwt` callback exchanges GitHub access token for SpringLens JWT via `POST /v1/auth/github`. `session` callback exposes `accessToken` and `user.id`. |
| `src/lib/api/client.ts` | Axios client: base URL from `NEXT_PUBLIC_API_URL`, request interceptor attaches `Authorization: Bearer` from next-auth session + `X-Request-ID` (uuid v4), response interceptor handles 401→signOut redirect and 422→structured passthrough. Also exports `serverFetch<T>()` for Server Components. |
| `src/lib/api/analysis.ts` | Typed API functions: `listSnapshots`, `getTimeline`, `getBeanGraph`, `compareSnapshots`, `getWorkspaceOverview`. TanStack Query key factories in `analysisKeys`. |
| `src/lib/api/recommendations.ts` | Typed API functions: `getRecommendations`, `updateRecommendationStatus`, `listCiBudgets`, `upsertCiBudget`. TanStack Query key factories in `recommendationKeys`. |

---

### Source: UI Components (`src/components/ui/`)

| File | Purpose |
|------|---------|
| `button.tsx` | CVA-based Button with variants: default, destructive, outline, secondary, ghost, link; sizes: default, sm, lg, icon |
| `card.tsx` | Card, CardHeader, CardTitle, CardDescription, CardContent, CardFooter |
| `badge.tsx` | Badge with variants: default, secondary, destructive, outline, success, warning, danger, info, purple, orange |
| `skeleton.tsx` | Pulse animation skeleton with aria-busy for accessible loading states |
| `input.tsx` | Styled Input with focus ring |
| `label.tsx` | Styled Label |
| `select.tsx` | Radix UI Select with full keyboard navigation and animations |
| `slider.tsx` | Radix UI Slider for duration filter |
| `tabs.tsx` | Radix UI Tabs for settings pages |
| `dialog.tsx` | Radix UI Dialog with overlay, close button, accessible title/description |
| `dropdown-menu.tsx` | Full Radix UI DropdownMenu with checkbox, radio, sub-menu items |
| `tooltip.tsx` | Radix UI Tooltip with TooltipProvider |

---

### Source: Layout Components (`src/components/layout/`)

| File | Purpose |
|------|---------|
| `Sidebar.tsx` | Left sidebar with workspace name, collapsible project list, per-project nav (Overview, Timeline, Bean Graph, Recommendations, Compare, Settings), workspace settings link. Active route highlighted. Full keyboard/aria support. |
| `Header.tsx` | Top header with page title and user dropdown menu (avatar, name, email, sign out). Uses next-auth `signOut`. |

---

### Source: Timeline Components (`src/components/timeline/`)

| File | Purpose |
|------|---------|
| `StartupTimeline.tsx` | D3 Gantt chart. x-axis = time ms, y-axis = beans sorted by start_ms. Color: red=bottleneck, orange=post-processor, blue=normal. Hover tooltip showing bean name, class, duration, start time, dependencies. D3 zoom+pan. ResizeObserver for responsiveness. |
| `PhaseBreakdown.tsx` | Recharts horizontal BarChart of startup phases. Color-coded per phase (context_refresh, bean_post_processors, etc.). Custom tooltip with duration and % of total. Color legend. |
| `BeanFilter.tsx` | Filter controls: Radix Slider for min duration, package prefix Input, bean name search Input with icon. Controlled component with `BeanFilterState`. |

---

### Source: Bean Graph Components (`src/components/bean-graph/`)

| File | Purpose |
|------|---------|
| `BeanDependencyGraph.tsx` | D3 force-directed graph. Nodes = beans, sized by duration_ms, colored red=bottleneck/blue=normal. Directional edges with arrowhead markers. D3 drag to reposition nodes. Click node → detail panel (class name, duration, connection count, status). Zoom in/out/reset buttons. ResizeObserver responsive. Legend. |

---

### Source: Recommendation Components (`src/components/recommendations/`)

| File | Purpose |
|------|---------|
| `CategoryBadge.tsx` | Colored badge mapping `RecommendationCategory` to human labels and badge variants |
| `RecommendationCard.tsx` | Full recommendation card: rank number, category badge, effort badge, title, savings (green), description, affected beans, warnings panel (yellow), GraalVM feasibility panel, expandable code snippet with react-syntax-highlighter (auto-detects Java/XML/properties), "Mark as Applied" / "Won't Fix" / "Reactivate" buttons using useMutation + cache invalidation |
| `RecommendationList.tsx` | useQuery-powered list with skeleton loading, category+status filters, stale data warning, total savings header, empty state |

---

### Source: Compare Components (`src/components/compare/`)

| File | Purpose |
|------|---------|
| `SnapshotDiff.tsx` | Two snapshot selectors (baseline/target). Total delta banner (red=regression, green=improvement). Changed beans table sorted by largest regression. Added beans table (red highlight). Removed beans table (green highlight, strikethrough). Fully typed from `SnapshotComparisonResponse`. |

---

### Source: Overview Components (`src/components/overview/`)

| File | Purpose |
|------|---------|
| `WorkspaceHealth.tsx` | Grid of project health cards. Startup time with color health badge (<5s green, 5-10s yellow, >10s red). 7-day trend icon. Click navigates to project. aria-label for accessibility. |
| `StartupTrend.tsx` | Recharts LineChart sparkline for startup time over recent snapshots. Custom tooltip with date and commit SHA. Auto-scales Y axis. |

---

### Source: App Routes (`src/app/`)

| File | Purpose |
|------|---------|
| `globals.css` | Tailwind directives + CSS variables for shadcn/ui design tokens (light/dark) |
| `layout.tsx` | Root HTML layout with Inter font, metadata, wraps `<Providers>` |
| `providers.tsx` | Client component: SessionProvider + QueryClientProvider (staleTime 30s) + TooltipProvider |
| `page.tsx` | Root page: auth check → redirect to /login or /dashboard |
| `(auth)/login/page.tsx` | Login page: GitHub OAuth button via next-auth server action, feature highlights |
| `(auth)/callback/page.tsx` | OAuth callback redirect fallback |
| `api/auth/[...nextauth]/route.ts` | next-auth GET/POST handlers |
| `(dashboard)/layout.tsx` | Dashboard root: auth guard |
| `(dashboard)/page.tsx` | /dashboard → redirects to first workspace |
| `(dashboard)/[workspaceSlug]/layout.tsx` | Workspace shell: Sidebar + Header with session data |
| `(dashboard)/[workspaceSlug]/page.tsx` | Workspace overview: project count, avg startup, trend stats, WorkspaceHealth grid |
| `(dashboard)/[workspaceSlug]/settings/page.tsx` | Workspace settings: General (name/slug), Members (invite table), Billing (plan tiers), API Keys (create/revoke) |
| `(dashboard)/[workspaceSlug]/[projectSlug]/page.tsx` | Project overview: latest snapshot metrics, StartupTrend chart, quick links, recent snapshots table |
| `(dashboard)/[workspaceSlug]/[projectSlug]/timeline/page.tsx` | Timeline page: snapshot selector, BeanFilter, PhaseBreakdown, StartupTimeline D3 chart |
| `(dashboard)/[workspaceSlug]/[projectSlug]/bean-graph/page.tsx` | Bean graph page: snapshot selector, node/edge counts, BeanDependencyGraph |
| `(dashboard)/[workspaceSlug]/[projectSlug]/recommendations/page.tsx` | Recommendations page: RecommendationList with filters |
| `(dashboard)/[workspaceSlug]/[projectSlug]/compare/page.tsx` | Compare page: loads snapshots, renders SnapshotDiff |
| `(dashboard)/[workspaceSlug]/[projectSlug]/settings/page.tsx` | Project settings: General (name, project ID), CI/CD Budgets (list+form, useMutation), Webhooks (Slack + custom URL) |

---

### Implementation Notes

**Project ID Resolution**
The scaffold uses placeholder UUIDs (`PLACEHOLDER_PROJECT_ID`) where the production implementation would resolve workspace/project slugs to IDs via an API call (e.g., `GET /v1/workspaces?slug={slug}` or a Next.js Server Component data fetch at layout level).

**Authentication Flow**
1. User clicks "Continue with GitHub" → next-auth initiates GitHub OAuth
2. `jwt` callback receives `account.access_token` → POSTs to `POST /v1/auth/github` → stores SpringLens JWT as `token.accessToken`
3. `session` callback exposes `session.accessToken` to client
4. API client reads session and attaches `Authorization: Bearer {accessToken}` to every request
5. 401 response → `signOut({ callbackUrl: '/login' })`

**Loading States**
All data-heavy components use `<Skeleton>` pulse loaders (no spinners) with `aria-busy` attributes.

**Error States**
API errors render inline error cards with `role="alert"`. The root QueryClient has `retry: 1`.

**Accessibility**
- All interactive elements have `aria-label`
- Navigation uses `aria-current="page"` for active routes
- Tables use `scope="col"` on headers
- SVG charts have `role="img"` with descriptive `aria-label`
- Sidebar has `aria-label="Sidebar navigation"`, expandable items have `aria-expanded`

**Responsive Design**
- Dashboard shell: 1280px+ (sidebar + main content)
- Tablet: 768px+ (sidebar collapses, grid switches to 1-column)
- SVGs use ResizeObserver to redraw on container width change
