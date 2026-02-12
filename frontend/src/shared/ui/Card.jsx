import styles from "./Card.module.css";

export default function Card({ children, className = "", ...props }) {
  const cls = [styles.card, className].filter(Boolean).join(" ");
  return (
    <div className={cls} {...props}>
      {children}
    </div>
  );
}
