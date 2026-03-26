import { useEffect, useState } from "react";
import { Navigate, useLocation } from "react-router-dom";
import { apiRequest } from "../api/http";
import { hasAccessToken } from "../auth/token";

export default function RequireAuth({ children }) {
  const location = useLocation();
  const [status, setStatus] = useState("checking");

  useEffect(() => {
    let alive = true;

    async function validateSession() {
      if (!hasAccessToken()) {
        if (alive) setStatus("unauthenticated");
        return;
      }

      try {
        const res = await apiRequest("/api/me");

        if (!alive) return;

        if (res.ok) {
          setStatus("authenticated");
          return;
        }

        setStatus("unauthenticated");
      } catch {
        if (alive) {
          setStatus("unauthenticated");
        }
      }
    }

    setStatus("checking");
    validateSession();

    return () => {
      alive = false;
    };
  }, [location.pathname]);

  if (status === "checking") {
    return null;
  }

  if (status === "unauthenticated") {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  }

  return children;
}