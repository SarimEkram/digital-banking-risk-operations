import "./App.css";
import { Navigate, Route, Routes } from "react-router-dom";
import RegisterPage from "../features/auth/RegisterPage.jsx";

function LoginPlaceholder() {
  return (
    <div className="page">
      <div className="card">
        <h1>Login</h1>
        <p className="sub">Coming next.</p>
      </div>
    </div>
  );
}

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<Navigate to="/register" replace />} />
      <Route path="/register" element={<RegisterPage />} />
      <Route path="/login" element={<LoginPlaceholder />} />
      <Route path="*" element={<Navigate to="/register" replace />} />
    </Routes>
  );
}
