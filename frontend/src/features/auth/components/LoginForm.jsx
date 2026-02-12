import { useState } from "react";
import { useNavigate } from "react-router-dom";
import TextField from "../../../shared/ui/TextField";
import Button from "../../../shared/ui/Button";
import { login } from "../api";
import { clearAccessToken, setAccessToken } from "../../../shared/auth/token";
import styles from "../../../styles/AuthForm.module.css";

export default function LoginForm() {
  const navigate = useNavigate();

  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");

  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState(null);

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
        const errText = typeof body === "string" ? body : JSON.stringify(body, null, 2);
        setResult({ kind: "error", text: `Login failed (${status})\n\n${errText}` });
        return;
      }

      const token = body?.accessToken;
      if (typeof token === "string" && token.length > 0) {
        setAccessToken(token);
        setResult({ kind: "success", text: `Login successful\n\nredirecting to /home...` });
        setPassword("");
        navigate("/home", { replace: true });
        return;
      }

      setResult({ kind: "error", text: "Login response missing accessToken" });
    } catch (err) {
      setResult({ kind: "error", text: `Network / server error\n\n${err?.message || String(err)}` });
    } finally {
      setLoading(false);
    }
  }

  function onLogout() {
    clearAccessToken();
    navigate("/login", { replace: true });
  }

  return (
    <>
      <form onSubmit={onSubmit} className={styles.form}>
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

        <Button type="button" disabled={loading} onClick={onLogout}>
          Logout (clear token)
        </Button>
      </form>

      {result && (
        <pre
          className={`${styles.output} ${
            result.kind === "error" ? styles.outputError : styles.outputSuccess
          }`}
        >
          {result.text}
        </pre>
      )}
    </>
  );
}
