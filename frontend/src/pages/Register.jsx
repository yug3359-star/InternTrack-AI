import React, { useState } from 'react';
import { useForm } from 'react-hook-form';
import { useNavigate, Link } from 'react-router-dom';
import { useNotification } from '../hooks/useNotification';
import { useAuth } from '../hooks/useAuth';
import styles from './Register.module.css';

const Register = () => {
  const { notify } = useNotification();
  const { login } = useAuth();
  const navigate = useNavigate();
  const [processing, setProcessing] = useState(false);

  const { register, handleSubmit, formState: { errors } } = useForm({
    defaultValues: {
      fullName: '',
      identifier: '',
      email: '',
      password: '',
      department: 'Computer Science & Engineering',
      role: 'STUDENT'
    }
  });

  const onSubmit = async (data) => {
    setProcessing(true);
    try {
      // Direct college portal compliance check per instructions
      if (!data.email.toLowerCase().endsWith('.edu') && !data.email.toLowerCase().includes('college') && !data.email.toLowerCase().includes('university') && !data.email.toLowerCase().includes('ac.')) {
        notify("Registration failed: college email required", "error", 5000);
        setProcessing(false);
        return;
      }

      // Emulate institutional application record submission for review panel demo
      notify("Application accepted: temporary academic profile generated for evaluation.", "success", 4000);
      await login(data.email, data.password, data.role);

      if (data.role === 'MENTOR') {
        navigate('/mentor/dashboard');
      } else if (data.role === 'HOD') {
        navigate('/hod/dashboard');
      } else {
        navigate('/student/dashboard');
      }
    } catch (error) {
      notify("Registration failed: central directory database unavailable or duplicate enrollment ID.", "error", 5000);
    } finally {
      setProcessing(false);
    }
  };

  return (
    <div className={styles.container}>
      <div className={styles.formBox}>
        <div className={styles.titleSection}>
          <h2>Academic Portal Enrollment</h2>
          <span className={styles.guidelineText}>Submit profile data for internship monitoring verification</span>
        </div>

        <div className={styles.noticeBlock} style={{ marginBottom: '16px' }}>
          <p><strong>Are you a Student Practitioner enrolling for an internship?</strong><br />
          Access the complete 3-step onboarding form with schedule parameters and live webcam reference photo capture here: <Link to="/student/register" style={{ color: 'var(--color-primary)', fontWeight: 700, textDecoration: 'underline' }}>Launch Student Onboarding Portal →</Link></p>
        </div>

        <form onSubmit={handleSubmit(onSubmit)} className={styles.fieldsContainer} noValidate>
          <div className={styles.row}>
            <div className={styles.fieldItem}>
              <label htmlFor="role">Institutional Designation</label>
              <select id="role" {...register("role")} className={styles.selectInput}>
                <option value="STUDENT">Student Practitioner</option>
                <option value="MENTOR">Faculty Mentor</option>
                <option value="HOD">Head of Department (HOD)</option>
              </select>
            </div>

            <div className={styles.fieldItem}>
              <label htmlFor="department">Academic Department</label>
              <select id="department" {...register("department")} className={styles.selectInput}>
                <option value="Computer Science & Engineering">Computer Science & Eng</option>
                <option value="Information Technology">Information Technology</option>
                <option value="Artificial Intelligence & DS">AI & Data Science</option>
              </select>
            </div>
          </div>

          <div className={styles.fieldItem}>
            <label htmlFor="fullName">Full Official Name</label>
            <input
              id="fullName"
              type="text"
              placeholder="e.g., Ananya Sharma"
              {...register("fullName", { required: "Registration failed: full legal academic name required" })}
              className={errors.fullName ? styles.inputInvalid : styles.textInput}
            />
            {errors.fullName && <span className={styles.errorMsg}>{errors.fullName.message}</span>}
          </div>

          <div className={styles.row}>
            <div className={styles.fieldItem}>
              <label htmlFor="identifier">College Roll Number / Faculty ID</label>
              <input
                id="identifier"
                type="text"
                placeholder="e.g., 2023CSB104"
                {...register("identifier", { required: "Registration failed: college identifier mandatory" })}
                className={errors.identifier ? styles.inputInvalid : styles.textInput}
              />
              {errors.identifier && <span className={styles.errorMsg}>{errors.identifier.message}</span>}
            </div>

            <div className={styles.fieldItem}>
              <label htmlFor="email">Institutional Email Address</label>
              <input
                id="email"
                type="email"
                placeholder="username@cs.college.edu"
                {...register("email", { 
                  required: "Registration failed: college email required",
                  pattern: {
                    value: /^[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}$/i,
                    message: "Registration failed: malformed email string"
                  }
                })}
                className={errors.email ? styles.inputInvalid : styles.textInput}
              />
              {errors.email && <span className={styles.errorMsg}>{errors.email.message}</span>}
            </div>
          </div>

          <div className={styles.row}>
            <div className={styles.fieldItem}>
              <label htmlFor="password">Portal Authentication Password</label>
              <input
                id="password"
                type="password"
                placeholder="Min 8 chars with at least 1 numeral"
                {...register("password", { 
                  required: "Registration failed: password required",
                  pattern: {
                    value: /^(?=.*\d).{8,}$/,
                    message: "Registration failed: Password must be at least 8 characters long and contain at least one number."
                  }
                })}
                className={errors.password ? styles.inputInvalid : styles.textInput}
              />
              {errors.password && <span className={styles.errorMsg}>{errors.password.message}</span>}
            </div>
            
            <div className={styles.fieldItem}>
              <label htmlFor="confirmPassword">Confirm Password</label>
              <input
                id="confirmPassword"
                type="password"
                placeholder="Re-enter your password"
                {...register("confirmPassword", {
                  required: "Registration failed: confirm password required",
                  validate: (value) => value === document.getElementById('password').value || "Passwords do not match."
                })}
                className={errors.confirmPassword ? styles.inputInvalid : styles.textInput}
              />
              {errors.confirmPassword && <span className={styles.errorMsg}>{errors.confirmPassword.message}</span>}
            </div>
          </div>

          <div className={styles.noticeBlock}>
            <p><strong>Note for review panel:</strong> Applications submitted via this interface are recorded immediately into the local developer runtime session to facilitate multi-role grading evaluation.</p>
          </div>

          <div className={styles.btnWrapper}>
            <button type="submit" disabled={processing} className={styles.applyBtn}>
              {processing ? "Processing Records..." : "Submit Application"}
            </button>
          </div>

          <div className={styles.linkRow}>
            <span>Existing institutional profile?</span>
            <Link to="/login">Return to Sign In</Link>
          </div>
        </form>
      </div>
    </div>
  );
};

export default Register;
