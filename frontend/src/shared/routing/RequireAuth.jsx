import { Navigate, useLocation } from "react-router-dom";
import { hasAccessToken } from "../auth/token";

export default function RequireAuth({ children }) {
  const location = useLocation();

  if (!hasAccessToken()) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  }

  return children;
}
