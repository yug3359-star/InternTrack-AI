import React from 'react';
import styles from './StatusBadge.module.css';

/**
 * Reusable status indicator pill component displaying official administrative state lifecycle:
 * Applied -> Approved -> Ongoing -> Completed (or Rejected).
 * Enforces institutional 4px border-radius and muted design color tokens.
 */
const StatusBadge = ({ status = 'Applied', showDot = true }) => {
  const normalized = String(status).trim().toLowerCase();

  let badgeClass = styles.badgeApplied;
  let labelText = status || 'Applied';

  if (normalized.includes('ongoing')) {
    badgeClass = styles.badgeOngoing;
    labelText = 'Ongoing Practice';
  } else if (normalized.includes('approved')) {
    badgeClass = styles.badgeApproved;
    labelText = 'Approved (Pre-Start)';
  } else if (normalized.includes('completed')) {
    badgeClass = styles.badgeCompleted;
    labelText = 'Completed & Verified';
  } else if (normalized.includes('rejected')) {
    badgeClass = styles.badgeRejected;
    labelText = 'Rejected by HOD';
  } else if (normalized.includes('pending')) {
    badgeClass = styles.badgeApplied;
    labelText = 'Pending Review';
  } else {
    badgeClass = styles.badgeApplied;
    labelText = 'Applied / Review Pending';
  }

  return (
    <span className={`${styles.badge} ${badgeClass}`} role="status">
      {showDot && <span className={styles.statusDot} aria-hidden="true"></span>}
      <span className={styles.statusText}>{labelText}</span>
    </span>
  );
};

export default StatusBadge;
