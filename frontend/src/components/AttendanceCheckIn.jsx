import React from 'react';
import CountdownTimer from './CountdownTimer';
import styles from './AttendanceCheckIn.module.css';

/**
 * Formal Daily Attendance Check-in Modal Dialog (Module 5b).
 * Provides a distinct visual layout from engagement pop-ups with a 1-hour live window,
 * primary "Mark Present" confirmation, and shared Meeting Quota override support.
 */
const AttendanceCheckIn = ({ activeAttendance, meetingQuota, onRespond, onClose, loading }) => {
  if (!activeAttendance) return null;

  const used = meetingQuota?.used || 0;
  const limit = meetingQuota?.limit || 5;
  const remaining = meetingQuota?.remaining ?? Math.max(0, limit - used);
  const isQuotaExhausted = remaining <= 0 || used >= limit;

  return (
    <div className={styles.modalOverlay} role="dialog" aria-labelledby="attendance-title" aria-modal="true">
      <div className={styles.modalDialog}>
        
        <div className={styles.header}>
          <h2 id="attendance-title" className={styles.title}>
            <span>📋</span> Daily Attendance
          </h2>
          <CountdownTimer 
            durationSeconds={3600} 
            deadline={activeAttendance.deadline}
            onTimeout={() => typeof onClose === 'function' && onClose('TIMEOUT')} 
          />
        </div>

        <p className={styles.copyText}>
          Confirm you are present for today's internship session. You have 1 hour from initial trigger to submit your daily check-in.
        </p>

        <div className={styles.infoBox}>
          <div className={styles.quotaLabel}>
            <span>Shared Monthly Meeting Quota:</span>
            <span><strong>{used} / {limit} used</strong> ({remaining} passes remaining)</span>
          </div>
          {isQuotaExhausted ? (
            <span className={styles.quotaExhausted}>
              ⚠️ Meeting quota exhausted for this month. You must mark Present directly.
            </span>
          ) : (
            <span style={{ fontSize: '0.85rem', color: '#64748B' }}>
              Note: Using a meeting pass here consumes 1 pass from the same monthly allowance used by engagement popups.
            </span>
          )}
        </div>

        <div className={styles.actions}>
          <button 
            type="button"
            className={styles.primaryBtn} 
            onClick={() => onRespond('present')}
            disabled={loading}
          >
            {loading ? 'Processing...' : '✓ Mark Present'}
          </button>

          <button 
            type="button"
            className={styles.secondaryBtn} 
            onClick={() => onRespond('meeting')}
            disabled={loading || isQuotaExhausted}
            title={isQuotaExhausted ? 'Monthly meeting override quota exhausted' : 'Excuses today\'s attendance check using 1 meeting pass'}
          >
            🤝 I'm in a meeting ({remaining} passes left)
          </button>
        </div>

        <p className={styles.footerNote}>
          🔒 Institutional verification logged automatically to real Firestore records.
        </p>

      </div>
    </div>
  );
};

export default AttendanceCheckIn;
