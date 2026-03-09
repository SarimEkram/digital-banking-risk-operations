import { apiRequest } from "../../shared/api/http";
// api.js for admin-risk
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

export async function listHeldTransfers() {
  const { ok, status, body } = await apiRequest("/api/admin/transfers/held");

  if (!ok) {
    throw buildError(status, body);
  }

  return Array.isArray(body) ? body : [];
}

export async function approveHeldTransfer(transferId) {
  const { ok, status, body } = await apiRequest(
    `/api/admin/transfers/${transferId}/approve`,
    {
      method: "POST",
    }
  );

  if (!ok) {
    throw buildError(status, body);
  }

  return body;
}

export async function rejectHeldTransfer(transferId, reason) {
  const trimmed = String(reason || "").trim();

  const { ok, status, body } = await apiRequest(
    `/api/admin/transfers/${transferId}/reject`,
    {
      method: "POST",
      body: trimmed ? JSON.stringify({ reason: trimmed }) : undefined,
    }
  );

  if (!ok) {
    throw buildError(status, body);
  }

  return body;
}