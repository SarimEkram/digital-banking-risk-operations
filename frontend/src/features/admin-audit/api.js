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
  "PAYEE_ENABLE",
];

export const AUDIT_SCOPES = {
  SELF: "self",
  USER: "user",
  ADMIN: "admin",
};

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

function buildQueryString({ page, size, scope, action, email }) {
  const params = new URLSearchParams();

  if (Number.isFinite(page)) params.set("page", String(page));
  if (Number.isFinite(size)) params.set("size", String(size));

  if (scope) params.set("scope", scope);

  const trimmedAction = String(action || "").trim();
  if (trimmedAction) params.set("action", trimmedAction);

  const trimmedEmail = String(email || "").trim();
  if (trimmedEmail) params.set("email", trimmedEmail);

  const qs = params.toString();
  return qs ? `?${qs}` : "";
}

export async function listAudit({
  page = 0,
  size = 50,
  scope = AUDIT_SCOPES.SELF,
  action = "",
  email = "",
} = {}) {
  const path = `/api/admin/audit${buildQueryString({ page, size, scope, action, email })}`;
  const { ok, status, body } = await apiRequest(path);

  if (!ok) {
    throw buildError(status, body);
  }

  return {
    items: Array.isArray(body?.items) ? body.items : [],
    page: Number.isFinite(body?.page) ? body.page : 0,
    size: Number.isFinite(body?.size) ? body.size : size,
    totalElements: Number.isFinite(body?.totalElements) ? body.totalElements : 0,
    totalPages: Number.isFinite(body?.totalPages) ? body.totalPages : 0,
  };
}

// ---- Details parsing & humanization ----

/**
 * Audit `details` strings are written by the backend as comma-separated `key=value` pairs.
 * Examples:
 *   "from=2, payee_id=1, to=3, amount_cents=100, currency=CAD"
 *   "from_treasury=1, to=2, amount_cents=700000, currency=CAD"
 *   "payee_email=alice@example.com, payee_user_id=3"
 *
 * Reasons may contain commas or "=" in their freeform text. We accept that risk because
 * the backend writes the reason as the LAST key in TRANSFER_REJECT and TRANSFER_HELD, so
 * a simple "split until the first known key boundary" parser would be more code than it's
 * worth. The naive split below mishandles reasons with literal commas. Acceptable trade-off
 * for a v1; if reasons start containing commas in practice we'll revisit.
 */
function parseKv(details) {
  const out = {};
  if (!details || typeof details !== "string") return out;

  const parts = details.split(",");
  for (const raw of parts) {
    const part = raw.trim();
    if (!part) continue;

    const eq = part.indexOf("=");
    if (eq < 0) continue;

    const key = part.slice(0, eq).trim();
    const value = part.slice(eq + 1).trim();
    if (key) out[key] = value;
  }
  return out;
}

function formatMoney(amountCents, currency = "CAD") {
  const n = Number(amountCents);
  if (!Number.isFinite(n)) return "";

  const amount = n / 100;
  try {
    return new Intl.NumberFormat(undefined, {
      style: "currency",
      currency,
    }).format(amount);
  } catch {
    return `${currency} ${amount.toFixed(2)}`;
  }
}

/**
 * Returns a friendly, human-readable description of an audit row's details.
 *
 * Falls back to the raw details string for unknown actions.
 */
export function formatAuditDetails(action, details) {
  const kv = parseKv(details);
  const currency = kv.currency || "CAD";
  const amount = kv.amount_cents ? formatMoney(kv.amount_cents, currency) : "";

  switch (String(action || "").toUpperCase()) {
    case "TRANSFER_CREATE":
      return [
        amount && `${amount} sent`,
        kv.from && kv.to && `from account #${kv.from} to account #${kv.to}`,
        kv.payee_id && `(payee #${kv.payee_id})`,
      ]
        .filter(Boolean)
        .join(" ");

    case "TRANSFER_HELD":
      return [
        amount && `${amount} held for review`,
        kv.from && kv.to && `from account #${kv.from} to account #${kv.to}`,
        kv.payee_id && `(payee #${kv.payee_id})`,
        kv.reason && `— ${kv.reason}`,
      ]
        .filter(Boolean)
        .join(" ");

    case "TRANSFER_APPROVE":
      return [
        amount && `${amount} approved`,
        kv.to && `credited to account #${kv.to}`,
      ]
        .filter(Boolean)
        .join(", ");

    case "TRANSFER_REJECT":
      return [
        amount && `${amount} rejected`,
        kv.from && `refunded to account #${kv.from}`,
        kv.reason && `— ${kv.reason}`,
      ]
        .filter(Boolean)
        .join(" ");

    case "ADMIN_DEPOSIT":
      return [
        amount && `${amount} deposited`,
        kv.from_treasury && kv.to && `from treasury account #${kv.from_treasury} to account #${kv.to}`,
      ]
        .filter(Boolean)
        .join(" ");

    case "PAYEE_ADD":
      return kv.payee_email
        ? `Added payee ${kv.payee_email}`
        : details || "";

    case "PAYEE_DISABLE":
      return kv.payee_email
        ? `Disabled payee ${kv.payee_email}`
        : details || "";

    case "PAYEE_ENABLE":
      return kv.payee_email
        ? `Re-enabled payee ${kv.payee_email}`
        : details || "";

    default:
      return details || "";
  }
}