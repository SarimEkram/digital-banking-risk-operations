import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import Card from "../../shared/ui/Card";
import { clearAccessToken } from "../../shared/auth/token";
import {
  listHeldTransfers,
  approveHeldTransfer,
  rejectHeldTransfer,
} from "./api";
import styles from "../../styles/AdminRiskPage.module.css";

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

export default function AdminRiskPage() {
  const navigate = useNavigate();

  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [busyKey, setBusyKey] = useState("");
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  const [reasons, setReasons] = useState({});

  async function loadQueue({ silent = false } = {}) {
    if (silent) {
      setRefreshing(true);
    } else {
      setLoading(true);
    }

    setError("");

    try {
      const data = await listHeldTransfers();
      setItems(data);
    } catch (e) {
      if (e?.status === 401) {
        clearAccessToken();
        navigate("/login", { replace: true });
        return;
      }

      setError(e?.message || "Failed to load held transfers");
    } finally {
      if (silent) {
        setRefreshing(false);
      } else {
        setLoading(false);
      }
    }
  }

  useEffect(() => {
    loadQueue();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  function onChangeReason(transferId, value) {
    setReasons((prev) => ({
      ...prev,
      [transferId]: value,
    }));
  }

  async function onApprove(transferId) {
    setBusyKey(`approve-${transferId}`);
    setError("");
    setSuccess("");

    try {
      await approveHeldTransfer(transferId);
      setSuccess(`Transfer ${transferId} approved.`);
      await loadQueue({ silent: true });
    } catch (e) {
      if (e?.status === 401) {
        clearAccessToken();
        navigate("/login", { replace: true });
        return;
      }

      setError(e?.message || `Failed to approve transfer ${transferId}`);
    } finally {
      setBusyKey("");
    }
  }

  async function onReject(transferId) {
    setBusyKey(`reject-${transferId}`);
    setError("");
    setSuccess("");

    try {
      await rejectHeldTransfer(transferId, reasons[transferId] || "");
      setReasons((prev) => ({
        ...prev,
        [transferId]: "",
      }));
      setSuccess(`Transfer ${transferId} rejected.`);
      await loadQueue({ silent: true });
    } catch (e) {
      if (e?.status === 401) {
        clearAccessToken();
        navigate("/login", { replace: true });
        return;
      }

      setError(e?.message || `Failed to reject transfer ${transferId}`);
    } finally {
      setBusyKey("");
    }
  }

  return (
    <div className={styles.page}>
      <Card className={styles.card}>
        <div className={styles.header}>
          <div>
            <h1 className={styles.title}>Admin Review</h1>
            <p className={styles.sub}>
              Review held transfers, approve settlement, or reject and refund the sender.
            </p>
          </div>

          <button
            type="button"
            className={styles.refreshButton}
            onClick={() => loadQueue({ silent: true })}
            disabled={loading || refreshing || !!busyKey}
          >
            {refreshing ? "Refreshing..." : "Refresh"}
          </button>
        </div>

        {loading && <p className={styles.sub}>Loading held transfers...</p>}

        {!loading && error && <div className={styles.errorBox}>{error}</div>}

        {!loading && !error && success && <div className={styles.successBox}>{success}</div>}

        {!loading && !error && items.length === 0 && (
          <div className={styles.emptyState}>
            No transfers are waiting for review.
          </div>
        )}

        {!loading && !error && items.length > 0 && (
          <div className={styles.list}>
            {items.map((item) => {
              const approveBusy = busyKey === `approve-${item.id}`;
              const rejectBusy = busyKey === `reject-${item.id}`;

              return (
                <section className={styles.transferCard} key={item.id}>
                  <div className={styles.topRow}>
                    <div>
                      <div className={styles.transferId}>Transfer #{item.id}</div>
                      <div className={styles.transferMeta}>
                        Created {formatDateTime(item.createdAt)}
                      </div>
                    </div>

                    <div className={`${styles.statusPill} ${styles.pendingPill}`}>
                      {item.status}
                    </div>
                  </div>

                  <div className={styles.amountRow}>
                    {formatMoney(item.amountCents, item.currency)}
                  </div>

                  <div className={styles.detailsGrid}>
                    <div className={styles.detailBox}>
                      <div className={styles.detailLabel}>From</div>
                      <div className={styles.detailValue}>{item.fromEmail || "-"}</div>
                    </div>

                    <div className={styles.detailBox}>
                      <div className={styles.detailLabel}>To</div>
                      <div className={styles.detailValue}>{item.toEmail || "-"}</div>
                    </div>

                    <div className={styles.detailBox}>
                      <div className={styles.detailLabel}>Currency</div>
                      <div className={styles.detailValue}>{item.currency || "-"}</div>
                    </div>

                    <div className={styles.detailBox}>
                      <div className={styles.detailLabel}>Amount (cents)</div>
                      <div className={styles.detailValue}>{item.amountCents}</div>
                    </div>
                  </div>

                  <div className={styles.actions}>
                    <label className={styles.reasonWrap}>
                      <span className={styles.reasonLabel}>Reject reason</span>
                      <input
                        className={styles.reasonInput}
                        value={reasons[item.id] || ""}
                        onChange={(e) => onChangeReason(item.id, e.target.value)}
                        placeholder="optional reason for rejection"
                        disabled={approveBusy || rejectBusy}
                      />
                    </label>

                    <div className={styles.buttonRow}>
                      <button
                        type="button"
                        className={styles.approveButton}
                        onClick={() => onApprove(item.id)}
                        disabled={!!busyKey}
                      >
                        {approveBusy ? "Approving..." : "Approve"}
                      </button>

                      <button
                        type="button"
                        className={styles.rejectButton}
                        onClick={() => onReject(item.id)}
                        disabled={!!busyKey}
                      >
                        {rejectBusy ? "Rejecting..." : "Reject"}
                      </button>
                    </div>
                  </div>
                </section>
              );
            })}
          </div>
        )}
      </Card>
    </div>
  );
}