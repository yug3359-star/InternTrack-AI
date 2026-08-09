import React, { useState } from 'react';
import { Link } from 'react-router-dom';
import { useNotification } from '../../hooks/useNotification';
import styles from './MentorDashboard.module.css';

const initialPendingLogs = [
  { id: 201, studentRoll: '2023CSB104', studentName: 'Ananya Sharma', company: 'Infosys Innovation Lab', week: '2026-07-20', hours: 36, tasks: 'Executed unit test coverage enhancements and resolved authorization edge bugs in security gateway.' },
  { id: 202, studentRoll: '2023CSB112', studentName: 'Rohan Mehta', company: 'Microsoft India R&D', week: '2026-07-20', hours: 40, tasks: 'Developed automated Terraform provisioning scripts for enterprise AI backend cluster architecture.' },
  { id: 203, studentRoll: '2023CSB145', studentName: 'Priya Patel', company: 'Wipro Technologies', week: '2026-07-20', hours: 38, tasks: 'Implemented predictive anomaly detection pipelines using Python Scikit-learn and AWS Lambda triggers.' },
  { id: 204, studentRoll: '2023CSB168', studentName: 'Vikrant Verma', company: 'TCS Digital', week: '2026-07-13', hours: 15, tasks: 'Attended corporate onboarding sessions and reviewed proprietary API specifications.' } // Low hours for flag evaluation
];

const MentorDashboard = () => {
  const { notify } = useNotification();
  const [queue, setQueue] = useState(initialPendingLogs);
  const [processedCount, setProcessedCount] = useState(14);
  const [filterText, setFilterText] = useState('');

  const handleApprove = (id, studentRoll) => {
    setQueue(queue.filter(item => item.id !== id));
    setProcessedCount(prev => prev + 1);
    notify(`Log verified: academic credit recorded for student [${studentRoll}].`, "success");
  };

  const handleReject = (id, studentRoll) => {
    const reason = prompt("Enter institutional rejection remarks for student rework:", "Insufficient engineering hour volume or imprecise technical documentation.");
    if (reason !== null) {
      setQueue(queue.filter(item => item.id !== id));
      notify(`Submission rejected: discrepancy notification dispatched to student [${studentRoll}].`, "error");
    }
  };

  const filteredQueue = queue.filter(item =>
    item.studentName.toLowerCase().includes(filterText.toLowerCase()) ||
    item.studentRoll.toLowerCase().includes(filterText.toLowerCase()) ||
    item.company.toLowerCase().includes(filterText.toLowerCase())
  );

  return (
    <div className={styles.wrapper}>
      <header className={styles.topSection}>
        <div>
          <h1 className={styles.pageTitle}>Faculty Verification Portal</h1>
          <p className={styles.metaSub}>Faculty Advisor: Dr. Rajesh K.</p>
        </div>
        <div style={{ display: 'flex', gap: '12px', alignItems: 'center', flexWrap: 'wrap' }}>
          <Link to="/mentor/borderline-reviews" style={{ backgroundColor: '#D97706', color: '#FFF', padding: '8px 16px', borderRadius: '4px', textDecoration: 'none', fontWeight: 700, fontSize: '14px', border: '1px solid #B45309' }}>
            Borderline Reviews (2 Pending)
          </Link>
          <Link to="/mentor/student-detail/dev-stud-107" style={{ backgroundColor: '#16A34A', color: '#FFF', padding: '8px 16px', borderRadius: '4px', textDecoration: 'none', fontWeight: 700, fontSize: '14px', border: '1px solid #15803D' }}>
            Student Attendance Ledger
          </Link>
          <div className={styles.headerBadge}>
            <span>Pending Validation Queue: <strong>{queue.length}</strong></span>
          </div>
        </div>
      </header>

      {/* Cohort Oversight Overview */}
      <section className={styles.summaryGrid}>
        <div className={styles.statusBox}>
          <span className={styles.boxLabel}>Verified & Approved Entries</span>
          <span className={styles.boxNum}>{processedCount}</span>
          <span className={styles.boxNote}>Semester verification cycle 2026</span>
        </div>
        <div className={styles.statusBox}>
          <span className={styles.boxLabel}>Awaiting Mentor Validation</span>
          <span className={styles.boxNumAlert}>{queue.length}</span>
          <span className={styles.boxNote}>Immediate audit action recommended</span>
        </div>
        <div className={styles.statusBox}>
          <span className={styles.boxLabel}>Cohort Compliance Rate</span>
          <span className={styles.boxNum}>91.6%</span>
          <span className={styles.boxNote}>22 of 24 students actively maintaining minimum hours</span>
        </div>
      </section>

      {/* Verification Queue Section */}
      <section className={styles.queueCard}>
        <div className={styles.cardHeaderRow}>
          <h2 className={styles.sectionTitle}>Weekly Activity Log Verification Queue</h2>
          <div className={styles.searchWrap}>
            <input
              type="text"
              placeholder="Filter by name, roll, or host..."
              value={filterText}
              onChange={(e) => setFilterText(e.target.value)}
              className={styles.filterInput}
            />
          </div>
        </div>

        <div className={styles.tableContainer}>
          <table className={styles.queueTable}>
            <thead>
              <tr>
                <th>Student Practitioner</th>
                <th>Corporate Host</th>
                <th>Week Commencing</th>
                <th>Hrs</th>
                <th>Technical Task Description</th>
                <th className={styles.actionsHeader}>Audit Controls</th>
              </tr>
            </thead>
            <tbody>
              {filteredQueue.length === 0 ? (
                <tr>
                  <td colSpan="6" className={styles.emptyNotice}>
                    No activity logs currently awaiting verification in the assigned department cohort.
                  </td>
                </tr>
              ) : (
                filteredQueue.map(item => (
                  <tr key={item.id} className={item.hours < 20 ? styles.rowWarning : null}>
                    <td>
                      <div className={styles.studentCell}>
                        <strong className={styles.studentName}>{item.studentName}</strong>
                        <span className={styles.rollCode}>{item.studentRoll}</span>
                        <Link to={`/mentor/student-detail/${item.studentRoll}`} style={{ fontSize: '12px', color: '#16A34A', fontWeight: 700, textDecoration: 'underline', marginTop: '2px' }}>
                          View Attendance Rate
                        </Link>
                      </div>
                    </td>
                    <td>{item.company}</td>
                    <td className={styles.dateText}>{item.week}</td>
                    <td className={styles.hoursText}>
                      <span className={item.hours < 20 ? styles.lowHoursBadge : null}>
                        {item.hours}
                      </span>
                    </td>
                    <td className={styles.taskText}>{item.tasks}</td>
                    <td className={styles.actionsCell}>
                      <button
                        type="button"
                        onClick={() => handleApprove(item.id, item.studentRoll)}
                        className={styles.approveBtn}
                        title="Authorize academic compliance log"
                      >
                        Approve
                      </button>
                      <button
                        type="button"
                        onClick={() => handleReject(item.id, item.studentRoll)}
                        className={styles.rejectBtn}
                        title="Return submission for correction"
                      >
                        Reject & Flag
                      </button>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </section>
    </div>
  );
};

export default MentorDashboard;
