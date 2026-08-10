import React, { useState, useEffect } from 'react';
import axios from 'axios';
import { useAuth } from '../../hooks/useAuth';
import styles from './SuspiciousDiaries.module.css';

const API_BASE = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';

const SuspiciousDiaries = () => {
  const { user, role } = useAuth();
  const [queue, setQueue] = useState([]);
  const [loading, setLoading] = useState(false);
  const [actionInProgress, setActionInProgress] = useState(null);
  const [notification, setNotification] = useState(null);

  const isHod = role === 'HOD' || window.location.pathname.includes('/hod/');
  const mentorUid = user?.uid || (isHod ? 'Dr. Sharma (Head of Department)' : 'Prof. Rajesh K. (Faculty Advisor)');

  useEffect(() => {
    fetchSuspiciousQueue();
  }, []);

  const fetchSuspiciousQueue = async () => {
    setLoading(true);
    try {
      const url = isHod ? `${API_BASE}/api/hod/suspicious` : `${API_BASE}/api/mentor/suspicious`;
      const resp = await axios.get(url);
      setQueue(resp.data || []);
    } catch (err) {
      console.error('Error fetching suspicious diaries:', err);
    } finally {
      setLoading(false);
    }
  };

  const handleAcceptOverride = async (item) => {
    setActionInProgress(item.id);
    setNotification(null);
    try {
      await axios.post(`${API_BASE}/api/mentor/suspicious/${item.id}/override`, {
        action: 'accept',
        mentorUid: mentorUid
      });
      setNotification(`✓ Faculty Override Applied: Log for ${item.studentName || item.uid} on ${item.date} transferred to Accepted Ledger.`);
      setQueue(prev => prev.filter(q => q.id !== item.id));
    } catch (err) {
      console.error('Error overriding suspicious log:', err);
      alert('Failed to execute override action. Please verify server connection.');
    } finally {
      setActionInProgress(null);
    }
  };

  const handleDeletePermanent = async (item) => {
    // STRICT INSTITUTIONAL CONSENT DIALOG
    const confirmed = window.confirm("This cannot be undone — delete permanently?");
    if (!confirmed) return;

    setActionInProgress(item.id);
    setNotification(null);
    try {
      await axios.post(`${API_BASE}/api/mentor/suspicious/${item.id}/override`, {
        action: 'delete',
        mentorUid: mentorUid
      });
      setNotification(`Permanent Removal Confirmed: Flagged activity record expunged from academic ledger.`);
      setQueue(prev => prev.filter(q => q.id !== item.id));
    } catch (err) {
      console.error('Error deleting suspicious log:', err);
      alert('Failed to execute permanent deletion. Please verify server connection.');
    } finally {
      setActionInProgress(null);
    }
  };

  return (
    <div className={styles.container}>
      <header className={styles.headerArea}>
        <div className={styles.titleSection}>
          <h1 className={styles.title}>
            {isHod ? 'Departmental Suspicious Diary Ledger' : 'Faculty Suspicious Log Verification Queue'}
          </h1>
          <p className={styles.subText}>
            Investigate student work logs flagged by the automated AI Compliance Pipeline for domain irrelevance or biometric mismatch.
          </p>
        </div>

        <div className={styles.badgeCounter}>
          <span>Active Discrepancies:</span>
          <span>{queue.length} Flagged {queue.length === 1 ? 'Log' : 'Logs'}</span>
        </div>
      </header>

      {notification && (
        <div style={{ background: '#ECFDF5', color: '#065F46', padding: '12px 16px', borderRadius: '4px', marginBottom: '20px', border: '1px solid #10B981', fontWeight: 600 }}>
          {notification}
        </div>
      )}

      <main className={styles.tableCard}>
        {loading ? (
          <div className={styles.emptyState}>
            <div className={styles.emptyText}>Synchronizing Flagged Records...</div>
            <div className={styles.emptySub}>Querying real-time institutional compliance database.</div>
          </div>
        ) : queue.length === 0 ? (
          <div className={styles.emptyState}>
            <div className={styles.emptyText}>Compliance Audit Queue Clean</div>
            <div className={styles.emptySub}>
              Zero suspicious activity logs or domain discrepancies currently pending review across your assigned student roster.
            </div>
          </div>
        ) : (
          <div className={styles.tableResponsive}>
            <table className={styles.table}>
              <thead>
                <tr>
                  <th className={styles.th}>Student Roster ID</th>
                  <th className={styles.th}>Submission Date</th>
                  <th className={styles.th}>Submitted Activity Ledger</th>
                  <th className={styles.th}>AI Audit Evaluation Report</th>
                  <th className={styles.th} style={{ textAlign: 'center' }}>Faculty Override Actions</th>
                </tr>
              </thead>
              <tbody>
                {queue.map((item) => (
                  <tr key={item.id || item.date} className={styles.tr}>
                    <td className={styles.td}>
                      <div className={styles.studentInfo}>
                        <span className={styles.studentName}>{item.studentName || 'Alex Vance'}</span>
                        <span className={styles.studentUid}>{item.uid}</span>
                      </div>
                    </td>

                    <td className={styles.td}>
                      <span className={styles.dateText}>{item.date}</span>
                      <span className={styles.timestamp}>
                        {item.flaggedAt ? `Flagged: ${new Date(item.flaggedAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}` : 'Automated Review'}
                      </span>
                    </td>

                    <td className={styles.td}>
                      <div className={styles.entryText}>{item.entryText}</div>
                    </td>

                    <td className={styles.td}>
                      <div className={styles.reasonBox}>
                        {item.reviewReason || 'Domain Discrepancy Flagged'}
                      </div>
                    </td>

                    <td className={`${styles.td} ${styles.actionCell}`} style={{ textAlign: 'center', verticalAlign: 'middle' }}>
                      <div className={styles.actionButtons}>
                        <button
                          type="button"
                          className={styles.acceptBtn}
                          onClick={() => handleAcceptOverride(item)}
                          disabled={actionInProgress === item.id}
                          title="Override AI evaluation and move record to Accepted ledger"
                        >
                          ✓ Accept anyway
                        </button>
                        <button
                          type="button"
                          className={styles.deleteBtn}
                          onClick={() => handleDeletePermanent(item)}
                          disabled={actionInProgress === item.id}
                          title="Permanently remove off-topic or fraudulent record"
                        >
                          Delete permanently
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </main>
    </div>
  );
};

export default SuspiciousDiaries;
