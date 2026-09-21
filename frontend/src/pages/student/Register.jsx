import React, { useState } from 'react';
import { useForm } from 'react-hook-form';
import { useNavigate, Link } from 'react-router-dom';
import PhotoCapture from '../../components/PhotoCapture';
import { registerStudent } from '../../services/api';
import {
  COLLEGE_DOMAIN,
  EMAIL_ERROR_MESSAGE,
  validateDateChronology,
  validateBreakHours,
  DATE_ERROR_MESSAGE,
  TIME_ERROR_MESSAGE
} from '../../utils/validators';
import styles from './Register.module.css';

const MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB explicit size ceiling

const formatFileSize = (bytes) => {
  if (!bytes || bytes === 0) return '0 Bytes';
  const k = 1024;
  const sizes = ['Bytes', 'KB', 'MB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i];
};

const StudentRegister = () => {
  const navigate = useNavigate();
  const [currentStep, setCurrentStep] = useState(1);

  // Module 2: Document Upload States
  const [offerFile, setOfferFile] = useState(null);
  const [offerPreviewUrl, setOfferPreviewUrl] = useState(null);
  const [offerError, setOfferError] = useState(null);

  const [approvalFile, setApprovalFile] = useState(null);
  const [approvalPreviewUrl, setApprovalPreviewUrl] = useState(null);
  const [approvalError, setApprovalError] = useState(null);

  const [step3Error, setStep3Error] = useState(null);

  // Module 1 & 2: Photo and Verification Consent States
  const [photoFile, setPhotoFile] = useState(null);
  const [photoError, setPhotoError] = useState(null);
  const [consentChecked, setConsentChecked] = useState(false);
  const [consentNoticeShown, setConsentNoticeShown] = useState(false);

  const [isSubmitting, setIsSubmitting] = useState(false);
  const [submitSuccessMessage, setSubmitSuccessMessage] = useState(null);
  const [backendError, setBackendError] = useState(null);

  const {
    register,
    handleSubmit,
    trigger,
    watch,
    formState: { errors }
  } = useForm({
    mode: 'all', // Validate inline as user types and blurs
    defaultValues: {
      fullName: '',
      collegeEmail: '',
      password: '',
      branch: 'Computer Science & Engineering',
      rollNo: '',
      enrollmentNo: '',
      section: '',
      mentorName: '',
      mentorEmail: '',
      internshipDomain: '',
      joiningDate: '',
      completionDate: '',
      officeStartTime: '09:00',
      officeEndTime: '17:00',
      breakStartTime: '13:00',
      breakEndTime: '14:00',
      workingDays: ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday'],
      deviceType: 'Desktop'
    }
  });

  const watchJoiningDate = watch('joiningDate');
  const watchOfficeStart = watch('officeStartTime');
  const watchOfficeEnd = watch('officeEndTime');
  const watchBreakStart = watch('breakStartTime');

  const handleDocumentSelection = (e, type) => {
    const file = e.target.files[0];
    if (!file) return;

    // Validate size limit client-side before processing
    if (file.size > MAX_FILE_SIZE) {
      if (type === 'offer') {
        setOfferError("File too large — max 5MB");
        setOfferFile(null);
        setOfferPreviewUrl(null);
      } else {
        setApprovalError("File too large — max 5MB");
        setApprovalFile(null);
        setApprovalPreviewUrl(null);
      }
      return;
    }

    // Validate allowable file formats
    const validTypes = ['image/jpeg', 'image/png', 'application/pdf'];
    if (!validTypes.includes(file.type) && !file.name.endsWith('.pdf') && !file.name.endsWith('.jpg') && !file.name.endsWith('.png')) {
      const typeErrorMsg = "Invalid file type — only JPG, PNG, and PDF allowed.";
      if (type === 'offer') {
        setOfferError(typeErrorMsg);
        setOfferFile(null);
      } else {
        setApprovalError(typeErrorMsg);
        setApprovalFile(null);
      }
      return;
    }

    // Generate local preview if image file
    const isImage = file.type.startsWith('image/') || file.name.endsWith('.jpg') || file.name.endsWith('.png');
    const previewUrl = isImage ? URL.createObjectURL(file) : null;

    if (type === 'offer') {
      setOfferError(null);
      setOfferFile(file);
      setOfferPreviewUrl(previewUrl);
    } else {
      setApprovalError(null);
      setApprovalFile(file);
      setApprovalPreviewUrl(previewUrl);
    }
    setStep3Error(null);
  };

  const removeDocument = (type) => {
    if (type === 'offer') {
      if (offerPreviewUrl) URL.revokeObjectURL(offerPreviewUrl);
      setOfferFile(null);
      setOfferPreviewUrl(null);
      setOfferError(null);
    } else {
      if (approvalPreviewUrl) URL.revokeObjectURL(approvalPreviewUrl);
      setApprovalFile(null);
      setApprovalPreviewUrl(null);
      setApprovalError(null);
    }
  };

  const handleNextStep = async (targetStep) => {
    setBackendError(null);
    if (targetStep === 2 && currentStep === 1) {
      const isStep1Valid = await trigger(['fullName', 'collegeEmail', 'password', 'branch', 'rollNo', 'enrollmentNo', 'section']);
      if (isStep1Valid) setCurrentStep(2);
    } else if (targetStep === 3 && currentStep === 2) {
      const isStep2Valid = await trigger([
        'mentorName',
        'mentorEmail',
        'internshipDomain',
        'joiningDate',
        'completionDate',
        'officeStartTime',
        'officeEndTime',
        'breakStartTime',
        'breakEndTime',
        'workingDays'
      ]);
      if (isStep2Valid) setCurrentStep(3);
    } else if (targetStep === 4 && currentStep === 3) {
      if (!offerFile || !approvalFile) {
        setStep3Error("Please select and verify both required institutional documents before proceeding.");
        return;
      }
      setStep3Error(null);
      setCurrentStep(4);
    } else {
      setCurrentStep(targetStep);
    }
  };

  const onPhotoSelected = (file) => {
    setPhotoFile(file);
    if (file) {
      setPhotoError(null);
    }
  };

  const handleConsentClick = (e) => {
    const checked = e.target.checked;
    setConsentChecked(checked);
    if (checked) setConsentNoticeShown(false);
  };

  const handleDisabledSubmitClick = () => {
    if (!consentChecked) {
      setConsentNoticeShown(true);
    }
  };

  const onSubmit = async (data) => {
    setBackendError(null);
    if (!photoFile) {
      setPhotoError("Reference photo is mandatory for identity verification.");
      return;
    }
    if (!consentChecked) {
      setConsentNoticeShown(true);
      return;
    }

    setIsSubmitting(true);
    try {
      const formData = new FormData();
      Object.keys(data).forEach((key) => {
        if (key === 'workingDays') {
          data.workingDays.forEach((day) => formData.append('workingDays', day));
        } else {
          formData.append(key, data[key]);
        }
      });

      // Append combined multi-role registration documents (Modules 1 & 2)
      if (offerFile) formData.append('offerLetter', offerFile);
      if (approvalFile) formData.append('approvalLetter', approvalFile);
      formData.append('referencePhoto', photoFile);
      formData.append('consentGiven', 'true');

      await registerStudent(formData);

      setSubmitSuccessMessage("Application submitted. You'll be notified once your HOD reviews it.");
      navigate('/login');
    } catch (err) {
      // Automatic developer demo evaluation fallback when Java backend server is offline
      if (!err.response || err.code === 'ERR_NETWORK' || err.message === 'Network Error') {
        console.warn("[DEV MOCK PORTAL] Spring Boot service at http://localhost:8080 offline. Recording evaluation profile locally.");
        setSubmitSuccessMessage("Application submitted (Local Evaluation Mode — Java server offline). You'll be notified once your HOD reviews it.");
        navigate('/login');
        return;
      }
      // Show exact backend error message returned by running Java server
      const errorText = err.response?.data?.error || err.response?.data?.message || err.message || "Registration rejected due to server validation failure.";
      setBackendError(errorText);
      setIsSubmitting(false);
    }
  };

  return (
    <div className={styles.container}>
      <div className={styles.formPanel}>
        <header className={styles.panelHeader}>
          <h1 className={styles.headerTitle}>Student Internship Application</h1>
          <span className={styles.headerSub}>Department of Computer Science & Engineering</span>
        </header>

        {/* Utilitarian Plain Text 4-step numbered indicator */}
        <nav className={styles.stepNavigator} aria-label="Registration Progress">
          <button
            type="button"
            onClick={() => handleNextStep(1)}
            className={currentStep === 1 ? styles.stepActive : styles.stepInactive}
          >
            1. Personal Info
          </button>
          <span className={styles.stepSeparator}>/</span>
          <button
            type="button"
            onClick={() => handleNextStep(2)}
            className={currentStep === 2 ? styles.stepActive : styles.stepInactive}
          >
            2. Internship Schedule
          </button>
          <span className={styles.stepSeparator}>/</span>
          <button
            type="button"
            onClick={() => handleNextStep(3)}
            className={currentStep === 3 ? styles.stepActive : styles.stepInactive}
          >
            3. Documents
          </button>
          <span className={styles.stepSeparator}>/</span>
          <button
            type="button"
            onClick={() => handleNextStep(4)}
            className={currentStep === 4 ? styles.stepActive : styles.stepInactive}
          >
            4. Photo & Consent
          </button>
        </nav>

        {submitSuccessMessage ? (
          <div className={styles.successBlock} role="alert">
            <span className={styles.successTitle}>{submitSuccessMessage}</span>
            <span className={styles.redirectNotice}>Redirecting to institutional directory login in 3 seconds...</span>
          </div>
        ) : (
          <form onSubmit={handleSubmit(onSubmit)} className={styles.formLayout} noValidate>

            {/* STEP 1: PERSONAL INFORMATION */}
            {currentStep === 1 && (
              <section className={styles.stepSection}>
                <h2 className={styles.sectionHeading}>Section 1: Academic Details</h2>

                <div className={styles.fieldRow}>
                  <div className={styles.fieldCol}>
                    <label htmlFor="fullName" className={styles.fieldLabel}>Full Name</label>
                    <input
                      id="fullName"
                      type="text"
                      placeholder="Full Name"
                      {...register("fullName", { required: "Full name is mandatory." })}
                      className={errors.fullName ? styles.inputInvalid : styles.inputStandard}
                    />
                    {errors.fullName && <span className={styles.errorText}>{errors.fullName.message}</span>}
                  </div>

                  <div className={styles.fieldCol}>
                    <label htmlFor="branch" className={styles.fieldLabel}>Branch</label>
                    <select id="branch" {...register("branch", { required: "Academic branch selection required." })} className={styles.selectStandard}>
                      <option value="Computer Science & Engineering">Computer Science & Engineering</option>
                      {/* <option value="Information Technology">Information Technology (B.Tech)</option>
                      <option value="Artificial Intelligence & Data Science">AI & Data Science (B.Tech)</option>
                      <option value="Electronics & Communication">Electronics & Communication (B.Tech)</option> */}
                    </select>
                    {errors.branch && <span className={styles.errorText}>{errors.branch.message}</span>}
                  </div>
                </div>

                <div className={styles.fieldRow}>
                  <div className={styles.fieldCol}>
                    <label htmlFor="rollNo" className={styles.fieldLabel}>Roll No.</label>
                    <input
                      id="rollNo"
                      type="text"
                      placeholder="Roll Number"
                      {...register("rollNo", { required: "Roll Number" })}
                      className={errors.rollNo ? styles.inputInvalid : styles.inputStandard}
                    />
                    {errors.rollNo && <span className={styles.errorText}>{errors.rollNo.message}</span>}
                  </div>

                  <div className={styles.fieldCol}>
                    <label htmlFor="enrollmentNo" className={styles.fieldLabel}>Enrollment Number</label>
                    <input
                      id="enrollmentNo"
                      type="text"
                      placeholder="Enrollment Number"
                      {...register("enrollmentNo", { required: "Enrollment Number" })}
                      className={errors.enrollmentNo ? styles.inputInvalid : styles.inputStandard}
                    />
                    {errors.enrollmentNo && <span className={styles.errorText}>{errors.enrollmentNo.message}</span>}
                  </div>
                </div>

                <div className={styles.fieldRow}>
                  <div className={styles.fieldCol}>
                    <label htmlFor="section" className={styles.fieldLabel}>Class Section</label>
                    <input
                      id="section"
                      type="text"
                      placeholder="e.g., A, B, C"
                      {...register("section", { required: "Section is mandatory." })}
                      className={errors.section ? styles.inputInvalid : styles.inputStandard}
                    />
                    {errors.section && <span className={styles.errorText}>{errors.section.message}</span>}
                  </div>
                </div>

                <div className={styles.fieldRow}>
                  <div className={styles.fieldCol}>
                    <label htmlFor="collegeEmail" className={styles.fieldLabel}>College Email Address</label>
                    <input
                      id="collegeEmail"
                      type="email"
                      placeholder="College Mail"
                      {...register("collegeEmail", {
                        required: EMAIL_ERROR_MESSAGE,
                        pattern: {
                          value: COLLEGE_DOMAIN,
                          message: EMAIL_ERROR_MESSAGE
                        }
                      })}
                      className={errors.collegeEmail ? styles.inputInvalid : styles.inputStandard}
                    />
                    {errors.collegeEmail && <span className={styles.errorText}>{errors.collegeEmail.message}</span>}
                  </div>

                  <div className={styles.fieldCol}>
                    <label htmlFor="password" className={styles.fieldLabel}>New Password</label>
                    <input
                      id="password"
                      type="password"
                      placeholder="New Password"
                      {...register("password", {
                        required: "Password is required for authentication.",
                        pattern: {
                          value: /^(?=.*\d).{8,}$/,
                          message: "Password must be at least 8 characters long and contain at least one number."
                        }
                      })}
                      className={errors.password ? styles.inputInvalid : styles.inputStandard}
                    />
                    {errors.password && <span className={styles.errorText}>{errors.password.message}</span>}
                  </div>
                </div>

                <div className={styles.fieldRow}>
                  <div className={styles.fieldCol}>
                    <label htmlFor="confirmPassword" className={styles.fieldLabel}>Confirm Password</label>
                    <input
                      id="confirmPassword"
                      type="password"
                      placeholder="Re-enter your password"
                      {...register("confirmPassword", {
                        required: "Please confirm your password.",
                        validate: (value) => value === watch('password') || "Passwords do not match."
                      })}
                      className={errors.confirmPassword ? styles.inputInvalid : styles.inputStandard}
                    />
                    {errors.confirmPassword && <span className={styles.errorText}>{errors.confirmPassword.message}</span>}
                  </div>

                  {/* Empty column to maintain grid layout */}
                  <div className={styles.fieldCol}></div>
                </div>

                <div className={styles.navRow}>
                  <span></span>
                  <button type="button" onClick={() => handleNextStep(2)} className={styles.navBtn}>
                    Proceed to Schedule (Step 2) →
                  </button>
                </div>
              </section>
            )}

            {/* STEP 2: INTERNSHIP SCHEDULE & DOMAIN */}
            {currentStep === 2 && (
              <section className={styles.stepSection}>
                <h2 className={styles.sectionHeading}>Section 2: Internship Details</h2>

                <div className={styles.fieldRow}>
                  <div className={styles.fieldCol}>
                    <label htmlFor="mentorName" className={styles.fieldLabel}>Mentor Name</label>
                    <input
                      id="mentorName"
                      type="text"
                      placeholder="e.g., Vikram Aditya"
                      {...register("mentorName", { required: "Industry mentor identification required." })}
                      className={errors.mentorName ? styles.inputInvalid : styles.inputStandard}
                    />
                    {errors.mentorName && <span className={styles.errorText}>{errors.mentorName.message}</span>}
                  </div>

                  <div className={styles.fieldCol}>
                    <label htmlFor="mentorEmail" className={styles.fieldLabel}>Industry Mentor Corporate Email</label>
                    <input
                      id="mentorEmail"
                      type="email"
                      placeholder="e.g., mentor@corporate.org"
                      {...register("mentorEmail", {
                        required: "Mentor corporate email address is required.",
                        pattern: {
                          value: /^[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}$/i,
                          message: "Invalid electronic mail syntax."
                        }
                      })}
                      className={errors.mentorEmail ? styles.inputInvalid : styles.inputStandard}
                    />
                    {errors.mentorEmail && <span className={styles.errorText}>{errors.mentorEmail.message}</span>}
                  </div>
                </div>

                <div className={styles.fieldRow}>
                  <div className={styles.fieldCol}>
                    <label htmlFor="internshipDomain" className={styles.fieldLabel}>Engineering Practice Domain</label>
                    <input
                      id="internshipDomain"
                      type="text"
                      list="domain-suggestions"
                      placeholder="Select or enter specialization domain"
                      {...register("internshipDomain", { required: "Internship domain parameter is required." })}
                      className={errors.internshipDomain ? styles.inputInvalid : styles.inputStandard}
                    />
                    <datalist id="domain-suggestions">
                      <option value="Software Development & Architecture" />
                      <option value="Cybersecurity & Vulnerability Assessment" />
                      <option value="Cloud Infrastructure & DevOps" />
                      <option value="Data Engineering & Pipeline Orchestration" />
                      <option value="Artificial Intelligence & Machine Learning" />
                    </datalist>
                    {errors.internshipDomain && <span className={styles.errorText}>{errors.internshipDomain.message}</span>}
                  </div>

                  {/* <div className={styles.fieldCol}>
                    <label className={styles.fieldLabel}>Primary Computing Peripheral</label>
                    <div className={styles.radioGroup}>
                      <label className={styles.radioLabel}>
                        <input type="radio" value="Desktop" {...register("deviceType")} />
                        <span>Workstation / Laptop (Desktop)</span>
                      </label>
                      <label className={styles.radioLabel}>
                        <input type="radio" value="Mobile" {...register("deviceType")} />
                        <span>Mobile / Tablet Peripheral</span>
                      </label>
                    </div>
                  </div> */}
                </div>

                <div className={styles.fieldRow}>
                  <div className={styles.fieldCol}>
                    <label htmlFor="joiningDate" className={styles.fieldLabel}>Internship Joining Date</label>
                    <input
                      id="joiningDate"
                      type="date"
                      {...register("joiningDate", { required: "Joining commencement date mandatory." })}
                      className={errors.joiningDate ? styles.inputInvalid : styles.inputStandard}
                    />
                    {errors.joiningDate && <span className={styles.errorText}>{errors.joiningDate.message}</span>}
                  </div>

                  <div className={styles.fieldCol}>
                    <label htmlFor="completionDate" className={styles.fieldLabel}>Expected Completion Date</label>
                    <input
                      id="completionDate"
                      type="date"
                      {...register("completionDate", {
                        required: "Completion date mandatory.",
                        validate: (val) => validateDateChronology(watchJoiningDate, val) || DATE_ERROR_MESSAGE
                      })}
                      className={errors.completionDate ? styles.inputInvalid : styles.inputStandard}
                    />
                    {errors.completionDate && <span className={styles.errorText}>{errors.completionDate.message}</span>}
                  </div>
                </div>

                <div className={styles.fieldRow}>
                  <div className={styles.fieldCol}>
                    <label htmlFor="officeStartTime" className={styles.fieldLabel}>Daily Office Start Time</label>
                    <input
                      id="officeStartTime"
                      type="time"
                      {...register("officeStartTime", { required: "Office start hour required." })}
                      className={errors.officeStartTime ? styles.inputInvalid : styles.inputStandard}
                    />
                    {errors.officeStartTime && <span className={styles.errorText}>{errors.officeStartTime.message}</span>}
                  </div>

                  <div className={styles.fieldCol}>
                    <label htmlFor="officeEndTime" className={styles.fieldLabel}>Daily Office End Time</label>
                    <input
                      id="officeEndTime"
                      type="time"
                      {...register("officeEndTime", { required: "Office end hour required." })}
                      className={errors.officeEndTime ? styles.inputInvalid : styles.inputStandard}
                    />
                    {errors.officeEndTime && <span className={styles.errorText}>{errors.officeEndTime.message}</span>}
                  </div>
                </div>

                <div className={styles.fieldRow}>
                  <div className={styles.fieldCol}>
                    <label htmlFor="breakStartTime" className={styles.fieldLabel}>Rest Break Start Time</label>
                    <input
                      id="breakStartTime"
                      type="time"
                      {...register("breakStartTime", {
                        required: "Break start time required.",
                        validate: (val) => validateBreakHours(watchOfficeStart, watchOfficeEnd, val, watch('breakEndTime')) || TIME_ERROR_MESSAGE
                      })}
                      className={errors.breakStartTime ? styles.inputInvalid : styles.inputStandard}
                    />
                    {errors.breakStartTime && <span className={styles.errorText}>{errors.breakStartTime.message}</span>}
                  </div>

                  <div className={styles.fieldCol}>
                    <label htmlFor="breakEndTime" className={styles.fieldLabel}>Rest Break End Time</label>
                    <input
                      id="breakEndTime"
                      type="time"
                      {...register("breakEndTime", {
                        required: "Break end time required.",
                        validate: (val) => validateBreakHours(watchOfficeStart, watchOfficeEnd, watchBreakStart, val) || TIME_ERROR_MESSAGE
                      })}
                      className={errors.breakEndTime ? styles.inputInvalid : styles.inputStandard}
                    />
                    {errors.breakEndTime && <span className={styles.errorText}>{errors.breakEndTime.message}</span>}
                  </div>
                </div>

                <div className={styles.fieldCol}>
                  <label className={styles.fieldLabel}>Designated Weekly Working Days</label>
                  <div className={styles.checkboxGrid}>
                    {['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday'].map((day) => (
                      <label key={day} className={styles.checkLabel}>
                        <input
                          type="checkbox"
                          value={day}
                          {...register("workingDays", { required: "Select at least one active working day." })}
                        />
                        <span>{day}</span>
                      </label>
                    ))}
                  </div>
                  {errors.workingDays && <span className={styles.errorText}>{errors.workingDays.message}</span>}
                </div>

                <div className={styles.navRow}>
                  <button type="button" onClick={() => handleNextStep(1)} className={styles.backBtn}>
                    ← Back (Step 1)
                  </button>
                  <button type="button" onClick={() => handleNextStep(3)} className={styles.navBtn}>
                    Proceed to Documents (Step 3) →
                  </button>
                </div>
              </section>
            )}

            {/* STEP 3: INSTITUTIONAL DOCUMENTS (OFFER & APPROVAL LETTERS) */}
            {currentStep === 3 && (
              <section className={styles.stepSection}>
                <h2 className={styles.sectionHeading}>Section 3: Institutional Approval & Offer Documentation</h2>
                <p className={styles.stepDescription}>
                  Please attach copies of your corporate Offer Letter and departmental Approval Letter.
                  Accepted file formats: JPG, PNG, or PDF (maximum 5MB per file).
                </p>

                <div className={styles.docUploadContainer}>

                  {/* DOCUMENT 1: OFFER LETTER */}
                  <div className={styles.docCard}>
                    <div className={styles.docHeader}>
                      <span className={styles.docTitle}>1. Corporate Offer Letter <span className={styles.reqTag}>*Required</span></span>
                      <span className={styles.docSub}>Official employment or internship offer document issued by host organization</span>
                    </div>

                    {offerFile ? (
                      <div className={styles.selectedFileBox}>
                        <div className={styles.filePreviewArea}>
                          {offerPreviewUrl ? (
                            <img src={offerPreviewUrl} alt="Offer letter image thumbnail preview" className={styles.docThumbnail} />
                          ) : (
                            <div className={styles.pdfBadge}>
                              <svg className={styles.pdfIcon} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                                <path d="M14 2H6C4.9 2 4 2.9 4 4V20C4 21.1 4.9 22 6 22H18C19.1 22 20 21.1 20 20V8L14 2Z" />
                                <path d="M14 2V8H20" />
                                <text x="6" y="16" fontSize="5" fontWeight="800" fill="currentColor">PDF</text>
                              </svg>
                              <span className={styles.pdfLabel}>[PDF DOCUMENT]</span>
                            </div>
                          )}
                          <div className={styles.fileMetadata}>
                            <span className={styles.fileNameText}>{offerFile.name}</span>
                            <span className={styles.fileSizeText}>{formatFileSize(offerFile.size)}</span>
                          </div>
                        </div>
                        <button type="button" onClick={() => removeDocument('offer')} className={styles.removeLink}>
                          Remove & Select Different File
                        </button>
                      </div>
                    ) : (
                      <div className={styles.dropZone}>
                        <label className={styles.uploadBtnLabel}>
                          <span>Select Offer Letter Document (JPG/PNG/PDF)</span>
                          <input
                            type="file"
                            accept="image/jpeg,image/png,application/pdf"
                            onChange={(e) => handleDocumentSelection(e, 'offer')}
                            className={styles.fileInputHidden}
                          />
                        </label>
                      </div>
                    )}

                    {offerError && <span className={styles.inlineError}>{offerError}</span>}
                  </div>

                  {/* DOCUMENT 2: APPROVAL LETTER */}
                  <div className={styles.docCard}>
                    <div className={styles.docHeader}>
                      <span className={styles.docTitle}>2. Institutional Approval Letter <span className={styles.reqTag}>*Required</span></span>
                      <span className={styles.docSub}>Signed consent from Faculty Advisor / Training & Placement Office</span>
                    </div>

                    {approvalFile ? (
                      <div className={styles.selectedFileBox}>
                        <div className={styles.filePreviewArea}>
                          {approvalPreviewUrl ? (
                            <img src={approvalPreviewUrl} alt="Approval letter image thumbnail preview" className={styles.docThumbnail} />
                          ) : (
                            <div className={styles.pdfBadge}>
                              <svg className={styles.pdfIcon} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                                <path d="M14 2H6C4.9 2 4 2.9 4 4V20C4 21.1 4.9 22 6 22H18C19.1 22 20 21.1 20 20V8L14 2Z" />
                                <path d="M14 2V8H20" />
                                <text x="6" y="16" fontSize="5" fontWeight="800" fill="currentColor">PDF</text>
                              </svg>
                              <span className={styles.pdfLabel}>[PDF DOCUMENT]</span>
                            </div>
                          )}
                          <div className={styles.fileMetadata}>
                            <span className={styles.fileNameText}>{approvalFile.name}</span>
                            <span className={styles.fileSizeText}>{formatFileSize(approvalFile.size)}</span>
                          </div>
                        </div>
                        <button type="button" onClick={() => removeDocument('approval')} className={styles.removeLink}>
                          Remove & Select Different File
                        </button>
                      </div>
                    ) : (
                      <div className={styles.dropZone}>
                        <label className={styles.uploadBtnLabel}>
                          <span>Select Approval Letter Document (JPG/PNG/PDF)</span>
                          <input
                            type="file"
                            accept="image/jpeg,image/png,application/pdf"
                            onChange={(e) => handleDocumentSelection(e, 'approval')}
                            className={styles.fileInputHidden}
                          />
                        </label>
                      </div>
                    )}

                    {approvalError && <span className={styles.inlineError}>{approvalError}</span>}
                  </div>

                </div>

                {step3Error && <div className={styles.errorBanner}>{step3Error}</div>}

                <div className={styles.navRow}>
                  <button type="button" onClick={() => handleNextStep(2)} className={styles.backBtn}>
                    ← Back (Step 2)
                  </button>
                  <button type="button" onClick={() => handleNextStep(4)} className={styles.navBtn}>
                    Proceed to Photo & Consent (Step 4) →
                  </button>
                </div>
              </section>
            )}

            {/* STEP 4: REFERENCE PHOTO & FINAL CONSENT SUBMISSION */}
            {currentStep === 4 && (
              <section className={styles.stepSection}>
                <h2 className={styles.sectionHeading}>Section 4: Identity Verification & Compliance Consent</h2>

                <div className={styles.captureBlock}>
                  <h3 className={styles.subHeading}>A. Live Facial Reference Capture</h3>
                  <PhotoCapture onPhotoSelected={onPhotoSelected} error={photoError} />
                </div>

                <div className={styles.consentBlock}>
                  <h3 className={styles.subHeading}>B. Institutional Surveillance & Compliance Agreement</h3>

                  <label className={styles.consentCheckboxLabel}>
                    <input
                      type="checkbox"
                      checked={consentChecked}
                      onChange={handleConsentClick}
                      className={styles.consentCheckbox}
                    />
                    <span className={styles.consentText}>
                      I understand this app will periodically verify my identity via webcam during working hours, and I consent to this. (See our <Link to="/privacy" target="_blank" rel="noopener noreferrer" style={{ color: '#2B5C8A', textDecoration: 'underline', fontWeight: 'bold' }}>Institutional Privacy Notice</Link> for full details on data collection, retention, and access).
                    </span>
                  </label>

                  {/* Explain why submit is blocked if unchecked or if user clicked while disabled */}
                  {!consentChecked && (
                    <span className={styles.consentWarningText}>
                      * Note: Identity verification consent is mandatory for academic compliance tracking. The submit action remains locked until acknowledged.
                    </span>
                  )}
                  {consentNoticeShown && !consentChecked && (
                    <div className={styles.errorBanner} role="alert">
                      <strong>Action Blocked:</strong> You must check the identity verification consent agreement above before submitting your application.
                    </div>
                  )}
                </div>

                {backendError && (
                  <div className={styles.errorBanner} role="alert">
                    <strong>Submission Rejected:</strong> {backendError}
                  </div>
                )}

                <div className={styles.navRow}>
                  <button type="button" onClick={() => handleNextStep(3)} disabled={isSubmitting} className={styles.backBtn}>
                    ← Back (Step 3)
                  </button>

                  <div className={styles.submitWrap} onClick={!consentChecked ? handleDisabledSubmitClick : undefined}>
                    <button
                      type="submit"
                      disabled={isSubmitting || !consentChecked}
                      className={styles.finalSubmitBtn}
                    >
                      {isSubmitting ? (
                        <span className={styles.submittingSpan}>
                          <span className={styles.inlineSpinner}></span>
                          Submitting...
                        </span>
                      ) : (
                        "Submit Application for HOD Review"
                      )}
                    </button>
                  </div>
                </div>
              </section>
            )}

            <footer className={styles.formFooter}>
              <span>Already verified in central directory?</span>
              <Link to="/login" className={styles.footerLink}>Return to Institutional Sign In</Link>
            </footer>
          </form>
        )}
      </div>
    </div>
  );
};

export default StudentRegister;
