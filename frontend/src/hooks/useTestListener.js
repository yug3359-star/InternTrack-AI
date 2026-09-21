import { useState, useEffect, useRef, useCallback } from 'react';
import { db } from '../services/firebase';
import { collection, query, where, onSnapshot } from 'firebase/firestore';
import { useBrowserNotification } from './useBrowserNotification';
import axios from 'axios';

const API_BASE_URL = 'http://localhost:8080/api';

/**
 * Custom hook subscribing to real-time Firestore proctored test triggers and history for a student.
 * Dispatches OS push notifications upon detection of active test windows (status: "awaiting_start").
 *
 * @param {string} studentUid - Active student identifier
 * @param {function} onNavigate - Optional callback to navigate to exam session (e.g. useNavigate hook)
 */
export function useTestListener(studentUid, onNavigate) {
  const [testHistory, setTestHistory] = useState([]);
  const [activeTest, setActiveTest] = useState(null);
  const [loading, setLoading] = useState(true);

  const { showNotification } = useBrowserNotification();
  const notifiedTestIdsRef = useRef(new Set());

  const fetchHistoryFromBackend = useCallback(async () => {
    if (!studentUid) return;
    try {
      const resp = await axios.get(`${API_BASE_URL}/test/history/${studentUid}`);
      if (resp.data && resp.data.records) {
        const records = resp.data.records;
        setTestHistory(records);
        const pending = records.find(r => r.status === 'awaiting_start' || r.status === 'in_progress');
        setActiveTest(pending || null);
      }
    } catch (err) {
      console.warn('Could not fetch test history from backend API, utilizing snapshot state:', err.message);
    } finally {
      setLoading(false);
    }
  }, [studentUid]);

  useEffect(() => {
    if (!studentUid) {
      setLoading(false);
      return;
    }

    let unsubscribe = null;
    try {
      if (db) {
        const testsRef = collection(db, 'tests');
        const q = query(testsRef, where('uid', '==', studentUid));

        unsubscribe = onSnapshot(q, (snapshot) => {
          const records = [];
          snapshot.forEach((doc) => {
            records.push(doc.data());
          });

          // Sort descending by date/triggeredAt
          records.sort((a, b) => (b.triggeredAt || 0) - (a.triggeredAt || 0));
          setTestHistory(records);
          setLoading(false);

          // Identify active exam awaiting start or currently in progress
          const currentActive = records.find(r => r.status === 'awaiting_start' || r.status === 'in_progress');
          setActiveTest(currentActive || null);

          // Trigger OS browser push notification for new unstarted test windows
          if (currentActive && currentActive.status === 'awaiting_start' && !notifiedTestIdsRef.current.has(currentActive.id)) {
            notifiedTestIdsRef.current.add(currentActive.id);
            console.info(`[Proctored AI Test] New weekly test detected: ${currentActive.id}. Dispatching OS push notification.`);
            
            showNotification(
              "InternTrack — Weekly Test Available",
              "You have 1 hour to start. Once started, you'll have 20 minutes to complete it.",
              () => {
                window.focus();
                if (onNavigate) {
                  onNavigate(`/student/test-session/${currentActive.id}`);
                }
              }
            );
          }
        }, (err) => {
          // Silent fallback to REST if firestore rules deny access (e.g. mentor masquerading as student)
          fetchHistoryFromBackend();
        });
      } else {
        fetchHistoryFromBackend();
      }
    } catch (e) {
      console.warn('Could not connect to Firebase Firestore SDK in useTestListener:', e.message);
      fetchHistoryFromBackend();
    }

    return () => {
      if (unsubscribe && typeof unsubscribe === 'function') {
        unsubscribe();
      }
    };
  }, [studentUid, showNotification, onNavigate, fetchHistoryFromBackend]);

  return { activeTest, testHistory, loading, refreshHistory: fetchHistoryFromBackend };
}
