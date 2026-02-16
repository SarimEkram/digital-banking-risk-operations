import { apiRequest } from "../../shared/api/http";

export async function listTransfers({ limit = 20, cursor = null } = {}) {
  const qs = new URLSearchParams();
  qs.set("limit", String(limit));
  if (cursor) qs.set("cursor", cursor);

  const { ok, status, body } = await apiRequest(`/api/transfers?${qs.toString()}`);

  if (!ok) {
    const msg =
      typeof body === "string"
        ? body
        : body?.message || body?.error || JSON.stringify(body);

    const err = new Error(msg || `Request failed (${status})`);
    err.status = status;
    err.body = body;
    throw err;
  }

  // expected shape: { items: [...], nextCursor: "..." | null }
  return body;
}
