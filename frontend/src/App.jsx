import React from 'react';
import { Routes, Route, Navigate, useLocation } from 'react-router-dom';
import Navbar from './components/Navbar';
import Sidebar from './components/Sidebar';
import Login from './pages/Login';
import Register from './pages/Register';
import StudentRegister from './pages/student/Register';
import PrivacyNotice from './pages/PrivacyNotice';
import Unauthorized from './pages/Unauthorized';
import ProtectedRoute from './components/ProtectedRoute';
import StudentDashboard from './pages/student/StudentDashboard';
import TestSession from './pages/student/TestSession';
import MentorDashboard from './pages/mentor/MentorDashboard';
import BorderlineReviews from './pages/mentor/BorderlineReviews';
import StudentDetail from './pages/mentor/StudentDetail';
import HodDashboard from './pages/hod/HodDashboard';
import PendingApplications from './pages/hod/PendingApplications';
import DiaryEntry from './pages/student/DiaryEntry';
import SuspiciousDiaries from './pages/mentor/SuspiciousDiaries';
import HighlightedStudents from './pages/hod/HighlightedStudents';
import StudentWarningHistory from './pages/hod/StudentWarningHistory';
import CompletionSummary from './pages/student/CompletionSummary';
import CompletedStudents from './pages/hod/CompletedStudents';
import Analytics from './pages/student/Analytics';
import AnalyticsOverview from './pages/hod/AnalyticsOverview';
import styles from './App.module.css';

const App = () => {
  const location = useLocation();
  
  // Verify if current path corresponds to an academic dashboard requiring persistent left navigation
  const isDashboardRoute = location.pathname.includes('/dashboard') || 
                           location.pathname.includes('/hod/pending-applications') || 
                           location.pathname.includes('/mentor/borderline-reviews') || 
                           location.pathname.includes('/mentor/student-detail') ||
                           location.pathname.includes('/diaries') ||
                           location.pathname.includes('/highlighted') ||
                           location.pathname.includes('/completed') ||
                           location.pathname.includes('/completion-summary') ||
                           location.pathname.includes('/analytics');

  return (
    <div className={styles.appWrapper}>
      <Navbar />
      
      <div className={isDashboardRoute ? styles.dashboardLayout : styles.standaloneLayout}>
        {isDashboardRoute && <Sidebar />}
        
        <main className={isDashboardRoute ? styles.dashboardMain : styles.standaloneMain}>
          <Routes>
            <Route path="/" element={<Navigate to="/login" replace />} />
            <Route path="/login" element={<Login />} />
            <Route path="/register" element={<Register />} />
            <Route path="/privacy" element={<PrivacyNotice />} />
            <Route path="/unauthorized" element={<Unauthorized />} />
            
            {/* Student-Scoped Institutional Routes */}
            <Route path="/student/register" element={<StudentRegister />} />
            <Route path="/student/dashboard" element={<ProtectedRoute allowedRoles={["student"]}><StudentDashboard /></ProtectedRoute>} />
            <Route path="/student/diaries" element={<ProtectedRoute allowedRoles={["student"]}><DiaryEntry /></ProtectedRoute>} />
            <Route path="/student/test-session/:testId?" element={<ProtectedRoute allowedRoles={["student"]}><TestSession /></ProtectedRoute>} />
            <Route path="/student/completion-summary/:uid?" element={<ProtectedRoute allowedRoles={["student"]}><CompletionSummary /></ProtectedRoute>} />
            <Route path="/student/analytics" element={<ProtectedRoute allowedRoles={["student"]}><Analytics /></ProtectedRoute>} />

            {/* Mentor-Scoped Faculty Routes */}
            <Route path="/mentor/dashboard" element={<ProtectedRoute allowedRoles={["mentor"]}><MentorDashboard /></ProtectedRoute>} />
            <Route path="/mentor/borderline-reviews" element={<ProtectedRoute allowedRoles={["mentor"]}><BorderlineReviews /></ProtectedRoute>} />
            <Route path="/mentor/suspicious-diaries" element={<ProtectedRoute allowedRoles={["mentor"]}><SuspiciousDiaries /></ProtectedRoute>} />
            <Route path="/mentor/student-detail/:studentId?" element={<ProtectedRoute allowedRoles={["mentor"]}><StudentDetail /></ProtectedRoute>} />

            {/* HOD-Scoped Department Administrative Routes */}
            <Route path="/hod/dashboard" element={<ProtectedRoute allowedRoles={["hod"]}><HodDashboard /></ProtectedRoute>} />
            <Route path="/hod/pending-applications" element={<ProtectedRoute allowedRoles={["hod"]}><PendingApplications /></ProtectedRoute>} />
            <Route path="/hod/suspicious-diaries" element={<ProtectedRoute allowedRoles={["hod"]}><SuspiciousDiaries /></ProtectedRoute>} />
            <Route path="/hod/highlighted" element={<ProtectedRoute allowedRoles={["hod"]}><HighlightedStudents /></ProtectedRoute>} />
            <Route path="/hod/highlighted/:uid/history" element={<ProtectedRoute allowedRoles={["hod"]}><StudentWarningHistory /></ProtectedRoute>} />
            <Route path="/hod/completed" element={<ProtectedRoute allowedRoles={["hod"]}><CompletedStudents /></ProtectedRoute>} />
            <Route path="/hod/completed/:uid/summary" element={<ProtectedRoute allowedRoles={["hod"]}><CompletionSummary /></ProtectedRoute>} />
            <Route path="/hod/analytics" element={<ProtectedRoute allowedRoles={["hod"]}><AnalyticsOverview /></ProtectedRoute>} />
            
            <Route path="*" element={<Navigate to="/login" replace />} />
          </Routes>
        </main>
      </div>
    </div>
  );
};



export default App;
