import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import Card from "../../shared/ui/Card";
import Button from "../../shared/ui/Button";
import { clearAccessToken } from "../../shared/auth/token";
import { getMe } from "./api";

export default function HomePage() {
  const navigate = useNavigate();

  const [loading, setLoading] = useState(true);
  const [me, setMe] = useState(null);
  const [error, setError] = useState("");

  async function load() {
    setLoading(true);
    setError("");

    const { ok, status, body } = await getMe();

    if (!ok) {
      if (status === 401) {
        clearAccessToken();
        navigate("/login", { replace: true });
        return;
      }

      const errText = typeof body === "string" ? body : JSON.stringify(body, null, 2);
      setError(`Failed to load /api/me (${status})\n\n${errText}`);
      setLoading(false);
      return;
    }

    setMe(body);
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
            <p className="sub">Protected page. Uses your JWT to call /api/me.</p>
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

        {!loading && error && (
          <pre className="output output--error">{error}</pre>
        )}

        {!loading && !error && me && (
          <pre className="output output--success">
{`userId: ${me.userId}
email: ${me.email}
role: ${me.role}`}
          </pre>
        )}
      </Card>
    </div>
  );
}
