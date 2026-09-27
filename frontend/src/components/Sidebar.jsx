import React, { useState, useEffect } from 'react';
import { NavLink, useLocation } from 'react-router-dom';
import { getHighlightedCount, getStudentStatus } from '../services/api';
import api from '../services/api';
import { useAuth } from '../hooks/useAuth';
import styles from './Sidebar.module.css';

const Sidebar = () => {
  const { role, isAuthenticated, user } = useAuth();
  const location = useLocation();
  const [suspiciousCount, setSuspiciousCount] = useState(0);
  const [highlightedCount, setHighlightedCount] = useState(0);
  const [studentStatus, setStudentStatus] = useState('Ongoing');

  useEffect(() => {
    if (role === 'STUDENT' && isAuthenticated && user?.uid) {
      fetchStudentStatus();
    }
    if (role === 'MENTOR' || role === 'HOD' || location.pathname.includes('/mentor') || location.pathname.includes('/hod')) {
      fetchSuspiciousCount();
    }
    if (role === 'HOD' || location.pathname.includes('/hod')) {
      fetchHighlightedCount();
    }
  }, [role, location.pathname, isAuthenticated, user?.uid]);

  const fetchStudentStatus = async () => {
    if (!user?.uid) return;
    try {
      const data = await getStudentStatus(user.uid);
      if (data && data.status) {
        setStudentStatus(data.status);
      }
    } catch (err) {
      console.warn("Could not load student status:", err);
    }
  };

  const fetchSuspiciousCount = async () => {
    try {
      const resp = await api.get('/mentor/suspicious/count');
      if (resp.data && typeof resp.data.count === 'number') {
        setSuspiciousCount(resp.data.count);
      }
    } catch (err) {
      console.error("Could not load suspicious badge count:", err);
    }
  };

  const fetchHighlightedCount = async () => {
    try {
      const resp = await getHighlightedCount();
      if (resp && typeof resp.count === 'number') {
        setHighlightedCount(resp.count);
      }
    } catch (err) {
      console.warn("Could not load highlighted student badge count, utilizing simulated fallback counter:", err);
      setHighlightedCount(0);
    }
  };

  if (!isAuthenticated && !location.pathname.includes('/dashboard') && !location.pathname.includes('/diaries')) {
    return null;
  }

  const renderNavGroup = () => {
    switch (role) {
      case 'STUDENT':
        return (
          <>
            <li className={styles.categoryHeader}>Student Services</li>
            {studentStatus !== 'Completed' && (
              <>
                <li>
                  <NavLink to="/student/dashboard" className={({ isActive }) => isActive ? styles.itemActive : styles.itemLink}>
                    Dashboard
                  </NavLink>
                </li>
                <li>
                  <NavLink to="/student/diaries" className={({ isActive }) => isActive ? styles.itemActive : styles.itemLink}>
                    Daily Diary
                  </NavLink>
                </li>
              </>
            )}
            <li>
              <NavLink to="/student/completion-summary" className={({ isActive }) => isActive ? styles.itemActive : styles.itemLink}>
                Completion Report
              </NavLink>
            </li>
            <li>
              <NavLink to="/student/analytics" className={({ isActive }) => isActive ? styles.itemActive : styles.itemLink}>
                Academic Analytics
              </NavLink>
            </li>
            {/* <li>
              <a href="#compliance-guidelines" className={styles.itemLink} onClick={(e) => { e.preventDefault(); alert("University Internship Bylaws: Minimum 160 verified logged hours required prior to final exam review."); }}>
                Academic Bylaws
              </a>
            </li>
            <li>
              <a href="#advisor-contact" className={styles.itemLink} onClick={(e) => { e.preventDefault(); alert("Your Faculty Mentor details can be viewed in your dashboard."); }}>
                Assigned Advisor
              </a>
            </li> */}
          </>
        );

      case 'MENTOR':
        return (
          <>
            <li className={styles.categoryHeader}>Faculty Validation</li>
            <li>
              <NavLink to="/mentor/dashboard" className={({ isActive }) => isActive ? styles.itemActive : styles.itemLink}>
                Dashboard
              </NavLink>
            </li>
            <li>
              <NavLink to="/mentor/suspicious-diaries" className={({ isActive }) => isActive ? styles.itemActive : styles.itemLink} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <span>Diary Review</span>
                {suspiciousCount > 0 && (
                  <span style={{ background: '#ef4444', color: '#fff', fontSize: '0.75rem', fontWeight: 800, padding: '0.15rem 0.55rem', borderRadius: '9999px', boxShadow: '0 0 10px rgba(239,68,68,0.5)' }}>
                    {suspiciousCount}
                  </span>
                )}
              </NavLink>
            </li>
            {/* <li>
              <a href="#roster" className={styles.itemLink} onClick={(e) => { e.preventDefault(); alert("Assigned Roster: Please view your allocated mentees on the Dashboard."); }}>
                Student Roster
              </a>
            </li>
            <li>
              <a href="#attendance-flags" className={styles.itemLink} onClick={(e) => { e.preventDefault(); alert("Discrepancy Alerts: Review flagged students on your dashboard."); }}>
                Discrepancy Alerts
              </a>
            </li> */}
          </>
        );

      case 'HOD':
        return (
          <>
            <li className={styles.categoryHeader}>Department Oversight</li>
            <li>
              <NavLink to="/hod/dashboard" className={({ isActive }) => isActive ? styles.itemActive : styles.itemLink}>
                Dashboard
              </NavLink>
            </li>
            <li>
              <NavLink to="/hod/analytics" className={({ isActive }) => isActive ? styles.itemActive : styles.itemLink}>
                Analytics
              </NavLink>
            </li>
            <li>
              <NavLink to="/hod/pending-applications" className={({ isActive }) => isActive ? styles.itemActive : styles.itemLink}>
                Pending Applications
              </NavLink>
            </li>
            <li>
              <NavLink to="/hod/suspicious-diaries" className={({ isActive }) => isActive ? styles.itemActive : styles.itemLink} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <span>Suspicious Diaries</span>
                {suspiciousCount > 0 && (
                  <span style={{ background: '#ef4444', color: '#fff', fontSize: '0.75rem', fontWeight: 800, padding: '0.15rem 0.55rem', borderRadius: '9999px', boxShadow: '0 0 10px rgba(239,68,68,0.5)' }}>
                    {suspiciousCount}
                  </span>
                )}
              </NavLink>
            </li>
            <li>
              <NavLink to="/hod/highlighted" className={({ isActive }) => isActive ? styles.itemActive : styles.itemLink} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <span>Highlighted Student</span>
                {highlightedCount > 0 && (
                  <span style={{ background: '#B3423A', color: '#fff', fontSize: '0.75rem', fontWeight: 700, padding: '0.15rem 0.55rem', borderRadius: '4px' }}>
                    {highlightedCount}
                  </span>
                )}
              </NavLink>
            </li>
            <li>
              <NavLink to="/hod/completed" className={({ isActive }) => isActive ? styles.itemActive : styles.itemLink}>
                Completed Students
              </NavLink>
            </li>
            {/* <li>
              <a href="#cohort-status" className={styles.itemLink} onClick={(e) => { e.preventDefault(); alert("Department Completion Summary: 142/180 students aligned with semester targets."); }}>
                Cohort Compliance
              </a>
            </li>
            <li>
              <a href="#export-report" className={styles.itemLink} onClick={(e) => { e.preventDefault(); alert("Exporting accreditation compliance audit spreadsheet to regional CSV ledger."); }}>
                Accreditation Ledgers
              </a>
            </li> */}
          </>
        );

      default:
        return (
          <li><span className={styles.itemDisabled}>Unregistered Role Access</span></li>
        );
    }
  };

  return (
    <aside className={styles.navWrapper}>
      <div className={styles.sectionTitle}>
        <span>Institutional Navigation</span>
      </div>

      <ul className={styles.menuList}>
        {renderNavGroup()}

        <li style={{ marginTop: '2rem', borderTop: '1px solid #E2E5EA', paddingTop: '1rem' }}>
          <NavLink to="/profile" className={({ isActive }) => isActive ? styles.itemActive : styles.itemLink} style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
            <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"></path>
              <circle cx="12" cy="7" r="4"></circle>
            </svg>
            My Profile
          </NavLink>
        </li>
      </ul>

      <div className={styles.footerHelp}>
        <span className={styles.systemStatus}>System: Online (v1.0-RC)</span>
        <span className={styles.supportContact}>Help Desk: portal-support@college.edu</span>
      </div>
    </aside>
  );
};

export default Sidebar;
