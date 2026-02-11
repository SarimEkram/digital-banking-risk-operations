import { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import Card from "../../shared/ui/Card";
import Button from "../../shared/ui/Button";
import { clearAccessToken } from "../../shared/auth/token";
import { getMe, getAccounts } from "./api";

function formatMoney(cents, currency = "CAD") {
  const amount = (Number(cents || 0) / 100);
  try {
    return new Intl.NumberFormat(undefined, {
      style: "currency",
      currency,
    }).format(amount);
  } catch {
    // fallback if currency code is weird
    return `${currency} ${amount.toFixed(2)}`;
  }
}

function statusTone(status) {
  const s = String(status || "").toUpperCase();
  if (s === "ACTIVE") return "pill pill--good";
  if (s === "FROZEN") return "pill pill--warn";
  if (s === "CLOSED") return "pill pill--bad";
  return "pill";
}

export default function HomePage() {
  const navigate = useNavigate();

  const [loading, setLoading] = useState(true);
  const [me, setMe] = useState(null);
  const [accounts, setAccounts] = useState([]);
  const [error, setError] = useState("");

  const title = useMemo(() => {
    const email = me?.email || "";
    return email ? `Welcome, ${email}` : "Welcome";
  }, [me]);

  async function load() {
    setLoading(true);
    setError("");

    const [meRes, accRes] = await Promise.all([getMe(), getAccounts()]);

    // auth fail -> kick to login
    if (!meRes.ok && meRes.status === 401) {
      clearAccessToken();
      navigate("/login", { replace: true });
      return;
    }
    if (!accRes.ok && accRes.status === 401) {
      clearAccessToken();
      navigate("/login", { replace: true });
      return;
    }

    if (!meRes.ok || !accRes.ok) {
      const which = !meRes.ok ? "/api/me" : "/api/accounts";
      const bad = !meRes.ok ? meRes : accRes;
      const errText =
        typeof bad.body === "string" ? bad.body : JSON.stringify(bad.body, null, 2);
      setError(`Failed to load ${which} (${bad.status})\n\n${errText}`);
      setLoading(false);
      return;
    }

    setMe(meRes.body);
    setAccounts(Array.isArray(accRes.body) ? accRes.body : []);
    setLoading(false);
  }

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  function onLogout() {
    clearAccessToken();
    navigate("/login", { replace: true });
  }

  return (
    <div className="page">
      <Card>
        <div className="dashTop">
          <div>
            <h1 className="dashTitle">{title}</h1>
            <p className="sub">Your dashboard: profile and accounts.</p>
          </div>

          <div className="dashActions">
            <Button type="button" onClick={load} disabled={loading}>
              {loading ? "Refreshing..." : "Refresh"}
            </Button>
            <Button type="button" onClick={onLogout} disabled={loading}>
              Logout
            </Button>
          </div>
        </div>

        {loading && <p className="sub">Loading your dashboard...</p>}

        {!loading && error && <pre className="output output--error">{error}</pre>}

        {!loading && !error && (
          <div className="dashGrid">
            {/* Profile */}
            <div className="panel">
              <div className="panelHeader">
                <h2 className="panelTitle">Profile</h2>
              </div>

              <div className="kv">
                <div className="kvRow">
                  <span className="kvKey">User ID</span>
                  <span className="kvVal">{me?.userId ?? "-"}</span>
                </div>
                <div className="kvRow">
                  <span className="kvKey">Email</span>
                  <span className="kvVal">{me?.email ?? "-"}</span>
                </div>
                <div className="kvRow">
                  <span className="kvKey">Role</span>
                  <span className="kvVal">
                    <span className={`pill ${me?.role === "ADMIN" ? "pill--admin" : "pill--user"}`}>
                      {me?.role ?? "UNKNOWN"}
                    </span>
                  </span>
                </div>
              </div>

              {me?.role === "ADMIN" && (
                <p className="hint">
                  Admin tools will appear here soon (deposit, risk review, freeze/unfreeze).
                </p>
              )}
            </div>

            {/* Accounts */}
            <div className="panel panel--wide">
              <div className="panelHeader">
                <h2 className="panelTitle">Accounts</h2>
                <span className="panelSub">
                  {accounts.length} {accounts.length === 1 ? "account" : "accounts"}
                </span>
              </div>

              {accounts.length === 0 ? (
                <p className="sub">No accounts found.</p>
              ) : (
                <div className="acctList">
                  {accounts.map((a) => (
                    <div className="acctRow" key={a.id}>
                      <div className="acctMain">
                        <div className="acctName">
                          <span className="mono">Account • {a.id}</span>
                        </div>
                        <div className="acctMeta">
                          <span className={statusTone(a.status)}>{a.status}</span>
                          <span className="dot" />
                          <span className="mono">{a.currency}</span>
                        </div>
                      </div>

                      <div className="acctBal">
                        <div className="acctBalLabel">Available</div>
                        <div className="acctBalValue">
                          {formatMoney(a.balanceCents, a.currency)}
                        </div>
                      </div>
                    </div>
                  ))}
                </div>
              )}

              <div className="divider" />

              {/* Placeholder for next features */}
              <div className="ctaRow">
                <div>
                  <h3 className="ctaTitle">Next</h3>
                  <p className="sub">
                    Transfers will live here next (with required Idempotency-Key).
                  </p>
                </div>
                <Button type="button" disabled>
                  Transfer money (coming soon)
                </Button>
              </div>
            </div>
          </div>
        )}
      </Card>
    </div>
  );
}
