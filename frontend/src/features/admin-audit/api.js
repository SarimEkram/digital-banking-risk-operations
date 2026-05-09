import { apiRequest } from "../../shared/api/http";

// Common audit actions for the dropdown. Free-text input still allowed for anything else.
export const AUDIT_ACTIONS = [
  "TRANSFER_CREATE",
  "TRANSFER_HELD",
  "TRANSFER_APPROVE",
  "TRANSFER_REJECT",
  "ADMIN_DEPOSIT",
  "PAYEE_ADD",
  "PAYEE_DISABLE",
];

// Action codes that originate from admin/system activity (used for visual tagging).
export const ADMIN_ACTION_CODES = new Set([
  "ADMIN_DEPOSIT",
  "TRANSFER_APPROVE",
  "TRANSFER_REJECT",
]);

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

function buildQueryString({ page, size, action, email }) {
  const params = new URLSearchParams();

  if (Number.isFinite(page)) params.set("page", String(page));
  if (Number.isFinite(size)) params.set("size", String(size));

  const trimmedAction = String(action || "").trim();
  if (trimmedAction) params.set("action", trimmedAction);

  const trimmedEmail = String(email || "").trim();
  if (trimmedEmail) params.set("email", trimmedEmail);

  const qs = params.toString();
  return qs ? `?${qs}` : "";
}

export async function listAudit({ page = 0, size = 50, action = "", email = "" } = {}) {
  const path = `/api/admin/audit${buildQueryString({ page, size, action, email })}`;
  const { ok, status, body } = await apiRequest(path);

  if (!ok) {
    throw buildError(status, body);
  }

  // Defensive: backend should always return the page envelope, but handle malformed responses.
  return {
    items: Array.isArray(body?.items) ? body.items : [],
    page: Number.isFinite(body?.page) ? body.page : 0,
    size: Number.isFinite(body?.size) ? body.size : size,
    totalElements: Number.isFinite(body?.totalElements) ? body.totalElements : 0,
    totalPages: Number.isFinite(body?.totalPages) ? body.totalPages : 0,
  };
}