import React, { createContext, useContext, useState, useEffect, useCallback } from 'react';
import { useNotification } from '../hooks/useNotification';

const BrowserNotificationContext = createContext();

/**
 * InternTrack AI — Centralized OS Browser Notification Provider (Module 10)
 * Manages Notification API permissions, Service Worker messaging, and in-tab toast fallbacks
 * once at app-level, preventing duplicated permission prompts across Modules 5a, 5b, and 5c.
 */
export const BrowserNotificationProvider = ({ children }) => {
  const { notify } = useNotification();
  const [permission, setPermission] = useState('unsupported');

  useEffect(() => {
    if (typeof window !== 'undefined' && 'Notification' in window) {
      setPermission(Notification.permission);
    } else {
      setPermission('unsupported');
    }
  }, []);

  const requestPermission = useCallback(async () => {
    if (typeof window !== 'undefined' && 'Notification' in window) {
      try {
        const res = await Notification.requestPermission();
        setPermission(res);
        if (res === 'granted') {
          notify("OS Browser push notifications officially granted & verified! ✓", "success", 4000);
        } else if (res === 'denied') {
          notify("Browser notifications denied. InternTrack will rely on in-tab audit fallback banners.", "warning", 5000);
        }
        return res;
      } catch (e) {
        console.error("Failed to request OS notification permission:", e);
        return Notification.permission;
      }
    }
    notify("OS Web Notification API is unsupported in this browser profile.", "warning", 5000);
    return 'unsupported';
  }, [notify]);

  const showNotification = useCallback((title, body, onClick) => {
    const isSupported = typeof window !== 'undefined' && 'Notification' in window;
    const currentPerm = isSupported ? Notification.permission : 'denied';
    setPermission(currentPerm);

    if (isSupported && currentPerm === 'granted') {
      try {
        if (navigator.serviceWorker && navigator.serviceWorker.controller) {
          navigator.serviceWorker.controller.postMessage({
            type: 'SHOW_NOTIFICATION',
            payload: {
              title,
              options: {
                body,
                icon: '/favicon.ico',
                badge: '/favicon.ico',
                requireInteraction: true,
                tag: 'interntrack-engagement-checkin'
              }
            }
          });
        } else {
          const notif = new Notification(title, {
            body,
            icon: '/favicon.ico',
            requireInteraction: true,
            tag: 'interntrack-engagement-checkin'
          });
          notif.onclick = () => {
            window.focus();
            notif.close();
            if (typeof onClick === 'function') onClick();
          };
        }

        if (typeof onClick === 'function') {
          const handleFocus = () => {
            onClick();
            window.removeEventListener('focus', handleFocus);
          };
          window.addEventListener('focus', handleFocus);
          if (document.hasFocus()) {
            onClick();
            window.removeEventListener('focus', handleFocus);
          }
        }
      } catch (err) {
        console.warn('[BrowserNotificationProvider] OS notification dispatch faulted, using fallback toast:', err);
        notify(`${title}: ${body}`, 'warning', 8000);
        if (typeof onClick === 'function') onClick();
      }
    } else {
      console.warn('[BrowserNotificationProvider] OS permissions denied or unavailable. Triggering in-tab fallback banner.');
      notify(`[ENGAGEMENT AUDIT] ${title} — ${body}`, 'warning', 10000);
      if (typeof onClick === 'function') onClick();
    }
  }, [notify]);

  return (
    <BrowserNotificationContext.Provider value={{ permission, requestPermission, showNotification }}>
      {children}
    </BrowserNotificationContext.Provider>
  );
};

export const useBrowserNotificationContext = () => {
  const context = useContext(BrowserNotificationContext);
  if (!context) {
    throw new Error("useBrowserNotificationContext must be utilized within a BrowserNotificationProvider scope.");
  }
  return context;
};
