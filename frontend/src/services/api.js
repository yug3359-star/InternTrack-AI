import axios from 'axios';
import { auth } from '../utils/firebase';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api';

const api = axios.create({
  baseURL: API_BASE_URL,
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json'
  }
});

// Request Interceptor: Automatically attach Firebase ID Token as a Bearer authorization header
api.interceptors.request.use(
  async (config) => {
    try {
      if (auth && auth.currentUser) {
        const token = await auth.currentUser.getIdToken(false);
        config.headers.Authorization = `Bearer ${token}`;
      } else {
        // Retrieve local simulated development token if user logged in via offline role selector
        const devToken = sessionStorage.getItem('interntrack_dev_token') || localStorage.getItem('interntrack_dev_token');
        if (devToken) {
          config.headers.Authorization = `Bearer ${devToken}`;
        }
      }
    } catch (err) {
      console.warn("Unable to attach authentication token to outgoing request:", err);
    }
    return config;
  },
  (error) => Promise.reject(error)
);

// Response Interceptor: Clean error reporting & seamless automatic silent JWT ID Token refresh on expiration (Task 8 & 13)
api.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error.config;
    if (error.response && error.response.status === 401 && !originalRequest._retry && auth && auth.currentUser) {
      originalRequest._retry = true;
      try {
        console.warn("Session token expired or rejected. Triggering automatic silent Firebase JWT refresh...");
        const refreshedToken = await auth.currentUser.getIdToken(true); // checkRevoked / forceRefresh = true
        originalRequest.headers.Authorization = `Bearer ${refreshedToken}`;
        return api(originalRequest);
      } catch (refreshErr) {
        console.error("Token refresh failed. Directing candidate to institutional login.", refreshErr);
      }
    }
    if (error.response && error.response.status === 401) {
      console.warn("Session rejected: institutional authentication credentials expired or unverified.");
    }
    return Promise.reject(error);
  }
);

/**
 * Submits multipart registration record including schedule parameters and portrait file bytes.
 */
export const registerStudent = async (formData) => {
  const response = await api.post('/auth/register', formData, {
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  });
  return response.data;
};

/* =========================================================================================
 * MODULE 3: HOD APPROVAL & COMPLIANCE EVALUATION API ENDPOINTS
 * Includes seamless local evaluation fallback when central Spring Boot service is offline.
 * ========================================================================================= */

