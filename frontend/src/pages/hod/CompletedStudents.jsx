import React, { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { getCompletedStudents } from '../../services/api';
import styles from './CompletedStudents.module.css';

const CompletedStudents = () => {
  const [students, setStudents] = useState([]);
  const [loading, setLoading] = useState(true);
  const [errorMsg, setErrorMsg] = useState(null);
  const [lastUpdated, setLastUpdated] = useState(new Date().toLocaleTimeString());

  useEffect(() => {
    const fetchCompleted = async () => {
      setLoading(true);
      setErrorMsg(null);
      try {
        const data = await getCompletedStudents();
        setStudents(Array.isArray(data) ? data : []);
        setLastUpdated(new Date().toLocaleTimeString());
      } catch (err) {
        setErrorMsg("Failed to query completed student ledger from institutional database. Please check connectivity.");
        console.error("Error loading completed students:", err);
      } finally {
        setLoading(false);
      }
    };

    fetchCompleted();
  }, []);

  const renderRiskLabel = (risk) => {
    const r = String(risk || 'Low');
    let rClass = styles.riskLow;
    if (r.toLowerCase() === "medium") rClass = styles.riskMedium;
    if (r.toLowerCase() === "high") rClass = styles.riskHigh;
    return <span className={rClass}>{r.toUpperCase()}</span>;
  };

  return (
    <div className={styles.container}>
      <header className={styles.header}>
        <div className={styles.headerLeft}>
          <h1 className={styles.title}>Completed Student </h1>
          <span className={styles.subtitle}>
            Authoritative Internship Completion Records | Head of Department Evaluation Portal
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
          <span className={styles.tableHeaderTitle}>Officially Completed & Verified Candidate Roster</span>
          <span className={styles.recordCount}>
            Total Completed: {students.length} | Last Synced: {lastUpdated}
          </span>
        </div>

        {loading ? (
          <div className={styles.loadingText}>
            Querying permanent completion ledgers from Firestore...
          </div>
        ) : students.length === 0 ? (
          <div className={styles.emptyState}>
            No student candidates have currently reached or passed their registered completion date.
          </div>
        ) : (
          <table className={styles.table}>
            <thead>
              <tr>
                <th>Student Name</th>
                <th>Branch</th>
                <th>Completion Date</th>
                <th>Attendance %</th>
                <th>Avg Exam Score</th>
                <th>Risk Level</th>
                <th>Action</th>
              </tr>
            </thead>
            <tbody>
              {students.map((stud) => {
                return (
                  <tr key={stud.uid || Math.random()}>
                    <td>
                      <div className={styles.studentName}>{stud.studentName || `Candidate Profile (${stud.uid})`}</div>
                      <div className={styles.studentId}>ID: {stud.uid}</div>
                    </td>
                    <td>{stud.branch || "Computer Science & Engineering"}</td>
                    <td>{stud.completionDate || "N/A"}</td>
                    <td className={styles.metricValue}>
                      {stud.attendancePercentage !== undefined ? `${stud.attendancePercentage}%` : "0%"}
                    </td>
                    <td className={styles.metricValue}>
                      {stud.averageTestScore !== undefined && stud.averageTestScore !== null
                        ? `${stud.averageTestScore} / 100`
                        : "No exams recorded"}
                    </td>
                    <td>{renderRiskLabel(stud.riskLevel)}</td>
                    <td>
                      <Link
                        to={`/hod/completed/${stud.uid}/summary`}
                        className={styles.summaryLink}
                      >
                        View Final Report
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

export default CompletedStudents;
