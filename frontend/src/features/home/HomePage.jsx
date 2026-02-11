import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import Card from "../../shared/ui/Card";
import Button from "../../shared/ui/Button";
import { clearAccessToken } from "../../shared/auth/token";
import { getMe, getAccounts } from "./api";

function formatMoney(cents, currency = "CAD") {
  const dollars = (Number(cents) / 100).toFixed(2);
  return `${currency} ${dollars}`;
}

export default function HomePage() {
  const navigate = useNavigate();

  const [loading, setLoading] = useState(true);
  const [me, setMe] = useState(null);
  const [accounts, setAccounts] = useState([]);
  const [error, setError] = useState("");

  async function load() {
    setLoading(true);
    setError("");

    const meRes = await getMe();
    if (!meRes.ok) {
      if (meRes.status === 401) {
        clearAccessToken();
        navigate("/login", { replace: true });
        return;
      }
      const errText = typeof meRes.body === "string" ? meRes.body : JSON.stringify(meRes.body, null, 2);
      setError(`Failed to load /api/me (${meRes.status})\n\n${errText}`);
      setLoading(false);
      return;
    }

    const accRes = await getAccounts();
    if (!accRes.ok) {
      if (accRes.status === 401) {
        clearAccessToken();
        navigate("/login", { replace: true });
        return;
      }
      const errText = typeof accRes.body === "string" ? accRes.body : JSON.stringify(accRes.body, null, 2);
      setError(`Failed to load /api/accounts (${accRes.status})\n\n${errText}`);
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
        <div className="homeHeader">
          <div>
            <h1>Home</h1>
            <p className="sub">Protected dashboard. Loads /api/me + /api/accounts.</p>
          </div>

          <div className="homeActions">
            <Button type="button" onClick={load} disabled={loading}>
              {loading ? "Refreshing..." : "Refresh"}
            </Button>
            <Button type="button" onClick={onLogout} disabled={loading}>
              Logout
            </Button>
          </div>
        </div>

        {loading && <p className="sub">Loading...</p>}

        {!loading && error && <pre className="output output--error">{error}</pre>}

        {!loading && !error && me && (
          <>
            <pre className="output output--success">
{`userId: ${me.userId}
email: ${me.email}
role: ${me.role}`}
            </pre>

            <h2 style={{ marginTop: 18, fontSize: 16 }}>Accounts</h2>

            {accounts.length === 0 ? (
              <p className="sub">No accounts found.</p>
            ) : (
              <div style={{ display: "grid", gap: 10, marginTop: 10 }}>
                {accounts.map((a) => (
                  <div key={a.id} className="output">
                    <div><b>Account ID:</b> {a.id}</div>
                    <div><b>Status:</b> {a.status}</div>
                    <div><b>Balance:</b> {formatMoney(a.balanceCents, a.currency)}</div>
                  </div>
                ))}
              </div>
            )}
          </>
        )}
      </Card>
    </div>
  );
}
