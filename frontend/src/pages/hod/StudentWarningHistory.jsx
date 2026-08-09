import React, { useState, useEffect } from 'react';
import { useParams, Link } from 'react-router-dom';
import { getStudentWarningHistory } from '../../services/api';
import styles from './StudentWarningHistory.module.css';

const StudentWarningHistory = () => {
  const { uid } = useParams();
  const [history, setHistory] = useState([]);
  const [loading, setLoading] = useState(true);
  const [errorMsg, setErrorMsg] = useState(null);

  useEffect(() => {
    const fetchHistory = async () => {
      setLoading(true);
      setErrorMsg(null);
      try {
        const data = await getStudentWarningHistory(uid || 'CS001');
        setHistory(Array.isArray(data) ? data : []);
      } catch (err) {
        setErrorMsg(`Failed to query historical warning evaluations for student candidate [${uid}]. Please verify institutional database connection.`);
        console.error("Error fetching warning history:", err);
      } finally {
        setLoading(false);
      }
    };

    fetchHistory();
  }, [uid]);

  const studentName = history[0]?.studentName || `Student Profile (${uid})`;
  const branch = history[0]?.branch || "Computer Science & Engineering";

  return (
    <div className={styles.container}>
      <header className={styles.header}>
        <div className={styles.headerLeft}>
          <Link to="/hod/highlighted" className={styles.backButton}>
            ← Back to Highlighted Ledger
          </Link>
          <h1 className={styles.title}>Candidate Escalation Audit History (Module 8)</h1>
          <span className={styles.subtitle}>{studentName}</span>
          <span className={styles.profileMeta}>
            Candidate ID: {uid} | Department of {branch} | Historical Ledger View
          </span>
        </div>
      </header>

      {errorMsg && (
        <div className={styles.errorBox}>
          {errorMsg}
        </div>
      )}

      <main className={styles.tableContainer}>
        <div className={styles.tableHeaderBar}>
          <span className={styles.tableHeaderTitle}>Month-by-Month Institutional Compliance Record</span>
        </div>

        {loading ? (
          <div className={styles.loadingText}>
            Retrieving historical monthly evaluations from database...
          </div>
        ) : history.length === 0 ? (
          <div className={styles.emptyState}>
            No historical warning evaluations recorded for candidate ID [{uid}]. The candidate has maintained zero infractions across past audit cycles.
          </div>
        ) : (
          <table className={styles.table}>
            <thead>
              <tr>
                <th>Month</th>
                <th>Diary Rejections</th>
                <th>Absences</th>
                <th>Excuse Usage %</th>
                <th>Highlighted</th>
              </tr>
            </thead>
            <tbody>
              {history.map((record, idx) => {
                const isHighlighted = Boolean(record.highlighted);
                const reasons = Array.isArray(record.reasons) ? record.reasons : [];

                return (
                  <tr key={`${record.month}_${idx}`}>
                    <td className={styles.monthCell}>{record.month || "N/A"}</td>
                    <td className={styles.metricValue}>
                      {record.diaryRejectionCount !== undefined ? record.diaryRejectionCount : 0} 
                      {record.diaryRejectionCount >= 3 && " (Threshold Breached)"}
                    </td>
                    <td className={styles.metricValue}>
                      {record.absenceCount !== undefined ? record.absenceCount : 0} 
                      {record.absenceCount >= 3 && " (Threshold Breached)"}
                    </td>
                    <td className={styles.metricValue}>
                      {record.excuseAbusePercentage !== undefined ? `${Math.round(record.excuseAbusePercentage)}%` : "0%"} 
                      {record.excuseAbusePercentage >= 25 && " (Threshold Breached)"}
                    </td>
                    <td>
                      <div>
                        <span className={isHighlighted ? styles.highlightYes : styles.highlightNo}>
                          {isHighlighted ? "YES (Flagged)" : "NO (Compliant)"}
                        </span>
                      </div>
                      {reasons.length > 0 && (
                        <div className={styles.reasonsList}>
                          {reasons.map((r, i) => (
                            <span key={i} className={styles.reasonTag}>{r}</span>
                          ))}
                        </div>
                      )}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        )}
      </main>
    </div>
  );
};

export default StudentWarningHistory;
