import Card from "../../shared/ui/Card";
import RegisterForm from "./components/RegisterForm";
import { Link } from "react-router-dom";
import styles from "../../styles/AuthPage.module.css";

export default function RegisterPage() {
  return (
    <div className={styles.page}>
      <Card className={styles.card}>
        <h1 className={styles.title}>Digital Banking</h1>
        <p className={styles.sub}>
          Create an account (default CAD account auto-created).
        </p>

        <RegisterForm />

        <p className={styles.helper}>
          Already have an account?{" "}
          <Link className={styles.link} to="/login">
            Login
          </Link>
        </p>
      </Card>
    </div>
  );
}
