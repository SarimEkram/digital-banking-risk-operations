import "./App.css";
import { Navigate, Route, Routes } from "react-router-dom";
import RegisterPage from "../features/auth/RegisterPage.jsx";
import LoginPage from "../features/auth/LoginPage.jsx";

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<Navigate to="/login" replace />} />
      <Route path="/register" element={<RegisterPage />} />
      <Route path="/login" element={<LoginPage />} />
      <Route path="*" element={<Navigate to="/login" replace />} />
    </Routes>
  );
}
