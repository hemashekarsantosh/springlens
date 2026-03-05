import axios, { AxiosError, type AxiosInstance, type AxiosRequestConfig } from "axios";
import { v4 as uuidv4 } from "uuid";
import { getSession, signOut } from "next-auth/react";
import type { ErrorResponse } from "@/types/api";

const BASE_URL = process.env.NEXT_PUBLIC_API_URL ?? "https://api.springlens.io";

/**
 * Create an Axios instance configured for SpringLens API calls.
 * - Attaches Authorization: Bearer from next-auth session
 * - Attaches X-Request-ID header (uuid v4)
 * - Handles 401 → redirect to /login
 * - Handles 422 → returns structured error object, does NOT throw
 */
function createApiClient(): AxiosInstance {
  const client = axios.create({
    baseURL: `${BASE_URL}/v1`,
    headers: {
      "Content-Type": "application/json",
    },
    timeout: 30_000,
  });

  // Request interceptor: attach auth + request ID
  client.interceptors.request.use(async (config) => {
    config.headers["X-Request-ID"] = uuidv4();

    // Only runs client-side
    if (typeof window !== "undefined") {
      const session = await getSession();
      if (session?.accessToken) {
        config.headers["Authorization"] = `Bearer ${session.accessToken}`;
      }
    }
    return config;
  });

  // Response interceptor: handle 401 and pass through 422
  client.interceptors.response.use(
    (response) => response,
    async (error: AxiosError<ErrorResponse>) => {
      if (error.response?.status === 401 && typeof window !== "undefined") {
        await signOut({ callbackUrl: "/login" });
        return Promise.reject(error);
      }

      // For 422, return the structured error data rather than throwing
      if (error.response?.status === 422) {
        return Promise.resolve(error.response);
      }

      return Promise.reject(error);
    }
  );

  return client;
}

export const apiClient: AxiosInstance = createApiClient();

/**
 * Server-side fetch helper that accepts a Bearer token directly.
 * Used in Server Components where getSession() is not available.
 */
export async function serverFetch<T>(
  path: string,
  accessToken: string,
  options?: RequestInit
): Promise<T> {
  const requestId = uuidv4();
  const url = `${BASE_URL}/v1${path}`;

  const response = await fetch(url, {
    ...options,
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${accessToken}`,
      "X-Request-ID": requestId,
      ...options?.headers,
    },
  });

  if (!response.ok) {
    const error: ErrorResponse = await response.json().catch(() => ({
      code: "UNKNOWN",
      message: response.statusText,
      trace_id: requestId,
    }));
    throw new Error(error.message ?? "API request failed");
  }

  return response.json() as Promise<T>;
}

export type { AxiosRequestConfig };
