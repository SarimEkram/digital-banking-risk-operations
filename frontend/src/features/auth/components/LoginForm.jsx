import { useState } from "react";
import TextField from "../../../shared/ui/TextField";
import Button from "../../../shared/ui/Button";
import { login } from "../api";
import {
  clearAccessToken,
  getAccessToken,
  setAccessToken,
} from "../../../shared/auth/token";

export default function LoginForm() {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");

  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState(null); // { kind: "success"|"error", text: string }
  const [hasSavedToken, setHasSavedToken] = useState(Boolean(getAccessToken()));

  async function onSubmit(e) {
    e.preventDefault();
    setLoading(true);
    setResult(null);

    try {
      const { ok, status, body } = await login({
        email: email.trim(),
        password,
      });

      if (!ok) {
        const errText =
          typeof body === "string" ? body : JSON.stringify(body, null, 2);

        setResult({
          kind: "error",
          text: `Login failed (${status})\n\n${errText}`,
        });
        return;
      }

      const token = body?.accessToken;
      if (typeof token === "string" && token.length > 0) {
        setAccessToken(token);
        setHasSavedToken(true);
      }

      const tokenPreview =
        typeof token === "string" && token.length > 0
          ? `${token.slice(0, 24)}...`
          : "(missing token)";

      setResult({
        kind: "success",
        text:
          `Login successful\n\n` +
          `userId: ${body.userId}\n` +
          `email: ${body.email}\n` +
          `role: ${body.role}\n` +
          `token saved: ${typeof token === "string" && token.length > 0 ? "yes" : "no"}\n` +
          `accessToken: ${tokenPreview}`,
      });

      setPassword("");
    } catch (err) {
      setResult({
        kind: "error",
        text: `Network / server error\n\n${err?.message || String(err)}`,
      });
    } finally {
      setLoading(false);
    }
  }

  function onLogout() {
    clearAccessToken();
    setHasSavedToken(false);
    setResult(null);
  }

  return (
    <>
      <form onSubmit={onSubmit} className="form">
        <TextField
          label="Email"
          type="email"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          autoComplete="email"
          required
        />

        <TextField
          label="Password"
          type="password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          maxLength={72}
          required
        />

        <Button type="submit" disabled={loading}>
          {loading ? "Signing in..." : "Login"}
        </Button>

        {hasSavedToken && (
          <Button type="button" disabled={loading} onClick={onLogout}>
            Logout (clear token)
          </Button>
        )}
      </form>

      {result && (
        <pre
          className={`output ${
            result.kind === "error" ? "output--error" : "output--success"
          }`}
        >
          {result.text}
        </pre>
      )}
    </>
  );
}
