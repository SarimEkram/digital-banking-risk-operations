import { apiRequest } from "../../shared/api/http";

export function createTransfer({ fromAccountId, payeeId, amountCents, currency }, idempotencyKey) {
  return apiRequest("/api/transfers", {
    method: "POST",
    headers: {
      "Idempotency-Key": idempotencyKey,
    },
    body: JSON.stringify({ fromAccountId, payeeId, amountCents, currency }),
  });
}
