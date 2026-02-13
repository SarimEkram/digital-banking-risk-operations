import Card from "../../shared/ui/Card";
import styles from "../../styles/PlaceholderPage.module.css";

export default function ActivityPage() {
  return (
    <div className={styles.wrap}>
      <Card className={styles.card}>
        <h1 className={styles.title}>Activity</h1>
        <p className={styles.sub}>Coming soon: transfer timeline, statuses, and audit trail.</p>
      </Card>
    </div>
  );
}
