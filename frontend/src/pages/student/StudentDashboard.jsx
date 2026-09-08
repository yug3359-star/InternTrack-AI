import React, { useState, useEffect } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { useNotification } from '../../hooks/useNotification';
import { useBrowserNotification } from '../../hooks/useBrowserNotification';
import { usePopupListener } from '../../hooks/usePopupListener';
import { useAttendanceListener } from '../../hooks/useAttendanceListener';
import { useTestListener } from '../../hooks/useTestListener';
import { useAuth } from '../../hooks/useAuth';
import StatusBadge from '../../components/StatusBadge';
import EngagementPopup from '../../components/EngagementPopup';
import AttendanceCheckIn from '../../components/AttendanceCheckIn';
import { getStudentStatus } from '../../services/api';
import axios from 'axios';
import styles from './StudentDashboard.module.css';

const StudentDashboard = () => {
  const { notify } = useNotification();
  const { requestPermission, permission } = useBrowserNotification();
  const { user } = useAuth();
  const studentUid = user?.uid || 'fallback-uid'; // Should always be available if authenticated

  const {
    activePopup,
    setActivePopup,
    meetingQuota,
    loadingResponse,
    respondToPopup,
    triggerDevTestPopup
  } = usePopupListener(studentUid, 'STUDENT');

  const {
    activeAttendance,
    setActiveAttendance,
    attendanceHistory,
    attendanceStats,
    loadingResponse: loadingAttendance,
    respondToAttendance,
    triggerTestAttendance
  } = useAttendanceListener(studentUid, 'STUDENT');

  const navigate = useNavigate();
  const { activeTest, testHistory } = useTestListener(studentUid, navigate);

  const handleTriggerTestNow = async () => {
    try {
      const res = await axios.post(`http://localhost:8080/api/test/trigger/${studentUid}`, {
        domain: 'Backend Engineering',
        questionsCount: 5,
        testId: `test-${Date.now()}`
      });
      const today = new Date().toISOString().split('T')[0];
      navigate(`/student/test-session/${studentUid}_${today}`);
    } catch (err) {
      console.error('Error simulating proctored exam, navigating to seamless fallback session:', err);
      const today = new Date().toISOString().split('T')[0];
      navigate(`/student/test-session/${studentUid}_${today}`);
    }
  };


  const [appStatus, setAppStatus] = useState(null);
  const [permissionState, setPermissionState] = useState(permission);
  const [claimingMeeting, setClaimingMeeting] = useState(false); useEffect(() => {
    if ('Notification' in window) {
      setPermissionState(Notification.permission);
    }
    const loadStatus = async () => {
      try {
        const data = await getStudentStatus(studentUid);
        if (data && (data.status === 'Applied' || data.status === 'Under Review')) {
          navigate('/student/verification-pending', { replace: true });
        } else {
          setAppStatus(data);
        }
      } catch (err) {
        console.error("Failed to load status chronology:", err);
      }
    };
    loadStatus();
  }, []);

  const handlePermissionRequest = async () => {
    const newPerm = await requestPermission();
    setPermissionState(newPerm);
    if (newPerm === 'granted') {
      notify("Browser notifications enabled! You will now receive background check-ins even when this tab is minimized.", "success", 5000);
    } else {
      notify("Notification permission denied. Please allow notifications in your browser address bar site settings.", "error", 6000);
    }
  };



  const rawUsed = meetingQuota?.used || 0;
  const quotaLimit = meetingQuota?.limit || 3;
  const cappedUsed = Math.min(rawUsed, quotaLimit); // Enterprise graceful downgrade if limit was reduced
  const quotaUsedPercent = Math.min(100, Math.round((cappedUsed / quotaLimit) * 100));
  const isQuotaExhausted = rawUsed >= quotaLimit;

  const todayDateStr = new Date().toISOString().split('T')[0];
  const alreadyClaimedToday = attendanceHistory?.some(rec => rec.date === todayDateStr && rec.status === 'excused_meeting');

  const handleClaimMeetingDay = async () => {
    if (isQuotaExhausted) {
      notify("Monthly meeting quota exhausted!", "error");
      return;
    }
    setClaimingMeeting(true);
    try {
      const res = await axios.post(`http://localhost:8080/api/popups/${studentUid}/claim-meeting-day`);
      notify(res.data.message || "Whole day meeting exemption claimed successfully!", "success", 5000);
      // Update local quota state if needed (or trigger a refresh via custom hook)
      if (meetingQuota) {
        meetingQuota.used = res.data.quotaUsed;
      }
    } catch (err) {
      console.error("Error claiming meeting day:", err);
      notify(err.response?.data?.message || "Failed to claim meeting exemption.", "error", 5000);
    } finally {
      setClaimingMeeting(false);
    }
  };

  return (
    <div className={styles.container}>
      {/* Module 5a: Persistent Non-Blocking Notification Permission Banner */}
      {permissionState !== 'granted' && (
        <div className={styles.notifBanner}>
          <div>
            <strong>Attendance Alert:</strong> Enable notifications to receive working-hour check-ins even when this tab isn't active — required for accurate attendance tracking.
          </div>
          <button type="button" onClick={handlePermissionRequest} className={styles.notifBtn}>
            Enable Notifications →
          </button>
        </div>
      )}

      <header className={styles.headerArea}>
        <div>
          <h1 className={styles.title}>Student Dashboard</h1>
          <span className={styles.subText}>Enrollment No. : {studentUid || 'Unknown'} | Faculty Mentor: {appStatus?.collegeMentor || 'Unassigned'} ({appStatus?.branch || 'Department Unassigned'})</span>
        </div>
      </header>

      {/* Module 10: Consolidated Notification Preferences Section */}
      <section className={styles.engagementSection} style={{ borderTop: '4px solid #2B5C8A', background: '#FFFFFF', border: '1px solid #E2E5EA', borderRadius: '4px', marginBottom: '24px', padding: '16px 20px', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <div>
          <span style={{ fontWeight: 700, color: '#2B5C8A', fontSize: '15px', display: 'block', marginBottom: '4px' }}>
            Institutional Engagement Alert Settings
          </span>
          <span style={{ fontSize: '13px', color: '#5A626A' }}>
            Current Push Permission State: <strong style={{ textTransform: 'uppercase' }}>{permissionState || permission || 'unsupported'}</strong> | Essential for background engagement check-ins and proctored exam announcements.
          </span>
        </div>
        <button
          type="button"
          onClick={async () => {
            const res = await requestPermission();
            setPermissionState(res);
          }}
          style={{
            padding: '8px 16px',
            borderRadius: '4px',
            border: '1px solid #2B5C8A',
            backgroundColor: permission === 'granted' ? '#2F7A4F' : '#2B5C8A',
            color: '#FFFFFF',
            fontWeight: 600,
            fontSize: '13px',
            cursor: 'pointer',
            whiteSpace: 'nowrap'
          }}
        >
          {permission === 'granted' ? 'OS Notifications Enabled' : 'Enable Browser Notifications'}
        </button>
      </section>

      {/* Module 5a: Automated Engagement "Are You Working?" Tracking Section */}
      <section className={styles.engagementSection}>
        <div className={styles.engagementHeader}>
          <span className={styles.engagementTitle}>Daily Working Popups</span>
          <button
            type="button"
            onClick={() => triggerDevTestPopup(5)}
            className={styles.testTriggerBtn}
          >
            Test Check-In & Push Notification (Fires in 5s — Try Minimizing!)
          </button>
        </div>

        <div className={styles.quotaContainer}>
          <div className={styles.quotaBox}>
            <span style={{ fontSize: '12px', fontWeight: 700, color: '#64748B', textTransform: 'uppercase' }}>Monthly Meeting Exemption Quota</span>
            <div className={styles.quotaCount}>
              {cappedUsed} / {quotaLimit} Used
            </div>
            <div className={styles.progressBarOuter}>
              <div className={styles.progressBarInner} style={{ width: `${quotaUsedPercent}%` }} />
            </div>
          </div>
          <div>
            <p style={{ margin: 0, fontSize: '14px', color: '#334155', lineHeight: 1.5 }}>
              <strong>Institutional Rules & Biometric Discipline:</strong> During declared working hours, our background Service Worker delivers 5 randomized check-in alerts per day. You may claim a <em>"Whole Day Meeting Exemption"</em> up to <strong>3 times per month</strong> without webcam disruption. This stops all pop-ups for the current day and marks you as excused. Once exhausted, optical Face-Match verification (over 75% AWS Rekognition similarity) is strictly enforced.
            </p>
            <button
              type="button"
              onClick={handleClaimMeetingDay}
              disabled={claimingMeeting || isQuotaExhausted || alreadyClaimedToday}
              style={{
                marginTop: '12px',
                padding: '8px 16px',
                backgroundColor: (isQuotaExhausted || alreadyClaimedToday) ? '#9CA3AF' : '#2B5C8A',
                color: 'white',
                border: 'none',
                borderRadius: '4px',
                cursor: (isQuotaExhausted || alreadyClaimedToday) ? 'not-allowed' : 'pointer',
                fontWeight: 600
              }}
            >
              {claimingMeeting ? 'Processing...' : alreadyClaimedToday ? 'Claimed for Today' : 'Claim Full Day Meeting Exemption'}
            </button>
            {isQuotaExhausted && !alreadyClaimedToday && (
              <span style={{ fontSize: '12px', color: '#DC2626', marginLeft: '12px' }}>
                Quota exhausted!
              </span>
            )}
          </div>
        </div>
      </section>

      {/* Module 5b: Formal Daily Attendance Marking Section */}
      <section className={styles.engagementSection} style={{ borderTop: '4px solid #16A34A' }}>
        <div className={styles.engagementHeader}>
          <span className={styles.engagementTitle} style={{ color: '#15803D' }}>Daily Attendance Check (1x Daily Check)</span>
          <button
            type="button"
            onClick={triggerTestAttendance}
            className={styles.testTriggerBtn}
            style={{ backgroundColor: '#16A34A' }}
          >
            Test Daily Attendance Check (1-Hour Window)
          </button>
        </div>
        <div className={styles.quotaContainer}>
          <div className={styles.quotaBox} style={{ minWidth: '220px' }}>
            <span style={{ fontSize: '12px', fontWeight: 700, color: '#64748B', textTransform: 'uppercase' }}>Real Attendance Percentage</span>
            <div className={styles.quotaCount} style={{ color: '#15803D', fontSize: '24px' }}>
              {attendanceStats?.attendancePercentage ?? 100}%
            </div>
            <span style={{ fontSize: '13px', color: '#475569' }}>
              Present: <strong>{attendanceStats?.presentDays ?? 0}</strong> | Excused: <strong>{attendanceStats?.excusedDays ?? 0}</strong> | Missed: <strong>{attendanceStats?.missedDays ?? 0}</strong>
            </span>
          </div>
          <div>
            <p style={{ margin: 0, fontSize: '14px', color: '#334155', lineHeight: 1.5 }}>
              <strong>Deliberate Daily Compliance:</strong> Unlike random engagement pop-ups, this formal daily attendance check fires once each morning (30 minutes after your registered office start time) for all students with <strong>Ongoing</strong> internships. You are granted a strict 1-hour window to mark your attendance. Unanswered check-ins automatically transition to <strong>Missed</strong> in real Firestore and trigger a daily absence penalty.
            </p>
          </div>
        </div>
      </section>

      {/* Module 5c: Personalized AI Proctored Test Section */}
      <section className={styles.engagementSection} style={{ borderTop: '4px solid #4F46E5', background: 'white' }}>
        <div className={styles.engagementHeader}>
          <span className={styles.engagementTitle} style={{ color: '#4338CA' }}>Proctored AI Test (2x/Week Timed Exam)</span>
          <button
            type="button"
            onClick={handleTriggerTestNow}
            className={styles.testTriggerBtn}
            style={{ backgroundColor: '#4F46E5', boxShadow: '0 4px 14px rgba(79, 70, 229, 0.3)' }}
          >
            Simulate Live Proctored AI Test (20m Server Deadline)
          </button>
        </div>
        <div className={styles.quotaContainer}>
          <div className={styles.quotaBox} style={{ minWidth: '220px', borderColor: '#C7D2FE', background: '#FFFFFF' }}>
            <span style={{ fontSize: '12px', fontWeight: 700, color: '#4338CA', textTransform: 'uppercase' }}>Active Exam Status</span>
            <div className={styles.quotaCount} style={{ color: '#4F46E5', fontSize: '20px' }}>
              {activeTest ? activeTest.status.toUpperCase().replace('_', ' ') : 'NO ACTIVE EXAM'}
            </div>
            <span style={{ fontSize: '13px', color: '#475569' }}>
              Completed Exams: <strong>{testHistory.filter(t => t.status === 'completed').length}</strong> | Absent: <strong>{testHistory.filter(t => t.status === 'absent').length}</strong>
            </span>
          </div>
          <div>
            <p style={{ margin: 0, fontSize: '14px', color: '#334155', lineHeight: 1.5 }}>
              <strong>Proctoring & Immutable Deadlines:</strong> Twice weekly (Mondays & Thursdays at 10 AM), an automated proctored examination is instantiated directly from your real internship domain parameters. You must authenticate via webcam facial recognition at Start and Submit (exceeding 40% similarity required). Upon initiating, an immutable <strong>20-minute deadline is locked server-side in real Firestore</strong>, and browser tab visibility exits are strictly audited!
            </p>
          </div>
        </div>
      </section>

      {/* Module 4: Internship Application Status & Chronology Card */}
      {appStatus && (
        <section className={styles.statusTrackerCard}>
          <div className={styles.statusHeader}>
            <span className={styles.statusHeadingText}>Internship Application Status</span>
            <StatusBadge status={appStatus.status} />
          </div>
          <div className={styles.statusGrid}>
            <div className={styles.statusItem}>
              <span className={styles.statusItemLabel}>Practice Domain</span>
              <span className={styles.statusItemVal}>{appStatus.internshipDomain || 'Software Architecture'}</span>
            </div>
            <div className={styles.statusItem}>
              <span className={styles.statusItemLabel}>Joining Schedule</span>
              <span className={styles.statusItemVal}>{appStatus.joiningDate || 'N/A'}</span>
            </div>
            <div className={styles.statusItem}>
              <span className={styles.statusItemLabel}>Completion Date</span>
              <span className={styles.statusItemVal}>{appStatus.completionDate || 'N/A'}</span>
            </div>
            <div className={styles.statusItem}>
              <span className={styles.statusItemLabel}>Ongoing Date</span>
              <span className={styles.statusItemVal}>
                {appStatus.ongoingSince
                  ? new Date(appStatus.ongoingSince).toLocaleDateString()
                  : (appStatus.status === 'Ongoing' ? appStatus.joiningDate : 'Pending Start Date')}
              </span>
            </div>
          </div>

          {/* Module 9 Final Compliance Report Notice Banner */}
          <div style={{ marginTop: '20px', padding: '14px 18px', backgroundColor: appStatus.status === 'Completed' ? '#EBF4EC' : '#FAFBFD', border: `1px solid ${appStatus.status === 'Completed' ? '#2F7A4F' : '#E2E5EA'}`, borderRadius: '4px', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <div style={{ paddingRight: '16px' }}>
              <strong style={{ color: appStatus.status === 'Completed' ? '#1B472D' : '#2B5C8A', display: 'block', fontSize: '15px' }}>
                {appStatus.status === 'Completed' ? 'Internship Officially Completed & Verified' : 'Academic Completion Certification & Final Report'}
              </strong>
              <span style={{ color: appStatus.status === 'Completed' ? '#2C5A3E' : '#5A626A', fontSize: '13px', display: 'inline-block', marginTop: '3px' }}>
                {appStatus.status === 'Completed'
                  ? 'Your tenure schedule has formally concluded. Final compliance percentages and proctored records are permanently archived in the institutional ledger.'
                  : 'View official compliance calculations across attendance, AI work diaries, proctored exams, and meeting quotas.'}
              </span>
            </div>
            <Link
              to="/student/completion-summary"
              style={{
                backgroundColor: appStatus.status === 'Completed' ? '#2F7A4F' : '#2B5C8A',
                color: '#FFFFFF',
                padding: '9px 18px',
                borderRadius: '4px',
                textDecoration: 'none',
                fontWeight: 600,
                fontSize: '13px',
                whiteSpace: 'nowrap',
                border: 'none',
                display: 'inline-block'
              }}
            >
              View Final Report →
            </Link>
          </div>
        </section>
      )}




      {/* Module 5b: Real Attendance History Table */}
      <section className={styles.tableCard}>
        <h2 className={styles.tableHeading}>Formal Attendance Audit</h2>
        <div className={styles.tableResponsive}>
          <table className={styles.ledgerTable}>
            <thead>
              <tr>
                <th>Attendance Date</th>
                <th>Document ID</th>
                <th>Verification Status</th>
                <th>Shared Meeting Pass Used?</th>
                <th>Responded Timestamp</th>
              </tr>
            </thead>
            <tbody>
              {(!attendanceHistory || attendanceHistory.length === 0) ? (
                <tr>
                  <td colSpan="5" className={styles.emptyRow}>No formal daily attendance check-ins logged yet.</td>
                </tr>
              ) : (
                attendanceHistory.map((rec) => {
                  const isPresent = rec.status === 'present';
                  const isExcused = rec.status === 'excused_meeting';
                  const isMissed = rec.status === 'missed';
                  return (
                    <tr key={rec.id}>
                      <td className={styles.dateCell}><strong>{rec.date}</strong></td>
                      <td><code>{rec.id}</code></td>
                      <td>
                        <span className={
                          isPresent ? styles.tagApproved :
                            isExcused ? styles.tagPending :
                              isMissed ? styles.tagRejected : styles.tagPending
                        } style={isExcused ? { backgroundColor: '#E0F2FE', color: '#0369A1' } : {}}>
                          {rec.status ? rec.status.toUpperCase() : 'PENDING'}
                        </span>
                      </td>
                      <td>{isExcused ? 'Yes (1 Pass Deducted)' : 'No (Direct Confirm)'}</td>
                      <td className={styles.numCell}>
                        {rec.respondedAt ? new Date(rec.respondedAt).toLocaleTimeString() : 'Awaiting Response...'}
                      </td>
                    </tr>
                  );
                })
              )}
            </tbody>
          </table>
        </div>
      </section>

      {/* Module 5c: Proctored AI Test History Ledger */}
      <section className={styles.tableCard} style={{ borderTop: '4px solid #4F46E5' }}>
        <h2 className={styles.tableHeading} style={{ color: '#312E81' }}>Proctored AI Test Audit</h2>
        <div className={styles.tableResponsive}>
          <table className={styles.ledgerTable}>
            <thead>
              <tr>
                <th>Exam Date</th>
                <th>Practice Domain</th>
                <th>Verification Status</th>
                <th>Score (Out of 5)</th>
                <th>Tab Switch Audit</th>
                <th>Action / Oversight</th>
              </tr>
            </thead>
            <tbody>
              {(!testHistory || testHistory.length === 0) ? (
                <tr>
                  <td colSpan="6" className={styles.emptyRow}>No proctored AI tests recorded in active semester ledger.</td>
                </tr>
              ) : (
                testHistory.map((test) => {
                  const isComp = test.status === 'completed';
                  const isAbs = test.status === 'absent' || test.status === 'rejected_biometric';
                  return (
                    <tr key={test.id} style={isAbs ? { backgroundColor: '#FEF2F2' } : {}}>
                      <td className={styles.dateCell}><strong>{test.date || 'N/A'}</strong></td>
                      <td><strong>{appStatus?.internshipDomain || 'Software Architecture & Microservices'}</strong></td>
                      <td>
                        <span className={isComp ? styles.tagApproved : isAbs ? styles.tagRejected : styles.tagPending}>
                          {test.status ? test.status.toUpperCase().replace('_', ' ') : 'AWAITING START'}
                        </span>
                      </td>
                      <td className={styles.numCell} style={{ fontSize: '16px', fontWeight: 800, color: isComp ? '#16A34A' : '#DC2626' }}>
                        {isComp ? `${test.score} / 5` : '0 / 5'}
                      </td>
                      <td className={styles.numCell}>
                        <span style={test.tabSwitchCount > 0 ? { color: '#D97706', fontWeight: 700 } : { color: '#64748B' }}>
                          {test.tabSwitchCount || 0} Exits
                        </span>
                      </td>
                      <td>
                        <button
                          type="button"
                          onClick={() => navigate(`/student/test-session/${test.id}`)}
                          style={{ padding: '0.4rem 0.8rem', background: '#4F46E5', color: 'white', border: 'none', borderRadius: '6px', cursor: 'pointer', fontWeight: 600, fontSize: '12px' }}
                        >
                          {isComp ? 'Review Result' : 'Open Session'}
                        </button>
                      </td>
                    </tr>
                  );
                })
              )}
            </tbody>
          </table>
        </div>
      </section>

      {/* Module 5a: Interactive Working-Hour Engagement Check-In Modal */}
      <EngagementPopup
        activePopup={activePopup}
        meetingQuota={meetingQuota}
        onRespond={respondToPopup}
        onClose={() => setActivePopup(null)}
        loading={loadingResponse}
      />

      {/* Module 5b: Formal Daily Attendance Modal */}
      <AttendanceCheckIn
        activeAttendance={activeAttendance}
        meetingQuota={meetingQuota}
        onRespond={respondToAttendance}
        onClose={() => setActiveAttendance(null)}
        loading={loadingAttendance}
      />
    </div>
  );
};

export default StudentDashboard;
