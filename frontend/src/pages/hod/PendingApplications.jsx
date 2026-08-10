import React, { useState, useEffect, useCallback } from 'react';
import { getPendingApplications, approveApplication, rejectApplication } from '../../services/api';
import { useNotification } from '../../hooks/useNotification';
import StatusBadge from '../../components/StatusBadge';
import styles from './PendingApplications.module.css';

/**
 * HOD Institutional Evaluation Portal: Data-dense spreadsheet view for reviewing student applications.
 * Integrated with Module 4 status filtering (Applied, Approved, Ongoing, Completed, Rejected) and StatusBadge pills.
 */
const PendingApplications = () => {
  const { notify } = useNotification();

  const [applications, setApplications] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [currentPage, setCurrentPage] = useState(1);
  const [totalPages, setTotalPages] = useState(1);
  const [totalRecords, setTotalRecords] = useState(0);

  // Client-side filtering controls
  const [searchTerm, setSearchTerm] = useState('');
  const [branchFilter, setBranchFilter] = useState('ALL');
  const [statusFilter, setStatusFilter] = useState('ALL');

  // Detail Drawer state
  const [selectedApp, setSelectedApp] = useState(null);
  const [showRejectBox, setShowRejectBox] = useState(false);
  const [showApproveBox, setShowApproveBox] = useState(false);
  const [selectedCollegeMentor, setSelectedCollegeMentor] = useState('');
  const [rejectReason, setRejectReason] = useState('');
  const [actionLoading, setActionLoading] = useState(false);

  const fetchApplications = useCallback(async (page, status) => {
    setLoading(true);
    setError(null);
    try {
      const data = await getPendingApplications(page, status);
      setApplications(data.applications || []);
      setCurrentPage(data.currentPage || page);
      setTotalPages(data.totalPages || 1);
      setTotalRecords(data.totalRecords || 0);
    } catch (err) {
      setError("Unable to connect to departmental ledger service. Please verify network or database emulation status.");
      notify("Network error occurred while fetching student evaluation ledgers.", "error", 4000);
    } finally {
      setLoading(false);
    }
  }, [notify]);

  useEffect(() => {
    fetchApplications(currentPage, statusFilter);
  }, [currentPage, statusFilter, fetchApplications]);

  const handleStatusFilterChange = (e) => {
    setStatusFilter(e.target.value);
    setCurrentPage(1);
  };

  const handleApprove = async (uid) => {
    if (!selectedCollegeMentor) {
      alert('Please allocate a college mentor before approving the application.');
      return;
    }
    setActionLoading(true);
    try {
      await approveApplication(uid, { collegeMentor: selectedCollegeMentor });
      notify("Application approved — status transitioned to Approved in institutional ledger.", "success", 4000);
      setApplications(prev => prev.map(a => a.uid === uid ? { ...a, status: 'Approved' } : a));
      setSelectedApp(null);
      setShowApproveBox(false);
      setSelectedCollegeMentor('');
    } catch (err) {
      notify("Failed to commit approval to central database.", "error", 4000);
    } finally {
      setActionLoading(false);
    }
  };

  const handleConfirmReject = async () => {
    if (!selectedApp) return;
    setActionLoading(true);
    try {
      await rejectApplication(selectedApp.uid, rejectReason);
      notify("Application rejected — student notified with correction instructions.", "warning", 5000);
      setApplications(prev => prev.map(a => a.uid === selectedApp.uid ? { ...a, status: 'Rejected', rejectionReason: rejectReason } : a));
      setSelectedApp(null);
      setShowRejectBox(false);
      setRejectReason('');
    } catch (err) {
      notify("Failed to execute rejection order.", "error", 4000);
    } finally {
      setActionLoading(false);
    }
  };

  const displayedApplications = applications.filter(app => {
    const matchesSearch = searchTerm === '' ||
      (app.fullName && app.fullName.toLowerCase().includes(searchTerm.toLowerCase())) ||
      (app.collegeEmail && app.collegeEmail.toLowerCase().includes(searchTerm.toLowerCase()));
    const matchesBranch = branchFilter === 'ALL' || app.branch === branchFilter;
    return matchesSearch && matchesBranch;
  });

  return (
    <div className={styles.pageWrapper}>
      <div className={styles.contentContainer}>
        <header className={styles.header}>
          <div>
            <h1 className={styles.headerTitle}>HOD Departmental Applications & Lifecycle Evaluation </h1>
            <div className={styles.headerSub}>G H Raisoni College of Engineering, Nagpur</div>
          </div>
          <div className={styles.recordBadge}>
            <span className={styles.badgeLabel}>Total Records:</span>
            <span className={styles.badgeCount}>{totalRecords}</span>
          </div>
        </header>

        {/* Search and Filter Section */}
        <section className={styles.filterSection} style={{ gridTemplateColumns: '1.5fr 1fr 1fr' }}>
          <div className={styles.searchWrap}>
            <label className={styles.filterLabel}>Search Candidate Registry</label>
            <input
              type="text"
              placeholder="Search candidate by name or official email..."
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              className={styles.searchInput}
            />
          </div>

          <div className={styles.selectWrap}>
            <label htmlFor="statusFilter" className={styles.filterLabel}>Status Lifecycle</label>
            <select
              id="statusFilter"
              value={statusFilter}
              onChange={handleStatusFilterChange}
              className={styles.branchSelect}
            >
              <option value="ALL">All Statuses</option>
              <option value="Applied">Applied (Pending Review)</option>
              <option value="Approved">Approved</option>
              <option value="Ongoing">Ongoing Practice</option>
              <option value="Completed">Completed & Verified</option>
              <option value="Rejected">Rejected</option>
            </select>
          </div>

          <div className={styles.selectWrap}>
            <label htmlFor="branchFilter" className={styles.filterLabel}>Department Branch</label>
            <select
              id="branchFilter"
              value={branchFilter}
              onChange={(e) => setBranchFilter(e.target.value)}
              className={styles.branchSelect}
            >
              <option value="ALL">All Engineering Branches</option>
              <option value="Computer Science & Engineering">Computer Science & Engineering</option>
              <option value="Information Technology">Information Technology</option>
              <option value="Artificial Intelligence & Data Science">Artificial Intelligence & Data Science</option>
              <option value="Electronics & Communication">Electronics & Communication</option>
            </select>
          </div>
        </section>

        {/* Dense Institutional Spreadsheet Table */}
        <section className={styles.tableSection}>
          {loading ? (
            <div className={styles.statusBox}>
              <div className={styles.inlineSpinner}></div>
              <div className={styles.emptyStateText}>Loading departmental student records...</div>
            </div>
          ) : error ? (
            <div className={styles.errorBox}>
              <div>{error}</div>
              <button type="button" onClick={() => fetchApplications(currentPage, statusFilter)} className={styles.retryBtn}>Retry Connection</button>
            </div>
          ) : (
            <div className={styles.tableResponsive}>
              <table className={styles.denseTable}>
                <thead>
                  <tr>
                    <th>Student Name</th>
                    <th>College Email</th>
                    <th>Branch</th>
                    <th>Practice Domain</th>
                    <th>Corporate Mentor</th>
                    <th>Joining Date</th>
                    <th>Lifecycle Status</th>
                    <th>Documents</th>
                    <th>Submitted On</th>
                    <th>Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {displayedApplications.length === 0 ? (
                    <tr>
                      <td colSpan="10">
                        <div className={styles.emptyStateBox}>
                          <span className={styles.emptyStateText}>No pending applications right now</span>
                        </div>
                      </td>
                    </tr>
                  ) : (
                    displayedApplications.map((app) => (
                      <tr key={app.uid} onClick={() => { setSelectedApp(app); setShowRejectBox(false); }}>
                        <td className={styles.primaryText}>{app.fullName}</td>
                        <td className={styles.cellEmail}>{app.collegeEmail}</td>
                        <td className={styles.cellBranch}>{app.branch}</td>
                        <td><span className={styles.domainTag}>{app.internshipDomain}</span></td>
                        <td>
                          <div className={styles.primaryText}>{app.mentorName}</div>
                          <div className={styles.cellEmail}>{app.mentorEmail}</div>
                        </td>
                        <td><strong>{app.joiningDate}</strong></td>
                        <td><StatusBadge status={app.status} /></td>
                        <td onClick={(e) => e.stopPropagation()}>
                          <div className={styles.docLinksGroup}>
                            {app.offerLetterUrl && (
                              <a href={app.offerLetterUrl} target="_blank" rel="noopener noreferrer" className={styles.docLink}>Offer [PDF] ↗</a>
                            )}
                            {app.approvalLetterUrl && (
                              <a href={app.approvalLetterUrl} target="_blank" rel="noopener noreferrer" className={styles.docLink}>Approval [PDF]</a>
                            )}
                          </div>
                        </td>
                        <td>
                          {app.createdAt ? new Date(app.createdAt).toLocaleDateString() : 'N/A'}
                        </td>
                        <td onClick={(e) => e.stopPropagation()}>
                          <button
                            type="button"
                            onClick={() => { setSelectedApp(app); setShowRejectBox(false); }}
                            className={styles.viewBtn}
                          >
                            View Details
                          </button>
                        </td>
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
            </div>
          )}

          {/* Pagination Row */}
          {!loading && !error && totalPages > 1 && (
            <div className={styles.paginationRow}>
              <span className={styles.pageInfo}>Showing page {currentPage} of {totalPages} ({totalRecords} total student records)</span>
              <div className={styles.pageButtons}>
                <button
                  type="button"
                  disabled={currentPage === 1}
                  onClick={() => setCurrentPage(prev => Math.max(1, prev - 1))}
                  className={styles.pageBtn}
                >
                  ← Previous 20
                </button>
                <button
                  type="button"
                  disabled={currentPage === totalPages}
                  onClick={() => setCurrentPage(prev => Math.min(totalPages, prev + 1))}
                  className={styles.pageBtn}
                >
                  Next 20 →
                </button>
              </div>
            </div>
          )}
        </section>

        {/* Detail Audit & Evaluation Modal Drawer */}
        {selectedApp && (
          <aside className={styles.modalOverlay} onClick={() => setSelectedApp(null)}>
            <div className={styles.modalPanel} onClick={(e) => e.stopPropagation()}>
              <div className={styles.modalHeader}>
                <div>
                  <h2 className={styles.modalTitle}>Candidate Profile: {selectedApp.fullName} (Section {selectedApp.section || 'N/A'}, Roll {selectedApp.rollNo || 'N/A'})</h2>
                  <div className={styles.modalSub}>{selectedApp.collegeEmail} | Enrollment No: {selectedApp.enrollmentNo || 'N/A'}</div>
                </div>
                <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                  <StatusBadge status={selectedApp.status} />
                  <button type="button" className={styles.closeBtn} onClick={() => setSelectedApp(null)}>✕</button>
                </div>
              </div>

              <div className={styles.modalBody}>
                {/* Section A: Registration & Schedule */}
                <div className={styles.detailSection}>
                  <h3 className={styles.detailSectionHeading}>Academic & Corporate Assignment</h3>
                  <div className={styles.grid2}>
                    <div className={styles.fieldItem}>
                      <span className={styles.fieldTitle}>Department Branch</span>
                      <span className={styles.fieldVal}>{selectedApp.branch}</span>
                    </div>
                    <div className={styles.fieldItem}>
                      <span className={styles.fieldTitle}>Internship Practice Domain</span>
                      <span className={styles.fieldVal}>{selectedApp.internshipDomain}</span>
                    </div>
                    <div className={styles.fieldItem}>
                      <span className={styles.fieldTitle}>Designated Mentor Name</span>
                      <span className={styles.fieldVal}>{selectedApp.mentorName}</span>
                    </div>
                    <div className={styles.fieldItem}>
                      <span className={styles.fieldTitle}>Official Mentor Email</span>
                      <span className={styles.fieldVal}>{selectedApp.mentorEmail}</span>
                    </div>
                  </div>
                </div>

                <div className={styles.detailSection}>
                  <h3 className={styles.detailSectionHeading}>Operating Schedule Chronology</h3>
                  <div className={styles.grid2}>
                    <div className={styles.fieldItem}>
                      <span className={styles.fieldTitle}>Joining Date</span>
                      <span className={styles.fieldVal}>{selectedApp.joiningDate}</span>
                    </div>
                    <div className={styles.fieldItem}>
                      <span className={styles.fieldTitle}>Target Completion Date</span>
                      <span className={styles.fieldVal}>{selectedApp.completionDate}</span>
                    </div>
                    <div className={styles.fieldItem}>
                      <span className={styles.fieldTitle}>Office Working Window</span>
                      <span className={styles.fieldVal}>{selectedApp.officeStartTime || '09:00'} – {selectedApp.officeEndTime || '17:00'} hrs</span>
                    </div>
                    <div className={styles.fieldItem}>
                      <span className={styles.fieldTitle}>Allocated Break Interval</span>
                      <span className={styles.fieldVal}>{selectedApp.breakStartTime || '13:00'} – {selectedApp.breakEndTime || '14:00'} hrs</span>
                    </div>
                  </div>
                  <div className={styles.fieldItemFull} style={{ marginTop: '8px' }}>
                    <span className={styles.fieldTitle}>Designated Working Days</span>
                    <div className={styles.daysBadgeGroup}>
                      {(selectedApp.workingDays || ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday']).map((d, idx) => (
                        <span key={idx} className={styles.dayBadge}>{d}</span>
                      ))}
                    </div>
                  </div>
                </div>

                {/* Section B: Verification Artifacts */}
                <div className={styles.detailSection}>
                  <h3 className={styles.detailSectionHeading}>Identity Verification & Institutional Documents</h3>
                  <div className={styles.mediaGrid}>
                    <div className={styles.photoPreviewBox}>
                      <span className={styles.mediaTitle}>Reference Portrait</span>
                      {selectedApp.referencePhotoUrl ? (
                        <img src={selectedApp.referencePhotoUrl} alt="Student Reference" className={styles.photoImage} />
                      ) : (
                        <div className={styles.noPhotoBox}>No Reference Photo On File</div>
                      )}
                      <div className={styles.consentConfirmationBox}>
                        <span className={styles.consentCheckIcon}></span>
                        <div className={styles.consentTextGroup}>
                          <span className={styles.consentMain}>Identity Surveillance Consent Verified</span>
                          <span className={styles.consentSub}>Recorded at time of formal submission</span>
                        </div>
                      </div>
                    </div>

                    <div className={styles.docsPreviewBox}>
                      <span className={styles.mediaTitle}>Attached Institutional Evidence:</span>
                      <div className={styles.docActionsList}>
                        <a href={selectedApp.offerLetterUrl || "#"} target="_blank" rel="noopener noreferrer" className={styles.externalDocBtn}>
                          <span className={styles.btnTitle}>Corporate Offer Letter [PDF]</span>
                          <span className={styles.btnLinkText}>View Cloud Doc ↗</span>
                        </a>
                        <a href={selectedApp.approvalLetterUrl || "#"} target="_blank" rel="noopener noreferrer" className={styles.externalDocBtn}>
                          <span className={styles.btnTitle}>Departmental Approval Letter [PDF]</span>
                          <span className={styles.btnLinkText}>View Cloud Doc ↗</span>
                        </a>
                        {selectedApp.ongoingSince && (
                          <div className={styles.externalDocBtn} style={{ backgroundColor: '#E0F2FE', borderColor: '#0284C7', cursor: 'default' }}>
                            <span className={styles.btnTitle}>⚡ Automated Ongoing Transition:</span>
                            <strong style={{ color: '#2B5C8A', fontSize: '12px' }}>{new Date(selectedApp.ongoingSince).toLocaleString()}</strong>
                          </div>
                        )}
                        {selectedApp.rejectionReason && (
                          <div className={styles.externalDocBtn} style={{ backgroundColor: '#FEF2F2', borderColor: '#DC2626', cursor: 'default' }}>
                            <span className={styles.btnTitle}>⚠ Rejection Justification:</span>
                            <strong style={{ color: '#991B1B', fontSize: '12px' }}>{selectedApp.rejectionReason}</strong>
                          </div>
                        )}
                      </div>
                    </div>
                  </div>
                </div>

                {/* Rejection Justification Input Area */}
                {showRejectBox && (
                  <div className={styles.rejectionInputArea}>
                    <label htmlFor="rejectReason" className={styles.rejectionLabel}>Departmental Rejection Reason / Correction Notes (Optional):</label>
                    <textarea
                      id="rejectReason"
                      rows="3"
                      placeholder="Specify required alterations (e.g., Offer letter missing formal corporate letterhead watermark or HR signature)..."
                      value={rejectReason}
                      onChange={(e) => setRejectReason(e.target.value)}
                      className={styles.rejectionTextarea}
                    />
                  </div>
                )}

                {/* Mentor Allocation Input Area */}
                {showApproveBox && (
                  <div className={styles.rejectionInputArea} style={{ backgroundColor: '#F0FDF4', borderColor: '#22C55E' }}>
                    <label htmlFor="collegeMentor" className={styles.rejectionLabel} style={{ color: '#166534' }}>Allocate College/Faculty Mentor:</label>
                    <select
                      id="collegeMentor"
                      value={selectedCollegeMentor}
                      onChange={(e) => setSelectedCollegeMentor(e.target.value)}
                      className={styles.rejectionTextarea}
                      style={{ padding: '8px', height: 'auto', border: '1px solid #BBF7D0' }}
                    >
                      <option value="">-- Select a Faculty Mentor --</option>
                      <option value="Dr. Rajesh K. (CS Dept)">Dr. Rajesh K. (CS Dept)</option>
                      <option value="Prof. A. Sharma (IT Dept)">Prof. A. Sharma (IT Dept)</option>
                      <option value="Dr. S. Gupta (AI Dept)">Dr. S. Gupta (AI Dept)</option>
                      <option value="Prof. V. Nair (EC Dept)">Prof. V. Nair (EC Dept)</option>
                    </select>
                  </div>
                )}
              </div>

              {/* Section C: Modal Footer Action Bar */}
              <div className={styles.modalFooter}>
                <button type="button" onClick={() => setSelectedApp(null)} className={styles.modalCancelBtn}>Close Preview</button>
                <div className={styles.actionBtnGroup}>
                  {!showRejectBox && !showApproveBox ? (
                    <>
                      <button
                        type="button"
                        disabled={actionLoading}
                        onClick={() => setShowRejectBox(true)}
                        className={styles.rejectBtn}
                      >
                        Reject Application / Request Corrections
                      </button>
                      <button
                        type="button"
                        disabled={actionLoading || selectedApp.status === 'Approved' || selectedApp.status === 'Ongoing'}
                        onClick={() => setShowApproveBox(true)}
                        className={styles.approveBtn}
                      >
                        {actionLoading ? "Processing..." : (selectedApp.status === 'Approved' ? "Already Approved ✓" : "Allocate Mentor & Approve")}
                      </button>
                    </>
                  ) : showRejectBox ? (
                    <>
                      <button type="button" onClick={() => setShowRejectBox(false)} className={styles.backToActionsBtn}>Cancel Rejection</button>
                      <button
                        type="button"
                        disabled={actionLoading}
                        onClick={handleConfirmReject}
                        className={styles.confirmRejectBtn}
                      >
                        Confirm Rejection
                      </button>
                    </>
                  ) : (
                    <>
                      <button type="button" onClick={() => setShowApproveBox(false)} className={styles.backToActionsBtn}>Cancel</button>
                      <button
                        type="button"
                        disabled={actionLoading || !selectedCollegeMentor}
                        onClick={() => handleApprove(selectedApp.uid)}
                        className={styles.approveBtn}
                      >
                        Confirm Approval
                      </button>
                    </>
                  )}
                </div>
              </div>
            </div>
          </aside>
        )}
      </div>
    </div>
  );
};

export default PendingApplications;
