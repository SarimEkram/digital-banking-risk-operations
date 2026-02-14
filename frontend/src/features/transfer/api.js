import { apiRequest } from "../../shared/api/http";

export function createTransfer({ fromAccountId, toAccountId, amountCents, currency }, idempotencyKey) {
  return apiRequest("/api/transfers", {
    method: "POST",
    headers: {
      "Idempotency-Key": idempotencyKey,
    },
    body: JSON.stringify({ fromAccountId, toAccountId, amountCents, currency }),
  });
}
