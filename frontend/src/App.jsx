import { useState } from "react";
import "./App.css";

export default function App() {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");

  const [loading, setLoading] = useState(false);
  const [output, setOutput] = useState(null); // string

  async function onSubmit(e) {
    e.preventDefault();
    setLoading(true);
    setOutput(null);

    try {
      const res = await fetch("/api/auth/register", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email: email.trim(), password }),
      });

      const contentType = res.headers.get("content-type") || "";
      const body = contentType.includes("application/json")
        ? await res.json()
        : await res.text();

      if (!res.ok) {
        const errText = typeof body === "string" ? body : JSON.stringify(body, null, 2);
        setOutput(`Registration failed (${res.status})\n\n${errText}`);
        return;
      }

      const accountId = body.accountId ?? body.defaultAccountId ?? "(unknown)";
      setOutput(
        `Registered \n\nuserId: ${body.userId}\nemail: ${body.email}\naccountId: ${accountId}`
      );

      setEmail("");
      setPassword("");
    } catch (err) {
      setOutput(`Network / server error\n\n${err?.message || String(err)}`);
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="page">
      <div className="card">
        <h1>Digital Banking</h1>
        <p className="sub">Create an account (default CAD account auto-created).</p>

        <form onSubmit={onSubmit} className="form">
          <label>
            Email
            <input
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              autoComplete="email"
              required
            />
          </label>

          <label>
            Password (8+ chars)
            <input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              minLength={8}
              maxLength={72}
              required
            />
          </label>

          <button type="submit" disabled={loading}>
            {loading ? "Registering..." : "Register"}
          </button>
        </form>

        {output && <pre className="output">{output}</pre>}
      </div>
    </div>
  );
}
