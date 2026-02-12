import { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import Card from "../../shared/ui/Card";
import Button from "../../shared/ui/Button";
import { clearAccessToken } from "../../shared/auth/token";
import { getMe, getAccounts } from "./api";
import styles from "../../styles/HomePage.module.css";

function formatMoney(cents, currency = "CAD") {
  const amount = Number(cents || 0) / 100;
  try {
    return new Intl.NumberFormat(undefined, { style: "currency", currency }).format(amount);
  } catch {
    return `${currency} ${amount.toFixed(2)}`;
  }
}

function statusTone(status) {
  const s = String(status || "").toUpperCase();
  if (s === "ACTIVE") return `${styles.pill} ${styles.pillGood}`;
  if (s === "FROZEN") return `${styles.pill} ${styles.pillWarn}`;
  if (s === "CLOSED") return `${styles.pill} ${styles.pillBad}`;
  return styles.pill;
}

export default function HomePage() {
  const navigate = useNavigate();

  const [loading, setLoading] = useState(true);
  const [me, setMe] = useState(null);
  const [accounts, setAccounts] = useState([]);
  const [error, setError] = useState("");

  const title = useMemo(() => {
    const email = me?.email || "";
    return email ? `Welcome, ${email}` : "Welcome";
  }, [me]);

  const total = useMemo(() => {
    const sum = accounts.reduce((acc, a) => acc + Number(a?.balanceCents || 0), 0);
    return formatMoney(sum, "CAD");
  }, [accounts]);

  async function load() {
    setLoading(true);
    setError("");

    const [meRes, accRes] = await Promise.all([getMe(), getAccounts()]);

    if (!meRes.ok && meRes.status === 401) {
      clearAccessToken();
      navigate("/login", { replace: true });
      return;
    }
    if (!accRes.ok && accRes.status === 401) {
      clearAccessToken();
      navigate("/login", { replace: true });
      return;
    }

    if (!meRes.ok || !accRes.ok) {
      const which = !meRes.ok ? "/api/me" : "/api/accounts";
      const bad = !meRes.ok ? meRes : accRes;
      const errText = typeof bad.body === "string" ? bad.body : JSON.stringify(bad.body, null, 2);
      setError(`Failed to load ${which} (${bad.status})\n\n${errText}`);
      setLoading(false);
      return;
    }

    setMe(meRes.body);
    setAccounts(Array.isArray(accRes.body) ? accRes.body : []);
    setLoading(false);
  }

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  function onLogout() {
    clearAccessToken();
    navigate("/login", { replace: true });
  }

  return (
    <div className={styles.page}>
      <Card className={styles.dashboardCard}>
        <div className={styles.dashboardInner}>
          <div className={styles.header}>
            <div>
              <h1 className={styles.title}>{title}</h1>
              <p className={styles.sub}>Your dashboard: profile and accounts.</p>
            </div>

            <div className={styles.actions}>
              <Button type="button" onClick={load} disabled={loading}>
                {loading ? "Refreshing..." : "Refresh"}
              </Button>
              <Button type="button" onClick={onLogout} disabled={loading}>
                Logout
              </Button>
            </div>
          </div>

          {loading && <p className={styles.sub}>Loading your dashboard...</p>}

          {!loading && error && (
            <pre className={`${styles.output} ${styles.outputError}`}>{error}</pre>
          )}

          {!loading && !error && (
            <div className={styles.grid}>
              {/* Profile */}
              <section className={styles.panel}>
                <div className={styles.panelHeader}>
                  <h2 className={styles.panelTitle}>Profile</h2>
                </div>

                <div className={styles.kv}>
                  <div className={styles.kvRow}>
                    <span className={styles.kvKey}>User ID</span>
                    <span className={styles.kvVal}>{me?.userId ?? "-"}</span>
                  </div>
                  <div className={styles.kvRow}>
                    <span className={styles.kvKey}>Email</span>
                    <span className={styles.kvVal}>{me?.email ?? "-"}</span>
                  </div>
                  <div className={styles.kvRow}>
                    <span className={styles.kvKey}>Role</span>
                    <span className={styles.kvVal}>
                      <span
                        className={`${styles.pill} ${
                          me?.role === "ADMIN" ? styles.pillAdmin : styles.pillUser
                        }`}
                      >
                        {me?.role ?? "UNKNOWN"}
                      </span>
                    </span>
                  </div>
                </div>

                {me?.role === "ADMIN" && (
                  <p className={styles.hint}>
                    Admin tools will appear here soon (deposit, risk review, freeze/unfreeze).
                  </p>
                )}
              </section>

              {/* Accounts */}
              <section className={styles.panel}>
                <div className={styles.panelHeader}>
                  <h2 className={styles.panelTitle}>Accounts</h2>
                  <span className={styles.panelSub}>
                    {accounts.length} {accounts.length === 1 ? "account" : "accounts"}
                  </span>
                </div>

                <div className={styles.balanceHero}>
                  <div className={styles.balanceLabel}>Total available</div>
                  <div className={styles.balanceValue}>{total}</div>
                </div>

                {accounts.length === 0 ? (
                  <p className={styles.sub}>No accounts found.</p>
                ) : (
                  <div className={styles.acctList}>
                    {accounts.map((a) => (
                      <div className={styles.acctRow} key={a.id}>
                        <div className={styles.acctMain}>
                          <div className={styles.acctName}>
                            <span className={styles.mono}>Account • {a.id}</span>
                          </div>
                          <div className={styles.acctMeta}>
                            <span className={statusTone(a.status)}>{a.status}</span>
                            <span className={styles.dot} />
                            <span className={styles.mono}>{a.currency}</span>
                          </div>
                        </div>

                        <div className={styles.acctBal}>
                          <div className={styles.acctBalLabel}>Available</div>
                          <div className={styles.acctBalValue}>
                            {formatMoney(a.balanceCents, a.currency)}
                          </div>
                        </div>
                      </div>
                    ))}
                  </div>
                )}

                <div className={styles.divider} />

                <div className={styles.ctaRow}>
                  <div>
                    <h3 className={styles.ctaTitle}>Next</h3>
                    <p className={styles.sub}>
                      Transfers will live here next (with required Idempotency-Key).
                    </p>
                  </div>
                  <Button type="button" disabled>
                    Transfer money (coming soon)
                  </Button>
                </div>
              </section>
            </div>
          )}
        </div>
      </Card>
    </div>
  );
}
