import { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";

import Card from "../../shared/ui/Card";
import Button from "../../shared/ui/Button";
import TextField from "../../shared/ui/TextField";

import { clearAccessToken } from "../../shared/auth/token";
import { getAccounts } from "../home/api";
import { createTransfer } from "./api";

import styles from "../../styles/TransferPage.module.css";

function formatMoney(cents, currency = "CAD") {
  const amount = Number(cents || 0) / 100;
  try {
    return new Intl.NumberFormat(undefined, { style: "currency", currency }).format(amount);
  } catch {
    return `${currency} ${amount.toFixed(2)}`;
  }
}

function parseMoneyToCents(input) {
  const v = String(input ?? "").trim();
  if (!v) return null;

  // allow "10", "10.5", "10.50"
  if (!/^\d+(\.\d{0,2})?$/.test(v)) return null;

  const [whole, fracRaw = ""] = v.split(".");
  const frac = (fracRaw + "00").slice(0, 2);
  const cents = Number(whole) * 100 + Number(frac);
  return Number.isFinite(cents) && cents > 0 ? cents : null;
}

function makeIdempotencyKey() {
  const uuid =
    typeof crypto !== "undefined" && crypto.randomUUID ? crypto.randomUUID() : null;
  return `web-${uuid ?? `${Date.now()}-${Math.random().toString(16).slice(2)}`}`;
}

export default function TransferPage() {
  const navigate = useNavigate();

  const [loading, setLoading] = useState(true);
  const [accounts, setAccounts] = useState([]);
  const [fromAccountId, setFromAccountId] = useState("");
  const [toAccountId, setToAccountId] = useState("");
  const [amount, setAmount] = useState("");

  const [submitting, setSubmitting] = useState(false);
  const [lastKey, setLastKey] = useState("");
  const [result, setResult] = useState(null);
  const [error, setError] = useState("");

  const fromAccount = useMemo(() => {
    const idNum = Number(fromAccountId);
    return accounts.find((a) => Number(a.id) === idNum) || null;
  }, [accounts, fromAccountId]);

  async function loadAccounts() {
    setLoading(true);
    setError("");

    const res = await getAccounts();
    if (!res.ok && res.status === 401) {
      clearAccessToken();
      navigate("/login", { replace: true });
      return;
    }
    if (!res.ok) {
      const errText = typeof res.body === "string" ? res.body : JSON.stringify(res.body, null, 2);
      setError(`Failed to load /api/accounts (${res.status})\n\n${errText}`);
      setLoading(false);
      return;
    }

    const list = Array.isArray(res.body) ? res.body : [];
    setAccounts(list);

    if (!fromAccountId && list.length > 0) {
      setFromAccountId(String(list[0].id));
    }

    setLoading(false);
  }

  useEffect(() => {
    loadAccounts();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  async function onSubmit(e) {
    e.preventDefault();
    setError("");
    setResult(null);

    const fromId = Number(fromAccountId);
    const toId = Number(toAccountId);
    const amountCents = parseMoneyToCents(amount);

    if (!Number.isFinite(fromId) || fromId <= 0) {
      setError("Pick a valid From account.");
      return;
    }
    if (!Number.isFinite(toId) || toId <= 0) {
      setError("Enter a valid To account id.");
      return;
    }
    if (fromId === toId) {
      setError("From and To accounts must be different.");
      return;
    }
    if (amountCents == null) {
      setError("Enter a valid amount like 10 or 10.50.");
      return;
    }

    const key = makeIdempotencyKey();
    setLastKey(key);
    setSubmitting(true);

    const res = await createTransfer(
      { fromAccountId: fromId, toAccountId: toId, amountCents, currency: "CAD" },
      key
    );

    if (!res.ok && res.status === 401) {
      clearAccessToken();
      navigate("/login", { replace: true });
      return;
    }

    if (!res.ok) {
      const errText = typeof res.body === "string" ? res.body : JSON.stringify(res.body, null, 2);
      setError(`Transfer failed (${res.status})\n\n${errText}`);
      setSubmitting(false);
      return;
    }

    setResult(res.body);
    await loadAccounts();
    setSubmitting(false);
  }

  return (
    <div className={styles.page}>
      <Card className={styles.card}>
        <div className={styles.header}>
          <div>
            <h1 className={styles.title}>Transfer</h1>
            <p className={styles.sub}>
              Send money using an idempotent backend call (Idempotency-Key required).
            </p>
          </div>
        </div>

        {loading && <p className={styles.sub}>Loading accounts...</p>}

        {!loading && (
          <div className={styles.grid}>
            <section className={styles.panel}>
              <h2 className={styles.panelTitle}>Create transfer</h2>

              <form className={styles.form} onSubmit={onSubmit}>
                <label className={styles.label}>
                  <span className={styles.labelText}>From account</span>
                  <select
                    className={styles.select}
                    value={fromAccountId}
                    onChange={(e) => setFromAccountId(e.target.value)}
                    disabled={submitting || accounts.length === 0}
                  >
                    {accounts.length === 0 ? (
                      <option value="">No accounts</option>
                    ) : (
                      accounts.map((a) => (
                        <option key={a.id} value={a.id}>
                          {a.id} • {a.accountType} • {formatMoney(a.balanceCents, a.currency)}
                        </option>
                      ))
                    )}
                  </select>
                </label>

                <TextField
                  label="To account id"
                  placeholder="e.g. 4"
                  inputMode="numeric"
                  value={toAccountId}
                  onChange={(e) => setToAccountId(e.target.value)}
                  disabled={submitting}
                />

                <TextField
                  label="Amount (CAD)"
                  placeholder="e.g. 10.50"
                  inputMode="decimal"
                  value={amount}
                  onChange={(e) => setAmount(e.target.value)}
                  disabled={submitting}
                />

                {fromAccount && (
                  <p className={styles.hint}>
                    Available: <span className={styles.mono}>{formatMoney(fromAccount.balanceCents, "CAD")}</span>
                  </p>
                )}

                <div className={styles.actions}>
                  <Button type="submit" disabled={submitting || accounts.length === 0}>
                    {submitting ? "Sending..." : "Send transfer"}
                  </Button>
                </div>
              </form>

              {lastKey && (
                <p className={styles.hint}>
                  Last Idempotency-Key: <span className={styles.mono}>{lastKey}</span>
                </p>
              )}

              {error && <pre className={`${styles.output} ${styles.outputError}`}>{error}</pre>}
              {result && <pre className={styles.output}>{JSON.stringify(result, null, 2)}</pre>}
            </section>

            <section className={styles.panel}>
              <h2 className={styles.panelTitle}>Your accounts</h2>

              {accounts.length === 0 ? (
                <p className={styles.sub}>No accounts found.</p>
              ) : (
                <div className={styles.acctList}>
                  {accounts.map((a) => (
                    <div className={styles.acctRow} key={a.id}>
                      <div>
                        <div className={styles.mono}>Account • {a.id}</div>
                        <div className={styles.subSmall}>{a.accountType} • {a.status} • {a.currency}</div>
                      </div>
                      <div className={styles.right}>
                        <div className={styles.subSmall}>Available</div>
                        <div className={styles.balance}>{formatMoney(a.balanceCents, a.currency)}</div>
                      </div>
                    </div>
                  ))}
                </div>
              )}

              <p className={styles.hint}>
                For now, you need the recipient’s account id. Later we’ll replace this with Payees.
              </p>
            </section>
          </div>
        )}
      </Card>
    </div>
  );
}
