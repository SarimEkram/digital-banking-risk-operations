import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import Card from "../../shared/ui/Card";
import { apiRequest } from "../../shared/api/http";
import { downloadStatement } from "./api";
import styles from "../../styles/StatementsPage.module.css";

export default function StatementsPage() {
  const navigate = useNavigate();

  const [loading, setLoading] = useState(true);
  const [accountCreatedAt, setAccountCreatedAt] = useState(null);
  const [error, setError] = useState("");
  const [downloading, setDownloading] = useState(null); // "YYYY-MM" when downloading

  // Load user's account creation date to determine available months
  useEffect(() => {
    let alive = true;

    async function loadAccountInfo() {
      try {
        const res = await apiRequest("/api/accounts");

        if (!alive) return;

        if (!res.ok) {
          if (res.status === 401) {
            navigate("/login", { replace: true });
            return;
          }
          setError("Failed to load account information");
          setLoading(false);
          return;
        }

        const accounts = Array.isArray(res.body) ? res.body : [];
        if (accounts.length === 0) {
          setError("No accounts found");
          setLoading(false);
          return;
        }

        // Find earliest account creation date
        const earliest = accounts
          .map((a) => new Date(a.createdAt))
          .sort((a, b) => a - b)[0];

        setAccountCreatedAt(earliest);
        setLoading(false);
      } catch (err) {
        if (alive) {
          setError("Failed to load account information");
          setLoading(false);
        }
      }
    }

    loadAccountInfo();

    return () => {
      alive = false;
    };
  }, [navigate]);

  // Generate list of available months (account creation through last complete month)
  const availableMonths = [];
  if (accountCreatedAt) {
    const now = new Date();
    const currentYear = now.getFullYear();
    const currentMonth = now.getMonth() + 1; // 1-based

    const startYear = accountCreatedAt.getFullYear();
    const startMonth = accountCreatedAt.getMonth() + 1; // 1-based

    // Last complete month is the month before current
    let endYear = currentYear;
    let endMonth = currentMonth - 1;
    if (endMonth === 0) {
      endMonth = 12;
      endYear -= 1;
    }

    // Build list from start to end
    let y = startYear;
    let m = startMonth;

    while (y < endYear || (y === endYear && m <= endMonth)) {
      availableMonths.push({ year: y, month: m });

      m++;
      if (m > 12) {
        m = 1;
        y++;
      }
    }

    // Reverse so newest first
    availableMonths.reverse();
  }

  async function handleDownload(year, month) {
    const key = `${year}-${String(month).padStart(2, "0")}`;
    setDownloading(key);
    setError("");

    try {
      await downloadStatement(year, month);
    } catch (err) {
      setError(err?.message || "Failed to download statement");
    } finally {
      setDownloading(null);
    }
  }

  const monthNames = [
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December"
  ];

  return (
    <div className={styles.page}>
      <Card className={styles.card}>
        <div className={styles.header}>
          <div>
            <h1 className={styles.title}>Monthly Statements</h1>
            <p className={styles.sub}>
              Download your monthly account statements as PDF files.
            </p>
          </div>
        </div>

        {loading && <p className={styles.sub}>Loading available statements...</p>}

        {!loading && error && <div className={styles.errorBox}>{error}</div>}

        {!loading && !error && (
          <>
            <div className={styles.notice}>
              Statements available from June 2026 onward. Earlier months cannot be generated accurately.
            </div>

            {availableMonths.length === 0 ? (
              <p className={styles.placeholderText}>
                No statements available yet. Statements are generated after the end of each month.
              </p>
            ) : (
              <div className={styles.statementGrid}>
                {availableMonths.map(({ year, month }) => {
                  const key = `${year}-${String(month).padStart(2, "0")}`;
                  const isDownloading = downloading === key;
                  const monthName = monthNames[month - 1];

                  return (
                    <div key={key} className={styles.statementCard}>
                      <div className={styles.statementMonth}>
                        {monthName} {year}
                      </div>
                      <button
                        type="button"
                        className={styles.downloadButton}
                        onClick={() => handleDownload(year, month)}
                        disabled={isDownloading}
                      >
                        {isDownloading ? "Downloading..." : "Download PDF"}
                      </button>
                    </div>
                  );
                })}
              </div>
            )}
          </>
        )}
      </Card>
    </div>
  );
}