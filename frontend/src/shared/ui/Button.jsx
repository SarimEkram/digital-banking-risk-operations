import styles from "./Button.module.css";

export default function Button({ children, className = "", ...props }) {
  const cls = [styles.button, className].filter(Boolean).join(" ");
  return (
    <button className={cls} {...props}>
      {children}
    </button>
  );
}
