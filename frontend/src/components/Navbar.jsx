import React from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../hooks/useAuth';
import { useNotification } from '../hooks/useNotification';
import styles from './Navbar.module.css';

const Navbar = () => {
  const { isAuthenticated, user, role, logout, switchRole, isDevMode } = useAuth();
  const { notify } = useNotification();
  const navigate = useNavigate();

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
        {isAuthenticated ? (
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

            <Link to="/privacy" style={{ fontSize: '12px', color: '#2B5C8A', marginRight: '14px', textDecoration: 'underline', fontWeight: '600', alignSelf: 'center' }}>Privacy Policy</Link>
            <button
              onClick={handleLogout}
              className={styles.signOutBtn}
              type="button"
            >
              Sign Out
            </button>
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
