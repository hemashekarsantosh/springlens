import { redirect } from "next/navigation";
import { auth } from "@/lib/auth";

export default async function RootPage() {
  const session = await auth();

  if (!session) {
    redirect("/login");
  }

  // Redirect authenticated users to their first workspace.
  // In production this would be fetched from the API; here we redirect
  // to a sensible default path that the dashboard layout will handle.
  redirect("/dashboard");
}
