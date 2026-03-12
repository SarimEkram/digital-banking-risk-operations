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

function makeIdempotencyKey() {
  if (typeof crypto !== "undefined" && crypto.randomUUID) {
    return `admin-deposit-${crypto.randomUUID()}`;
  }

  return `admin-deposit-${Date.now()}-${Math.random().toString(36).slice(2)}`;
}

export async function createAdminDeposit({ toAccountId, amountCents }) {
  const idempotencyKey = makeIdempotencyKey();

  const { ok, status, body } = await apiRequest("/api/admin/deposit", {
    method: "POST",
    headers: {
      "Idempotency-Key": idempotencyKey,
    },
    body: JSON.stringify({
      toAccountId,
      amountCents,
    }),
  });

  if (!ok) {
    throw buildError(status, body);
  }

  return {
    data: body,
    idempotencyKey,
  };
}