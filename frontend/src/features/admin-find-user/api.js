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

// Lookup user by email - returns user with all accounts regardless of status
export async function lookupUserByEmail(email) {
  const trimmed = String(email || "").trim();

  const { ok, status, body } = await apiRequest(
    `/api/admin/users/lookup?email=${encodeURIComponent(trimmed)}`
  );

  if (!ok) {
    throw buildError(status, body);
  }

  return body;
}

// Get all accounts for a user
export async function getUserAccounts(userId) {
  const { ok, status, body } = await apiRequest(
    `/api/admin/users/${userId}/accounts`
  );

  if (!ok) {
    throw buildError(status, body);
  }

  return body;
}

// Freeze an account
export async function freezeAccount(accountId) {
  const { ok, status, body } = await apiRequest(
    `/api/admin/accounts/${accountId}/freeze`,
    {
      method: "POST",
    }
  );

  if (!ok) {
    throw buildError(status, body);
  }

  return body;
}

// Unfreeze an account
export async function unfreezeAccount(accountId) {
  const { ok, status, body } = await apiRequest(
    `/api/admin/accounts/${accountId}/unfreeze`,
    {
      method: "POST",
    }
  );

  if (!ok) {
    throw buildError(status, body);
  }

  return body;
}

// Close an account
export async function closeAccount(accountId) {
  const { ok, status, body } = await apiRequest(
    `/api/admin/accounts/${accountId}/close`,
    {
      method: "POST",
    }
  );

  if (!ok) {
    throw buildError(status, body);
  }

  return body;
}

// Get activity summary for user
export async function getUserActivitySummary(userId) {
  const { ok, status, body } = await apiRequest(
    `/api/admin/users/${userId}/activity-summary`
  );

  if (!ok) {
    throw buildError(status, body);
  }

  return body;
}

// Get risk profile for user
export async function getUserRiskProfile(userId, limit = 5) {
  const { ok, status, body } = await apiRequest(
    `/api/admin/users/${userId}/risk-profile?limit=${limit}`
  );

  if (!ok) {
    throw buildError(status, body);
  }

  return body;
}

// Get payees for user
export async function getUserPayees(userId) {
  const { ok, status, body } = await apiRequest(
    `/api/admin/users/${userId}/payees`
  );

  if (!ok) {
    throw buildError(status, body);
  }

  return body;
}