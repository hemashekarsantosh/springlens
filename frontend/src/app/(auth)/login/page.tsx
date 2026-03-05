import React from "react";
import type { Metadata } from "next";
import { redirect } from "next/navigation";
import { auth } from "@/lib/auth";
import { Zap } from "lucide-react";
import SignInButton from "@/components/auth/SignInButton";

export const metadata: Metadata = {
  title: "Sign In",
  description: "Sign in to SpringLens to optimize your Spring Boot startup performance.",
};

export default async function LoginPage() {
  const session = await auth();
  if (session) redirect("/dashboard");

  return (
    <main className="flex min-h-screen items-center justify-center bg-gradient-to-br from-gray-50 to-blue-50 px-4">
      <div className="w-full max-w-md">
        {/* Logo & title */}
        <div className="mb-8 text-center">
          <div className="mb-4 flex justify-center">
            <div className="flex h-14 w-14 items-center justify-center rounded-2xl bg-blue-600 shadow-lg">
              <Zap className="h-8 w-8 text-white" aria-hidden="true" />
            </div>
          </div>
          <h1 className="text-3xl font-bold text-gray-900">SpringLens</h1>
          <p className="mt-2 text-gray-500">
            Spring Boot Startup Optimization
          </p>
        </div>

        {/* Sign-in card */}
        <div className="rounded-2xl border bg-white p-8 shadow-sm">
          <h2 className="mb-2 text-xl font-semibold text-gray-900">Welcome back</h2>
          <p className="mb-6 text-sm text-gray-500">
            Sign in with your GitHub account to continue
          </p>

          <SignInButton />

          <p className="mt-6 text-center text-xs text-gray-400">
            By signing in, you agree to SpringLens&apos;s Terms of Service and
            Privacy Policy.
          </p>
        </div>

        {/* Feature highlights */}
        <div className="mt-8 grid grid-cols-3 gap-4 text-center">
          {[
            { label: "Bean Timeline", desc: "D3 Gantt visualization" },
            { label: "AI Recommendations", desc: "Ranked optimizations" },
            { label: "CI/CD Gates", desc: "Startup budget enforcement" },
          ].map(({ label, desc }) => (
            <div key={label} className="rounded-lg border bg-white p-3 shadow-sm">
              <p className="text-xs font-semibold text-gray-900">{label}</p>
              <p className="mt-0.5 text-xs text-gray-400">{desc}</p>
            </div>
          ))}
        </div>
      </div>
    </main>
  );
}