// Simulated evaluation roster for presentation demonstrations when offline
let mockOfflinePendingList = [
  {
    uid: 'dev-stud-101',
    fullName: 'Ananya Sharma',
    collegeEmail: 'ananya.sharma@cs.college.edu',
    branch: 'Computer Science & Engineering',
    internshipDomain: 'Cloud Infrastructure & DevOps',
    mentorName: 'Vikram Aditya',
    mentorEmail: 'vikram@cloudcorp.org',
    deviceType: 'Desktop',
    joiningDate: '2026-08-01',
    completionDate: '2026-11-30',
    officeStartTime: '09:00',
    officeEndTime: '17:00',
    breakStartTime: '13:00',
    breakEndTime: '14:00',
    workingDays: ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday'],
    status: 'Applied',
    consentGiven: true,
    consentTimestamp: Date.now() - 432000000,
    createdAt: Date.now() - 432000000,
    referencePhotoUrl: 'https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=200&auto=format&fit=crop&q=80',
    offerLetterUrl: 'https://interntrack.dev/documents/dev-stud-101/offer-letter.pdf',
    approvalLetterUrl: 'https://interntrack.dev/documents/dev-stud-101/approval-letter.pdf'
  },
  {
    uid: 'dev-stud-102',
    fullName: 'Rohit Verma',
    collegeEmail: 'rohit.verma@cs.college.edu',
    branch: 'Information Technology',
    internshipDomain: 'Artificial Intelligence & Machine Learning',
    mentorName: 'Priya Nair',
    mentorEmail: 'priya.nair@ai-labs.io',
    deviceType: 'Desktop',
    joiningDate: '2026-08-05',
    completionDate: '2026-12-05',
    officeStartTime: '09:30',
    officeEndTime: '17:30',
    breakStartTime: '13:00',
    breakEndTime: '14:00',
    workingDays: ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday'],
    status: 'Applied',
    consentGiven: true,
    consentTimestamp: Date.now() - 345600000,
    createdAt: Date.now() - 345600000,
    referencePhotoUrl: 'https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=200&auto=format&fit=crop&q=80',
    offerLetterUrl: 'https://interntrack.dev/documents/dev-stud-102/offer-letter.pdf',
    approvalLetterUrl: 'https://interntrack.dev/documents/dev-stud-102/approval-letter.pdf'
  },
  {
    uid: 'dev-stud-103',
    fullName: 'Siddharth Rao',
    collegeEmail: 'siddharth.r@ai.college.edu',
    branch: 'Artificial Intelligence & Data Science',
    internshipDomain: 'Cybersecurity & Vulnerability Assessment',
    mentorName: 'Amitabh Ghosh',
    mentorEmail: 'aghosh@sec-guard.com',
    deviceType: 'Desktop',
    joiningDate: '2026-08-10',
    completionDate: '2026-12-10',
    officeStartTime: '10:00',
    officeEndTime: '18:00',
    breakStartTime: '13:30',
    breakEndTime: '14:30',
    workingDays: ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'],
    status: 'Applied',
    consentGiven: true,
    consentTimestamp: Date.now() - 259200000,
    createdAt: Date.now() - 259200000,
    referencePhotoUrl: 'https://images.unsplash.com/photo-1500648767791-00dcc994a43e?w=200&auto=format&fit=crop&q=80',
    offerLetterUrl: 'https://interntrack.dev/documents/dev-stud-103/offer-letter.pdf',
    approvalLetterUrl: 'https://interntrack.dev/documents/dev-stud-103/approval-letter.pdf'
  },
  {
    uid: 'dev-stud-104',
    fullName: 'Kavya Patel',
    collegeEmail: 'kavya.p@ec.college.edu',
    branch: 'Electronics & Communication',
    internshipDomain: 'Embedded IoT Systems & Firmware',
    mentorName: 'Dr. Rajeshwar Singh',
    mentorEmail: 'rsingh@tech-devices.org',
    deviceType: 'Desktop',
    joiningDate: '2026-08-12',
    completionDate: '2026-11-15',
    officeStartTime: '09:00',
    officeEndTime: '17:00',
    breakStartTime: '13:00',
    breakEndTime: '14:00',
    workingDays: ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday'],
    status: 'Applied',
    consentGiven: true,
    consentTimestamp: Date.now() - 172800000,
    createdAt: Date.now() - 172800000,
    referencePhotoUrl: 'https://images.unsplash.com/photo-1544005313-94ddf0286df2?w=200&auto=format&fit=crop&q=80',
    offerLetterUrl: 'https://interntrack.dev/documents/dev-stud-104/offer-letter.pdf',
    approvalLetterUrl: 'https://interntrack.dev/documents/dev-stud-104/approval-letter.pdf'
  },
  {
    uid: 'dev-stud-105',
    fullName: 'Arjun Mehta',
    collegeEmail: 'arjun.mehta@cs.college.edu',
    branch: 'Computer Science & Engineering',
    internshipDomain: 'Software Development & Architecture',
    mentorName: 'Sneha Kulkarni',
    mentorEmail: 'sneha@fintech-solutions.co',
    deviceType: 'Desktop',
    joiningDate: '2026-08-15',
    completionDate: '2026-12-20',
    officeStartTime: '09:00',
    officeEndTime: '17:30',
    breakStartTime: '13:00',
    breakEndTime: '14:00',
    workingDays: ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday'],
    status: 'Applied',
    consentGiven: true,
    consentTimestamp: Date.now() - 86400000,
    createdAt: Date.now() - 86400000,
    referencePhotoUrl: 'https://images.unsplash.com/photo-1519085360753-af0119f7cbe7?w=200&auto=format&fit=crop&q=80',
    offerLetterUrl: 'https://interntrack.dev/documents/dev-stud-105/offer-letter.pdf',
    approvalLetterUrl: 'https://interntrack.dev/documents/dev-stud-105/approval-letter.pdf'
  },
  {
    uid: 'dev-stud-106',
    fullName: 'Priya Shinde',
    collegeEmail: 'priya.s@cs.college.edu',
    branch: 'Computer Science & Engineering',
    internshipDomain: 'Cloud Infrastructure & DevOps',
    mentorName: 'Vikram Aditya',
    mentorEmail: 'vikram@cloudcorp.org',
    deviceType: 'Desktop',
    joiningDate: new Date().toISOString().split('T')[0], // Today's date to test automatic Ongoing transition
    completionDate: '2026-11-30',
    officeStartTime: '09:00',
    officeEndTime: '17:30',
    breakStartTime: '13:00',
    breakEndTime: '14:00',
    workingDays: ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday'],
    status: 'Approved',
    approvedAt: Date.now() - 3600000,
    consentGiven: true,
    consentTimestamp: Date.now() - 172800000,
    createdAt: Date.now() - 172800000,
    referencePhotoUrl: 'https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=200&auto=format&fit=crop&q=80',
    offerLetterUrl: 'https://interntrack.dev/documents/dev-stud-106/offer-letter.pdf',
    approvalLetterUrl: 'https://interntrack.dev/documents/dev-stud-106/approval-letter.pdf'
  },
  {
    uid: 'dev-stud-107',
    fullName: 'Rahul Nair',
    collegeEmail: 'rahul.n@it.college.edu',
    branch: 'Information Technology',
    internshipDomain: 'Data Systems & ETL Pipelines',
    mentorName: 'Dr. Rajesh K.',
    mentorEmail: 'rajesh.k@cs.college.edu',
    deviceType: 'Desktop',
    joiningDate: '2026-07-01',
    completionDate: '2026-10-31',
    officeStartTime: '09:00',
    officeEndTime: '17:00',
    breakStartTime: '13:00',
    breakEndTime: '14:00',
    workingDays: ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday'],
    status: 'Ongoing',
    approvedAt: Date.now() - 604800000,
    ongoingSince: Date.now() - 518400000,
    consentGiven: true,
    consentTimestamp: Date.now() - 700000000,
    createdAt: Date.now() - 700000000,
    referencePhotoUrl: 'https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=200&auto=format&fit=crop&q=80',
    offerLetterUrl: `https://interntrack.dev/documents/dev-stud-107/offer-letter.pdf`,
    approvalLetterUrl: `https://interntrack.dev/documents/dev-stud-107/approval-letter.pdf`
  }
];

