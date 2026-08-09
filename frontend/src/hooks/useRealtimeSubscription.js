import { useEffect, useRef } from 'react';
import { db } from '../services/firebase';
import { collection, query, where, onSnapshot } from 'firebase/firestore';

/**
 * InternTrack AI — Centralized Real-Time Firestore Subscription Hook (Module 10 Consolidation)
 * Replaces redundant onSnapshot boilerplate across Modules 5a (Popups), 5b (Attendance), and 5c (Tests).
 * Gracefully manages SDK disconnection faults, offline dev fallback simulations, and memory unmounts.
 */
export const useRealtimeSubscription = ({
  collectionName,
  queryConditions = [],
  onDataReceived,
  onFallbackQuery,
  enabled = true
}) => {
  const isMountedRef = useRef(true);

  useEffect(() => {
    isMountedRef.current = true;
    if (!enabled || !collectionName) return;

    let unsubscribe = null;
    let fallbackExecuted = false;

    try {
      if (db && typeof onSnapshot === 'function') {
        const colRef = collection(db, collectionName);
        let q = colRef;

        if (Array.isArray(queryConditions) && queryConditions.length > 0) {
          queryConditions.forEach(cond => {
            if (Array.isArray(cond) && cond.length === 3) {
              q = query(q, where(cond[0], cond[1], cond[2]));
            }
          });
        }

        unsubscribe = onSnapshot(
          q,
          (snapshot) => {
            if (!isMountedRef.current) return;
            const docs = [];
            snapshot.forEach(doc => {
              docs.push({ id: doc.id, ...doc.data() });
            });
            if (typeof onDataReceived === 'function') {
              onDataReceived(docs);
            }
          },
          (error) => {
            console.warn(`[Module 10 Real-Time Subscription] Firestore onSnapshot stream offline for [${collectionName}]:`, error.message);
            if (!fallbackExecuted && typeof onFallbackQuery === 'function') {
              fallbackExecuted = true;
              onFallbackQuery();
            }
          }
        );
      } else if (typeof onFallbackQuery === 'function') {
        onFallbackQuery();
      }
    } catch (err) {
      console.warn(`[Module 10 Real-Time Subscription] Could not attach Firebase subscription to [${collectionName}]. Using local simulation:`, err.message);
      if (typeof onFallbackQuery === 'function') {
        onFallbackQuery();
      }
    }

    return () => {
      isMountedRef.current = false;
      if (unsubscribe && typeof unsubscribe === 'function') {
        unsubscribe();
      }
    };
  }, [collectionName, enabled, JSON.stringify(queryConditions)]);
};
