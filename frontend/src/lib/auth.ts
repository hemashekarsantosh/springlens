import NextAuth from "next-auth";
import { getServerSession } from "next-auth";
import GitHubProvider from "next-auth/providers/github";
import type { NextAuthOptions } from "next-auth";

export const authOptions: NextAuthOptions = {
  providers: [
    GitHubProvider({
      clientId: process.env.AUTH_GITHUB_ID!,
      clientSecret: process.env.AUTH_GITHUB_SECRET!,
    }),
  ],
  session: {
    strategy: "jwt",
  },
  pages: {
    signIn: "/login",
    error: "/login",
  },
  callbacks: {
    async jwt({ token, account }) {
      // On initial sign-in, exchange GitHub access token for SpringLens JWT
      if (account?.access_token) {
        token.githubAccessToken = account.access_token;
        try {
          const baseUrl = process.env.NEXT_PUBLIC_API_URL ?? "https://api.springlens.io";
          const response = await fetch(`${baseUrl}/v1/auth/github`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ github_access_token: account.access_token }),
          });
          if (response.ok) {
            const data = (await response.json()) as { access_token: string };
            token.accessToken = data.access_token;
          }
        } catch {
          // If exchange fails, fall through — session will lack accessToken
        }
      }
      return token;
    },
    async session({ session, token }) {
      if (token.accessToken) {
        session.accessToken = token.accessToken as string;
      }
      if (token.sub) {
        session.user.id = token.sub;
      }
      return session;
    },
  },
};

const handler = NextAuth(authOptions);

export { handler as handlers };

export async function auth() {
  return getServerSession(authOptions);
}
