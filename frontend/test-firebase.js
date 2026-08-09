import dotenv from 'dotenv';
import { initializeApp } from 'firebase/app';
import { getFirestore, collection, getDocs, limit, query } from 'firebase/firestore';

dotenv.config();

const firebaseConfig = {
  apiKey: process.env.VITE_FIREBASE_API_KEY,
  authDomain: process.env.VITE_FIREBASE_AUTH_DOMAIN,
  projectId: process.env.VITE_FIREBASE_PROJECT_ID,
  storageBucket: process.env.VITE_FIREBASE_STORAGE_BUCKET,
  messagingSenderId: process.env.VITE_FIREBASE_MESSAGING_SENDER_ID,
  appId: process.env.VITE_FIREBASE_APP_ID
};

console.log("Firebase config loaded, API Key length:", firebaseConfig.apiKey ? firebaseConfig.apiKey.length : 0);

try {
  const app = initializeApp(firebaseConfig);
  const db = getFirestore(app);
  
  console.log("Firebase App Initialized successfully!");
  
  // Try a simple operation
  const testConnection = async () => {
    try {
      // Just try to read 1 document from any dummy collection to test connection
      const q = query(collection(db, "test_connection_dummy"), limit(1));
      await getDocs(q);
      console.log("Firestore connection test passed!");
      process.exit(0);
    } catch (e) {
      if (e.code === 'permission-denied') {
        console.log("Firestore connection test passed! (Permission denied is expected for random collections but connection works)");
        process.exit(0);
      } else {
        console.error("Firestore test failed:", e);
        process.exit(1);
      }
    }
  };
  
  testConnection();
} catch (e) {
  console.error("Failed to initialize Firebase:", e);
  process.exit(1);
}
