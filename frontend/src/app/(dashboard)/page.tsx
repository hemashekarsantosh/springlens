import { redirect } from "next/navigation";

/**
 * /dashboard — redirects to the user's first workspace.
 * In a real implementation, the workspace slug would be fetched from the API
 * using the user's session. For now, redirect to a placeholder.
 */
export default function DashboardPage() {
  // This will be replaced by an API call to get the user's workspaces.
  // The middleware or a server component would fetch the first workspace slug.
  redirect("/my-workspace");
}