export const getPendingApplications = async (page = 1, status = 'ALL') => {
  try {
    const response = await api.get(`/hod/applications?page=${page}&status=${encodeURIComponent(status)}`);
    return response.data;
  } catch (err) {
    if (!err.response || err.code === 'ERR_NETWORK' || err.message === 'Network Error') {
      console.warn(`[DEV MOCK PORTAL] Central backend offline. Loading local evaluation ledger (Filter: ${status}).`);
      const cleanFilter = String(status || 'ALL').trim().toUpperCase();
      const activeFiltered = mockOfflinePendingList.filter(item => {
        if (cleanFilter === 'ALL' || cleanFilter === 'ALL STATUSES') return true;
        return item.status && item.status.toUpperCase() === cleanFilter;
      });
      const pageSize = 20;
      const totalRecords = activeFiltered.length;
      const totalPages = Math.ceil(totalRecords / pageSize) || 1;
      const fromIndex = (page - 1) * pageSize;
      const slice = activeFiltered.slice(fromIndex, fromIndex + pageSize);
      return {
        applications: slice,
        currentPage: page,
        pageSize,
        totalRecords,
        totalPages,
        activeFilter: status
      };
    }
    throw err;
  }
};

export const getAssignedStudents = async (mentorName) => {
  try {
    const response = await api.get(`/mentor/students`, {
      params: { mentorName }
    });
    return response.data;
  } catch (err) {
    if (!err.response || err.code === 'ERR_NETWORK' || err.message === 'Network Error') {
      console.warn(`[DEV MOCK PORTAL] Simulating assigned students for ${mentorName}`);
      return [];
    }
    throw err;
  }
};

