import { useEffect, useMemo, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";

import Card from "../../shared/ui/Card";
import Button from "../../shared/ui/Button";
import TextField from "../../shared/ui/TextField";

import { clearAccessToken } from "../../shared/auth/token";
import { getAccounts } from "../home/api";
import { listPayees } from "../payees/api";
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
  if (!/^\d+(\.\d{0,2})?$/.test(v)) return null;

  const [whole, fracRaw = ""] = v.split(".");
  const frac = (fracRaw + "00").slice(0, 2);
  const cents = Number(whole) * 100 + Number(frac);
  return Number.isFinite(cents) && cents > 0 ? cents : null;
}

function makeIdempotencyKey() {
  const uuid = typeof crypto !== "undefined" && crypto.randomUUID ? crypto.randomUUID() : null;
  return `web-${uuid ?? `${Date.now()}-${Math.random().toString(16).slice(2)}`}`;
}

export default function TransferPage() {
  const navigate = useNavigate();

  const [loading, setLoading] = useState(true);
  const [accounts, setAccounts] = useState([]);
  const [payees, setPayees] = useState([]);

  const [fromAccountId, setFromAccountId] = useState("");
  const [toPayeeId, setToPayeeId] = useState("");
  const [amount, setAmount] = useState("");

  const [submitting, setSubmitting] = useState(false);
  const [lastKey, setLastKey] = useState("");
  const [result, setResult] = useState(null);
  const [error, setError] = useState("");

  // IMPORTANT: idempotency key must be stable for the same "draft transfer"
  // useRef so it updates synchronously (state can lag during fast double-submit).
  const draftIdemKeyRef = useRef("");

  const fromAccount = useMemo(() => {
    const idNum = Number(fromAccountId);
    return accounts.find((a) => Number(a.id) === idNum) || null;
  }, [accounts, fromAccountId]);

  const selectedPayee = useMemo(() => {
    const idNum = Number(toPayeeId);
    return payees.find((p) => Number(p.id) === idNum) || null;
  }, [payees, toPayeeId]);

  // If user edits the transfer details, this is a *new* intent → new idempotency key.
  useEffect(() => {
    draftIdemKeyRef.current = "";
  }, [fromAccountId, toPayeeId, amount]);

  function getOrCreateDraftIdemKey() {
    if (!draftIdemKeyRef.current) {
      draftIdemKeyRef.current = makeIdempotencyKey();
    }
    return draftIdemKeyRef.current;
  }

  function clearDraftIdemKey() {
    draftIdemKeyRef.current = "";
  }

  async function loadData() {
    setLoading(true);
    setError("");

    const [acctRes, payeeRes] = await Promise.all([getAccounts(), listPayees()]);

    for (const res of [acctRes, payeeRes]) {
      if (!res.ok && res.status === 401) {
        clearAccessToken();
        navigate("/login", { replace: true });
        return;
      }
    }

    if (!acctRes.ok) {
      const errText = typeof acctRes.body === "string" ? acctRes.body : JSON.stringify(acctRes.body, null, 2);
      setError(`Failed to load /api/accounts (${acctRes.status})\n\n${errText}`);
      setLoading(false);
      return;
    }

    if (!payeeRes.ok) {
      const errText = typeof payeeRes.body === "string" ? payeeRes.body : JSON.stringify(payeeRes.body, null, 2);
      setError(`Failed to load /api/payees (${payeeRes.status})\n\n${errText}`);
      setLoading(false);
      return;
    }

    const acctList = Array.isArray(acctRes.body) ? acctRes.body : [];
    const payeeList = Array.isArray(payeeRes.body) ? payeeRes.body : [];

    setAccounts(acctList);
    setPayees(payeeList);

    if (!fromAccountId && acctList.length > 0) {
      setFromAccountId(String(acctList[0].id));
    }
    if (!toPayeeId && payeeList.length > 0) {
      setToPayeeId(String(payeeList[0].id));
    }

    setLoading(false);
  }

  useEffect(() => {
    loadData();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  async function onSubmit(e) {
    e.preventDefault();
    setError("");
    setResult(null);

    const fromId = Number(fromAccountId);
    const payeeIdNum = Number(toPayeeId);
    const amountCents = parseMoneyToCents(amount);

    if (!Number.isFinite(fromId) || fromId <= 0) {
      setError("Pick a valid From account.");
      return;
    }
    if (!Number.isFinite(payeeIdNum) || payeeIdNum <= 0) {
      setError("Pick a valid Payee.");
      return;
    }
    if (amountCents == null) {
      setError("Enter a valid amount like 10 or 10.50.");
      return;
    }
    if (!selectedPayee) {
      setError("Pick a valid Payee.");
      return;
    }

    const ok = window.confirm(
      `Confirm transfer\n\nTo: ${selectedPayee.email}\nAmount: ${formatMoney(amountCents, "CAD")}`
    );
    if (!ok) return;

    // KEY CHANGE: reuse the same key for retries of the same intent
    const key = getOrCreateDraftIdemKey();
    setLastKey(key);

    setSubmitting(true);

    const res = await createTransfer(
      { fromAccountId: fromId, payeeId: payeeIdNum, amountCents, currency: "CAD" },
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
      // keep draft key so a retry uses the same key
      return;
    }

    setResult(res.body);
    await loadData();
    setSubmitting(false);

    // Success means this intent is done; next transfer should get a new key.
    clearDraftIdemKey();
    setAmount("");
  }

  return (
    <div className={styles.page}>
      <Card className={styles.card}>
        <div className={styles.header}>
          <div>
            <h1 className={styles.title}>Transfer</h1>
            <p className={styles.sub}>Send money to an active payee (Idempotency-Key required).</p>
          </div>
        </div>

        {loading && <p className={styles.sub}>Loading accounts and payees...</p>}

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
                          {a.accountType} • {formatMoney(a.balanceCents, a.currency)}
                        </option>
                      ))
                    )}
                  </select>
                </label>

                <label className={styles.label}>
                  <span className={styles.labelText}>To payee</span>
                  <select
                    className={styles.select}
                    value={toPayeeId}
                    onChange={(e) => setToPayeeId(e.target.value)}
                    disabled={submitting || payees.length === 0}
                  >
                    {payees.length === 0 ? (
                      <option value="">No payees yet</option>
                    ) : (
                      payees.map((p) => (
                        <option key={p.id} value={p.id}>
                          {p.email}
                          {p.label ? ` (${p.label})` : ""}
                        </option>
                      ))
                    )}
                  </select>
                </label>

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
                  <Button type="submit" disabled={submitting || accounts.length === 0 || payees.length === 0}>
                    {submitting ? "Sending..." : "Send transfer"}
                  </Button>
                </div>
              </form>

              {payees.length === 0 && (
                <p className={styles.hint}>Add a payee first, then come back here to send money.</p>
              )}

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
                        <div className={styles.mono}>{a.accountType}</div>
                        <div className={styles.subSmall}>
                          {a.status} • {a.currency}
                        </div>
                      </div>
                      <div className={styles.right}>
                        <div className={styles.subSmall}>Available</div>
                        <div className={styles.balance}>{formatMoney(a.balanceCents, a.currency)}</div>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </section>
          </div>
        )}
      </Card>
    </div>
  );
}
