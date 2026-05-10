import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import Card from "../../shared/ui/Card";

import {
  listAudit,
  AUDIT_ACTIONS,
  AUDIT_SCOPES,
  formatAuditDetails,
} from "./api";
import styles from "../../styles/AdminAuditPage.module.css";

const PAGE_SIZE = 50;

const SCOPE_TABS = [
  { value: AUDIT_SCOPES.SELF, label: "My audit" },
  { value: AUDIT_SCOPES.USER, label: "User" },
  { value: AUDIT_SCOPES.ADMIN, label: "Admin" },
];

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

function emailRequiredFor(scope) {
  return scope === AUDIT_SCOPES.USER || scope === AUDIT_SCOPES.ADMIN;
}

export default function AdminAuditPage() {
  const navigate = useNavigate();

  // "Applied" filters drive the query; "draft" filters are what the inputs hold
  // until Apply is clicked. This avoids firing a request on every keystroke.
  const [appliedScope, setAppliedScope] = useState(AUDIT_SCOPES.SELF);
  const [appliedAction, setAppliedAction] = useState("");
  const [appliedEmail, setAppliedEmail] = useState("");

  const [draftScope, setDraftScope] = useState(AUDIT_SCOPES.SELF);
  const [draftAction, setDraftAction] = useState("");
  const [draftEmail, setDraftEmail] = useState("");

  const [page, setPage] = useState(0);

  const [items, setItems] = useState([]);
  const [totalElements, setTotalElements] = useState(0);
  const [totalPages, setTotalPages] = useState(0);

  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState("");

  async function load({
    silent = false,
    pageOverride,
    scopeOverride,
    actionOverride,
    emailOverride,
  } = {}) {
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
        scope: scopeOverride ?? appliedScope,
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

  function onSelectScope(nextScope) {
    setDraftScope(nextScope);

    // Switching back to "My audit" doesn't need an email; apply immediately so the
    // admin doesn't have to also click Apply. For User/Admin, the admin must enter
    // an email and click Apply, which matches the validation contract.
    if (nextScope === AUDIT_SCOPES.SELF) {
      setAppliedScope(nextScope);
      setAppliedEmail("");
      setDraftEmail("");
      setPage(0);
      load({
        pageOverride: 0,
        scopeOverride: nextScope,
        emailOverride: "",
      });
    }
  }

  const draftEmailTrimmed = draftEmail.trim();
  const applyDisabled =
    loading ||
    refreshing ||
    (emailRequiredFor(draftScope) && draftEmailTrimmed.length === 0);

  function onApplyFilters() {
    if (applyDisabled) return;

    setAppliedScope(draftScope);
    setAppliedAction(draftAction);
    setAppliedEmail(draftEmail);
    setPage(0);

    load({
      pageOverride: 0,
      scopeOverride: draftScope,
      actionOverride: draftAction,
      emailOverride: draftEmail,
    });
  }

  function onClearFilters() {
    setDraftScope(AUDIT_SCOPES.SELF);
    setDraftAction("");
    setDraftEmail("");
    setAppliedScope(AUDIT_SCOPES.SELF);
    setAppliedAction("");
    setAppliedEmail("");
    setPage(0);

    load({
      pageOverride: 0,
      scopeOverride: AUDIT_SCOPES.SELF,
      actionOverride: "",
      emailOverride: "",
    });
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
  const showEmailField = emailRequiredFor(draftScope);

  return (
    <div className={styles.page}>
      <Card className={styles.card}>
        <div className={styles.header}>
          <div>
            <h1 className={styles.title}>Audit Log</h1>
            <p className={styles.sub}>
              Default view shows your own audit entries. Switch to User or Admin to look up another actor by email.
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
          <div className={styles.scopeTabs} role="tablist" aria-label="Audit scope">
            {SCOPE_TABS.map((tab) => {
              const isActive = draftScope === tab.value;
              return (
                <button
                  type="button"
                  role="tab"
                  aria-selected={isActive}
                  key={tab.value}
                  className={
                    isActive
                      ? `${styles.scopeTab} ${styles.scopeTabActive}`
                      : styles.scopeTab
                  }
                  onClick={() => onSelectScope(tab.value)}
                  disabled={loading || refreshing}
                >
                  {tab.label}
                </button>
              );
            })}
          </div>

          <label className={styles.field}>
            <span className={styles.fieldLabel}>Action (optional)</span>
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

          {showEmailField && (
            <label className={styles.field}>
              <span className={styles.fieldLabel}>
                Actor email contains <span className={styles.requiredMark}>*</span>
              </span>
              <input
                className={styles.input}
                type="text"
                value={draftEmail}
                onChange={(e) => setDraftEmail(e.target.value)}
                placeholder="required for this scope"
                disabled={loading || refreshing}
              />
            </label>
          )}

          <div className={styles.filterButtons}>
            <button
              type="button"
              className={styles.applyButton}
              onClick={onApplyFilters}
              disabled={applyDisabled}
              title={
                emailRequiredFor(draftScope) && draftEmailTrimmed.length === 0
                  ? "Enter an email to search this scope"
                  : ""
              }
            >
              Apply
            </button>

            <button
              type="button"
              className={styles.secondaryButton}
              onClick={onClearFilters}
              disabled={loading || refreshing}
            >
              Reset
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
                  {items.map((row) => (
                    <tr key={row.id} className={styles.row}>
                      <td className={styles.mono}>{formatDateTime(row.createdAt)}</td>

                      <td>
                        <span className={styles.actionPill}>{row.action}</span>
                      </td>

                      <td>
                        <span className={styles.actorEmail}>
                          {row.actorEmail || `user #${row.actorUserId ?? "—"}`}
                        </span>
                      </td>

                      <td>
                        <div className={styles.entityCell}>
                          <span className={styles.entityType}>{row.entityType}</span>
                          <span className={styles.entityId}>#{row.entityId}</span>
                        </div>
                      </td>

                      <td className={styles.details}>
                        <div className={styles.detailsText}>
                          {formatAuditDetails(row.action, row.details)}
                        </div>
                        {row.correlationId && (
                          <div
                            className={styles.correlationId}
                            title={row.correlationId}
                          >
                            cid: {row.correlationId}
                          </div>
                        )}
                      </td>
                    </tr>
                  ))}
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