export const approveApplication = async (uid, payload = {}) => {
  try {
    const response = await api.patch(`/hod/applications/${uid}/approve`, payload);
    return response.data;
  } catch (err) {
    if (!err.response || err.code === 'ERR_NETWORK' || err.message === 'Network Error') {
      console.warn(`[DEV MOCK PORTAL] Simulating approval for application ${uid}`);
      mockOfflinePendingList = mockOfflinePendingList.map(item => {
        if (item.uid === uid) {
          return { ...item, status: "Approved", approvedAt: Date.now() };
        }
        return item;
      });
      return { uid, status: "Approved", message: "Application approved (Local Evaluation Mode)" };
    }
    throw err;
  }
};

export const rejectApplication = async (uid, reason) => {
  try {
    const response = await api.patch(`/hod/applications/${uid}/reject`, { reason });
    return response.data;
  } catch (err) {
    if (!err.response || err.code === 'ERR_NETWORK' || err.message === 'Network Error') {
      console.warn(`[DEV MOCK PORTAL] Simulating rejection for application ${uid} with reason: ${reason}`);
      mockOfflinePendingList = mockOfflinePendingList.map(item => {
        if (item.uid === uid) {
          return { ...item, status: "Rejected", rejectedAt: Date.now(), rejectionReason: reason };
        }
        return item;
      });
      return { uid, status: "Rejected", message: "Application rejected (Local Evaluation Mode)" };
    }
    throw err;
  }
};

export const getStudentStatus = async (uid = 'dev-stud-106') => {
  try {
    const response = await api.get(`/status/${uid}`);
    return response.data;
  } catch (err) {
    if (!err.response || err.code === 'ERR_NETWORK' || err.message === 'Network Error') {
      console.warn(`[DEV MOCK PORTAL] Fetching simulated student lifecycle status for ${uid}`);
      const found = mockOfflinePendingList.find(item => item.uid === uid) || mockOfflinePendingList[0] || {};
      return {
        uid,
        status: found.status || 'Ongoing',
        joiningDate: found.joiningDate || '2026-07-20',
        completionDate: found.completionDate || '2026-11-30',
        internshipDomain: found.internshipDomain || 'Cloud Infrastructure & DevOps',
        mentorName: found.mentorName || 'Dr. Rajesh K.',
        mentorEmail: found.mentorEmail || 'rajesh.k@cs.college.edu',
        approvedAt: found.approvedAt || (Date.now() - 360000000),
        ongoingSince: found.status === 'Ongoing' ? (found.ongoingSince || (Date.now() - 180000000)) : null,
        rejectionReason: found.rejectionReason || null
      };
    }
    throw err;
  }
};

export const runStatusCheckNow = async () => {
  try {
    const response = await api.post('/admin/run-status-job');
    return response.data;
  } catch (err) {
    if (!err.response || err.code === 'ERR_NETWORK' || err.message === 'Network Error') {
      console.warn("[DEV MOCK PORTAL] Executing local simulation for on-demand status transition job.");
      const today = new Date().toISOString().split('T')[0];
      let transitioned = 0;
      mockOfflinePendingList = mockOfflinePendingList.map(item => {
        if (item.status === 'Approved' && item.joiningDate && item.joiningDate <= today) {
          transitioned++;
          return { ...item, status: "Ongoing", ongoingSince: Date.now() };
        }
        return item;
      });
      return { status: "SUCCESS", message: "Status check completed", transitionCount: transitioned };
    }
    throw err;
  }
};

