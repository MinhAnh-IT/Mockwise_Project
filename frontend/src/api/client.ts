import { API_BASE_URL } from '@/lib/env';
import type { ApiEnvelope, ApiErrorBody } from '@/types/api';

export class ApiError extends Error {
  readonly status: number;
  readonly code?: number;

  constructor(message: string, status: number, code?: number) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
  }
}

export type RequestOptions = {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';
  body?: unknown;
  query?: Record<string, string | number | undefined>;
  auth?: boolean;
  signal?: AbortSignal;
};

type TokenAccessor = {
  getAccessToken: () => string | null;
  setAccessToken: (token: string | null) => void;
  refresh: () => Promise<string | null>;
  onAuthFailure: () => void;
};

let accessor: TokenAccessor | null = null;

export function configureApiClient(next: TokenAccessor): void {
  accessor = next;
}

function buildUrl(path: string, query?: RequestOptions['query']): string {
  // When API_BASE_URL is empty (dev proxy / nginx prod), use current origin as
  // base — `new URL('/api/...')` alone throws "Invalid URL" without a base.
  const base = API_BASE_URL || window.location.origin;
  const url = new URL(path, base);
  if (query) {
    for (const [key, value] of Object.entries(query)) {
      if (value !== undefined && value !== null && value !== '') {
        url.searchParams.set(key, String(value));
      }
    }
  }
  return url.toString();
}

async function parseError(response: Response): Promise<ApiError> {
  const fallback = `Request failed with status ${response.status}`;
  try {
    const body = (await response.json()) as Partial<ApiErrorBody>;
    return new ApiError(body.message ?? fallback, response.status, body.code);
  } catch {
    return new ApiError(fallback, response.status);
  }
}

async function rawRequest(
  path: string,
  options: RequestOptions,
  token: string | null,
): Promise<Response> {
  const headers: Record<string, string> = {
    Accept: 'application/json',
  };
  if (options.body !== undefined) {
    headers['Content-Type'] = 'application/json';
  }
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }

  return fetch(buildUrl(path, options.query), {
    method: options.method ?? 'GET',
    headers,
    credentials: 'include',
    body: options.body !== undefined ? JSON.stringify(options.body) : undefined,
    signal: options.signal,
  });
}

export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const useAuth = options.auth ?? true;
  const token = useAuth ? accessor?.getAccessToken() ?? null : null;

  let response = await rawRequest(path, options, token);

  if (response.status === 401 && useAuth && accessor) {
    const renewed = await accessor.refresh();
    if (!renewed) {
      accessor.onAuthFailure();
      throw await parseError(response);
    }
    response = await rawRequest(path, options, renewed);
  }

  if (!response.ok) {
    throw await parseError(response);
  }

  if (response.status === 204) {
    return undefined as T;
  }

  return (await response.json()) as T;
}

export async function unwrap<T>(path: string, options?: RequestOptions): Promise<T> {
  const envelope = await request<ApiEnvelope<T>>(path, options);
  return envelope.data;
}
