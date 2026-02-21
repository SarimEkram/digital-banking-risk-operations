import { Fragment, useEffect, useMemo, useState } from "react";
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
// aa
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

function startOfLocalDay(d) {
  const x = new Date(d);
  x.setHours(0, 0, 0, 0);
  return x;
}

export default function ActivityPage() {
  const [items, setItems] = useState([]);
  const [nextCursor, setNextCursor] = useState(null);

  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError] = useState("");

  // --- filters ---
  const [direction, setDirection] = useState("ALL"); // ALL | SENT | RECEIVED
  const [status, setStatus] = useState("ALL"); // ALL | COMPLETED | ...
  const [range, setRange] = useState("ALL"); // ALL | TODAY | 7D | 30D
  const [query, setQuery] = useState("");

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

  function onClearFilters() {
    setDirection("ALL");
    setStatus("ALL");
    setRange("ALL");
    setQuery("");
  }

  const statusOptions = useMemo(() => {
    const set = new Set();
    for (const t of items) {
      if (t?.status) set.add(String(t.status));
    }
    return Array.from(set).sort((a, b) => a.localeCompare(b));
  }, [items]);

  const filteredItems = useMemo(() => {
    const q = query.trim().toLowerCase();

    // date range start (local time)
    let startMs = null;
    const now = new Date();
    if (range === "TODAY") {
      startMs = startOfLocalDay(now).getTime();
    } else if (range === "7D") {
      const d = startOfLocalDay(now);
      d.setDate(d.getDate() - 6); // include today = 7 days total
      startMs = d.getTime();
    } else if (range === "30D") {
      const d = startOfLocalDay(now);
      d.setDate(d.getDate() - 29);
      startMs = d.getTime();
    }

    return items.filter((t) => {
      const dir = String(t?.direction || "").toUpperCase();
      const st = String(t?.status || "").toUpperCase();

      if (direction !== "ALL" && dir !== direction) return false;
      if (status !== "ALL" && st !== String(status).toUpperCase()) return false;

      if (startMs != null) {
        const createdMs = new Date(t.createdAt).getTime();
        if (!Number.isFinite(createdMs) || createdMs < startMs) return false;
      }

      if (q) {
        const counterparty = String(t?.counterpartyEmail || "").toLowerCase();
        const fromEmail = String(t?.fromEmail || "").toLowerCase();
        const toEmail = String(t?.toEmail || "").toLowerCase();
        const idStr = String(t?.id || "");

        const hit =
          counterparty.includes(q) ||
          fromEmail.includes(q) ||
          toEmail.includes(q) ||
          idStr.includes(q);

        if (!hit) return false;
      }

      return true;
    });
  }, [items, direction, status, range, query]);

  // [{ key, label, rows: [...] }, ...] newest -> oldest
  const grouped = useMemo(() => {
    const out = [];
    let current = null;

    for (const t of filteredItems) {
      const key = localDayKey(t.createdAt);
      if (!current || current.key !== key) {
        current = { key, label: formatDayLabel(t.createdAt), rows: [] };
        out.push(current);
      }
      current.rows.push(t);
    }
    return out;
  }, [filteredItems]);

  return (
    <div className={styles.page}>
      <Card className={styles.card}>
        <div className={styles.header}>
          <div>
            <h1 className={styles.title}>Activity</h1>
            {!loading && !error && (
              <p className={styles.sub}>
                Showing <span className={styles.mono}>{filteredItems.length}</span> of{" "}
                <span className={styles.mono}>{items.length}</span> loaded transfers
              </p>
            )}
          </div>
        </div>

        {/* Filters */}
        {!loading && !error && (
          <div className={styles.controls}>
            <label className={styles.control}>
              <span className={styles.controlLabel}>Search</span>
              <input
                className={styles.input}
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                placeholder="email or transfer id"
              />
            </label>

            <label className={styles.control}>
              <span className={styles.controlLabel}>Direction</span>
              <select
                className={styles.select}
                value={direction}
                onChange={(e) => setDirection(e.target.value)}
              >
                <option value="ALL">All</option>
                <option value="SENT">Sent</option>
                <option value="RECEIVED">Received</option>
              </select>
            </label>

            <label className={styles.control}>
              <span className={styles.controlLabel}>Status</span>
              <select
                className={styles.select}
                value={status}
                onChange={(e) => setStatus(e.target.value)}
              >
                <option value="ALL">All</option>
                {statusOptions.map((s) => (
                  <option key={s} value={s}>
                    {s}
                  </option>
                ))}
              </select>
            </label>

            <label className={styles.control}>
              <span className={styles.controlLabel}>Date</span>
              <select
                className={styles.select}
                value={range}
                onChange={(e) => setRange(e.target.value)}
              >
                <option value="ALL">All time (loaded)</option>
                <option value="TODAY">Today</option>
                <option value="7D">Last 7 days</option>
                <option value="30D">Last 30 days</option>
              </select>
            </label>

            <button className={styles.buttonAlt} onClick={onClearFilters}>
              Clear
            </button>
          </div>
        )}

        {loading ? (
          <p className={styles.sub}>Loading transfers...</p>
        ) : error ? (
          <p className={styles.sub}>Error: {error}</p>
        ) : filteredItems.length === 0 ? (
          <p className={styles.sub}>No transfers match your filters.</p>
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
                    <Fragment key={g.key}>
                      <tr className={styles.groupRow}>
                        <td colSpan={6}>{g.label}</td>
                      </tr>

                      {g.rows.map((t) => {
                        const dir = String(t.direction || "").toUpperCase();
                        const isSent = dir === "SENT";
                        const dirClass = isSent ? styles.pillSent : styles.pillReceived;
                        const dirText = isSent ? "Sent" : "Received";

                        return (
                          <tr key={t.id} className={styles.row}>
                            <td>{formatTimeOnly(t.createdAt)}</td>
                            <td>{formatMoney(t.amountCents, t.currency)}</td>
                            <td>{t.status}</td>
                            <td>
                              <span className={`${styles.pill} ${dirClass}`}>{dirText}</span>
                            </td>
                            <td>{t.counterpartyEmail}</td>
                            <td className={styles.mono}>{t.id}</td>
                          </tr>
                        );
                      })}
                    </Fragment>
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

            <p className={styles.hint}>
              Filters apply to transfers you’ve loaded so far. If you want filters to work across
              your entire history without “Load more”, we’ll add backend query params next.
            </p>
          </>
        )}
      </Card>
    </div>
  );
}
