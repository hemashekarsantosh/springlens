import { redirect } from "next/navigation";
import { auth } from "@/lib/auth";

/**
 * OAuth callback landing page.
 * next-auth handles the actual token exchange at /api/auth/callback/github.
 * This page exists only as a fallback redirect in case the user lands here.
 */
export default async function CallbackPage() {
  const session = await auth();
  if (session) {
    redirect("/dashboard");
  }
  redirect("/login");
}
