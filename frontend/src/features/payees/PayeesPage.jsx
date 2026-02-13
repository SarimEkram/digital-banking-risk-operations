import Card from "../../shared/ui/Card";
import styles from "../../styles/PlaceholderPage.module.css";

export default function PayeesPage() {
  return (
    <div className={styles.wrap}>
      <Card className={styles.card}>
        <h1 className={styles.title}>Payees</h1>
        <p className={styles.sub}>Coming soon: add, manage, and verify payees.</p>
      </Card>
    </div>
  );
}
