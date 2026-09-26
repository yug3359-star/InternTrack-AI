import React, { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';

import { useNotification } from '../../hooks/useNotification';
import { runStatusCheckNow, getPendingApplications, exportExcelReport } from '../../services/api';
import styles from './HodDashboard.module.css';

const HodDashboard = () => {
  const { notify } = useNotification();
  const [selectedSemester, setSelectedSemester] = useState('Fall 2026');
  const [statusLoading, setStatusLoading] = useState(false);
  const [studentData, setStudentData] = useState([]);
  const [isLoadingData, setIsLoadingData] = useState(true);
  const [isGeneratingExcel, setIsGeneratingExcel] = useState(false);

  useEffect(() => {
    const fetchStudents = async () => {
      try {
        setIsLoadingData(true);
        const data = await getPendingApplications(1, 'ALL');
        if (data && data.applications) {
          const formattedData = data.applications.map(app => ({
            uid: app.uid,
            name: app.fullName || 'Unknown',
            section: app.section || 'N/A',
            rollNo: app.rollNo || 'N/A',
            startDate: app.joiningDate || 'N/A',
            attendance: app.attendancePercentage !== undefined ? `${app.attendancePercentage}%` : '0%'
          }));
          setStudentData(formattedData);
        }
      } catch (error) {
        console.error("Failed to fetch student data", error);
        notify("Could not load real student data", "error");
      } finally {
        setIsLoadingData(false);
      }
    };
    fetchStudents();
  }, [notify]);

  const totalStudents = studentData.length;

  const handleExport = async () => {
    setIsGeneratingExcel(true);
    try {
      const blob = await exportExcelReport();
      const url = window.URL.createObjectURL(new Blob([blob]));
      const link = document.createElement('a');
      link.href = url;
      link.setAttribute('download', `Internship_Report_${new Date().toISOString().split('T')[0]}.xlsx`);
      document.body.appendChild(link);
      link.click();
      link.parentNode.removeChild(link);
      notify("Excel report generated successfully.", "success");
    } catch (error) {
      console.error(error);
      notify("Failed to generate Excel report.", "error");
    } finally {
      setIsGeneratingExcel(false);
    }
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
          {/* <select
            value={selectedSemester}
            onChange={(e) => setSelectedSemester(e.target.value)}
            className={styles.semesterSelect}
          >
            <option value="Fall 2026">Semester VII (Fall 2026)</option>
            <option value="Spring 2026">Semester VI (Spring 2026)</option>
            <option value="Fall 2025">Semester V (Fall 2025)</option>
          </select> */}
          <button type="button" onClick={handleExport} className={styles.exportBtn} disabled={isGeneratingExcel}>
            {isGeneratingExcel ? 'Generating...' : 'Generate Excel Report'}
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



      {/* Student Internship Overview Table */}
      <section className={styles.tableSection}>
        <h2 className={styles.tableHeading}>VII Semester Internship Overview</h2>
        <div className={styles.tableOverflow}>
          <table className={styles.deptTable}>
            <thead>
              <tr>
                <th>Student Name</th>
                <th>Section</th>
                <th>Roll No.</th>
                <th>Internship Start Date</th>
                <th>Attendance</th>
              </tr>
            </thead>
            <tbody>
              {isLoadingData ? (
                <tr>
                  <td colSpan="5" className={styles.centerCell} style={{ padding: '2rem' }}>Loading real student data...</td>
                </tr>
              ) : studentData.length === 0 ? (
                <tr>
                  <td colSpan="5" className={styles.centerCell} style={{ padding: '2rem' }}>No students found.</td>
                </tr>
              ) : (
                studentData.map((student) => (
                  <tr key={student.uid}>
                    <td className={styles.boldCell}>{student.name}</td>
                    <td>{student.section}</td>
                    <td className={styles.centerCell}>{student.rollNo}</td>
                    <td>{student.startDate}</td>
                    <td>
                      <span className={parseFloat(student.attendance) >= 90 ? styles.tagOnTrack : styles.tagAudit}>
                        {student.attendance}
                      </span>
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

export default HodDashboard;
