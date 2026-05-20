import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import Card from "../../shared/ui/Card";
import { apiRequest } from "../../shared/api/http";

import {
  lookupUserByEmail,
  getUserAccounts,
  freezeAccount,
  unfreezeAccount,
  closeAccount
} from "./api";
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
  const [userAccounts, setUserAccounts] = useState([]);
  const [accountsLoading, setAccountsLoading] = useState(false);

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
    setUserAccounts([]);

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

      // New lookup endpoint returns accounts directly
      if (result.role !== "ADMIN" && result.accounts) {
        setUserAccounts(result.accounts);
      }
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

  async function handleFreezeAccount(accountId) {
    setError("");
    setSuccess("");

    try {
      await freezeAccount(accountId);
      setSuccess("Account frozen successfully.");

      // Reload accounts
      const accountsData = await getUserAccounts(selectedUser.userId);
      setUserAccounts(accountsData.accounts || []);
    } catch (err) {
      if (err?.status === 401) {
        navigate("/login", { replace: true });
        return;
      }
      setError(err?.message || "Failed to freeze account.");
    }
  }

  async function handleUnfreezeAccount(accountId) {
    setError("");
    setSuccess("");

    try {
      await unfreezeAccount(accountId);
      setSuccess("Account unfrozen successfully.");

      // Reload accounts
      const accountsData = await getUserAccounts(selectedUser.userId);
      setUserAccounts(accountsData.accounts || []);
    } catch (err) {
      if (err?.status === 401) {
        navigate("/login", { replace: true });
        return;
      }
      setError(err?.message || "Failed to unfreeze account.");
    }
  }

  async function handleCloseAccount(accountId) {
    setError("");
    setSuccess("");

    if (!window.confirm("Are you sure you want to close this account? This action cannot be undone.")) {
      return;
    }

    try {
      await closeAccount(accountId);
      setSuccess("Account closed successfully.");

      // Reload accounts
      const accountsData = await getUserAccounts(selectedUser.userId);
      setUserAccounts(accountsData.accounts || []);
    } catch (err) {
      if (err?.status === 401) {
        navigate("/login", { replace: true });
        return;
      }
      setError(err?.message || "Failed to close account.");
    }
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
                    <div className={styles.detailLabel}>Role</div>
                    <div className={styles.detailValue}>
                      <span className={selectedUser.role === "ADMIN" ? styles.statusPill : `${styles.statusPill} ${styles.statusActive}`}>
                        {selectedUser.role || "USER"}
                      </span>
                    </div>
                  </div>

                  {selectedUser.role !== "ADMIN" && (
                    <div className={styles.detailBox}>
                      <div className={styles.detailLabel}>Account Count</div>
                      <div className={styles.detailValue}>
                        {accountsLoading ? "Loading..." : `${userAccounts.length} account(s)`}
                      </div>
                    </div>
                  )}
                </div>
              </section>

              {selectedUser.role === "ADMIN" ? (
                /* Admin-specific view */
                <section className={styles.placeholderSection}>
                  <p className={styles.placeholderText}>
                    Admin users do not have transferable accounts. View this admin's activity in the Audit Log section.
                  </p>
                </section>
              ) : (
                /* Regular user view with accounts and actions */
                <>
                  {/* Accounts List */}
                  <section className={styles.accountsList}>
                    <div className={styles.sectionHeader}>
                      <h2 className={styles.sectionTitle}>Accounts</h2>
                    </div>

                    {accountsLoading ? (
                      <p className={styles.placeholderText}>Loading accounts...</p>
                    ) : userAccounts.length === 0 ? (
                      <p className={styles.placeholderText}>No accounts found for this user.</p>
                    ) : (
                      userAccounts.map((account) => (
                        <div key={account.accountId} className={styles.accountCard}>
                          <div className={styles.accountHeader}>
                            <div className={styles.accountId}>
                              Account #{account.accountId}
                            </div>
                            <span className={getStatusClass(account.status)}>
                              {account.status}
                            </span>
                          </div>

                          <div className={styles.detailGrid}>
                            <div className={styles.detailBox}>
                              <div className={styles.detailLabel}>Account Type</div>
                              <div className={styles.detailValue}>{account.accountType}</div>
                            </div>

                            <div className={styles.detailBox}>
                              <div className={styles.detailLabel}>Currency</div>
                              <div className={styles.detailValue}>{account.currency}</div>
                            </div>

                            <div className={styles.detailBox}>
                              <div className={styles.detailLabel}>Balance</div>
                              <div className={styles.detailValue}>
                                {formatMoney(account.balanceCents, account.currency)}
                              </div>
                            </div>
                          </div>

                          <div className={styles.accountActions} style={{ marginTop: "12px" }}>
                            {account.status.toUpperCase() === "ACTIVE" && (
                              <button
                                type="button"
                                className={styles.actionButton}
                                onClick={() => handleFreezeAccount(account.accountId)}
                              >
                                Freeze Account
                              </button>
                            )}

                            {account.status.toUpperCase() === "FROZEN" && (
                              <button
                                type="button"
                                className={styles.actionButton}
                                onClick={() => handleUnfreezeAccount(account.accountId)}
                              >
                                Unfreeze Account
                              </button>
                            )}

                            {(account.status.toUpperCase() === "ACTIVE" || account.status.toUpperCase() === "FROZEN") && (
                              <button
                                type="button"
                                className={styles.actionButton}
                                onClick={() => handleCloseAccount(account.accountId)}
                                disabled={account.balanceCents !== 0}
                                title={account.balanceCents !== 0 ? "Cannot close account with non-zero balance" : ""}
                              >
                                Close Account
                              </button>
                            )}
                          </div>
                        </div>
                      ))
                    )}
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
            </>
          )}
        </Card>
      </div>
    );
  }