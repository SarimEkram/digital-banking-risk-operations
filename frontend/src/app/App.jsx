import { Navigate, Route, Routes } from "react-router-dom";
import RegisterPage from "../features/auth/RegisterPage.jsx";
import LoginPage from "../features/auth/LoginPage.jsx";
import HomePage from "../features/home/HomePage.jsx";
import RequireAuth from "../shared/routing/RequireAuth.jsx";
import RequireAdmin from "../shared/routing/RequireAdmin.jsx";
import RequireUser from "../shared/routing/RequireUser.jsx";
import { hasAccessToken } from "../shared/auth/token";

import AuthedLayout from "../shared/layout/AuthedLayout.jsx";
import TransferPage from "../features/transfer/TransferPage.jsx";
import PayeesPage from "../features/payees/PayeesPage.jsx";
import ActivityPage from "../features/activity/ActivityPage.jsx";
import AdminRiskPage from "../features/admin-risk/AdminRiskPage.jsx";
import AdminDepositPage from "../features/admin-deposit/AdminDepositPage.jsx";

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
        <Route path="home" element={<HomePage />} />

        <Route
          path="transfer"
          element={
            <RequireUser>
              <TransferPage />
            </RequireUser>
          }
        />

        <Route
          path="payees"
          element={
            <RequireUser>
              <PayeesPage />
            </RequireUser>
          }
        />

        <Route
          path="activity"
          element={
            <RequireUser>
              <ActivityPage />
            </RequireUser>
          }
        />

        <Route
          path="admin/deposit"
          element={
            <RequireAdmin>
              <AdminDepositPage />
            </RequireAdmin>
          }
        />

        <Route
          path="admin/risk"
          element={
            <RequireAdmin>
              <AdminRiskPage />
            </RequireAdmin>
          }
        />
      </Route>

      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}