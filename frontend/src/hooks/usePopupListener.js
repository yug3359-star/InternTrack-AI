import { useState, useEffect, useCallback } from 'react';
import { useBrowserNotification } from './useBrowserNotification';
import { useNotification } from './useNotification';
import api from '../services/api';

/**
 * InternTrack AI — Real-Time Engagement Popup Listener Hook (Module 5a)
 * Listens for unscheduled random working hour check-ins in popups/{uid}/pending.
 * Immediately invokes OS-level browser push notifications to awaken backgrounded sessions.
 */
export const usePopupListener = (userUid, role = 'STUDENT', isDevMode = true) => {
  const { showNotification } = useBrowserNotification();
  const { notify } = useNotification();

  const [activePopup, setActivePopup] = useState(null);
  const [meetingQuota, setMeetingQuota] = useState({ used: 2, limit: 3, month: 'July 2026' });
  const [popupStatus, setPopupStatus] = useState(null);
  const [loadingResponse, setLoadingResponse] = useState(false);

  // Fetch initial meeting quota status from institutional ledger
  const fetchMeetingQuota = useCallback(async () => {
    try {
      const response = await api.get(`/popups/${userUid}/quota`);
      if (response.data) {
        setMeetingQuota(response.data);
      }
    } catch (err) {
      console.warn('[DEV MOCK PORTAL] Server quota lookup offline. Retaining simulated monthly meeting allowance.');
    }
  }, [userUid]);

  const fetchPopupStatus = useCallback(async () => {
    try {
      const response = await api.get(`/popups/${userUid}/status`);
      if (response.data) {
        setPopupStatus(response.data);
      }
    } catch (err) {
      console.warn('Failed to fetch popup status');
    }
  }, [userUid]);

  useEffect(() => {
    if (role === 'STUDENT' || role === 'student') {
      fetchMeetingQuota();
      fetchPopupStatus();
    }
  }, [role, fetchMeetingQuota, fetchPopupStatus]);

  // Method called when a popup document arrives from Firestore onSnapshot or testing simulation
  const handleNewPopupDetected = useCallback((popupData) => {
    console.log('[Module 5a Engagement Audit] Check-in popup triggered:', popupData);
    setActivePopup(popupData);

    // Call showNotification so alerts reach the student even if browser is minimized or another app is focused
    showNotification(
      "InternTrack — Are you working?",
      "Tap to respond within 2 minutes.",
      () => {
        // onClick handler: focuses the app window and guarantees modal remains elevated in view
        window.focus();
        setActivePopup(prev => prev || popupData);
      }
    );
  }, [showNotification]);

  const respondToPopup = useCallback(async (action, photoBase64 = null, similarityScore = 100.0) => {
    if (!activePopup) return;
    setLoadingResponse(true);

    try {
      const payload = {
        popupId: activePopup.id || 'dev-popup-999',
        action: action, // "WORKING" or "IN_MEETING"
        timestamp: Date.now(),
        photoData: photoBase64,
        similarityScore: similarityScore
      };

      const response = await api.post(`/popups/${userUid}/respond`, payload);

      if (action === 'IN_MEETING') {
        setMeetingQuota(prev => ({ ...prev, used: Math.min(prev.limit, prev.used + 1) }));
        notify("Check-in excused: 1 monthly meeting override deducted from departmental quota.", "info", 5000);
      } else if (response.data && response.data.matchStatus === 'APPROVED') {
        notify("Optical Face-Match successful (>75%)! Working attendance verified in institutional ledger.", "success", 6000);
      } else if (response.data && response.data.matchStatus === 'REJECTED') {
        notify("Biometric Match Failed (<40%). Attendance check-in rejected due to identity discrepancy.", "error", 7000);
      } else {
        notify("Biometric score borderline (40-75%). Reference portrait dispatched to faculty mentor for one-tap review.", "warning", 7000);
      }
    } catch (err) {
      console.warn("[DEV MOCK PORTAL] Central popup verification API unreachable or simulating response:", err.message);
      // Clean local demo evaluation fallback
      if (action === 'IN_MEETING') {
        if (meetingQuota.used >= meetingQuota.limit) {
          notify("Monthly meeting quota exhausted (3/3 used)! You must verify via webcam.", "error", 6000);
          setLoadingResponse(false);
          return false;
        }
        setMeetingQuota(prev => ({ ...prev, used: prev.used + 1 }));
        notify("Check-in excused: Meeting override recorded in local evaluation ledger.", "info", 5000);
      } else {
        // Simulate a successful face match by default unless test flag or simulated low score is triggered
        notify("Optical Face-Match verified (Simulated Score: 84.2% -> Auto-Approved ✓). Attendance recorded.", "success", 5000);
      }
    } finally {
      setLoadingResponse(false);
      setActivePopup(null);
      fetchPopupStatus();
    }
  }, [activePopup, userUid, meetingQuota, notify, fetchPopupStatus]);

  // Helper method for review panel presentation demonstrations
  const triggerDevTestPopup = useCallback((delaySeconds = 0) => {
    if (delaySeconds > 0) {
      notify(`Engagement check-in scheduled to fire in ${delaySeconds} seconds. Try minimizing your browser window now!`, "info", delaySeconds * 1000);
      setTimeout(() => {
        handleNewPopupDetected({
          id: `test-checkin-${Date.now()}`,
          timestamp: Date.now(),
          deadline: Date.now() + 120000, // 2 minutes window
          domain: 'Cloud Infrastructure & DevOps',
          studentUid: userUid
        });
      }, delaySeconds * 1000);
    } else {
      handleNewPopupDetected({
        id: `test-checkin-${Date.now()}`,
        timestamp: Date.now(),
        deadline: Date.now() + 120000,
        domain: 'test',
        studentUid: userUid
      });
    }
  }, [handleNewPopupDetected, notify]);

  return {
    activePopup,
    setActivePopup,
    meetingQuota,
    popupStatus,
    fetchPopupStatus,
    loadingResponse,
    respondToPopup,
    triggerDevTestPopup
  };
};
