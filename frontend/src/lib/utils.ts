import { clsx, type ClassValue } from "clsx";
import { twMerge } from "tailwind-merge";

/**
 * Merge Tailwind CSS class names safely.
 */
export function cn(...inputs: ClassValue[]): string {
  return twMerge(clsx(inputs));
}

/**
 * Format a duration in milliseconds to a human-readable string.
 * e.g., 1234 -> "1.2s", 234 -> "234ms"
 */
export function formatMs(ms: number): string {
  if (ms >= 1000) {
    return `${(ms / 1000).toFixed(1)}s`;
  }
  return `${ms}ms`;
}

/**
 * Format an ISO date string to a locale date string.
 */
export function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString("en-US", {
    year: "numeric",
    month: "short",
    day: "numeric",
  });
}

/**
 * Format an ISO date string to a locale date-time string.
 */
export function formatDateTime(iso: string): string {
  return new Date(iso).toLocaleString("en-US", {
    year: "numeric",
    month: "short",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
}

/**
 * Truncate a long string with an ellipsis.
 */
export function truncate(str: string, maxLength: number): string {
  if (str.length <= maxLength) return str;
  return `${str.slice(0, maxLength)}…`;
}

/**
 * Extract the last segment of a fully-qualified Java class name.
 * e.g., "com.example.MyService" -> "MyService"
 */
export function shortClassName(className: string): string {
  const parts = className.split(".");
  return parts[parts.length - 1] ?? className;
}

/**
 * Derive a startup health color class based on total startup ms.
 */
export function startupHealthColor(ms: number): string {
  if (ms < 5000) return "text-green-600";
  if (ms < 10000) return "text-yellow-600";
  return "text-red-600";
}

/**
 * Derive a startup health background badge class.
 */
export function startupHealthBadgeClass(ms: number): string {
  if (ms < 5000) return "bg-green-100 text-green-800";
  if (ms < 10000) return "bg-yellow-100 text-yellow-800";
  return "bg-red-100 text-red-800";
}
