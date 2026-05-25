import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import Card from "../../shared/ui/Card";
import { apiRequest } from "../../shared/api/http";

import {
  lookupUserByEmail,
  getUserAccounts,
  freezeAccount,
  unfreezeAccount,
  closeAccount,
  getUserActivitySummary,
  getUserRiskProfile,
  getUserPayees
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

function formatDateTime(iso) {
  if (!iso) return "-";
  try {
    return new Intl.DateTimeFormat(undefined, {
      month: "short",
      day: "2-digit",
      year: "numeric",
      hour: "numeric",
      minute: "2-digit",
    }).format(new Date(iso));
  } catch {
    return iso;
  }
}

function formatDate(iso) {
  if (!iso) return "-";
  try {
    return new Intl.DateTimeFormat(undefined, {
      month: "short",
      day: "2-digit",
      year: "numeric",
    }).format(new Date(iso));
  } catch {
    return iso;
  }
}

export default function AdminFindUserPage() {
  const navigate = useNavigate();

  const [checkingRole, setCheckingRole] = useState(true);
  const [searchEmail, setSearchEmail] = useState("");
  const [searchLoading, setSearchLoading] = useState(false);

  const [selectedUser, setSelectedUser] = useState(null);
  const [userAccounts, setUserAccounts] = useState([]);

  const [activitySummary, setActivitySummary] = useState([]);
  const [riskProfile, setRiskProfile] = useState(null);
  const [statementYear, setStatementYear] = useState("");
  const [statementMonth, setStatementMonth] = useState("");
  const [downloadingStatement, setDownloadingStatement] = useState(false);
  const [riskLimit, setRiskLimit] = useState(5);
  const [payees, setPayees] = useState([]);

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
    setActivitySummary([]);
    setRiskProfile(null);
    setPayees([]);

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

        // Load additional insights for USER role
        try {
          const [activity, risk, payeesList] = await Promise.all([
            getUserActivitySummary(result.userId),
            getUserRiskProfile(result.userId, riskLimit),
            getUserPayees(result.userId)
          ]);
          setActivitySummary(activity);
          setRiskProfile(risk);
          setPayees(payeesList);
        } catch (insightErr) {
          console.error("Failed to load user insights:", insightErr);
        }
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

  async function handleRiskLimitChange(newLimit) {
    setRiskLimit(newLimit);

    if (!selectedUser?.userId) return;

    try {
      const risk = await getUserRiskProfile(selectedUser.userId, newLimit);
      setRiskProfile(risk);
    } catch (err) {
      console.error("Failed to reload risk profile:", err);
    }
  }

  async function handleDownloadStatement() {
      if (!selectedUser?.userId || !statementYear || !statementMonth) {
        setError("Please select both year and month");
        return;
      }

      setDownloadingStatement(true);
      setError("");
      setSuccess("");

      try {
        const token = localStorage.getItem("dbrisk.accessToken");
        const response = await fetch(
          `/api/admin/users/${selectedUser.userId}/statement?year=${statementYear}&month=${statementMonth}`,
          {
            headers: {
              Authorization: `Bearer ${token}`,
            },
          }
        );

        if (!response.ok) {
          throw new Error("Failed to download statement");
        }

        const blob = await response.blob();
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement("a");
        a.href = url;
        a.download = `statement_user${selectedUser.userId}_${statementYear}_${String(statementMonth).padStart(2, "0")}.pdf`;
        document.body.appendChild(a);
        a.click();
        window.URL.revokeObjectURL(url);
        document.body.removeChild(a);

        setSuccess(`Statement for ${statementMonth}/${statementYear} downloaded successfully`);
      } catch (err) {
        if (err?.status === 401) {
          navigate("/login", { replace: true });
          return;
        }
        setError(err?.message || "Failed to download statement");
      } finally {
        setDownloadingStatement(false);
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
                      {userAccounts.length} account(s)
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

                  {userAccounts.length === 0 ? (
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

                {/* Account Activity Summary */}
                <section className={styles.accountsList}>
                  <div className={styles.sectionHeader}>
                    <h2 className={styles.sectionTitle}>Account Activity Summary</h2>
                  </div>

                  {/* Monthly Statement Download */}
                  <div className={styles.statementDownloadSection}>
                    <h3 style={{ fontSize: "14px", fontWeight: "700", marginBottom: "12px" }}>
                      Download Monthly Statement
                    </h3>
                    <div style={{ display: "flex", gap: "12px", alignItems: "flex-end", flexWrap: "wrap" }}>
                      <label style={{ display: "flex", flexDirection: "column", gap: "6px", minWidth: "120px" }}>
                        <span style={{ fontSize: "12px", color: "var(--text-muted)" }}>Year</span>
                        <input
                          type="number"
                          value={statementYear}
                          onChange={(e) => setStatementYear(e.target.value)}
                          placeholder="2026"
                          min="2020"
                          max="2100"
                          style={{
                            padding: "8px 12px",
                            border: "1px solid var(--border)",
                            borderRadius: "var(--radius-sm)",
                            fontSize: "14px",
                          }}
                        />
                      </label>

                      <label style={{ display: "flex", flexDirection: "column", gap: "6px", minWidth: "120px" }}>
                        <span style={{ fontSize: "12px", color: "var(--text-muted)" }}>Month</span>
                        <select
                          value={statementMonth}
                          onChange={(e) => setStatementMonth(e.target.value)}
                          style={{
                            padding: "8px 12px",
                            border: "1px solid var(--border)",
                            borderRadius: "var(--radius-sm)",
                            fontSize: "14px",
                            background: "var(--surface)",
                          }}
                        >
                          <option value="">Select month</option>
                          <option value="1">January</option>
                          <option value="2">February</option>
                          <option value="3">March</option>
                          <option value="4">April</option>
                          <option value="5">May</option>
                          <option value="6">June</option>
                          <option value="7">July</option>
                          <option value="8">August</option>
                          <option value="9">September</option>
                          <option value="10">October</option>
                          <option value="11">November</option>
                          <option value="12">December</option>
                        </select>
                      </label>

                      <button
                        type="button"
                        className={styles.actionButton}
                        onClick={handleDownloadStatement}
                        disabled={downloadingStatement || !statementYear || !statementMonth}
                        style={{ marginBottom: "0" }}
                      >
                        {downloadingStatement ? "Downloading..." : "Download PDF"}
                      </button>
                    </div>
                  </div>

                  {activitySummary.length === 0 ? (
                    <p className={styles.placeholderText}>No activity data available.</p>
                  ) : (
                    activitySummary.map((summary) => (
                      <div key={summary.accountId} className={styles.accountCard}>
                        <div className={styles.accountHeader}>
                          <div className={styles.accountId}>
                            Account #{summary.accountId} ({summary.accountType})
                          </div>
                        </div>

                        <div className={styles.detailGrid}>
                          <div className={styles.detailBox}>
                            <div className={styles.detailLabel}>Current Balance</div>
                            <div className={styles.detailValue}>
                              {formatMoney(summary.currentBalanceCents, summary.currency)}
                            </div>
                          </div>

                          <div className={styles.detailBox}>
                            <div className={styles.detailLabel}>Transfers Sent</div>
                            <div className={styles.detailValue}>
                              {summary.totalTransfersSent} ({formatMoney(summary.totalAmountSentCents, summary.currency)})
                            </div>
                          </div>

                          <div className={styles.detailBox}>
                            <div className={styles.detailLabel}>Transfers Received</div>
                            <div className={styles.detailValue}>
                              {summary.totalTransfersReceived} ({formatMoney(summary.totalAmountReceivedCents, summary.currency)})
                            </div>
                          </div>

                          <div className={styles.detailBox}>
                            <div className={styles.detailLabel}>Account Created</div>
                            <div className={styles.detailValue}>{formatDate(summary.accountCreatedAt)}</div>
                          </div>

                          <div className={styles.detailBox}>
                            <div className={styles.detailLabel}>Last Activity</div>
                            <div className={styles.detailValue}>
                              {summary.lastActivityAt ? formatDateTime(summary.lastActivityAt) : "No activity"}
                            </div>
                          </div>
                        </div>
                      </div>
                    ))
                  )}
                </section>

                {/* Risk Profile */}
                <section className={styles.accountsList}>
                  <div className={styles.sectionHeader}>
                    <h2 className={styles.sectionTitle}>Risk Profile</h2>
                    <div style={{ display: "flex", gap: "8px", alignItems: "center" }}>
                      <label style={{ fontSize: "12px", color: "var(--text-muted)" }}>
                        Show last:
                      </label>
                      <select
                        value={riskLimit}
                        onChange={(e) => handleRiskLimitChange(Number(e.target.value))}
                        style={{
                          padding: "4px 8px",
                          borderRadius: "var(--radius-sm)",
                          border: "1px solid var(--border)",
                          background: "var(--surface)",
                          color: "var(--text)",
                          fontSize: "12px",
                        }}
                      >
                        <option value={5}>5 transfers</option>
                        <option value={10}>10 transfers</option>
                        <option value={15}>15 transfers</option>
                        <option value={20}>20 transfers</option>
                      </select>
                    </div>
                  </div>

                  {riskProfile ? (
                    <>
                      <div className={styles.detailGrid} style={{ marginBottom: "16px" }}>
                        <div className={styles.detailBox}>
                          <div className={styles.detailLabel}>Held Transfers</div>
                          <div className={styles.detailValue}>{riskProfile.totalHeldTransfers}</div>
                        </div>

                        <div className={styles.detailBox}>
                          <div className={styles.detailLabel}>Blocked Transfers</div>
                          <div className={styles.detailValue}>{riskProfile.totalBlockedTransfers}</div>
                        </div>

                        <div className={styles.detailBox}>
                          <div className={styles.detailLabel}>Rejected Transfers</div>
                          <div className={styles.detailValue}>{riskProfile.totalRejectedTransfers}</div>
                        </div>

                        <div className={styles.detailBox}>
                          <div className={styles.detailLabel}>Average Risk Score</div>
                          <div className={styles.detailValue}>
                            {riskProfile.averageRiskScore != null
                              ? riskProfile.averageRiskScore.toFixed(1)
                              : "N/A"}
                          </div>
                        </div>
                      </div>

                      {riskProfile.recentRiskReasons.length > 0 ? (
                        <>
                          <h3 style={{ margin: "16px 0 12px", fontSize: "14px", fontWeight: "700" }}>
                            Recent Risk Flags (last {riskLimit})
                          </h3>
                          <div className={styles.riskFlagsContainer}>
                            {riskProfile.recentRiskReasons.map((item) => (
                              <div key={item.transferId} className={styles.accountCard}>
                                <div className={styles.detailGrid}>
                                  <div className={styles.detailBox}>
                                    <div className={styles.detailLabel}>Transfer ID</div>
                                    <div className={styles.detailValue}>#{item.transferId}</div>
                                  </div>

                                  <div className={styles.detailBox}>
                                    <div className={styles.detailLabel}>Status</div>
                                    <div className={styles.detailValue}>
                                      <span className={getStatusClass(item.status)}>{item.status}</span>
                                    </div>
                                  </div>

                                  <div className={styles.detailBox}>
                                    <div className={styles.detailLabel}>Amount</div>
                                    <div className={styles.detailValue}>
                                      {formatMoney(item.amountCents, item.currency)}
                                    </div>
                                  </div>

                                  <div className={styles.detailBox}>
                                    <div className={styles.detailLabel}>Risk Score</div>
                                    <div className={styles.detailValue}>{item.riskScore ?? "N/A"}</div>
                                  </div>

                                  <div className={styles.detailBox}>
                                    <div className={styles.detailLabel}>Risk Decision</div>
                                    <div className={styles.detailValue}>{item.riskDecision || "N/A"}</div>
                                  </div>

                                  <div className={styles.detailBox}>
                                    <div className={styles.detailLabel}>Created</div>
                                    <div className={styles.detailValue}>{formatDateTime(item.createdAt)}</div>
                                  </div>
                                </div>

                                {item.riskReasons && (
                                  <div style={{ marginTop: "12px", padding: "8px", background: "var(--surface-2)", borderRadius: "var(--radius-sm)" }}>
                                    <div style={{ fontSize: "12px", color: "var(--text-muted)", marginBottom: "4px" }}>
                                      Risk Reasons:
                                    </div>
                                    <div style={{ fontSize: "13px", color: "var(--text)" }}>
                                      {item.riskReasons}
                                    </div>
                                  </div>
                                )}
                              </div>
                            ))}
                          </div>
                        </>
                      ) : (
                        <p className={styles.placeholderText}>No recent risk flags found.</p>
                      )}
                    </>
                  ) : (
                    <p className={styles.placeholderText}>Loading risk profile...</p>
                  )}
                </section>

                {/* Payees */}
                <section className={styles.accountsList}>
                  <div className={styles.sectionHeader}>
                    <h2 className={styles.sectionTitle}>Payees</h2>
                  </div>

                  {payees.length === 0 ? (
                    <p className={styles.placeholderText}>No payees found for this user.</p>
                  ) : (
                    payees.map((payee) => (
                      <div key={payee.payeeId} className={styles.accountCard}>
                        <div className={styles.accountHeader}>
                          <div className={styles.accountId}>{payee.payeeEmail}</div>
                          <span className={getStatusClass(payee.status)}>{payee.status}</span>
                        </div>

                        <div className={styles.detailGrid}>
                          <div className={styles.detailBox}>
                            <div className={styles.detailLabel}>Payee User ID</div>
                            <div className={styles.detailValue}>#{payee.payeeUserId}</div>
                          </div>

                          <div className={styles.detailBox}>
                            <div className={styles.detailLabel}>Label</div>
                            <div className={styles.detailValue}>{payee.label || "-"}</div>
                          </div>

                          <div className={styles.detailBox}>
                            <div className={styles.detailLabel}>Added On</div>
                            <div className={styles.detailValue}>{formatDate(payee.createdAt)}</div>
                          </div>
                        </div>
                      </div>
                    ))
                  )}
                </section>
              </>
            )}
          </>
        )}
      </Card>
    </div>
  );
}