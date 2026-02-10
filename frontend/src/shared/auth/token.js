const TOKEN_KEY = "dbrisk.accessToken";

export function getAccessToken() {
  return localStorage.getItem(TOKEN_KEY) || "";
}

export function hasAccessToken() {
  const t = getAccessToken();
  return typeof t === "string" && t.length > 0;
}

export function setAccessToken(token) {
  localStorage.setItem(TOKEN_KEY, token);
}

export function clearAccessToken() {
  localStorage.removeItem(TOKEN_KEY);
}
