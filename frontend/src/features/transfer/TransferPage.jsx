import Card from "../../shared/ui/Card";
import styles from "../../styles/PlaceholderPage.module.css";

export default function TransferPage() {
  return (
    <div className={styles.wrap}>
      <Card className={styles.card}>
        <h1 className={styles.title}>Transfer</h1>
        <p className={styles.sub}>Coming soon: idempotent transfers, risk decisions, and ledger entries.</p>
      </Card>
    </div>
  );
}
