import { apiRequest } from "../../shared/api/http";

export function getMe() {
  return apiRequest("/api/me");
}
