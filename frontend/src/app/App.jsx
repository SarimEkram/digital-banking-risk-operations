import "./App.css";
import { Navigate, Route, Routes } from "react-router-dom";
import RegisterPage from "../features/auth/RegisterPage.jsx";
import LoginPage from "../features/auth/LoginPage.jsx";
import HomePage from "../features/home/HomePage.jsx";
import RequireAuth from "../shared/routing/RequireAuth.jsx";
import { hasAccessToken } from "../shared/auth/token";

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
        path="/home"
        element={
          <RequireAuth>
            <HomePage />
          </RequireAuth>
        }
      />

      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
