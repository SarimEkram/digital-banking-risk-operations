import { apiRequest } from "../../shared/api/http";

export function createTransfer({ fromAccountId, payeeId, amountCents, currency }, idempotencyKey) {
  return apiRequest("/api/transfers", {
    method: "POST",
    headers: {
      "Idempotency-Key": idempotencyKey,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({ fromAccountId, payeeId, amountCents, currency }),
  });
}
