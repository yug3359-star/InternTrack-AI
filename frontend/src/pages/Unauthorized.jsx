import React from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import styles from './Unauthorized.module.css';

/**
 * Page presented when a logged-in user attempts to navigate directly to an unauthorized departmental URL.
 */
const Unauthorized = () => {
  const navigate = useNavigate();
  const { role } = useAuth();

  const getHomeRoute = () => {
    const currentRole = role ? String(role).toLowerCase() : '';
    if (currentRole === 'hod') return '/hod/dashboard';
    if (currentRole === 'mentor') return '/mentor/dashboard';
    return '/student/dashboard';
  };

  const handleReturnHome = () => {
    navigate(getHomeRoute(), { replace: true });
  };

  return (
    <div className={styles.unauthorizedContainer}>
      <div className={styles.card} role="alert" aria-live="assertive">
        <div className={styles.iconWrap}>
          <svg width="36" height="36" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
            <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" />
            <line x1="12" y1="8" x2="12" y2="12" />
            <line x1="12" y1="16" x2="12.01" y2="16" />
          </svg>
        </div>
        
        <h1 className={styles.title}>Access Denied</h1>
        <p className={styles.message}>You don't have permission to view this page.</p>
        
        <p className={styles.description}>
          This module is restricted to designated faculty members or institutional administrators. Your current authentication credentials do not include authorization for this department view.
          <br />
          <span className={styles.roleHighlight}>Current Role: {role || 'STUDENT'}</span>
        </p>

        <div className={styles.actions}>
          <button onClick={handleReturnHome} className={styles.homeButton}>
            Return to My Dashboard
          </button>
        </div>
      </div>
    </div>
  );
};

export default Unauthorized;
