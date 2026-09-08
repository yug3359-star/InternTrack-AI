import React from 'react';
import { Link, useNavigate, useLocation } from 'react-router-dom';
import { useAuth } from '../hooks/useAuth';
import { useNotification } from '../hooks/useNotification';
import styles from './Navbar.module.css';

const Navbar = () => {
  const { isAuthenticated, user, role, logout, switchRole, isDevMode } = useAuth();
  const { notify } = useNotification();
  const navigate = useNavigate();
  const location = useLocation();

  const isAuthPage = location.pathname === '/login' || location.pathname === '/register' || location.pathname === '/student/register';

  const handleLogout = async () => {
    try {
      await logout();
      notify("Session terminated: successfully disconnected from academic portal.", "info");
      navigate('/login');
    } catch (err) {
      notify("Logout error encountered. Please close terminal window.", "error");
    }
  };

  const handleRoleSwitch = (e) => {
    const targetRole = e.target.value;
    switchRole(targetRole);
    notify(`Switched demonstration view to active role: [${targetRole}]`, "warning", 3000);
    if (targetRole === 'STUDENT') navigate('/student/dashboard');
    if (targetRole === 'MENTOR') navigate('/mentor/dashboard');
    if (targetRole === 'HOD') navigate('/hod/dashboard');
  };

  return (
    <header className={styles.topBar}>
      <div className={styles.brandSection}>
        <span className={styles.portalLogo}>InternTrack</span>
        <span className={styles.departmentSubtitle}>G H Raisoni College of Engineering, Nagpur</span>
      </div>

      <nav className={styles.controlsArea}>
        {isAuthenticated && !isAuthPage ? (
          <>
            <div className={styles.sessionMeta}>
              <span className={styles.userEmail}>{user?.email || 'authenticated_user@college.edu'}</span>

              {/* Review panel demonstration selector to easily evaluate multi-role portals */}
              <div className={styles.roleSelectorWrap}>
                <label htmlFor="role-select" className={styles.roleLabel}>Active Role:</label>
                <select
                  id="role-select"
                  className={styles.roleDropdown}
                  value={role}
                  onChange={handleRoleSwitch}
                  title="Switch institutional access claim for review demo"
                >
                  <option value="STUDENT">Student</option>
                  <option value="MENTOR">Faculty Mentor</option>
                  <option value="HOD">HOD</option>
                </select>
              </div>
            </div>

            <Link to="/profile" style={{ fontSize: '12px', color: '#2B5C8A', marginRight: '14px', textDecoration: 'none', fontWeight: '600', alignSelf: 'center', display: 'flex', alignItems: 'center', gap: '4px' }}>
              <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"></path>
                <circle cx="12" cy="7" r="4"></circle>
              </svg>
              Profile
            </Link>
            <Link to="/privacy" style={{ fontSize: '12px', color: '#2B5C8A', marginRight: '14px', textDecoration: 'underline', fontWeight: '600', alignSelf: 'center' }}>Privacy Policy</Link>
            {/* <button
              onClick={handleLogout}
              className={styles.signOutBtn}
              type="button"
            >
              Sign Out
            </button> */}
          </>
        ) : (
          <div className={styles.guestLinks}>
            <Link to="/privacy" className={styles.authLink}>Privacy Notice</Link>
            <Link to="/login" className={styles.authLink}>Sign In</Link>
            <Link to="/register" className={styles.registerLink}>New Registration</Link>
          </div>
        )}
      </nav>
    </header>
  );
};

export default Navbar;
