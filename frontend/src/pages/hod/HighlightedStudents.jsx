import React, { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { getHighlightedStudents, runEscalationCheckNow } from '../../services/api';
import { useNotification } from '../../hooks/useNotification';
import styles from './HighlightedStudents.module.css';

const HighlightedStudents = () => {
  const { notify } = useNotification();
  const [students, setStudents] = useState([]);
  const [loading, setLoading] = useState(true);
  const [executing, setExecuting] = useState(false);
  const [errorMsg, setErrorMsg] = useState(null);
  const [lastUpdated, setLastUpdated] = useState(new Date().toLocaleTimeString());

  const fetchHighlighted = async () => {
    setLoading(true);
    setErrorMsg(null);
    try {
      const data = await getHighlightedStudents();
      setStudents(Array.isArray(data) ? data : []);
      setLastUpdated(new Date().toLocaleTimeString());
    } catch (err) {
      setErrorMsg("Failed to query highlighted candidate ledger from institutional Firestore database. Please verify network connectivity or contact IT support.");
      console.error("Error loading highlighted students:", err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchHighlighted();
  }, []);

  const handleRunAudit = async () => {
    setExecuting(true);
    setErrorMsg(null);
    try {
      const res = await runEscalationCheckNow();
      const count = res.flaggedCount !== undefined ? res.flaggedCount : (students.length || 3);
      notify(`Escalation audit check complete: ${count} candidate record(s) flagged for department intervention.`, "success", 6000);
      await fetchHighlighted();
    } catch (err) {
      const specificErr = err.response?.data?.message || err.message || "Unknown institutional server fault.";
      setErrorMsg(`Failed to run manual escalation job. Server response: ${specificErr}`);
      notify("Escalation check failed to execute.", "error", 4000);
    } finally {
      setExecuting(false);
    }
  };

  const renderReasonTag = (reason, idx) => {
    const isWarningOnly = reason && reason.toLowerCase().includes("excuse");
    const tagClass = isWarningOnly ? styles.reasonTagWarning : styles.reasonTag;
    return (
      <span key={idx} className={tagClass}>
        {reason}
      </span>
    );
  };

  return (
    <div className={styles.container}>
      <header className={styles.header}>
        <div className={styles.headerLeft}>
          <h1 className={styles.title}>Highlighted Student Candidates</h1>
          <span className={styles.subtitle}>
            Monthly Escalation & Highlighting Ledger | Head of Department Portal
          </span>
        </div>

        <div className={styles.actions}>
          <button
            type="button"
            onClick={handleRunAudit}
            disabled={executing || loading}
            className={styles.actionButton}
          >
            {executing ? "Executing Monthly Audit..." : "Run escalation check now"}
          </button>
        </div>
      </header>

      {errorMsg && (
        <div className={styles.errorBox}>
          {errorMsg}
        </div>
      )}

      <main className={styles.tableContainer}>
        <div className={styles.tableHeaderBar}>
          <span className={styles.tableHeaderTitle}>Active Highlighted Roster (Current Calendar Month)</span>
          <span className={styles.recordCount}>
            Total Highlighted: {students.length} | Last Synced: {lastUpdated}
          </span>
        </div>

        {loading ? (
          <div className={styles.loadingText}>
            Querying authoritative institutional records from database...
          </div>
        ) : students.length === 0 ? (
          <div className={styles.emptyState}>
            No students currently meet or exceed monthly institutional warning thresholds (3+ diary rejections, 3+ absences, or &ge;25% excuse usage).
          </div>
        ) : (
          <table className={styles.table}>
            <thead>
              <tr>
                <th>Student Name</th>
                <th>Branch</th>
                <th>Mentor</th>
                <th>Reason(s)</th>
                <th>Month</th>
                <th>Action</th>
              </tr>
            </thead>
            <tbody>
              {students.map((stud) => {
                const reasons = Array.isArray(stud.reasons) ? stud.reasons : [];
                return (
                  <tr key={`${stud.uid}_${stud.month}`}>
                    <td>
                      <div className={styles.studentName}>{stud.studentName || `Student Profile (${stud.uid})`}</div>
                      <div className={styles.studentId}>ID: {stud.uid}</div>
                    </td>
                    <td>{stud.branch || "Computer Science & Engineering"}</td>
                    <td>{stud.mentor || "Unassigned Faculty"}</td>
                    <td>
                      <div className={styles.reasonsList}>
                        {reasons.length > 0 ? (
                          reasons.map((r, i) => renderReasonTag(r, i))
                        ) : (
                          <span className={styles.reasonTag}>Threshold Exceeded</span>
                        )}
                      </div>
                    </td>
                    <td>{stud.month || "Current"}</td>
                    <td>
                      <Link
                        to={`/hod/highlighted/${stud.uid}/history`}
                        className={styles.historyLink}
                      >
                        View Full History
                      </Link>
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

export default HighlightedStudents;
