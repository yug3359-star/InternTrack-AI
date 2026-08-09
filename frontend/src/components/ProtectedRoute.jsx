import React from 'react';
import { Navigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import Loader from './Loader';

/**
 * Reusable route security gatekeeper component that blocks unauthorized manual address bar navigation.
 * 
 * IMPORTANT ARCHITECTURAL NOTE:
 * This prevents a confusing UI state for legitimate users, but the REAL security enforcement is the backend RBAC (@RequireRole) and Firestore rules from Fix A — a technically sophisticated attacker could bypass frontend routing entirely and call the API directly, which is why backend/rules enforcement is what actually protects data, not this component.
 */
const ProtectedRoute = ({ allowedRoles = [], children }) => {
  const { isAuthenticated, role, loading } = useAuth();

  // Block rendering even momentarily before the authentication and role custom claim verification resolves
  if (loading) {
    return <Loader message="Verifying institutional academic permissions..." />;
  }

  // If no user is logged into an authenticated institutional session, redirect to login
  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  // Verify user's token custom claim role against authorized array (case-insensitive for robustness)
  const currentRole = role ? String(role).toLowerCase() : 'student';
  const isAuthorized = allowedRoles.some(
    (authorizedRole) => authorizedRole.toLowerCase() === currentRole
  );

  // If logged in user lacks permission for this role dashboard, redirect to explicit Unauthorized page (never login)
  if (!isAuthorized) {
    return <Navigate to="/unauthorized" replace />;
  }

  return children;
};

export default ProtectedRoute;
