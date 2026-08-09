import React from 'react';
import { useNavigate } from 'react-router-dom';
import styles from './PrivacyNotice.module.css';

const PrivacyNotice = () => {
  const navigate = useNavigate();

  return (
    <div className={styles.container}>
      <main className={styles.documentPanel}>
        <header className={styles.header}>
          <h1 className={styles.title}>Institutional Privacy Notice & Data Governance Policy</h1>
          <div className={styles.subtitle}>Department of Computer Science & Engineering — Academic Compliance Division</div>
        </header>

        <div className={styles.metaRow}>
          <span className={styles.metaItem}><strong>Policy Classification:</strong> Institutional Surveillance & GDPR/DPDP Compliant</span>
          <span className={styles.metaItem}><strong>Effective:</strong> Active Rollout</span>
        </div>

        {/* SECTION 1: WHAT DATA IS COLLECTED */}
        <section className={styles.section}>
          <h2 className={styles.sectionTitle}>
            <span className={styles.sectionNumber}>1.</span> What Data is Collected
          </h2>
          <p className={styles.bodyText}>
            To preserve academic integrity without intruding upon personal privacy, InternTrack AI applies strictly bounded optical and textual data extraction during active student internships:
          </p>
          <ul className={styles.dataList}>
            <li className={styles.dataItem}>
              <span className={styles.dataItemTitle}>Biometric Facial Reference & Check-In Snapshots</span>
              <span className={styles.dataItemDesc}>
                During preliminary registration, a single portrait reference photograph is captured and stored securely in Google Cloud Storage. During student-declared working hours only, periodic random engagement check-ins and proctored automated tests activate your webcam to capture instantaneous frames for automated AWS Rekognition optical comparison.
              </span>
            </li>
            <li className={styles.dataItem}>
              <span className={styles.dataItemTitle}>Daily Engineering Activity Diaries</span>
              <span className={styles.dataItemDesc}>
                Textual summaries submitted into the work log ledger are evaluated via real-time Natural Language Processing (OpenAI / Claude LLM pipelines) to verify engineering domain relevance and academic competency continuity.
              </span>
            </li>
            <li className={styles.dataItem}>
              <span className={styles.dataItemTitle}>Weekly Proctored Exam Telemetry</span>
              <span className={styles.dataItemDesc}>
                Responses to AI-generated topical evaluation tests, time taken per item, and verification confidence metrics are recorded to generate automated completion certificates.
              </span>
            </li>
          </ul>
        </section>

        {/* SECTION 2: RETENTION SCHEDULE */}
        <section className={styles.section}>
          <h2 className={styles.sectionTitle}>
            <span className={styles.sectionNumber}>2.</span> Data Retention Rules & Purge Schedules
          </h2>
          <p className={styles.bodyText}>
            In adherence to institutional data minimization principles, recorded telemetry follows explicit lifetime boundaries:
          </p>
          <table className={styles.retentionTable}>
            <thead>
              <tr>
                <th>Data Category</th>
                <th>Retention Window</th>
                <th>Automated Disposal Policy</th>
              </tr>
            </thead>
            <tbody>
              <tr>
                <td>Accepted Daily Diaries</td>
                <td><span className={styles.tagNormal}>2 Weeks (14 Days)</span></td>
                <td>Retained solely for LLM historical continuity evaluation; purged automatically from operational indexes after 14 days.</td>
              </tr>
              <tr>
                <td>Routine Biometric Check-Ins (&gt;75% Match)</td>
                <td><span className={styles.tagNormal}>2 Weeks (14 Days)</span></td>
                <td>Temporary storage in encrypted cloud buckets; automatically wiped once attendance credit is committed to ledger.</td>
              </tr>
              <tr>
                <td>Suspicious Diaries &amp; AI Anomaly Flags</td>
                <td><span className={styles.tagPermanent}>Indefinite (Until HOD Audit)</span></td>
                <td>Preserved permanently within immutable departmental investigation records until formally adjudicated by faculty authorities.</td>
              </tr>
              <tr>
                <td>Borderline Biometric Snapshots (&lt;75% Match)</td>
                <td><span className={styles.tagPermanent}>Indefinite (Audit Ledger)</span></td>
                <td>Retained for academic council review to document suspected imposter substitution or attendance fraud.</td>
              </tr>
            </tbody>
          </table>
        </section>

        {/* SECTION 3: WHO CAN SEE YOUR DATA */}
        <section className={styles.section}>
          <h2 className={styles.sectionTitle}>
            <span className={styles.sectionNumber}>3.</span> Institutional Access Control & Role Boundaries
          </h2>
          <p className={styles.bodyText}>
            Access to student personal identifiers and surveillance artifacts is strictly restricted by cryptographic Role-Based Access Control (RBAC) claims enforced simultaneously at the API layer and database security level:
          </p>
          <div className={styles.accessGrid}>
            <div className={styles.accessCard}>
              <span className={styles.accessRole}>Faculty Mentor Access</span>
              <div className={styles.accessDetails}>
                Mentors are <strong>never permitted to browse live video streams or routine accepted snapshots</strong>. Mentors only receive one-tap review items when AWS Rekognition scores fall in the borderline interval (40%–75%) or when an AI work log is rejected for being off-topic.
              </div>
            </div>
            <div className={styles.accessCard}>
              <span className={styles.accessRole}>HOD / Departmental Admin Access</span>
              <div className={styles.accessDetails}>
                The Head of Department retains oversight over aggregated department completion summaries, student warning history records (yellow/red cards), and formal identity fraud escalations. HODs cannot export individual student portraits without explicit auditing authorization.
              </div>
            </div>
          </div>

          <div className={styles.noticeBox}>
            <strong>Zero External Third-Party Sharing:</strong> Student records, photographic likenesses, and academic performance profiles are never traded, advertised, or exported outside the official institutional Google Cloud and approved cloud LLM evaluation conduits.
          </div>
        </section>

        <footer className={styles.footer}>
          <span>InternTrack AI Institutional Security Documentation — Published for College Rollout</span>
          <button
            type="button"
            onClick={() => navigate(-1)}
            className={styles.backButton}
          >
            ← Return to Previous Page
          </button>
        </footer>
      </main>
    </div>
  );
};

export default PrivacyNotice;
