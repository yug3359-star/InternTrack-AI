import React from 'react';
import ReactDOM from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import { AuthProvider } from './context/AuthContext';
import { NotificationProvider } from './context/NotificationContext';
import { BrowserNotificationProvider } from './context/BrowserNotificationProvider';
import App from './App';
import './index.css';

// Register background Service Worker for resilient OS-level working hour push notifications (Module 5a/10)
if ('serviceWorker' in navigator) {
  window.addEventListener('load', () => {
    navigator.serviceWorker.register('/sw.js')
      .then((registration) => {
        console.log('[ServiceWorker] Successfully registered for institutional engagement auditing:', registration.scope);
      })
      .catch((err) => {
        console.warn('[ServiceWorker] Registration failed (verify localhost or HTTPS origin requirement):', err);
      });
  });
}

ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <BrowserRouter>
      <NotificationProvider>
        <BrowserNotificationProvider>
          <AuthProvider>
            <App />
          </AuthProvider>
        </BrowserNotificationProvider>
      </NotificationProvider>
    </BrowserRouter>
  </React.StrictMode>
);
