import React, { useState } from 'react';
import { useForm } from 'react-hook-form';
import { useNavigate, Link } from 'react-router-dom';
import { useAuth } from '../hooks/useAuth';
import { useNotification } from '../hooks/useNotification';
import styles from './Login.module.css';

const Login = () => {
  const { login, logout } = useAuth();
  const { notify } = useNotification();
  const navigate = useNavigate();
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [unverifiedEmail, setUnverifiedEmail] = useState(null);
  const [resending, setResending] = useState(false);

  const { register, handleSubmit, formState: { errors } } = useForm({
    defaultValues: {
      email: 'student.s26@cs.college.edu',
      password: 'password123',
      role: 'STUDENT'
    }
  });

  const handleResend = async () => {
    if (!unverifiedEmail) return;
    setResending(true);
    try {
      const { resendVerificationEmail } = await import('../services/api');
      await resendVerificationEmail(unverifiedEmail);
      notify("Verification email sent! Please check your inbox.", "success");
    } catch (err) {
      notify("Failed to resend verification email. Please try again.", "error");
    } finally {
      setResending(false);
    }
  };

  const onSubmit = async (data) => {
    setIsSubmitting(true);
    setUnverifiedEmail(null);
    try {
      // if (!data.email.includes('.edu') && !data.email.includes('@')) {
      //   notify("Authentication failed: institutional college email address required.", "error", 5000);
      //   setIsSubmitting(false);
      //   return;
      // }

      const authResult = await login(data.email, data.password, data.role);
      
      // Check if email is verified for live Firebase auth users
      if (authResult?.user && authResult.user.emailVerified === false && !authResult.user.uid.includes('dev-')) {
        await logout();
        setUnverifiedEmail(data.email);
        notify("Please verify your email address before logging in.", "error", 7000);
        setIsSubmitting(false);
        return;
      }

      notify("Authentication verified: accessing academic departmental records.", "success");

      // Redirect to assigned institutional role dashboard
      if (data.role === 'MENTOR') {
        navigate('/mentor/dashboard');
      } else if (data.role === 'HOD') {
        navigate('/hod/dashboard');
      } else {
        try {
          const { getStudentStatus } = await import('../services/api');
          const studentUid = authResult?.user?.uid || 'dev-stud-106'; // fallback to mock uid
          const statusData = await getStudentStatus(studentUid);
          if (statusData && (statusData.status === 'Applied' || statusData.status === 'Under Review')) {
            navigate('/student/verification-pending');
          } else {
            navigate('/student/dashboard');
          }
        } catch (e) {
          navigate('/student/dashboard');
        }
      }
    } catch (err) {
      notify(err.message || "Sign-in rejected: institutional credentials unrecognized or directory session timed out.", "error", 5000);
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <main className={styles.authContainer}>
      <section className={styles.loginCard}>
        <header className={styles.portalHeader}>
          <h1 className={styles.portalTitle}>Sign In</h1>
          <p className={styles.portalSubtitle}>Internship Monitoring System</p>
        </header>

        <form onSubmit={handleSubmit(onSubmit)} className={styles.formGrid} noValidate>
          <div className={styles.fieldGroup}>
            <label htmlFor="role" className={styles.fieldLabel}>Role</label>
            <select
              id="role"
              {...register("role", { required: "Role selection is mandatory for route designation." })}
              className={styles.inputControl}
            >
              <option value="STUDENT">Student Practitioner</option>
              <option value="MENTOR">Faculty Mentor</option>
              <option value="HOD">Head of Department (HOD)</option>
            </select>
          </div>

          <div className={styles.fieldGroup}>
            <label htmlFor="email" className={styles.fieldLabel}>College Email Address</label>
            <input
              id="email"
              type="email"
              placeholder="e.g. rollnumber@cs.college.edu"
              {...register("email", {
                required: "Authentication failed: college email required",
                pattern: {
                  value: /^[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}$/i,
                  message: "Authentication failed: invalid email address syntax"
                }
              })}
              className={errors.email ? styles.inputError : styles.inputControl}
            />
            {errors.email && <span className={styles.errorText}>{errors.email.message}</span>}
          </div>

          <div className={styles.fieldGroup}>
            <label htmlFor="password" className={styles.fieldLabel}>Password</label>
            <input
              id="password"
              type="password"
              {...register("password", { required: "Directory authentication requires password." })}
              className={errors.password ? styles.inputError : styles.inputControl}
            />
            {errors.password && <span className={styles.errorText}>{errors.password.message}</span>}
          </div>

          <div className={styles.actionRow}>
            <button
              type="submit"
              disabled={isSubmitting || resending}
              className={styles.submitBtn}
            >
              {isSubmitting ? "Verifying Credentials..." : "Submit Credentials"}
            </button>
          </div>
          
          {unverifiedEmail && (
            <div className={styles.actionRow} style={{ marginTop: '10px' }}>
              <button
                type="button"
                onClick={handleResend}
                disabled={resending || isSubmitting}
                className={styles.submitBtn}
                style={{ backgroundColor: '#2B5C8A', border: '1px solid #1c3c5e' }}
              >
                {resending ? "Sending..." : "Resend Verification Email"}
              </button>
            </div>
          )}

          <footer className={styles.cardFooter}>
            <span>New user?</span>
            <Link to="/register" className={styles.navLink}>Sign-up</Link>
          </footer>
        </form>
      </section>
    </main>
  );
};

export default Login;
