import { apiRequest } from "../../shared/api/http";

export function listPayees() {
  return apiRequest("/api/payees", { method: "GET" });
}

export function addPayee({ email, label }) {
  return apiRequest("/api/payees", {
    method: "POST",
    body: JSON.stringify({ email, label }),
  });
}

export function disablePayee(payeeId) {
  return apiRequest(`/api/payees/${payeeId}/disable`, { method: "PATCH" });
}
