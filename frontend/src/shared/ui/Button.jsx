export default function Button({ children, disabled, ...props }) {
  return (
    <button disabled={disabled} {...props}>
      {children}
    </button>
  );
}
