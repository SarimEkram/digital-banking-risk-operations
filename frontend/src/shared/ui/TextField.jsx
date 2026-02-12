import styles from "./TextField.module.css";

export default function TextField({
  label,
  className = "",
  inputClassName = "",
  ...inputProps
}) {
  const labelCls = [styles.label, className].filter(Boolean).join(" ");
  const inputCls = [styles.input, inputClassName].filter(Boolean).join(" ");

  return (
    <label className={labelCls}>
      <span className={styles.labelText}>{label}</span>
      <input className={inputCls} {...inputProps} />
    </label>
  );
}
