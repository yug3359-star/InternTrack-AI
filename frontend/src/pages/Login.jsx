import React, { useState } from 'react';
import { useForm } from 'react-hook-form';
import { useNavigate, Link } from 'react-router-dom';
import { useAuth } from '../hooks/useAuth';
import { useNotification } from '../hooks/useNotification';
import styles from './Login.module.css';

const Login = () => {
  const { login } = useAuth();
  const { notify } = useNotification();
  const navigate = useNavigate();
  const [isSubmitting, setIsSubmitting] = useState(false);
  
  const { register, handleSubmit, formState: { errors } } = useForm({
    defaultValues: {
      email: 'student.s26@cs.college.edu',
      password: 'password123',
      role: 'STUDENT'
    }
  });

  const onSubmit = async (data) => {
    setIsSubmitting(true);
    try {
      if (!data.email.includes('.edu') && !data.email.includes('@')) {
        notify("Authentication failed: institutional college email address required.", "error", 5000);
        setIsSubmitting(false);
        return;
      }

      await login(data.email, data.password, data.role);
      notify("Authentication verified: accessing academic departmental records.", "success");
      
      // Redirect to assigned institutional role dashboard
      if (data.role === 'MENTOR') {
        navigate('/mentor/dashboard');
      } else if (data.role === 'HOD') {
        navigate('/hod/dashboard');
      } else {
        navigate('/student/dashboard');
      }
    } catch (err) {
      notify("Sign-in rejected: institutional credentials unrecognized or directory session timed out.", "error", 5000);
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <main className={styles.authContainer}>
      <section className={styles.loginCard}>
        <header className={styles.portalHeader}>
          <h1 className={styles.portalTitle}>Institutional Sign In</h1>
          <p className={styles.portalSubtitle}>Academic Internship Compliance System — Central Directory Access</p>
        </header>

        <form onSubmit={handleSubmit(onSubmit)} className={styles.formGrid} noValidate>
          <div className={styles.fieldGroup}>
            <label htmlFor="role" className={styles.fieldLabel}>Assigned Academic Role</label>
            <select
              id="role"
              {...register("role", { required: "Role selection is mandatory for route designation." })}
              className={styles.inputControl}
            >
              <option value="STUDENT">Student Practitioner</option>
              <option value="MENTOR">Faculty Advisor / Mentor</option>
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
            <label htmlFor="password" className={styles.fieldLabel}>Directory Password</label>
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
              disabled={isSubmitting}
              className={styles.submitBtn}
            >
              {isSubmitting ? "Verifying Credentials..." : "Submit Credentials"}
            </button>
          </div>

          <footer className={styles.cardFooter}>
            <span>Unregistered user?</span>
            <Link to="/register" className={styles.navLink}>Submit Registration Inquiry</Link>
          </footer>
        </form>
      </section>
    </main>
  );
};

export default Login;
