import Card from "../../shared/ui/Card";
import LoginForm from "./components/LoginForm";
import { Link } from "react-router-dom";

export default function LoginPage() {
  return (
    <div className="page">
      <Card>
        <h1>Login</h1>
        <p className="sub">Sign in to get an access token.</p>

        <LoginForm />

        <p className="helper">
          Need an account? <Link className="link" to="/register">Create one</Link>
        </p>
      </Card>
    </div>
  );
}
