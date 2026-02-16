import { useEffect, useMemo, useState } from "react";
import Card from "../../shared/ui/Card";
import styles from "../../styles/ActivityPage.module.css";
import { listTransfers } from "./api";

function formatMoney(amountCents, currency) {
  const amount = Number(amountCents) / 100;

  try {
    return new Intl.NumberFormat(undefined, {
      style: "currency",
      currency: currency || "CAD",
    }).format(amount);
  } catch {
    return `${amount.toFixed(2)} ${currency || ""}`.trim();
  }
}

function formatDayLabel(iso) {
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

function formatTimeOnly(iso) {
  try {
    return new Intl.DateTimeFormat(undefined, {
      hour: "numeric",
      minute: "2-digit",
      second: "2-digit",
    }).format(new Date(iso));
  } catch {
    return iso;
  }
}

function localDayKey(iso) {
  const d = new Date(iso);
  const yyyy = d.getFullYear();
  const mm = String(d.getMonth() + 1).padStart(2, "0");
  const dd = String(d.getDate()).padStart(2, "0");
  return `${yyyy}-${mm}-${dd}`;
}

export default function ActivityPage() {
  const [items, setItems] = useState([]);
  const [nextCursor, setNextCursor] = useState(null);

  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    let alive = true;

    async function run() {
      setError("");
      setLoading(true);
      try {
        const data = await listTransfers({ limit: 20 });
        if (!alive) return;

        setItems(Array.isArray(data?.items) ? data.items : []);
        setNextCursor(data?.nextCursor ?? null);
      } catch (e) {
        if (!alive) return;
        setError(e?.message || "Failed to load transfers");
      } finally {
        if (alive) setLoading(false);
      }
    }

    run();
    return () => {
      alive = false;
    };
  }, []);

  async function onLoadMore() {
    if (!nextCursor || loadingMore) return;

    setError("");
    setLoadingMore(true);
    try {
      const data = await listTransfers({ limit: 20, cursor: nextCursor });
      const newItems = Array.isArray(data?.items) ? data.items : [];

      setItems((prev) => [...prev, ...newItems]);
      setNextCursor(data?.nextCursor ?? null);
    } catch (e) {
      setError(e?.message || "Failed to load more transfers");
    } finally {
      setLoadingMore(false);
    }
  }

  // [{ key, label, rows: [...] }, ...] in the same order as items (newest -> oldest)
  const grouped = useMemo(() => {
    const out = [];
    let current = null;

    for (const t of items) {
      const key = localDayKey(t.createdAt);
      if (!current || current.key !== key) {
        current = { key, label: formatDayLabel(t.createdAt), rows: [] };
        out.push(current);
      }
      current.rows.push(t);
    }
    return out;
  }, [items]);

  return (
    <div className={styles.page}>
      <Card className={styles.card}>
        <h1 className={styles.title}>Activity</h1>

        {loading ? (
          <p className={styles.sub}>Loading transfers...</p>
        ) : error ? (
          <p className={styles.sub}>Error: {error}</p>
        ) : items.length === 0 ? (
          <p className={styles.sub}>No transfers yet.</p>
        ) : (
          <>
            <div className={styles.tableWrap}>
              <table className={styles.table}>
                <thead className={styles.thead}>
                  <tr>
                    <th>Time</th>
                    <th>Amount</th>
                    <th>Status</th>
                    <th>Direction</th>
                    <th>Counterparty</th>
                    <th>Transfer ID</th>
                  </tr>
                </thead>

                <tbody className={styles.tbody}>
                  {grouped.map((g) => (
                    <>
                      <tr key={g.key} className={styles.groupRow}>
                        <td colSpan={6}>{g.label}</td>
                      </tr>

                      {g.rows.map((t) => {
                        const isSent = String(t.direction || "").toUpperCase() === "SENT";
                        const dirClass = isSent ? styles.pillSent : styles.pillReceived;

                        return (
                          <tr key={t.id} className={styles.row}>
                            <td>{formatTimeOnly(t.createdAt)}</td>
                            <td>{formatMoney(t.amountCents, t.currency)}</td>
                            <td>{t.status}</td>
                            <td>
                              <span className={`${styles.pill} ${dirClass}`}>
                                {t.direction}
                              </span>
                            </td>
                            <td>{t.counterpartyEmail}</td>
                            <td>{t.id}</td>
                          </tr>
                        );
                      })}
                    </>
                  ))}
                </tbody>
              </table>
            </div>

            <div className={styles.footer}>
              {nextCursor ? (
                <button className={styles.button} onClick={onLoadMore} disabled={loadingMore}>
                  {loadingMore ? "Loading..." : "Load more"}
                </button>
              ) : (
                <p className={styles.sub}>You’re all caught up.</p>
              )}
            </div>
          </>
        )}
      </Card>
    </div>
  );
}
