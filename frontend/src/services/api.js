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

/**
 * Resend verification email
 */
export const resendVerificationEmail = async (email) => {
  const response = await api.post('/auth/resend-verification', { email });
  return response.data;
};

/* =========================================================================================
 * MODULE 3: HOD APPROVAL & COMPLIANCE EVALUATION API ENDPOINTS
 * Includes seamless local evaluation fallback when central Spring Boot service is offline.
 * ========================================================================================= */


export const getPendingApplications = async (page = 1, status = 'ALL') => {
  try {
    const response = await api.get(`/hod/applications?page=${page}&status=${encodeURIComponent(status)}`);
    return response.data;
  } catch (err) {
    console.error("Failed to fetch pending applications:", err.message);
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

export const createApplication = async (data) => {
  try {
    const response = await api.post('/hod/applications', data);
    return response.data;
  } catch (err) {
    if (!err.response || err.code === 'ERR_NETWORK' || err.message === 'Network Error') {
      console.warn(`[DEV MOCK PORTAL] Simulating creation for application`);
      const newApp = {
        uid: 'dev-stud-' + Date.now(),
        ...data,
        createdAt: Date.now()
      };
      mockOfflinePendingList = [newApp, ...mockOfflinePendingList];
      return newApp;
    }
    throw err;
  }
};

export const updateApplication = async (uid, data) => {
  try {
    const response = await api.put(`/hod/applications/${uid}`, data);
    return response.data;
  } catch (err) {
    if (!err.response || err.code === 'ERR_NETWORK' || err.message === 'Network Error') {
      console.warn(`[DEV MOCK PORTAL] Simulating update for application ${uid}`);
      mockOfflinePendingList = mockOfflinePendingList.map(item => {
        if (item.uid === uid) {
          return { ...item, ...data, updatedAt: Date.now() };
        }
        return item;
      });
      return { uid, message: "Application updated (Local Evaluation Mode)" };
    }
    throw err;
  }
};

export const deleteApplication = async (uid) => {
  try {
    const response = await api.delete(`/hod/applications/${uid}`);
    return response.data;
  } catch (err) {
    if (!err.response || err.code === 'ERR_NETWORK' || err.message === 'Network Error') {
      console.warn(`[DEV MOCK PORTAL] Simulating deletion for application ${uid}`);
      mockOfflinePendingList = mockOfflinePendingList.filter(item => item.uid !== uid);
      return { uid, message: "Application deleted (Local Evaluation Mode)" };
    }
    throw err;
  }
};

export const exportExcelReport = async (branch = '', status = '') => {
  try {
    const params = new URLSearchParams();
    if (branch) params.append('branch', branch);
    if (status) params.append('status', status);

    const response = await api.get(`/hod/export-excel?${params.toString()}`, {
      responseType: 'blob' // Essential for receiving binary files
    });
    return response.data;
  } catch (err) {
    if (!err.response || err.code === 'ERR_NETWORK' || err.message === 'Network Error') {
      console.warn(`[DEV MOCK PORTAL] Simulating Excel export failure (Network offline)`);
      throw new Error("Unable to download Excel report in offline simulation mode.");
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
  try {
    const response = await api.get(`/completion/${uid}`);
    return response.data;
  } catch (err) {
    if (err.response && err.response.status === 404) {
      return null;
    }
    throw err;
  }
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
export const getFacultyMentors = async () => {
  try {
    const response = await api.get('/hod/applications/mentors');
    return response.data;
  } catch (err) {
    if (!err.response || err.code === 'ERR_NETWORK') {
      return [];
    }
    throw err;
  }
};

export default api;

