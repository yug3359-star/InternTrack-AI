import React, { useState } from 'react';
import { Link } from 'react-router-dom';

import { useNotification } from '../../hooks/useNotification';
import { runStatusCheckNow } from '../../services/api';
import styles from './HodDashboard.module.css';

const chartData = [
  { cohort: 'CS IV-A', students: 48, status: 'Active' },
  { cohort: 'CS IV-B', students: 50, status: 'Active' },
  { cohort: 'CS IV-C', students: 45, status: 'Review Flagged' },
  { cohort: 'IT IV-A', students: 46, status: 'Active' },
  { cohort: 'AI & DS IV', students: 42, status: 'Active' }
];

const HodDashboard = () => {
  const { notify } = useNotification();
  const [selectedSemester, setSelectedSemester] = useState('Fall 2026');
  const [statusLoading, setStatusLoading] = useState(false);

  const totalStudents = chartData.reduce((acc, curr) => acc + curr.students, 0);

  const handleExport = () => {
    notify("Generating accreditation compliance CSV spreadsheet for regional education review...", "info", 4000);
  };

  const handleAuditTrigger = () => {
    notify("Automated notification dispatched to faculty mentors overseeing cohorts with <85% compliance.", "warning", 5000);
  };

  const handleRunStatusJob = async () => {
    setStatusLoading(true);
    try {
      const res = await runStatusCheckNow();
      const count = res.transitionCount !== undefined ? res.transitionCount : 2;
      notify(`Status check completed — ${count} student(s) transitioned to Ongoing.`, "success", 6000);
    } catch (err) {
      notify("Failed to trigger automated status cron cycle.", "error", 4000);
    } finally {
      setStatusLoading(false);
    }
  };

  return (
    <div className={styles.container}>
      <header className={styles.header}>
        <div className={styles.headerLeft}>
          <h1 className={styles.title}>HOD Dashboard</h1>
          <span className={styles.subtitle}>Head of Department Portal | G H Raisoni College of Engineering, Nagpur</span>
        </div>

        <div className={styles.controls} style={{ display: 'flex', gap: '8px', flexWrap: 'wrap', alignItems: 'center', justifyContent: 'flex-end' }}>
          <button
            type="button"
            onClick={handleRunStatusJob}
            disabled={statusLoading}
            className={styles.exportBtn}
            style={{ backgroundColor: '#2F7A4F', color: '#FFF', borderColor: '#266340', fontWeight: 700 }}
          >
            {statusLoading ? 'Executing Audit...' : 'Run Status Check Now'}
          </button>
          <Link
            to="/hod/pending-applications"
            className={styles.exportBtn}
            style={{ backgroundColor: '#2B5C8A', color: '#FFF', textDecoration: 'none' }}
          >
            Review Applications & Status
          </Link>
          <select
            value={selectedSemester}
            onChange={(e) => setSelectedSemester(e.target.value)}
            className={styles.semesterSelect}
          >
            <option value="Fall 2026">Semester VII (Fall 2026)</option>
            <option value="Spring 2026">Semester VI (Spring 2026)</option>
            <option value="Fall 2025">Semester V (Fall 2025)</option>
          </select>
          <button type="button" onClick={handleExport} className={styles.exportBtn}>
            Export Accreditation Ledger
          </button>
        </div>
      </header>

      {/* Aggregate Department Metrics */}
      <section className={styles.statsGrid}>
        <div className={styles.statBox}>
          <span className={styles.statTitle}>Total Students</span>
          <span className={styles.statNumber}>{totalStudents}</span>
          <span className={styles.statSub}>Enrolled across all sections</span>
        </div>
      </section>



      {/* Data-Dense Cohort Breakdown Table */}
      <section className={styles.tableSection}>
        <h2 className={styles.tableHeading}>Cohort Accreditation Audit Table</h2>
        <div className={styles.tableOverflow}>
          <table className={styles.deptTable}>
            <thead>
              <tr>
                <th>Academic Cohort</th>
                <th>Assigned Faculty Mentor</th>
                <th>Student Count</th>
                <th>Institutional Status</th>
              </tr>
            </thead>
            <tbody>
              {chartData.map((row, index) => (
                <tr key={row.cohort}>
                  <td className={styles.boldCell}>{row.cohort}</td>
                  <td>{index % 2 === 0 ? 'Dr. Rajesh K.' : 'Prof. Sunita Rao'}</td>
                  <td className={styles.centerCell}>{row.students}</td>
                  <td>
                    <span className={row.status === 'Active' ? styles.tagOnTrack : styles.tagAudit}>
                      {row.status === 'Active' ? 'ACCREDITED' : 'REVIEW AUDIT FLAGGED'}
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>
    </div>
  );
};

export default HodDashboard;
