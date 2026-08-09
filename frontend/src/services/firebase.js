import { initializeApp, getApps } from 'firebase/app';
import { getAuth } from 'firebase/auth';
import { getFirestore } from 'firebase/firestore';
import { getStorage } from 'firebase/storage';

// Strictly load Firebase Web SDK connection properties from Vite .env environment variables.
// Never hardcode API keys or bucket names directly in git-committed source code.
const firebaseConfig = {
  apiKey: import.meta.env.VITE_FIREBASE_API_KEY || "",
  authDomain: import.meta.env.VITE_FIREBASE_AUTH_DOMAIN || "",
  projectId: import.meta.env.VITE_FIREBASE_PROJECT_ID || "",
  storageBucket: import.meta.env.VITE_FIREBASE_STORAGE_BUCKET || "",
  messagingSenderId: import.meta.env.VITE_FIREBASE_MESSAGING_SENDER_ID || "",
  appId: import.meta.env.VITE_FIREBASE_APP_ID || ""
};

let app;
try {
  if (!getApps().length) {
    app = initializeApp(firebaseConfig);
  } else {
    app = getApps()[0];
  }
} catch (error) {
  console.error("[InternTrack SDK Error] Failed to initialize Firebase App runtime:", error);
}

export const auth = app ? getAuth(app) : null;
export const db = app ? getFirestore(app) : null;
export const storage = app ? getStorage(app) : null;

// Expose core SDK instances globally on window to enable live DevTools security rule auditing & validation scripts
if (typeof window !== 'undefined') {
  window.firebaseDb = db;
  window.firebaseAuth = auth;
  window.firebaseStorage = storage;
}

// Re-export core initialization routines and service getters for consistent consumption
export { initializeApp, getAuth, getFirestore, getStorage };

// Diagnostic console log confirming successful client SDK connection on app load
if (auth && auth.app) {
  console.info(`[InternTrack SDK Diagnostic] Firebase Client SDK connected successfully. Project ID: [${auth.app.options.projectId}]`);
}

export default app;
