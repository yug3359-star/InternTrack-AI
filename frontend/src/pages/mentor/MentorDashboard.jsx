import React, { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import api, { getAssignedStudents } from '../../services/api';
import { useNotification } from '../../hooks/useNotification';
import { useAuth } from '../../hooks/useAuth';
import styles from './MentorDashboard.module.css';

const MentorDashboard = () => {
  const { user } = useAuth();
  const { notify } = useNotification();
  const [students, setStudents] = useState([]);
  const [loading, setLoading] = useState(true);
  const [filterText, setFilterText] = useState('');
  const [pendingReviewsCount, setPendingReviewsCount] = useState(0);
  const [mentorName, setMentorName] = useState(user?.displayName || 'Faculty Mentor');

  useEffect(() => {
    const fetchDashboardData = async () => {
      try {
        if (!user) return;
        
        let fetchedName = mentorName;
        // Fetch accurate mentor profile name from backend
        try {
          const profileRes = await api.get('/profile');
          if (profileRes.data && profileRes.data.fullName) {
            fetchedName = profileRes.data.fullName;
            setMentorName(fetchedName);
          }
        } catch (e) {
          console.warn("Could not fetch mentor profile name", e);
        }

        // Fetch students
        const data = await getAssignedStudents(fetchedName);
        setStudents(data);

        // Fetch pending review counts
        try {
          const reviewRes = await api.get('/mentor/borderline-reviews');
          if (reviewRes?.data?.totalPending !== undefined) {
             setPendingReviewsCount(reviewRes.data.totalPending);
          }
        } catch (e) {
          console.warn("Could not fetch borderline review counts", e);
        }
      } catch (err) {
        notify("Failed to load dashboard metrics from server.", "error");
      } finally {
        setLoading(false);
      }
    };
    fetchDashboardData();
  }, [notify, user]);

  const filteredStudents = students.filter(item =>
    (item.fullName && item.fullName.toLowerCase().includes(filterText.toLowerCase())) ||
    (item.rollNo && item.rollNo.toLowerCase().includes(filterText.toLowerCase())) ||
    (item.branch && item.branch.toLowerCase().includes(filterText.toLowerCase()))
  );

  return (
    <div className={styles.wrapper}>
      <header className={styles.topSection}>
        <div>
          <h1 className={styles.pageTitle}>Faculty Dashboard</h1>
          <p className={styles.metaSub}>Faculty Advisor: {mentorName}</p>
        </div>
        <div style={{ display: 'flex', gap: '12px', alignItems: 'center', flexWrap: 'wrap' }}>
          <Link to="/mentor/borderline-reviews" style={{ backgroundColor: '#D97706', color: '#FFF', padding: '8px 16px', borderRadius: '4px', textDecoration: 'none', fontWeight: 700, fontSize: '14px', border: '1px solid #B45309' }}>
            Borderline Reviews ({pendingReviewsCount} Pending)
          </Link>

          <div className={styles.headerBadge}>
            <span>Total Allocated Students: <strong>{students.length}</strong></span>
          </div>
        </div>
      </header>

      {/* Cohort Oversight Overview */}
      <section className={styles.summaryGrid}>
        <div className={styles.statusBox}>
          <span className={styles.boxLabel}>Assigned Students</span>
          <span className={styles.boxNum}>{students.length}</span>
          <span className={styles.boxNote}>Active mentees under your supervision</span>
        </div>

        <div className={styles.statusBox}>
          <span className={styles.boxLabel}>Pending Reviews</span>
          <span className={styles.boxNumAlert}>{pendingReviewsCount}</span>
          <span className={styles.boxNote}>Requires your immediate audit</span>
        </div>
      </section>

      {/* Verification Queue Section */}
      <section className={styles.queueCard}>
        <div className={styles.cardHeaderRow}>
          <h2 className={styles.sectionTitle}>Your Allocated Mentee Cohort</h2>
          <div className={styles.searchWrap}>
            <input
              type="text"
              placeholder="Filter by name, roll, or branch..."
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
                <th>Branch</th>
                <th>Current Status</th>
                <th>Attendance</th>
                <th>Joining Date</th>
                <th className={styles.actionsHeader}>Audit Controls</th>
              </tr>
            </thead>
            <tbody>
              {loading ? (
                <tr>
                  <td colSpan="6" style={{ padding: '24px', textAlign: 'center' }}>
                    Loading your assigned students...
                  </td>
                </tr>
              ) : filteredStudents.length === 0 ? (
                <tr>
                  <td colSpan="6" className={styles.emptyNotice}>
                    No students currently allocated to you by the Head of Department.
                  </td>
                </tr>
              ) : (
                filteredStudents.map(item => (
                  <tr key={item.uid}>
                    <td>
                      <div className={styles.studentCell}>
                        <strong className={styles.studentName}>{item.fullName}</strong>
                        <span className={styles.rollCode}>{item.rollNo || 'Registration: ' + (item.registrationNumber || item.enrollmentNo) || 'N/A'}</span>
                        <span style={{ fontSize: '11px', color: '#64748B' }}>{item.collegeEmail}</span>
                      </div>
                    </td>
                    <td>{item.branch}</td>
                    <td>
                      <span className={item.status === 'Ongoing' ? styles.lowHoursBadge : ''} style={{ padding: '4px 8px', borderRadius: '4px', backgroundColor: '#F0FDF4', color: '#166534', border: '1px solid #BBF7D0', fontWeight: 600 }}>
                        {item.status || 'Approved'}
                      </span>
                    </td>
                    <td>
                      <span style={{ fontWeight: 600, color: (item.attendancePercentage && item.attendancePercentage < 75) ? '#dc2626' : '#166534' }}>
                        {item.attendancePercentage ?? 0.0}%
                      </span>
                    </td>
                    <td className={styles.dateText}>{item.joiningDate || 'Pending'}</td>
                    <td className={styles.actionsCell}>
                      <Link
                        to={`/mentor/student-detail/${item.uid}`}
                        className={styles.approveBtn}
                        style={{ textDecoration: 'none', display: 'inline-block' }}
                        title="View Attendance Ledger & Logs"
                      >
                        View Ledger
                      </Link>
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
