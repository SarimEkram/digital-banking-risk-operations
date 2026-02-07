import Card from "../../shared/ui/Card";
import RegisterForm from "./components/RegisterForm";

export default function RegisterPage() {
  return (
    <div className="page">
      <Card>
        <h1>Digital Banking</h1>
        <p className="sub">Create an account (default CAD account auto-created).</p>
        <RegisterForm />
      </Card>
    </div>
  );
}
