import { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import Card from "../../shared/ui/Card";
import { apiRequest } from "../../shared/api/http";
import { clearAccessToken } from "../../shared/auth/token";
import { createAdminDeposit } from "./api";
import styles from "../../styles/AdminDepositPage.module.css";

function formatMoney(amountCents, currency = "CAD") {
  const amount = Number(amountCents || 0) / 100;
  try {
    return new Intl.NumberFormat(undefined, {
      style: "currency",
      currency,
    }).format(amount);
  } catch {
    return `${currency} ${amount.toFixed(2)}`;
  }
}

function parseAmountToCents(raw) {
  const value = String(raw || "").trim();

  if (!value) {
    throw new Error("Enter an amount.");
  }

  if (!/^\d+(\.\d{1,2})?$/.test(value)) {
    throw new Error("Amount must be a valid number with up to 2 decimals.");
  }

  const cents = Math.round(Number(value) * 100);

  if (!Number.isFinite(cents) || cents <= 0) {
    throw new Error("Amount must be greater than zero.");
  }

  return cents;
}

function formatDateTime(iso) {
  try {
    return new Intl.DateTimeFormat(undefined, {
      month: "short",
      day: "2-digit",
      year: "numeric",
      hour: "numeric",
      minute: "2-digit",
    }).format(new Date(iso));
  } catch {
    return iso || "-";
  }
}

export default function AdminDepositPage() {
  const navigate = useNavigate();

  const [checkingRole, setCheckingRole] = useState(true);
  const [toAccountId, setToAccountId] = useState("");
  const [amount, setAmount] = useState("");
  const [submitting, setSubmitting] = useState(false);

  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  const [lastDeposit, setLastDeposit] = useState(null);
  const [lastIdempotencyKey, setLastIdempotencyKey] = useState("");

  useEffect(() => {
    let alive = true;

    async function verifyAdmin() {
      const res = await apiRequest("/api/me");

      if (!alive) return;

      if (!res.ok) {
        if (res.status === 401) {
          clearAccessToken();
          navigate("/login", { replace: true });
          return;
        }

        navigate("/home", { replace: true });
        return;
      }

      if (res.body?.role !== "ADMIN") {
        navigate("/home", { replace: true });
        return;
      }

      setCheckingRole(false);
    }

    verifyAdmin();

    return () => {
      alive = false;
    };
  }, [navigate]);

  const previewAmount = useMemo(() => {
    const trimmed = String(amount || "").trim();
    if (!trimmed || !/^\d+(\.\d{1,2})?$/.test(trimmed)) return null;
    return Math.round(Number(trimmed) * 100);
  }, [amount]);

  async function onSubmit(e) {
    e.preventDefault();

    setError("");
    setSuccess("");
    setLastDeposit(null);
    setLastIdempotencyKey("");

    let parsedAccountId;
    let amountCents;

    try {
      parsedAccountId = Number.parseInt(String(toAccountId || "").trim(), 10);

      if (!Number.isFinite(parsedAccountId) || parsedAccountId <= 0) {
        throw new Error("To Account ID must be a positive whole number.");
      }

      amountCents = parseAmountToCents(amount);
    } catch (err) {
      setError(err.message || "Invalid form input.");
      return;
    }

    setSubmitting(true);

    try {
      const result = await createAdminDeposit({
        toAccountId: parsedAccountId,
        amountCents,
      });

      setLastDeposit(result.data);
      setLastIdempotencyKey(result.idempotencyKey);
      setSuccess(
        `Deposit completed successfully for account ${parsedAccountId}.`
      );
      setAmount("");
    } catch (err) {
      if (err?.status === 401) {
        clearAccessToken();
        navigate("/login", { replace: true });
        return;
      }

      if (err?.status === 403) {
        navigate("/home", { replace: true });
        return;
      }

      setError(err?.message || "Failed to complete admin deposit.");
    } finally {
      setSubmitting(false);
    }
  }

  if (checkingRole) {
    return (
      <div className={styles.page}>
        <Card className={styles.card}>
          <h1 className={styles.title}>Admin Deposit</h1>
          <p className={styles.sub}>Checking admin access...</p>
        </Card>
      </div>
    );
  }

  return (
    <div className={styles.page}>
      <Card className={styles.card}>
        <div className={styles.header}>
          <div>
            <h1 className={styles.title}>Admin Deposit</h1>
            <p className={styles.sub}>
              Fund a customer account from treasury for testing and demos.
            </p>
          </div>
        </div>

        <form className={styles.form} onSubmit={onSubmit}>
          <label className={styles.field}>
            <span className={styles.label}>To Account ID</span>
            <input
              className={styles.input}
              type="number"
              min="1"
              step="1"
              inputMode="numeric"
              value={toAccountId}
              onChange={(e) => setToAccountId(e.target.value)}
              placeholder="e.g. 5"
              disabled={submitting}
            />
          </label>

          <label className={styles.field}>
            <span className={styles.label}>Amount (CAD)</span>
            <input
              className={styles.input}
              type="text"
              inputMode="decimal"
              value={amount}
              onChange={(e) => setAmount(e.target.value)}
              placeholder="e.g. 250.00"
              disabled={submitting}
            />
          </label>

          <div className={styles.previewBox}>
            <div className={styles.previewLabel}>Deposit preview</div>
            <div className={styles.previewValue}>
              {previewAmount == null ? "Enter a valid amount" : formatMoney(previewAmount, "CAD")}
            </div>
          </div>

          <div className={styles.actions}>
            <button
              type="submit"
              className={styles.primaryButton}
              disabled={submitting}
            >
              {submitting ? "Depositing..." : "Submit Deposit"}
            </button>
          </div>
        </form>

        {error && <div className={styles.errorBox}>{error}</div>}
        {success && <div className={styles.successBox}>{success}</div>}

        {lastDeposit && (
          <section className={styles.resultCard}>
            <div className={styles.resultHeader}>
              <h2 className={styles.resultTitle}>Last Deposit Result</h2>
            </div>

            <div className={styles.resultGrid}>
              <div className={styles.detailBox}>
                <div className={styles.detailLabel}>Transfer ID</div>
                <div className={styles.detailValue}>{lastDeposit.id}</div>
              </div>

              <div className={styles.detailBox}>
                <div className={styles.detailLabel}>Status</div>
                <div className={styles.detailValue}>{lastDeposit.status}</div>
              </div>

              <div className={styles.detailBox}>
                <div className={styles.detailLabel}>Amount</div>
                <div className={styles.detailValue}>
                  {formatMoney(lastDeposit.amountCents, lastDeposit.currency)}
                </div>
              </div>

              <div className={styles.detailBox}>
                <div className={styles.detailLabel}>To Account</div>
                <div className={styles.detailValue}>{lastDeposit.toAccountId}</div>
              </div>

              <div className={styles.detailBox}>
                <div className={styles.detailLabel}>From Email</div>
                <div className={styles.detailValue}>
                  {lastDeposit.fromEmail || "-"}
                </div>
              </div>

              <div className={styles.detailBox}>
                <div className={styles.detailLabel}>To Email</div>
                <div className={styles.detailValue}>
                  {lastDeposit.toEmail || "-"}
                </div>
              </div>

              <div className={styles.detailBox}>
                <div className={styles.detailLabel}>Created</div>
                <div className={styles.detailValue}>
                  {formatDateTime(lastDeposit.createdAt)}
                </div>
              </div>

              <div className={styles.detailBox}>
                <div className={styles.detailLabel}>Idempotency Key</div>
                <div className={styles.detailValueMono}>
                  {lastIdempotencyKey || "-"}
                </div>
              </div>
            </div>
          </section>
        )}
      </Card>
    </div>
  );
}