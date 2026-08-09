/**
 * InternTrack AI — Resilient Working-Hour Engagement Service Worker (Module 5a)
 * 
 * WHY A SERVICE WORKER IS REQUIRED:
 * Standard in-page React state and DOM timers pause or get aggressively throttled by modern browsers
 * when a tab is backgrounded or minimized. A Service Worker runs in a separate background thread,
 * allowing InternTrack AI to reliably fire OS-level web push notifications even when the student's 
 * window is minimized or another application (e.g., IDE, docs) is actively in focus.
 * 
 * WHY PERMISSIONS CAN BE DENIED:
 * Browsers strictly protect users from unsolicited notifications. Permission will be denied if:
 * 1. The user explicitly clicks "Block" or "Don't Allow" when prompted.
 * 2. The browser's site settings or enterprise group policies globally disable web notifications.
 * 3. In private/incognito browsing modes, some browsers auto-deny notification requests.
 * 
 * CRITICAL PRODUCTION DEPLOYMENT REQUIREMENT (MODULE 14):
 * Web Notification APIs and Service Worker registrations have a strict secure origin requirement.
 * They WILL ONLY FUNCTION OVER HTTPS in production environments. Over plain HTTP, notifications are
 * silently rejected for security reasons (except on localhost / 127.0.0.1 during development).
 * Ensure standard TLS certificates are configured before deploying to live institutional review servers.
 * 
 * HARD SYSTEM CONSTRAINT (BROWSER PROCESS TERMINATION):
 * This service worker can only awaken tabs or emit alarms while the main browser application remains 
 * running in the OS background (minimized, background tab, or another window). If the student completely 
 * closes/terminates the browser process, zero JavaScript executes. In InternTrack compliance rules,
 * fully closing the browser during working hours is treated as non-compliant / absent, equivalent to logging out.
 */

self.addEventListener('install', (event) => {
  // Activate service worker immediately without waiting for existing tabs to terminate
  self.skipWaiting();
});

self.addEventListener('activate', (event) => {
  // Claim control over all open InternTrack tabs instantly upon activation
  event.waitUntil(self.clients.claim());
});

/**
 * Handle notification click events (when the student taps the Windows/macOS popup notification).
 * Automatically brings the InternTrack tab into user view and focuses the browser window.
 */
self.addEventListener('notificationclick', (event) => {
  event.notification.close();

  event.waitUntil(
    self.clients.matchAll({ type: 'window', includeUncontrolled: true }).then((clientList) => {
      // If an InternTrack dashboard window or tab exists, focus it immediately
      for (let i = 0; i < clientList.length; i++) {
        const client = clientList[i];
        if (client.url && 'focus' in client) {
          return client.focus();
        }
      }
      // If no open window is found, launch a new client session directly to the student dashboard
      if (self.clients.openWindow) {
        return self.clients.openWindow('/student/dashboard');
      }
    })
  );
});

/**
 * Listener for explicit notification postMessage commands sent by React client hooks (useBrowserNotification).
 */
self.addEventListener('message', (event) => {
  if (event.data && event.data.type === 'SHOW_NOTIFICATION') {
    const { title, options } = event.data.payload;
    self.registration.showNotification(title, options);
  }
});
