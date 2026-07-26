import { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import Card from "../../shared/ui/Card";
import { apiRequest } from "../../shared/api/http";

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

function formatCount(n) {
  return new Intl.NumberFormat(undefined).format(Number(n || 0));
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
  const [stats, setStats] = useState(null);
  const [error, setError] = useState("");

  const isAdmin = me?.role === "ADMIN";

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

    try {
      const meRes = await getMe();

      if (!meRes.ok && (meRes.status === 401 || meRes.status === 403)) {
        navigate("/login", { replace: true });
        return;
      }

      if (!meRes.ok) {
        const errText =
          typeof meRes.body === "string" ? meRes.body : JSON.stringify(meRes.body, null, 2);
        setError(`Failed to load /api/me (${meRes.status})\n\n${errText}`);
        setLoading(false);
        return;
      }

      setMe(meRes.body);

      const admin = meRes.body?.role === "ADMIN";

      if (admin) {
        const statsRes = await apiRequest("/api/admin/stats");

        if (!statsRes.ok && (statsRes.status === 401 || statsRes.status === 403)) {
          navigate("/login", { replace: true });
          return;
        }

        if (statsRes.ok) {
          setStats(statsRes.body);
        }
        setLoading(false);
        return;
      }

      const accRes = await getAccounts();

      if (!accRes.ok && (accRes.status === 401 || accRes.status === 403)) {
        navigate("/login", { replace: true });
        return;
      }

      if (!accRes.ok) {
        const errText =
          typeof accRes.body === "string" ? accRes.body : JSON.stringify(accRes.body, null, 2);
        setError(`Failed to load /api/accounts (${accRes.status})\n\n${errText}`);
        setLoading(false);
        return;
      }

      setAccounts(Array.isArray(accRes.body) ? accRes.body : []);
      setLoading(false);
    } catch {
      setError("Failed to load your dashboard. Please try again.");
      setLoading(false);
    }
  }

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return (
    <div className={styles.page}>
      <Card className={styles.dashboardCard}>
        <div className={styles.dashboardInner}>
          <div className={styles.header}>
            <div>
              <h1 className={styles.title}>{title}</h1>
              <p className={styles.sub}>
                {isAdmin
                  ? "Operations overview: review queue, activity, and platform totals."
                  : "Your dashboard: profile and accounts."}
              </p>
            </div>
          </div>

          {loading && <p className={styles.sub}>Loading your dashboard...</p>}

          {!loading && error && (
            <pre className={`${styles.output} ${styles.outputError}`}>{error}</pre>
          )}

          {!loading && !error && isAdmin && (
            <AdminDashboard stats={stats} navigate={navigate} />
          )}

          {!loading && !error && !isAdmin && (
            <UserDashboard
              me={me}
              accounts={accounts}
              total={total}
              navigate={navigate}
            />
          )}
        </div>
      </Card>
    </div>
  );
}

function AdminDashboard({ stats, navigate }) {
  const pending = Number(stats?.pendingReviewCount || 0);
  const hasPending = pending > 0;

  return (
    <div className={styles.adminWrap}>
      {/* Review queue hero */}
      <section
        className={`${styles.reviewHero} ${hasPending ? styles.reviewHeroActive : ""}`}
      >
        <div>
          <div className={styles.reviewHeroLabel}>Transfers awaiting review</div>
          <div className={styles.reviewHeroValue}>{formatCount(pending)}</div>
          <div className={styles.reviewHeroSub}>
            {hasPending
              ? `${formatMoney(stats?.heldAmountCents, "CAD")} held pending decision`
              : "No transfers are waiting. You're all caught up."}
          </div>
        </div>

        <button
          type="button"
          className={hasPending ? styles.primaryButton : styles.secondaryButton}
          onClick={() => navigate("/admin/risk")}
        >
          {hasPending ? "Review now" : "Open Admin Review"}
        </button>
      </section>

      {/* Stat tiles */}
      <div className={styles.statGrid}>
        <StatTile
          label="Completed transfers today"
          value={formatCount(stats?.transfersTodayCount)}
          sub={`${formatMoney(stats?.transfersTodayVolumeCents, "CAD")} moved today`}
        />
        <StatTile
          label="Active users"
          value={formatCount(stats?.activeUserCount)}
          sub="Excludes system treasury"
        />
        <StatTile
          label="Customer balances"
          value={formatMoney(stats?.systemBalanceCents, "CAD")}
          sub="Total held across all accounts"
        />
      </div>

      {/* Lifetime breakdown */}
      <section className={styles.panel}>
        <div className={styles.panelHeader}>
          <h2 className={styles.panelTitle}>Lifetime transfer outcomes</h2>
        </div>
        <div className={styles.outcomeRow}>
          <OutcomeChip
            tone="good"
            label="Completed"
            value={formatCount(stats?.completedCount)}
          />
          <OutcomeChip
            tone="warn"
            label="Held"
            value={formatCount(stats?.pendingReviewCount)}
          />
          <OutcomeChip
            tone="bad"
            label="Rejected"
            value={formatCount(stats?.rejectedCount)}
          />
          <OutcomeChip
            tone="bad"
            label="Blocked"
            value={formatCount(stats?.blockedCount)}
          />
        </div>
      </section>
    </div>
  );
}

function StatTile({ label, value, sub }) {
  return (
    <div className={styles.statTile}>
      <div className={styles.statLabel}>{label}</div>
      <div className={styles.statValue}>{value}</div>
      {sub && <div className={styles.statSub}>{sub}</div>}
    </div>
  );
}

function OutcomeChip({ tone, label, value }) {
  const toneClass =
    tone === "good"
      ? styles.pillGood
      : tone === "warn"
      ? styles.pillWarn
      : tone === "bad"
      ? styles.pillBad
      : "";

  return (
    <div className={styles.outcomeChip}>
      <span className={`${styles.pill} ${toneClass}`}>{label}</span>
      <span className={styles.outcomeValue}>{value}</span>
    </div>
  );
}

function UserDashboard({ me, accounts, total, navigate }) {
  return (
    <div className={styles.grid}>
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
              <span className={`${styles.pill} ${styles.pillUser}`}>
                {me?.role ?? "UNKNOWN"}
              </span>
            </span>
          </div>
        </div>
      </section>

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
            <h3 className={styles.ctaTitle}>Quick actions</h3>
            <p className={styles.sub}>
              Manage payees, send transfers, and track activity from the main navigation.
            </p>
          </div>

          <div className={styles.ctaActions}>
            <button
              type="button"
              className={styles.primaryButton}
              onClick={() => navigate("/transfer")}
            >
              Transfer money
            </button>
            <button
              type="button"
              className={styles.secondaryButton}
              onClick={() => navigate("/statements")}
            >
              View statements
            </button>
          </div>
        </div>
      </section>
    </div>
  );
}