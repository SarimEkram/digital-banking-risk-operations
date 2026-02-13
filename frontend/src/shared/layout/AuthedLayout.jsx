import { NavLink, Outlet, useNavigate } from "react-router-dom";
import { clearAccessToken } from "../auth/token";
import styles from "../../styles/AuthedLayout.module.css";

function linkClass({ isActive }) {
  return isActive ? `${styles.navLink} ${styles.navLinkActive}` : styles.navLink;
}

export default function AuthedLayout() {
  const navigate = useNavigate();

  function onLogout() {
    clearAccessToken();
    navigate("/login", { replace: true });
  }

  return (
    <div className={styles.shell}>
      <header className={styles.topNav}>
        <div className={styles.topNavInner}>
          <div className={styles.brand}>
            <div className={styles.brandName}>Digital Banking &amp; Risk Ops</div>
            <div className={styles.brandSub}>Prototype</div>
          </div>

          <nav className={styles.navLinks} aria-label="Primary">
            <NavLink to="/home" className={linkClass}>Home</NavLink>
            <NavLink to="/transfer" className={linkClass}>Transfer</NavLink>
            <NavLink to="/payees" className={linkClass}>Payees</NavLink>
            <NavLink to="/activity" className={linkClass}>Activity</NavLink>
          </nav>

          <button type="button" className={styles.logoutBtn} onClick={onLogout}>
            Logout
          </button>
        </div>
      </header>

      <main className={styles.main}>
        <Outlet />
      </main>
    </div>
  );
}
