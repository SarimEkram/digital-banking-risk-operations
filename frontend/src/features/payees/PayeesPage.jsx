import { useEffect, useMemo, useState } from "react";
import Card from "../../shared/ui/Card";
import styles from "../../styles/PayeesPage.module.css";
import { addPayee, disablePayee, listPayees } from "./api";

function getErrorMessage(res) {
  if (!res) return "unknown error";
  if (typeof res.body === "string" && res.body.trim().length > 0) return res.body;
  if (res.body && typeof res.body === "object") {
    if (res.body.error) return res.body.error;
    try {
      return JSON.stringify(res.body);
    } catch {
      return "request failed";
    }
  }
  return `request failed (status ${res.status})`;
}

export default function PayeesPage() {
  const [payees, setPayees] = useState([]);
  const [loading, setLoading] = useState(true);

  const [email, setEmail] = useState("");
  const [label, setLabel] = useState("");

  const [submitting, setSubmitting] = useState(false);
  const [disablingId, setDisablingId] = useState(null);

  const [notice, setNotice] = useState(null); // { kind: "ok" | "err", text: string }

  const canSubmit = useMemo(() => {
    return email.trim().length > 0 && !submitting;
  }, [email, submitting]);

  async function refresh() {
    setLoading(true);
    setNotice(null);

    const res = await listPayees();
    if (!res.ok) {
      setPayees([]);
      setNotice({ kind: "err", text: getErrorMessage(res) });
      setLoading(false);
      return;
    }

    setPayees(Array.isArray(res.body) ? res.body : []);
    setLoading(false);
  }

  useEffect(() => {
    refresh();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  async function onAdd(e) {
    e.preventDefault();
    setNotice(null);

    const eNorm = email.trim().toLowerCase();
    if (!eNorm) {
      setNotice({ kind: "err", text: "email is required" });
      return;
    }

    setSubmitting(true);
    const res = await addPayee({
      email: eNorm,
      label: label.trim().length ? label.trim() : null,
    });

    if (!res.ok) {
      setSubmitting(false);
      setNotice({ kind: "err", text: getErrorMessage(res) });
      return;
    }

    setEmail("");
    setLabel("");
    setSubmitting(false);
    setNotice({ kind: "ok", text: `payee active: ${res.body.email}` });
    await refresh();
  }

  async function onDisable(id) {
    setNotice(null);
    setDisablingId(id);

    const res = await disablePayee(id);
    if (!res.ok) {
      setDisablingId(null);
      setNotice({ kind: "err", text: getErrorMessage(res) });
      return;
    }

    setDisablingId(null);
    setNotice({ kind: "ok", text: `payee disabled: ${res.body.email}` });
    await refresh();
  }

  return (
    <div className={styles.page}>
      <Card className={styles.card}>
        <div className={styles.header}>
          <div>
            <h1 className={styles.title}>Payees</h1>
            <p className={styles.sub}>Add payees by email, then send transfers to their chequing account.</p>
          </div>
        </div>

        <div className={styles.grid}>
          <div className={styles.panel}>
            <h3 className={styles.panelTitle}>Add payee</h3>

            <form className={styles.form} onSubmit={onAdd}>
              <label className={styles.label}>
                <span className={styles.labelText}>Payee email</span>
                <input
                  className={styles.input}
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  placeholder="test1@example.com"
                  autoComplete="email"
                />
              </label>

              <label className={styles.label}>
                <span className={styles.labelText}>Label (optional)</span>
                <input
                  className={styles.input}
                  value={label}
                  onChange={(e) => setLabel(e.target.value)}
                  placeholder="e.g., Rent, Friend, Mom"
                />
              </label>

              <div className={styles.actions}>
                <button className={`${styles.btn} ${styles.btnPrimary}`} type="submit" disabled={!canSubmit}>
                  {submitting ? "Adding..." : "Add payee"}
                </button>
              </div>
            </form>

            <div className={styles.hint}>
              If the payee was previously disabled, adding again will re-enable it (same id).
            </div>

            {notice && (
              <div className={`${styles.notice} ${notice.kind === "err" ? styles.noticeError : ""}`}>
                {notice.text}
              </div>
            )}
          </div>

          <div className={styles.panel}>
            <h3 className={styles.panelTitle}>
              Active payees{" "}
              <span className={`${styles.mono}`} style={{ color: "#a8b3cf" }}>
                ({payees.length})
              </span>
            </h3>

            {loading ? (
              <div className={styles.notice}>Loading...</div>
            ) : payees.length === 0 ? (
              <div className={styles.notice}>No active payees yet.</div>
            ) : (
              <div className={styles.list}>
                {payees.map((p) => {
                  const created = p.createdAt ? new Date(p.createdAt).toLocaleString() : "—";
                  return (
                    <div className={styles.row} key={p.id}>
                      <div className={styles.rowLeft}>
                        <div className={styles.email}>{p.email}</div>
                        <div className={styles.meta}>
                          {p.label ? `label: ${p.label}` : "no label"} · created {created}
                        </div>
                      </div>

                      <div>
                        <button
                          className={`${styles.btn} ${styles.btnDanger}`}
                          onClick={() => onDisable(p.id)}
                          disabled={disablingId === p.id}
                          type="button"
                        >
                          {disablingId === p.id ? "Disabling..." : "Disable"}
                        </button>
                      </div>
                    </div>
                  );
                })}
              </div>
            )}
          </div>
        </div>
      </Card>
    </div>
  );
}