export const getHighlightedStudents = async () => {
  try {
    const response = await api.get('/hod/highlighted');
    return response.data;
  } catch (err) {
    if (!err.response || err.code === 'ERR_NETWORK' || err.message === 'Network Error') {
      console.warn("[DEV MOCK PORTAL] Fetching simulated highlighted students list for HOD review.");
      return [
        { uid: "CS001", studentName: "Aditya Sharma", branch: "Computer Science & Engineering", mentor: "Dr. Rajesh K.", reasons: ["3+ absences", "excuse abuse 33%"], month: "2026-07", diaryRejectionCount: 1, absenceCount: 4, excuseAbusePercentage: 32.5 },
        { uid: "CS002", studentName: "Priya Patel", branch: "Information Technology", mentor: "Dr. Meenakshi S.", reasons: ["3+ diary rejections", "3+ absences"], month: "2026-07", diaryRejectionCount: 4, absenceCount: 3, excuseAbusePercentage: 10.0 },
        { uid: "CS003", studentName: "Rohan Verma", branch: "Artificial Intelligence & DS", mentor: "Prof. Suresh B.", reasons: ["excuse abuse 40%"], month: "2026-07", diaryRejectionCount: 0, absenceCount: 1, excuseAbusePercentage: 40.0 }
      ];
    }
    throw err;
  }
};

export const getStudentWarningHistory = async (uid) => {
  try {
    const response = await api.get(`/hod/highlighted/${uid}/history`);
    return response.data;
  } catch (err) {
    if (!err.response || err.code === 'ERR_NETWORK' || err.message === 'Network Error') {
      console.warn(`[DEV MOCK PORTAL] Fetching simulated warning history for candidate ${uid}`);
      if (uid === 'CS002') {
        return [
          { uid: "CS002", month: "2026-07", diaryRejectionCount: 4, absenceCount: 3, excuseAbusePercentage: 10.0, highlighted: true, reasons: ["3+ diary rejections", "3+ absences"] },
          { uid: "CS002", month: "2026-06", diaryRejectionCount: 3, absenceCount: 0, excuseAbusePercentage: 0.0, highlighted: true, reasons: ["3+ diary rejections"] },
          { uid: "CS002", month: "2026-05", diaryRejectionCount: 1, absenceCount: 0, excuseAbusePercentage: 5.0, highlighted: false, reasons: [] }
        ];
      }
      return [
        { uid, month: "2026-07", diaryRejectionCount: 1, absenceCount: 4, excuseAbusePercentage: 32.5, highlighted: true, reasons: ["3+ absences", "excuse abuse 33%"] },
        { uid, month: "2026-06", diaryRejectionCount: 0, absenceCount: 1, excuseAbusePercentage: 12.0, highlighted: false, reasons: [] }
      ];
    }
    throw err;
  }
};

export const getHighlightedCount = async () => {
  try {
    const response = await api.get('/hod/highlighted/count');
    return response.data;
  } catch (err) {
    if (!err.response || err.code === 'ERR_NETWORK' || err.message === 'Network Error') {
      return { count: 3, status: "SUCCESS" };
    }
    throw err;
  }
};

export const runEscalationCheckNow = async (targetMonth) => {
  try {
    const url = targetMonth ? `/admin/run-escalation-job?month=${encodeURIComponent(targetMonth)}` : '/admin/run-escalation-job';
    const response = await api.post(url);
    return response.data;
  } catch (err) {
    if (!err.response || err.code === 'ERR_NETWORK' || err.message === 'Network Error') {
      console.warn("[DEV MOCK PORTAL] Executing local simulation for manual escalation check.");
      return { status: "SUCCESS", message: "Escalation audit completed successfully.", flaggedCount: 3 };
    }
    throw err;
  }
};

export const getCompletionSummary = async (uid = 'CS001') => {
  const response = await api.get(`/completion/${uid}`);
  return response.data;
};

export const getCompletedStudents = async () => {
  try {
    const response = await api.get('/hod/completed');
    return response.data;
  } catch (err) {
    if (!err.response || err.code === 'ERR_NETWORK' || err.message === 'Network Error') {
      console.warn("[DEV MOCK PORTAL] Fetching simulated completed candidate list for HOD certification.");
      return [
        {
          uid: "CS001",
          studentName: "Aditya Sharma",
          branch: "Computer Science & Engineering",
          mentorName: "Dr. Rajesh K.",
          internshipDomain: "Cloud Infrastructure & DevOps",
          completionDate: "2026-07-26",
          attendancePercentage: 93.5,
          averageTestScore: 84.5,
          riskLevel: "Low"
        },
        {
          uid: "CS002",
          studentName: "Priya Patel",
          branch: "Information Technology",
          mentorName: "Dr. Meenakshi S.",
          internshipDomain: "Artificial Intelligence & Data Science",
          completionDate: "2026-07-22",
          attendancePercentage: 78.5,
          averageTestScore: 71.0,
          riskLevel: "High"
        }
      ];
    }
    throw err;
  }
};

