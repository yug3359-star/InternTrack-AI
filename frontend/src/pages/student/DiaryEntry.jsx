import React, { useState, useEffect } from 'react';
import axios from 'axios';
import { useAuth } from '../../hooks/useAuth';
import { getStudentStatus } from '../../services/api';
import styles from './DiaryEntry.module.css';

const API_BASE = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';

const DiaryEntry = () => {
  const { user } = useAuth();
  const studentUid = user?.uid;
  const studentName = user?.name || 'Alex Vance (2023CSB104)';

  const todayStr = new Date().toISOString().split('T')[0];
  const [entryText, setEntryText] = useState('');
  const [diaries, setDiaries] = useState([]);
  const [loading, setLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [errorMsg, setErrorMsg] = useState(null);
  const [successMsg, setSuccessMsg] = useState(null);
  const [appStatus, setAppStatus] = useState(null);

  useEffect(() => {
    fetchDiaries();
    if (studentUid) {
      getStudentStatus(studentUid).then(data => setAppStatus(data)).catch(console.error);
    }
  }, [studentUid]);

  const fetchDiaries = async () => {
    setLoading(true);
    try {
      const resp = await axios.get(`${API_BASE}/api/diaries/student/${studentUid}`);
      setDiaries(resp.data || []);
    } catch (err) {
      console.error('Error fetching student diaries:', err);
    } finally {
      setLoading(false);
    }
  };

  const checkTodayDecided = () => {
    return diaries.find(d => d.date === todayStr);
  };

  const todayRecord = checkTodayDecided();

  const handlePrefillOnTopic = () => {
    setEntryText(
      "Engineered a stateless JWT security verification filter in Spring Boot to enforce role-based access control and integrated asynchronous frontend state management using custom React Hooks for real-time compliance tracking."
    );
    setErrorMsg(null);
  };

  const handlePrefillOffTopic = () => {
    setEntryText(
      "Spent the entire working morning reviewing traditional culinary pasta recipes, testing tomato marinara sauce consistency in the kitchen and practicing sourdough flour baking techniques."
    );
    setErrorMsg(null);
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!entryText || !entryText.trim()) {
      setErrorMsg("Please enter detailed work activities before submitting.");
      return;
    }

    setSubmitting(true);
    setErrorMsg(null);
    setSuccessMsg(null);

    try {
      const resp = await axios.post(`${API_BASE}/api/diaries/submit`, {
        uid: studentUid,
        studentName: studentName,
        date: todayStr,
        entryText: entryText,
        rejectedDueToFaceMismatch: false
      });

      const data = resp.data;
      if (data.status === 'accepted') {
        setSuccessMsg("✓ Work log evaluated by Consolidated AI Pipeline: ACCEPTED (Domain Verified).");
      } else {
        setSuccessMsg("✕ Work log evaluated by Consolidated AI Pipeline: FLAGGED & REJECTED (Sent to Faculty Queue).");
      }
      setEntryText('');
      await fetchDiaries();
    } catch (err) {
      console.error("Error submitting diary:", err);
      setErrorMsg(err.response?.data?.message || "Failed to process work log. Please retry or contact technical support.");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className={styles.container}>
      <header className={styles.headerArea}>
        <div>
          <h1 className={styles.title}>Daily Internship Work</h1>
          <p className={styles.subText}>
            Submit your deliberate engineering progress logs for automated AI compliance analysis & topic extraction.
          </p>
        </div>
      </header>

      {errorMsg && (
        <div style={{ background: '#FEF2F2', color: '#991B1B', padding: '12px 16px', borderRadius: '4px', marginBottom: '16px', border: '1px solid #EF4444', fontWeight: 600 }}>
          {errorMsg}
        </div>
      )}

      {successMsg && (
        <div style={{ background: '#ECFDF5', color: '#065F46', padding: '12px 16px', borderRadius: '4px', marginBottom: '16px', border: '1px solid #10B981', fontWeight: 600 }}>
          {successMsg}
        </div>
      )}

      {appStatus?.status === 'Completed' ? (
        <div className={styles.submissionCard} style={{ textAlign: 'center', padding: '40px' }}>
          <h2 style={{ color: '#166534', marginBottom: '16px' }}>Internship Completed</h2>
          <p style={{ color: '#475569', fontSize: '15px' }}>
            Your internship program has officially concluded. The Daily Work Diary module is now locked and no further entries can be submitted.
          </p>
        </div>
      ) : (
      <>
      <section className={styles.submissionCard}>
        <div className={styles.formHeader}>
          <span className={styles.formTitle}>Today's Activity</span>
          <span className={styles.dateBadge}>Date: {todayStr}</span>
        </div>

        {!todayRecord && (
          <div className={styles.prefillContainer}>
            <button type="button" onClick={handlePrefillOnTopic} className={styles.prefillBtn} title="Prefill realistic software engineering activity">
              Prefill On-Topic Log (Software Engineering)
            </button>
            <button type="button" onClick={handlePrefillOffTopic} className={`${styles.prefillBtn} ${styles.prefillOffTopic}`} title="Prefill unrelated culinary test to trigger automated AI rejection">
              Prefill Off-Topic Log (Culinary Recipes {"->"} Trigger Rejection)
            </button>
          </div>
        )}

        <form onSubmit={handleSubmit}>
          <textarea
            className={styles.textarea}
            placeholder={todayRecord ? `Log for ${todayStr} already evaluated as [${todayRecord.status.toUpperCase()}]. Resubmission is blocked.` : "Describe today's technical achievements, algorithms implemented, debugging resolutions, or engineering architectures..."}
            value={entryText}
            onChange={(e) => setEntryText(e.target.value)}
            disabled={Boolean(todayRecord) || submitting}
          />

          <div className={styles.submitActions}>
            {todayRecord && (
              <span className={styles.alreadyEvaluatedText}>
                Today's log is locked ({todayRecord.status.toUpperCase()}) — resubmissions disabled.
              </span>
            )}
            <button
              type="submit"
              className={styles.submitButton}
              disabled={Boolean(todayRecord) || submitting || !entryText.trim()}
            >
              {submitting ? 'Analyzing AI Domain Compliance...' : 'Submit Log for AI Evaluation'}
            </button>
          </div>
        </form>
      </section>

      <section className={styles.historySection}>
        <h2 className={styles.sectionHeading}>Chronological Audit Timeline & AI Decision Trail</h2>

        {loading ? (
          <div className={styles.emptyState}>Syncing chronological records from institutional ledger...</div>
        ) : diaries.length === 0 ? (
          <div className={styles.emptyState}>No past activity logs recorded for this student account.</div>
        ) : (
          <div className={styles.historyList}>
            {diaries.map((item) => {
              const isAccepted = item.status === 'accepted';
              return (
                <div
                  key={item.id || item.date}
                  className={`${styles.logItem} ${isAccepted ? styles.logItemAccepted : styles.logItemRejected}`}
                >
                  <div className={styles.logHeader}>
                    <span className={styles.logDate}>Ledger Date: {item.date}</span>
                    <span className={`${styles.statusBadge} ${isAccepted ? styles.statusAccepted : styles.statusRejected}`}>
                      {isAccepted ? 'Accepted by AI Compliance' : 'Rejected by Automated Review'}
                      {item.mentorOverridden && ' (Mentor Overridden)'}
                    </span>
                  </div>

                  <div className={styles.logContent}>{item.entryText}</div>

                  {item.reviewReason && (
                    <div className={`${styles.auditReasonBox} ${isAccepted ? styles.auditAccepted : styles.auditRejected}`}>
                      <div className={styles.auditTitle}>Automated Faculty Compliance Assessment Report</div>
                      <div className={styles.auditText}>{item.reviewReason}</div>
                    </div>
                  )}

                  {item.topics && Array.isArray(item.topics) && item.topics.length > 0 && (
                    <div className={styles.topicsContainer}>
                      <span className={styles.topicsTitle}>Extracted Learning Themes (Module 6 Pipeline):</span>
                      {item.topics.map((t, idx) => (
                        <span key={idx} className={styles.topicTag}>#{t}</span>
                      ))}
                    </div>
                  )}
                </div>
              );
            })}
          </div>
        )}
      </section>
      </>
      )}
    </div>
  );
};

export default DiaryEntry;
