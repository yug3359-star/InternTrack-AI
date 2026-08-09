import { useState, useEffect, useCallback } from 'react';

/**
 * Custom hook leveraging HTML5 Page Visibility API to monitor tab switching during proctored exams.
 * Increments an audit counter whenever document.hidden becomes true while an exam is active.
 *
 * @param {boolean} isExamActive - Flag indicating if the student is actively engaged in a timed test session
 * @returns {object} { tabSwitchCount, resetTabSwitches }
 */
export function useTabVisibility(isExamActive = true) {
  const [tabSwitchCount, setTabSwitchCount] = useState(0);

  useEffect(() => {
    if (!isExamActive) return;

    const handleVisibilityChange = () => {
      if (document.hidden) {
        setTabSwitchCount((prev) => {
          const updated = prev + 1;
          console.warn(`[Proctoring Audit] Tab exit or blur detected during active exam. Total switches: ${updated}`);
          return updated;
        });
      }
    };

    document.addEventListener('visibilitychange', handleVisibilityChange);

    return () => {
      document.removeEventListener('visibilitychange', handleVisibilityChange);
    };
  }, [isExamActive]);

  const resetTabSwitches = useCallback(() => {
    setTabSwitchCount(0);
  }, []);

  return { tabSwitchCount, resetTabSwitches };
}
