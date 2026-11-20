const BASE = (import.meta.env.VITE_API_URL || "http://localhost:8080/api").replace(/\/$/, "");

export class ApiError extends Error {
  status: number;
  body: any;
  constructor(status: number, body: any) {
    super(body?.message ?? `Request failed (${status})`);
    this.status = status;
    this.body = body; // 400 -> body.fieldErrors, 409 -> body.allowedNext
  }
}

export async function api<T>(path: string, opts: RequestInit = {}): Promise<T> {
  const token = localStorage.getItem("token");
  const res = await fetch(BASE + path, {
    ...opts,
    headers: {
      "Content-Type": "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...opts.headers,
    },
  });

  const body = res.status === 204 ? null : await res.json().catch(() => null);

  // Expired or invalid token: clear session and go to login
  // (but not for a wrong password on the login call itself)
  if (res.status === 401 && !path.startsWith("/auth/login")) {
    localStorage.removeItem("token");
    window.location.href = "/login";
  }

  if (!res.ok) throw new ApiError(res.status, body);
  return body as T;
}
