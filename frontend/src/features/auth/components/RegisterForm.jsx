import { useState } from "react";
import { useNavigate } from "react-router-dom";
import TextField from "../../../shared/ui/TextField";
import Button from "../../../shared/ui/Button";
import { register } from "../api";
import styles from "../../../styles/AuthForm.module.css";

export default function RegisterForm() {
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
      const { ok, status, body } = await register({
        email: email.trim(),
        password,
      });

      if (!ok) {
        const errText = typeof body === "string" ? body : JSON.stringify(body, null, 2);
        setResult({ kind: "error", text: `Registration failed (${status})\n\n${errText}` });
        return;
      }

      const accountId = body.accountId ?? "(unknown)";
      setResult({
        kind: "success",
        text: `Registration successful\n\nuserId: ${body.userId}\nemail: ${body.email}\naccountId: ${accountId}`,
      });

      setEmail("");
      setPassword("");

      window.alert("Registration successful. Please log in.");
      navigate("/login", { replace: true });
    } catch (err) {
      setResult({ kind: "error", text: `Network / server error\n\n${err?.message || String(err)}` });
    } finally {
      setLoading(false);
    }
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
          label="Password (8+ chars)"
          type="password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          minLength={8}
          maxLength={72}
          required
        />

        <Button type="submit" disabled={loading}>
          {loading ? "Registering..." : "Register"}
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
