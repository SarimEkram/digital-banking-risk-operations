import { apiRequest } from "../../shared/api/http";

export function getMe() {
  return apiRequest("/api/me");
}

export function getAccounts() {
  return apiRequest("/api/accounts");
}
