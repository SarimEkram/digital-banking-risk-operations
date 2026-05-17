import { apiRequest } from "../../shared/api/http";

function buildError(status, body) {
  const msg =
    typeof body === "string"
      ? body
      : body?.message || body?.error || JSON.stringify(body);

  const err = new Error(msg || `Request failed (${status})`);
  err.status = status;
  err.body = body;
  return err;
}

// Temporarily reusing the deposit lookup endpoint until we build the full user lookup endpoint
export async function lookupUserByEmail(email) {
  const trimmed = String(email || "").trim();

  const { ok, status, body } = await apiRequest(
    `/api/admin/deposit/lookup?email=${encodeURIComponent(trimmed)}`
  );

  if (!ok) {
    throw buildError(status, body);
  }

  return body;
}