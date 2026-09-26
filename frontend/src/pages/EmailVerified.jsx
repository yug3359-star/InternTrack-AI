import React from 'react';
import { Link } from 'react-router-dom';
import styles from './Login.module.css';

const EmailVerified = () => {
  return (
    <main className={styles.authContainer}>
      <section className={styles.loginCard} style={{ textAlign: 'center', padding: '40px' }}>
        <header className={styles.portalHeader}>
          <h1 className={styles.portalTitle}>Email Verified</h1>
          <p className={styles.portalSubtitle} style={{ marginTop: '16px' }}>
            Your email has been verified. You can now log in to the InternTrack AI platform.
          </p>
        </header>

        <div className={styles.actionRow} style={{ marginTop: '24px' }}>
          <Link to="/login" className={styles.submitBtn} style={{ display: 'inline-block', textDecoration: 'none' }}>
            Go to Login
          </Link>
        </div>
      </section>
    </main>
  );
};

export default EmailVerified;