// --- MODULE 10 SUPPORTING SYSTEMS (ANALYTICS & REPORTING) ---

export const getStudentAnalytics = async (uid) => {
  try {
    const response = await api.get(`/analytics/student/${uid}`);
    return response.data;
  } catch (err) {
    if (!err.response || err.code === 'ERR_NETWORK' || err.message === 'Network Error') {
      console.warn("[DEV MOCK PORTAL] Fetching simulated candidate analytics series (Module 10).");
      if (uid && uid.startsWith('empty-')) {
        return { hasData: false, message: "Not enough data to display yet", attendanceOverTime: [], diaryComplianceOverTime: [], testScoresOverTime: [] };
      }
      return {
        hasData: true,
        uid: uid,
        excuseUsagePercentage: 40.0,
        attendanceOverTime: [
          { date: 'Wk 1 (Jul 04)', value: 100.0 },
          { date: 'Wk 2 (Jul 11)', value: 85.0 },
          { date: 'Wk 3 (Jul 18)', value: 92.0 },
          { date: 'Wk 4 (Jul 25)', value: 96.5 }
        ],
        diaryComplianceOverTime: [
          { date: 'Wk 1 (Jul 04)', value: 80.0 },
          { date: 'Wk 2 (Jul 11)', value: 100.0 },
          { date: 'Wk 3 (Jul 18)', value: 90.0 },
          { date: 'Wk 4 (Jul 25)', value: 95.0 }
        ],
        testScoresOverTime: [
          { date: 'Midterm 1', value: 78.5 },
          { date: 'Weekly Quiz 2', value: 88.0 },
          { date: 'Proctored Exam 3', value: 92.0 }
        ]
      };
    }
    throw err;
  }
};

export const getHodAnalyticsOverview = async () => {
  try {
    const response = await api.get('/analytics/hod/overview');
    return response.data;
  } catch (err) {
    if (!err.response || err.code === 'ERR_NETWORK' || err.message === 'Network Error') {
      console.warn("[DEV MOCK PORTAL] Fetching simulated cached HOD overview aggregates (Module 10).");
      return {
        totalOngoing: 24,
        totalCompleted: 2,
        totalHighlighted: 3,
        avgAttendancePercentage: 91.2,
        avgTestScore: 83.5,
        suspiciousDiariesThisMonth: 1,
        highlightedThisMonth: 3,
        warningTrends: [
          { month: 'May 2026', count: 1 },
          { month: 'Jun 2026', count: 1 },
          { month: 'Jul 2026', count: 3 }
        ]
      };
    }
    throw err;
  }
};

export const getHodBranchComparison = async () => {
  try {
    const response = await api.get('/analytics/hod/branch-comparison');
    return response.data;
  } catch (err) {
    if (!err.response || err.code === 'ERR_NETWORK' || err.message === 'Network Error') {
      console.warn("[DEV MOCK PORTAL] Fetching simulated departmental branch comparison series (Module 10).");
      return [
        { branch: 'CSE', fullName: 'Computer Science & Eng.', attendance: 92.4, testScore: 85.2, studentCount: 42, hasData: true },
        { branch: 'ECE', fullName: 'Electronics & Comm. Eng.', attendance: 87.8, testScore: 79.5, studentCount: 35, hasData: true },
        { branch: 'IT', fullName: 'Information Technology', attendance: 94.1, testScore: 88.0, studentCount: 28, hasData: true },
        { branch: 'ME', fullName: 'Mechanical Engineering', attendance: 0.0, testScore: 0.0, studentCount: 0, hasData: false, statusText: 'No data yet' }
      ];
    }
    throw err;
  }
};

export default api;

