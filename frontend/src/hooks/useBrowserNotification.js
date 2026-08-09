import { useBrowserNotificationContext } from '../context/BrowserNotificationProvider';

/**
 * InternTrack AI — Consolidated Browser Notification Hook (Module 10)
 * Delegates directly to the single top-level BrowserNotificationProvider to prevent
 * duplicate permission requests and redundant service worker postMessage logic across feature modules.
 */
export const useBrowserNotification = () => {
  try {
    return useBrowserNotificationContext();
  } catch (err) {
    console.warn("[useBrowserNotification] Utilized outside provider scope, returning safe no-op fallback:", err.message);
    return {
      showNotification: () => {},
      requestPermission: async () => 'denied',
      permission: 'unsupported'
    };
  }
};

