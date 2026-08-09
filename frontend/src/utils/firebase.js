// Re-export all Firebase instances and functions from centralized service integration point
import app, { auth, db, storage, initializeApp, getAuth, getFirestore, getStorage } from '../services/firebase';

export { auth, db, storage, initializeApp, getAuth, getFirestore, getStorage };
export default app;
