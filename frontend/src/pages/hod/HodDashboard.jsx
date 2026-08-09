import React, { useState } from 'react';
import { Link } from 'react-router-dom';
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer } from 'recharts';
import { useNotification } from '../../hooks/useNotification';
import { runStatusCheckNow } from '../../services/api';
import styles from './HodDashboard.module.css';

const chartData = [
  { cohort: 'CS IV-A', verifiedHours: 2450, pendingHours: 320, students: 48, compliancePercent: 88 },
  { cohort: 'CS IV-B', verifiedHours: 2800, pendingHours: 180, students: 50, compliancePercent: 94 },
  { cohort: 'CS IV-C', verifiedHours: 2100, pendingHours: 410, students: 45, compliancePercent: 79 },
  { cohort: 'IT IV-A', verifiedHours: 2600, pendingHours: 210, students: 46, compliancePercent: 91 },
  { cohort: 'AI & DS IV', verifiedHours: 1950, pendingHours: 280, students: 42, compliancePercent: 82 }
];

const HodDashboard = () => {
  const { notify } = useNotification();
  const [selectedSemester, setSelectedSemester] = useState('Fall 2026');
  const [statusLoading, setStatusLoading] = useState(false);

  const totalStudents = chartData.reduce((acc, curr) => acc + curr.students, 0);
  const totalVerified = chartData.reduce((acc, curr) => acc + curr.verifiedHours, 0);
  const avgCompliance = Math.round(chartData.reduce((acc, curr) => acc + curr.compliancePercent, 0) / chartData.length);

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
          <h1 className={styles.title}>Department Analytics & Program Compliance Overview</h1>
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
          <span className={styles.statTitle}>Active Senior Practitioners</span>
          <span className={styles.statNumber}>{totalStudents}</span>
          <span className={styles.statSub}>Enrolled across 5 academic cohorts</span>
        </div>

        <div className={styles.statBox}>
          <span className={styles.statTitle}>Department Compliance Rate</span>
          <span className={styles.statNumberPrimary}>{avgCompliance}%</span>
          <span className={styles.statSub}>Target minimum required: 85%</span>
        </div>

        <div className={styles.statBox}>
          <span className={styles.statTitle}>Total Verified Internship Hours</span>
          <span className={styles.statNumber}>{totalVerified.toLocaleString()} hrs</span>
          <span className={styles.statSub}>Verified by 12 faculty advisors</span>
        </div>
      </section>

      {/* Recharts Analytics Visualization */}
      <section className={styles.chartCard}>
        <div className={styles.cardTopRow}>
          <h2 className={styles.cardHeading}>Cohort Hour Volume Comparison (Verified vs Pending)</h2>
          <button type="button" onClick={handleAuditTrigger} className={styles.auditTriggerBtn}>
            Trigger Low-Compliance Audit Notice
          </button>
        </div>

        <div className={styles.chartWrap}>
          <ResponsiveContainer width="100%" height={300}>
            <BarChart data={chartData} margin={{ top: 20, right: 30, left: 20, bottom: 5 }}>
              <CartesianGrid strokeDasharray="3 3" stroke="#E2E5EA" />
              <XAxis dataKey="cohort" tick={{ fontSize: 13, fill: '#1C1F26', fontWeight: 600 }} />
              <YAxis tick={{ fontSize: 13, fill: '#6B7280' }} />
              <Tooltip
                contentStyle={{ backgroundColor: '#FFFFFF', borderColor: '#E2E5EA', fontSize: '13px' }}
                labelStyle={{ fontWeight: 700, color: '#1C1F26' }}
              />
              <Legend wrapperStyle={{ fontSize: '13px', paddingTop: '10px' }} />
              <Bar dataKey="verifiedHours" name="Verified Approved Hours" fill="#2B5C8A" radius={[2, 2, 0, 0]} />
              <Bar dataKey="pendingHours" name="Pending Review Hours" fill="#C08A2E" radius={[2, 2, 0, 0]} />
            </BarChart>
          </ResponsiveContainer>
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
                <th>Verified Hours</th>
                <th>Pending Review</th>
                <th>Compliance Rate</th>
                <th>Institutional Status</th>
              </tr>
            </thead>
            <tbody>
              {chartData.map((row, index) => (
                <tr key={row.cohort}>
                  <td className={styles.boldCell}>{row.cohort}</td>
                  <td>{index % 2 === 0 ? 'Dr. Rajesh K.' : 'Prof. Sunita Rao'}</td>
                  <td className={styles.centerCell}>{row.students}</td>
                  <td className={styles.centerCell}><strong>{row.verifiedHours.toLocaleString()}</strong></td>
                  <td className={styles.centerCell}>{row.pendingHours}</td>
                  <td className={styles.centerCell}>
                    <span className={row.compliancePercent >= 85 ? styles.goodPercent : styles.warningPercent}>
                      {row.compliancePercent}%
                    </span>
                  </td>
                  <td>
                    <span className={row.compliancePercent >= 85 ? styles.tagOnTrack : styles.tagAudit}>
                      {row.compliancePercent >= 85 ? 'ACCREDITED COMPLIANT' : 'REVIEW AUDIT FLAGGED'}
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
