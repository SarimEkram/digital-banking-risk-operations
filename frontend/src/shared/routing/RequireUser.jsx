import { useEffect, useState } from "react";
import { Navigate, useLocation } from "react-router-dom";
import { apiRequest } from "../api/http";
import { clearAccessToken, hasAccessToken } from "../auth/token";

export default function RequireUser({ children }) {
  const location = useLocation();
  const [status, setStatus] = useState("checking");

  useEffect(() => {
    let alive = true;

    async function checkUserAccess() {
      if (!hasAccessToken()) {
        if (alive) setStatus("unauthenticated");
        return;
      }

      try {
        const res = await apiRequest("/api/me");

        if (!alive) return;

        if (!res.ok) {
          if (res.status === 401 || res.status === 403) {
            clearAccessToken();
            setStatus("unauthenticated");
            return;
          }

          setStatus("unauthenticated");
          return;
        }

        if (res.body?.role === "ADMIN") {
          setStatus("forbidden");
          return;
        }

        setStatus("allowed");
      } catch {
        if (alive) {
          clearAccessToken();
          setStatus("unauthenticated");
        }
      }
    }

    checkUserAccess();

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

  if (status === "forbidden") {
    return <Navigate to="/home" replace />;
  }

  return children;
}