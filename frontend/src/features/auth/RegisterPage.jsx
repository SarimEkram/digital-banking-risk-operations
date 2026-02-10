import Card from "../../shared/ui/Card";
import RegisterForm from "./components/RegisterForm";
import { Link } from "react-router-dom";

export default function RegisterPage() {
  return (
    <div className="page">
      <Card>
        <h1>Digital Banking</h1>
        <p className="sub">Create an account (default CAD account auto-created).</p>

        <RegisterForm />

        <p className="helper">
          Already have an account? <Link className="link" to="/login">Login</Link>
        </p>
      </Card>
    </div>
  );
}
