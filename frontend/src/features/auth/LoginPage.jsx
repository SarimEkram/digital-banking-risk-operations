import Card from "../../shared/ui/Card";
import LoginForm from "./components/LoginForm";
import { Link } from "react-router-dom";
import styles from "../../styles/AuthPage.module.css";

export default function LoginPage() {
  return (
    <div className={styles.page}>
      <Card className={styles.card}>
        <h1 className={styles.title}>Login</h1>
        <p className={styles.sub}>Sign in to get an access token.</p>

        <LoginForm />

        <p className={styles.helper}>
          Need an account?{" "}
          <Link className={styles.link} to="/register">
            Create one
          </Link>
        </p>
      </Card>
    </div>
  );
}
