import { useEffect, useState } from "react";
import { Navigate, useLocation } from "react-router-dom";
import { apiRequest } from "../api/http";
import { hasAccessToken } from "../auth/token";

export default function RequireAdmin({ children }) {
  const location = useLocation();
  const [status, setStatus] = useState("checking");

  useEffect(() => {
    let alive = true;

    async function checkAdmin() {
      if (!hasAccessToken()) {
        if (alive) setStatus("unauthenticated");
        return;
      }

      try {
        const res = await apiRequest("/api/me");

        if (!alive) return;

        if (!res.ok) {
          setStatus("unauthenticated");
          return;
        }

        if (res.body?.role === "ADMIN") {
          setStatus("allowed");
          return;
        }

        setStatus("forbidden");
      } catch {
        if (alive) {
          setStatus("unauthenticated");
        }
      }
    }

    setStatus("checking");
    checkAdmin();

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