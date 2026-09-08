import React from 'react';
import { Link } from 'react-router-dom';

const VerificationPending = () => {
  return (
    <div style={{ minHeight: '80vh', display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', backgroundColor: 'var(--color-bg)' }}>
      <div style={{ backgroundColor: 'var(--color-surface)', padding: '40px', borderRadius: '8px', border: '1px solid var(--color-border)', textAlign: 'center', maxWidth: '600px', boxShadow: '0 4px 12px rgba(0,0,0,0.05)' }}>
        <h1 style={{ color: 'var(--color-text-primary)', fontFamily: 'var(--font-serif)', marginBottom: '16px', fontSize: '28px' }}>
          Verification Pending
        </h1>
        <p style={{ color: 'var(--color-text-secondary)', fontSize: '16px', lineHeight: '1.6', marginBottom: '24px' }}>
          Your internship application is currently under review by the Head of Department (HOD). 
          You will gain access to the Student Dashboard once your application is formally approved.
        </p>
        <div style={{ padding: '16px', backgroundColor: '#F0F9FF', color: '#0369A1', borderRadius: '6px', fontSize: '14px', fontWeight: '500', marginBottom: '24px', border: '1px solid #BAE6FD' }}>
          Please check back later or contact your departmental coordinator for updates.
        </div>
        <Link to="/login" style={{ display: 'inline-block', backgroundColor: 'var(--color-primary)', color: 'white', padding: '10px 24px', borderRadius: '4px', textDecoration: 'none', fontWeight: '600', fontSize: '14px', transition: 'background-color 0.2s ease' }}>
          Return to Sign In
        </Link>
      </div>
    </div>
  );
};

export default VerificationPending;
