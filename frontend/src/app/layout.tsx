import type { Metadata } from "next";
import { Inter } from "next/font/google";
import "./globals.css";
import { Providers } from "./providers";

const inter = Inter({ subsets: ["latin"] });

export const metadata: Metadata = {
  title: {
    default: "SpringLens — Spring Boot Startup Optimization",
    template: "%s | SpringLens",
  },
  description:
    "Continuous visibility into Spring Boot application startup performance. Identify bottlenecks, get ranked recommendations, track regressions in CI/CD.",
  robots: "noindex",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en" suppressHydrationWarning>
      <body className={inter.className}>
        <Providers>{children}</Providers>
      </body>
    </html>
  );
}
