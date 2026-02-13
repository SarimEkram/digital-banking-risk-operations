import { Navigate, Route, Routes } from "react-router-dom";
import RegisterPage from "../features/auth/RegisterPage.jsx";
import LoginPage from "../features/auth/LoginPage.jsx";
import HomePage from "../features/home/HomePage.jsx";
import RequireAuth from "../shared/routing/RequireAuth.jsx";
import { hasAccessToken } from "../shared/auth/token";

import AuthedLayout from "../shared/layout/AuthedLayout.jsx";
import TransferPage from "../features/transfer/TransferPage.jsx";
import PayeesPage from "../features/payees/PayeesPage.jsx";
import ActivityPage from "../features/activity/ActivityPage.jsx";

export default function App() {
  return (
    <Routes>
      <Route
        path="/"
        element={<Navigate to={hasAccessToken() ? "/home" : "/login"} replace />}
      />

      <Route path="/register" element={<RegisterPage />} />
      <Route path="/login" element={<LoginPage />} />

      <Route
        element={
          <RequireAuth>
            <AuthedLayout />
          </RequireAuth>
        }
      >
        <Route path="/home" element={<HomePage />} />
        <Route path="/transfer" element={<TransferPage />} />
        <Route path="/payees" element={<PayeesPage />} />
        <Route path="/activity" element={<ActivityPage />} />
      </Route>

      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
