import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import Card from "../../shared/ui/Card";
import { apiRequest } from "../../shared/api/http";

import { lookupUserByEmail } from "./api";
import styles from "../../styles/AdminFindUserPage.module.css";

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

function getStatusClass(status) {
  const s = String(status || "").toUpperCase();
  if (s === "ACTIVE") return `${styles.statusPill} ${styles.statusActive}`;
  if (s === "FROZEN") return `${styles.statusPill} ${styles.statusFrozen}`;
  if (s === "CLOSED") return `${styles.statusPill} ${styles.statusClosed}`;
  return styles.statusPill;
}

export default function AdminFindUserPage() {
  const navigate = useNavigate();

  const [checkingRole, setCheckingRole] = useState(true);
  const [searchEmail, setSearchEmail] = useState("");
  const [searchLoading, setSearchLoading] = useState(false);

  const [selectedUser, setSelectedUser] = useState(null);

  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");

  useEffect(() => {
    let alive = true;

    async function verifyAdmin() {
      const res = await apiRequest("/api/me");

      if (!alive) return;

      if (!res.ok) {
        if (res.status === 401) {
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

  async function onSearch(e) {
    e.preventDefault();

    setError("");
    setSuccess("");
    setSelectedUser(null);

    const trimmed = String(searchEmail || "").trim();
    if (!trimmed) {
      setError("Enter an email to search.");
      return;
    }

    setSearchLoading(true);

    try {
      const result = await lookupUserByEmail(trimmed);
      setSelectedUser(result);
      setSuccess(`Found user: ${result.email}`);
    } catch (err) {
      if (err?.status === 401) {
        navigate("/login", { replace: true });
        return;
      }

      if (err?.status === 403) {
        navigate("/home", { replace: true });
        return;
      }

      setSelectedUser(null);
      setError(err?.message || "Failed to find user by email.");
    } finally {
      setSearchLoading(false);
    }
  }

  function onChangeEmail(value) {
    setSearchEmail(value);
    setError("");
    setSuccess("");
  }

  if (checkingRole) {
    return (
      <div className={styles.page}>
        <Card className={styles.card}>
          <h1 className={styles.title}>Manage Accounts</h1>
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
            <h1 className={styles.title}>Manage Accounts</h1>
            <p className={styles.sub}>
              Search for a user by email to view their accounts, recent activity, and manage account status.
            </p>
          </div>
        </div>

        <form className={styles.searchForm} onSubmit={onSearch}>
          <label className={styles.field}>
            <span className={styles.label}>User email</span>
            <input
              className={styles.input}
              type="email"
              value={searchEmail}
              onChange={(e) => onChangeEmail(e.target.value)}
              placeholder="e.g. user@example.com"
              disabled={searchLoading}
            />
          </label>

          <div className={styles.searchActions}>
            <button
              type="submit"
              className={styles.primaryButton}
              disabled={searchLoading}
            >
              {searchLoading ? "Searching..." : "Search User"}
            </button>
          </div>
        </form>

        {error && <div className={styles.errorBox}>{error}</div>}
        {success && <div className={styles.successBox}>{success}</div>}

        {selectedUser && (
          <>
            {/* User Details */}
            <section className={styles.userDetailsSection}>
              <div className={styles.sectionHeader}>
                <h2 className={styles.sectionTitle}>User Details</h2>
              </div>

              <div className={styles.detailGrid}>
                <div className={styles.detailBox}>
                  <div className={styles.detailLabel}>User ID</div>
                  <div className={styles.detailValue}>{selectedUser.userId}</div>
                </div>

                <div className={styles.detailBox}>
                  <div className={styles.detailLabel}>Email</div>
                  <div className={styles.detailValue}>{selectedUser.email}</div>
                </div>

                <div className={styles.detailBox}>
                  <div className={styles.detailLabel}>Account Count</div>
                  <div className={styles.detailValue}>
                    1 account (full list coming with backend update)
                  </div>
                </div>
              </div>
            </section>

            {/* Accounts List */}
            <section className={styles.accountsList}>
              <div className={styles.sectionHeader}>
                <h2 className={styles.sectionTitle}>Accounts</h2>
              </div>

              <div className={styles.accountCard}>
                <div className={styles.accountHeader}>
                  <div className={styles.accountId}>
                    Account #{selectedUser.accountId}
                  </div>
                  <span className={getStatusClass(selectedUser.status)}>
                    {selectedUser.status}
                  </span>
                </div>

                <div className={styles.detailGrid}>
                  <div className={styles.detailBox}>
                    <div className={styles.detailLabel}>Currency</div>
                    <div className={styles.detailValue}>{selectedUser.currency}</div>
                  </div>

                  <div className={styles.detailBox}>
                    <div className={styles.detailLabel}>Balance</div>
                    <div className={styles.detailValue}>
                      {formatMoney(selectedUser.balanceCents, selectedUser.currency)}
                    </div>
                  </div>

                  <div className={styles.detailBox}>
                    <div className={styles.detailLabel}>Account Type</div>
                    <div className={styles.detailValue}>CHEQUING</div>
                  </div>
                </div>

                <div className={styles.accountActions} style={{ marginTop: "12px" }}>
                  <button
                    type="button"
                    className={styles.actionButton}
                    disabled
                    title="Backend endpoint coming soon"
                  >
                    Freeze Account
                  </button>
                  <button
                    type="button"
                    className={styles.actionButton}
                    disabled
                    title="Backend endpoint coming soon"
                  >
                    Unfreeze Account
                  </button>
                  <button
                    type="button"
                    className={styles.actionButton}
                    disabled
                    title="Backend endpoint coming soon"
                  >
                    Close Account
                  </button>
                </div>
              </div>
            </section>

            {/* Recent Transfers - Placeholder */}
            <section className={styles.placeholderSection}>
              <div className={styles.sectionHeader}>
                <h2 className={styles.sectionTitle}>Recent Transfers</h2>
              </div>
              <p className={styles.placeholderText}>
                Recent transfer history will appear here once the backend endpoint is ready.
              </p>
            </section>

            {/* Held Transfers - Placeholder */}
            <section className={styles.placeholderSection}>
              <div className={styles.sectionHeader}>
                <h2 className={styles.sectionTitle}>Held Transfers</h2>
              </div>
              <p className={styles.placeholderText}>
                Pending/held transfers for this user will appear here with approve/reject actions once the backend endpoint is ready.
              </p>
            </section>

            {/* Admin Deposits - Placeholder */}
            <section className={styles.placeholderSection}>
              <div className={styles.sectionHeader}>
                <h2 className={styles.sectionTitle}>Recent Admin Deposits</h2>
              </div>
              <p className={styles.placeholderText}>
                Recent admin deposits to this user will appear here once the backend endpoint is ready.
              </p>
            </section>
          </>
        )}
      </Card>
    </div>
  );
}