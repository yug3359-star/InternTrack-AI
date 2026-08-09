import React, { createContext, useReducer, useEffect, useCallback, useContext } from 'react';
import { auth } from '../utils/firebase';
import { onAuthStateChanged, signInWithEmailAndPassword, signOut } from 'firebase/auth';

export const AuthContext = createContext();

export const useAuth = () => useContext(AuthContext);

// Reducer actions for institutional authentication lifecycle
const ACTIONS = {
  SET_AUTH_STATE: 'SET_AUTH_STATE',
  SET_LOADING: 'SET_LOADING',
  SET_DEV_SESSION: 'SET_DEV_SESSION',
  LOGOUT: 'LOGOUT',
  SET_ERROR: 'SET_ERROR'
};

const authReducer = (state, action) => {
  switch (action.type) {
    case ACTIONS.SET_AUTH_STATE:
      return {
        ...state,
        isAuthenticated: !!action.payload.user,
        user: action.payload.user,
        role: action.payload.role || state.role,
        loading: false,
        error: null
      };
    case ACTIONS.SET_DEV_SESSION:
      return {
        ...state,
        isAuthenticated: true,
        user: action.payload.user,
        role: action.payload.role,
        isDevMode: true,
        loading: false,
        error: null
      };
    case ACTIONS.LOGOUT:
      return {
        ...state,
        isAuthenticated: false,
        user: null,
        loading: false,
        error: null
      };
    case ACTIONS.SET_LOADING:
      return { ...state, loading: action.payload };
    case ACTIONS.SET_ERROR:
      return { ...state, error: action.payload, loading: false };
    default:
      return state;
  }
};

const initialState = {
  isAuthenticated: false,
  user: null,
  role: 'STUDENT', // Default initial portal view
  loading: true,
  isDevMode: false,
  error: null
};

export const AuthProvider = ({ children }) => {
  const [state, dispatch] = useReducer(authReducer, initialState);

  // Initialize session state from Firebase auth or existing developer storage session
  useEffect(() => {
    const savedDevSession = sessionStorage.getItem('interntrack_dev_session');
    if (savedDevSession) {
      try {
        const parsed = JSON.parse(savedDevSession);
        dispatch({ type: ACTIONS.SET_DEV_SESSION, payload: parsed });
        return;
      } catch (e) {
        sessionStorage.removeItem('interntrack_dev_session');
      }
    }

    if (!auth) {
      // Fallback developer initialization for testing UI layouts without Firebase active
      dispatch({ 
        type: ACTIONS.SET_DEV_SESSION, 
        payload: { 
          user: { uid: 'dev-student-01', email: 'student.s26@cs.college.edu', displayName: 'Ananya Sharma (Dev Mode)' },
          role: 'STUDENT'
        } 
      });
      return;
    }

    const unsubscribe = onAuthStateChanged(auth, async (firebaseUser) => {
      if (firebaseUser) {
        try {
          const tokenResult = await firebaseUser.getIdTokenResult();
          const userRole = tokenResult.claims.role || 'STUDENT';
          dispatch({
            type: ACTIONS.SET_AUTH_STATE,
            payload: { user: firebaseUser, role: userRole }
          });
        } catch (err) {
          console.warn("Failed retrieving ID token claims:", err);
          dispatch({ type: ACTIONS.SET_AUTH_STATE, payload: { user: firebaseUser, role: 'STUDENT' } });
        }
      } else {
        dispatch({ type: ACTIONS.LOGOUT });
      }
    });

    return () => unsubscribe();
  }, []);

  // Module 5a: Automatically prompt for OS-level working hour notification permission on student authentication
  useEffect(() => {
    if (state.isAuthenticated && (state.role === 'STUDENT' || state.role === 'student')) {
      if ('Notification' in window && Notification.permission === 'default') {
        Notification.requestPermission()
          .then(status => {
            console.log(`[Module 5a Engagement Notification] Permission status: ${status}`);
          })
          .catch(err => console.warn("Browser prevented notification request:", err));
      }
    }
  }, [state.isAuthenticated, state.role]);

  const login = useCallback(async (email, password, selectedRole = null) => {
    dispatch({ type: ACTIONS.SET_LOADING, payload: true });
    
    // Direct institutional verification warning rule
    if (!email || !email.includes('@')) {
      const errorMsg = "Authentication rejected: valid college email required";
      dispatch({ type: ACTIONS.SET_ERROR, payload: errorMsg });
      throw new Error(errorMsg);
    }

    // Emulate login during demonstration if Firebase credentials aren't live
    if (!auth || email.endsWith('.dev@college.edu') || selectedRole) {
      const role = selectedRole || 'STUDENT';
      const devUser = {
        uid: `dev-${role.toLowerCase()}-id`,
        email: email,
        displayName: `${role} Profile Account`
      };
      const devToken = `DEV_TOKEN_${role}`;
      
      sessionStorage.setItem('interntrack_dev_token', devToken);
      sessionStorage.setItem('interntrack_dev_session', JSON.stringify({ user: devUser, role }));
      
      dispatch({ type: ACTIONS.SET_DEV_SESSION, payload: { user: devUser, role } });
      return { user: devUser, role };
    }

    try {
      const userCredential = await signInWithEmailAndPassword(auth, email, password);
      const tokenResult = await userCredential.user.getIdTokenResult();
      const role = tokenResult.claims.role || selectedRole || 'STUDENT';
      
      dispatch({ type: ACTIONS.SET_AUTH_STATE, payload: { user: userCredential.user, role } });
      return { user: userCredential.user, role };
    } catch (err) {
      const portalError = "Sign-in rejected: credentials unrecognized in university directory or account inactive.";
      dispatch({ type: ACTIONS.SET_ERROR, payload: portalError });
      throw new Error(portalError);
    }
  }, []);

  const logout = useCallback(async () => {
    sessionStorage.removeItem('interntrack_dev_token');
    sessionStorage.removeItem('interntrack_dev_session');
    localStorage.removeItem('interntrack_dev_token');
    localStorage.removeItem('interntrack_dev_session');
    if (auth && auth.currentUser) {
      await signOut(auth);
    }
    dispatch({ type: ACTIONS.LOGOUT });
  }, []);

  const switchRole = useCallback((newRole) => {
    const devUser = state.user || { uid: 'portal-user-01', email: 'faculty@cs.college.edu', displayName: 'Institutional Reviewer' };
    const payload = { user: devUser, role: newRole };
    sessionStorage.setItem('interntrack_dev_token', `DEV_TOKEN_${newRole}`);
    sessionStorage.setItem('interntrack_dev_session', JSON.stringify(payload));
    dispatch({ type: ACTIONS.SET_DEV_SESSION, payload });
  }, [state.user]);

  const getToken = useCallback(async () => {
    if (auth && auth.currentUser) {
      return await auth.currentUser.getIdToken(false);
    }
    return sessionStorage.getItem('interntrack_dev_token') || 'DEV_TOKEN_STUDENT';
  }, []);

  return (
    <AuthContext.Provider
      value={{
        isAuthenticated: state.isAuthenticated,
        user: state.user,
        role: state.role,
        loading: state.loading,
        error: state.error,
        isDevMode: state.isDevMode,
        login,
        logout,
        switchRole,
        getToken
      }}
    >
      {children}
    </AuthContext.Provider>
  );
};
