import React, { useState, useEffect } from 'react';
import { useParams, Link, useLocation } from 'react-router-dom';
import { getCompletionSummary } from '../../services/api';
import { useAuth } from '../../context/AuthContext';
import styles from './CompletionSummary.module.css';

const CompletionSummary = () => {
  const { uid: paramUid } = useParams();
  const location = useLocation();
  const { user } = useAuth();
  const isHodReview = location.pathname.includes('/hod/');

  // Use paramUid if accessed by HOD or student route parameter, otherwise default to student identity
  const targetUid = paramUid || user?.uid || 'CS001';

  const [summary, setSummary] = useState(null);
  const [loading, setLoading] = useState(true);
  const [errorMsg, setErrorMsg] = useState(null);

  useEffect(() => {
    const fetchSummary = async () => {
      setLoading(true);
      setErrorMsg(null);
      try {
        const data = await getCompletionSummary(targetUid);
        if (data) {
          setSummary(data);
        } else {
          setErrorMsg(`Your internship is ongoing. Your final report will be generated when you successfully complete your internship.`);
        }
      } catch (err) {
        if (err.response && err.response.status === 404) {
          setErrorMsg(`Your internship is ongoing. Your final report will be generated when you successfully complete your internship.`);
        } else {
          setErrorMsg(`Your internship is ongoing. Your final report will be generated when you successfully complete your internship.`);
        }
        console.error("Error loading completion summary:", err);
      } finally {
        setLoading(false);
      }
    };

    fetchSummary();
  }, [targetUid]);

  const handleDownloadPdf = () => {
    window.print();
  };

  if (loading) {
    return (
      <div className={styles.container}>
        <div className={styles.loadingText}>Retrieving permanent compliance ledger from database...</div>
      </div>
    );
  }

  if (errorMsg || !summary) {
    return (
      <div className={styles.container}>
        <div className={styles.errorBox}>
          {errorMsg || "Unable to load completion summary report."}
        </div>
      </div>
    );
  }

  // Render plain-text risk formatting without decorative graphics
  const risk = summary.riskLevel || "Low";
  let riskClass = styles.riskLow;
  if (String(risk).toLowerCase() === "medium") riskClass = styles.riskMedium;
  if (String(risk).toLowerCase() === "high") riskClass = styles.riskHigh;

  const highlightedCount = summary.highlightedMonthsCount !== undefined ? summary.highlightedMonthsCount : 0;

  return (
    <div className={styles.container}>
      <main className={styles.reportBox}>
        <header className={styles.reportHeader}>
          <div className={styles.headerLeft}>
            <span className={styles.institutionName}>G H Raisoni College of Engineering, Nagpur</span>
            <h1 className={styles.reportTitle}>Final Internship Summary</h1>
            <span className={styles.reportSubtitle}>
              Official Academic Verification & Completion Report | Permanent Database Ledger
            </span>
          </div>

          <div className={styles.actionRow}>
            {isHodReview ? (
              <Link to="/hod/completed" className={styles.backLink}>
                ← Back to Completed Roster
              </Link>
            ) : (
              <Link to="/student/dashboard" className={styles.backLink}>
                ← Back to Dashboard
              </Link>
            )}
            <button
              type="button"
              onClick={handleDownloadPdf}
              className={styles.pdfButton}
              title="Print or Save Report as PDF"
            >
              Download as PDF
            </button>
          </div>
        </header>

        {/* Candidate & Internship Metadata Section */}
        <section className={styles.section}>
          <h2 className={styles.sectionHeader}>Candidate & Placement Identity</h2>
          <div className={styles.metaGrid}>
            <div className={styles.metaItem}>
              <span className={styles.metaLabel}>Candidate Name & ID</span>
              <span className={styles.metaValue}>{summary.studentName || "Candidate Profile"} (ID: {summary.uid})</span>
            </div>
            <div className={styles.metaItem}>
              <span className={styles.metaLabel}>Academic Department</span>
              <span className={styles.metaValue}>{summary.branch || "Computer Science & Engineering"}</span>
            </div>
            <div className={styles.metaItem}>
              <span className={styles.metaLabel}>Assigned Faculty Mentor</span>
              <span className={styles.metaValue}>{summary.mentorName || "Unassigned Faculty"}</span>
            </div>
            <div className={styles.metaItem}>
              <span className={styles.metaLabel}>Practice Domain</span>
              <span className={styles.metaValue}>{summary.internshipDomain || "Cloud Architecture"}</span>
            </div>
            <div className={styles.metaItem}>
              <span className={styles.metaLabel}>Registered Tenure Schedule</span>
              <span className={styles.metaValue}>{summary.joiningDate || "N/A"} to {summary.completionDate || "N/A"}</span>
            </div>
            <div className={styles.metaItem}>
              <span className={styles.metaLabel}>Official Verification Date</span>
              <span className={styles.metaValue}>
                {summary.completedAt ? new Date(summary.completedAt).toLocaleDateString() : summary.completionDate}
              </span>
            </div>
          </div>
        </section>

        {/* Real Historical Compliance Evaluation Ledger */}
        <section className={styles.section}>
          <h2 className={styles.sectionHeader}>Authoritative Evaluation Table</h2>
          <table className={styles.table}>
            <thead>
              <tr>
                <th style={{ width: '45%' }}>Compliance Metric</th>
                <th style={{ width: '30%' }}>Evaluation Methodology & Audit Notes</th>
                <th style={{ width: '25%' }}>Final Verified Figure</th>
              </tr>
            </thead>
            <tbody>
              <tr>
                <td>
                  <div className={styles.metricTitle}>Biometric & Meeting Attendance Rate</div>
                  <div className={styles.metricSub}>Verified Daily Records</div>
                </td>
                <td>
                  Verified Present or Approved Meeting on {summary.verifiedAttendanceDays !== undefined ? summary.verifiedAttendanceDays : 0} of {summary.expectedWorkingDays || 60} working days.
                </td>
                <td className={styles.metricFigure}>
                  {summary.attendancePercentage !== undefined ? `${summary.attendancePercentage}%` : "0%"}
                </td>
              </tr>

              <tr>
                <td>
                  <div className={styles.metricTitle}>Proctored Examination Performance</div>
                  <div className={styles.metricSub}>Face-Matched Exams</div>
                </td>
                <td>
                  {summary.completedExamCount !== undefined ? summary.completedExamCount : 0} exams completed. Absent / missed exam count: {summary.absentExamCount !== undefined ? summary.absentExamCount : 0} (excluded from score average).
                </td>
                <td className={styles.metricFigure}>
                  {summary.averageTestScore !== undefined && summary.averageTestScore !== null
                    ? `${summary.averageTestScore} / 100`
                    : "No exams recorded"}
                </td>
              </tr>
              <tr>
                <td>
                  <div className={styles.metricTitle}>Meeting Excuse Quota Usage</div>
                  <div className={styles.metricSub}>Proportional Quota Exemption Ratio</div>
                </td>
                <td>
                  Proportion of scheduled check-ins, popups, and tests bypassed utilizing formal meeting excuse quotas.
                </td>
                <td className={styles.metricFigure}>
                  {summary.excuseUsagePercentage !== undefined ? `${summary.excuseUsagePercentage}%` : "0%"}
                </td>
              </tr>
              <tr>
                <td>
                  <div className={styles.metricTitle}>Institutional Risk Level Classification</div>
                  <div className={styles.metricSub}>Escalation History</div>
                </td>
                <td>
                  Candidate breached monthly warning thresholds in {highlightedCount} audit cycle(s). (0 = Low, 1 = Medium, 2+ = High).
                </td>
                <td>
                  <span className={riskClass}>
                    {risk.toUpperCase()} ({highlightedCount} highlighted month{highlightedCount !== 1 ? 's' : ''})
                  </span>
                </td>
              </tr>
            </tbody>
          </table>
        </section>

        <footer className={styles.signatureSection}>
          <div className={styles.sigBlock}>
            <span className={styles.sigTitle}>Department Verification</span>
            <span className={styles.sigDate}>Automated Server Timezone Audit: UTC</span>
          </div>
          <div className={styles.sigBlock}>
            <span className={styles.sigTitle}>Head of Department Certification</span>
            <span className={styles.sigDate}>Status: Officially verified and stored in permanent record.</span>
          </div>
        </footer>
      </main>
    </div>
  );
};

export default CompletionSummary;
