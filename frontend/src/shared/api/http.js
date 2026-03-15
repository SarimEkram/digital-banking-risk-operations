import { clearAccessToken, getAccessToken } from "../auth/token";

export async function apiRequest(path, options = {}) {
  const headers = new Headers(options.headers || {});

  if (!headers.has("Content-Type") && options.body != null) {
    headers.set("Content-Type", "application/json");
  }

  const token = getAccessToken();
  if (token) {
    headers.set("Authorization", `Bearer ${token}`);
  }

  const res = await fetch(path, { ...options, headers });

  if (res.status === 401) {
    clearAccessToken();
  }

  if (res.status === 204) {
    return { ok: res.ok, status: res.status, body: null };
  }

  const contentType = res.headers.get("content-type") || "";

  let body;
  if (contentType.includes("application/json")) {
    try {
      body = await res.json();
    } catch {
      body = null;
    }
  } else {
    body = await res.text();
  }

  return { ok: res.ok, status: res.status, body };
}