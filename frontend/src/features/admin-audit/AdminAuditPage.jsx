import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import Card from "../../shared/ui/Card";

import { listAudit, AUDIT_ACTIONS, ADMIN_ACTION_CODES } from "./api";
import styles from "../../styles/AdminAuditPage.module.css";

const PAGE_SIZE = 50;

function formatDateTime(iso) {
  try {
    return new Intl.DateTimeFormat(undefined, {
      month: "short",
      day: "2-digit",
      year: "numeric",
      hour: "numeric",
      minute: "2-digit",
      second: "2-digit",
    }).format(new Date(iso));
  } catch {
    return iso || "-";
  }
}

function isAdminAction(action) {
  return ADMIN_ACTION_CODES.has(String(action || "").toUpperCase());
}

export default function AdminAuditPage() {
  const navigate = useNavigate();

  // Applied filters drive the actual query. The "draft" filters are what the inputs hold
  // until the user clicks Apply; this avoids firing a request on every keystroke.
  const [appliedAction, setAppliedAction] = useState("");
  const [appliedEmail, setAppliedEmail] = useState("");
  const [draftAction, setDraftAction] = useState("");
  const [draftEmail, setDraftEmail] = useState("");

  const [page, setPage] = useState(0);

  const [items, setItems] = useState([]);
  const [totalElements, setTotalElements] = useState(0);
  const [totalPages, setTotalPages] = useState(0);

  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState("");

  async function load({ silent = false, pageOverride, actionOverride, emailOverride } = {}) {
    if (silent) {
      setRefreshing(true);
    } else {
      setLoading(true);
    }

    setError("");

    try {
      const data = await listAudit({
        page: pageOverride ?? page,
        size: PAGE_SIZE,
        action: actionOverride ?? appliedAction,
        email: emailOverride ?? appliedEmail,
      });

      setItems(data.items);
      setTotalElements(data.totalElements);
      setTotalPages(data.totalPages);
    } catch (e) {
      if (e?.status === 401) {
        navigate("/login", { replace: true });
        return;
      }

      setError(e?.message || "Failed to load audit log");
    } finally {
      if (silent) {
        setRefreshing(false);
      } else {
        setLoading(false);
      }
    }
  }

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  function onApplyFilters() {
    // Reset to page 0 when filters change so we don't end up on a non-existent page.
    setAppliedAction(draftAction);
    setAppliedEmail(draftEmail);
    setPage(0);
    load({ pageOverride: 0, actionOverride: draftAction, emailOverride: draftEmail });
  }

  function onClearFilters() {
    setDraftAction("");
    setDraftEmail("");
    setAppliedAction("");
    setAppliedEmail("");
    setPage(0);
    load({ pageOverride: 0, actionOverride: "", emailOverride: "" });
  }

  function onPrev() {
    if (page <= 0) return;
    const next = page - 1;
    setPage(next);
    load({ pageOverride: next });
  }

  function onNext() {
    if (page >= totalPages - 1) return;
    const next = page + 1;
    setPage(next);
    load({ pageOverride: next });
  }

  const showingFrom = totalElements === 0 ? 0 : page * PAGE_SIZE + 1;
  const showingTo = Math.min((page + 1) * PAGE_SIZE, totalElements);

  return (
    <div className={styles.page}>
      <Card className={styles.card}>
        <div className={styles.header}>
          <div>
            <h1 className={styles.title}>Audit Log</h1>
            <p className={styles.sub}>
              Every action across the platform, newest first. Filter by action type or actor email.
            </p>
          </div>

          <button
            type="button"
            className={styles.refreshButton}
            onClick={() => load({ silent: true })}
            disabled={loading || refreshing}
          >
            {refreshing ? "Refreshing..." : "Refresh"}
          </button>
        </div>

        <div className={styles.filterBar}>
          <label className={styles.field}>
            <span className={styles.fieldLabel}>Action</span>
            <input
              className={styles.input}
              list="audit-actions-list"
              value={draftAction}
              onChange={(e) => setDraftAction(e.target.value)}
              placeholder="Any action"
              disabled={loading || refreshing}
            />
            <datalist id="audit-actions-list">
              {AUDIT_ACTIONS.map((a) => (
                <option key={a} value={a} />
              ))}
            </datalist>
          </label>

          <label className={styles.field}>
            <span className={styles.fieldLabel}>Actor email contains</span>
            <input
              className={styles.input}
              type="text"
              value={draftEmail}
              onChange={(e) => setDraftEmail(e.target.value)}
              placeholder="e.g. admin@bank.local"
              disabled={loading || refreshing}
            />
          </label>

          <div className={styles.filterButtons}>
            <button
              type="button"
              className={styles.applyButton}
              onClick={onApplyFilters}
              disabled={loading || refreshing}
            >
              Apply
            </button>

            <button
              type="button"
              className={styles.secondaryButton}
              onClick={onClearFilters}
              disabled={loading || refreshing}
            >
              Clear
            </button>
          </div>
        </div>

        {loading && <p className={styles.sub}>Loading audit log...</p>}

        {!loading && error && <div className={styles.errorBox}>{error}</div>}

        {!loading && !error && items.length === 0 && (
          <div className={styles.emptyState}>
            No audit entries match the current filters.
          </div>
        )}

        {!loading && !error && items.length > 0 && (
          <>
            <div className={styles.tableWrap}>
              <table className={styles.table}>
                <thead className={styles.thead}>
                  <tr>
                    <th>When</th>
                    <th>Action</th>
                    <th>Actor</th>
                    <th>Entity</th>
                    <th>Details</th>
                  </tr>
                </thead>
                <tbody className={styles.tbody}>
                  {items.map((row) => {
                    const isSystem = row.actorUserId == null;
                    const isAdminTyped = isAdminAction(row.action);

                    return (
                      <tr key={row.id} className={styles.row}>
                        <td className={styles.mono}>{formatDateTime(row.createdAt)}</td>

                        <td>
                          <span className={styles.actionPill}>{row.action}</span>
                        </td>

                        <td>
                          {isSystem ? (
                            <span className={`${styles.tag} ${styles.tagSystem}`}>system</span>
                          ) : (
                            <div className={styles.actorCell}>
                              <span className={styles.actorEmail}>
                                {row.actorEmail || `user #${row.actorUserId}`}
                              </span>
                              {isAdminTyped && (
                                <span className={`${styles.tag} ${styles.tagAdmin}`}>admin</span>
                              )}
                            </div>
                          )}
                        </td>

                        <td>
                          <div className={styles.entityCell}>
                            <span className={styles.entityType}>{row.entityType}</span>
                            <span className={styles.entityId}>#{row.entityId}</span>
                          </div>
                        </td>

                        <td className={styles.details}>
                          <div className={styles.detailsText} title={row.details || ""}>
                            {row.details || "-"}
                          </div>
                          {row.correlationId && (
                            <div className={styles.correlationId} title={row.correlationId}>
                              cid: {row.correlationId}
                            </div>
                          )}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>

            <div className={styles.footer}>
              <div className={styles.footerInfo}>
                Showing {showingFrom}&ndash;{showingTo} of {totalElements}
              </div>

              <div className={styles.footerButtons}>
                <button
                  type="button"
                  className={styles.secondaryButton}
                  onClick={onPrev}
                  disabled={loading || refreshing || page <= 0}
                >
                  Previous
                </button>

                <span className={styles.pageIndicator}>
                  Page {page + 1} of {Math.max(1, totalPages)}
                </span>

                <button
                  type="button"
                  className={styles.secondaryButton}
                  onClick={onNext}
                  disabled={loading || refreshing || page >= totalPages - 1}
                >
                  Next
                </button>
              </div>
            </div>
          </>
        )}
      </Card>
    </div>
  );
}