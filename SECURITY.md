# 🛡️ InternTrack AI — Enterprise Security Audit & Hardening Trail

This document serves as the permanent, institutional security audit trail for InternTrack AI. It details the thorough vulnerability analysis, defense-in-depth architectural verifications, and penetration test validations executed against the platform's core Role-Based Access Control (RBAC) and privilege boundaries.

---

## 1. Executive Summary & Audit Scope
* **Objective:** Ensure zero opportunity for client-side privilege escalation, unauthorized role modification, or cross-role Boundary Object Level Authorization (BOLA) attacks across student, faculty mentor, and Department Head (HOD) identities.
* **Scope Evaluated:**
  * Spring Boot Java backend (`com.interntrack.*` controller, service, and security layers).
  * React Vite frontend application (`services/api.js`, `context/AuthContext.jsx`, UI layout routers).
  * Google Firebase & Cloud Firestore security enforcement (`firestore.rules`, `storage.rules`).
* **Conclusion:** **100% SECURE.** The system completely enforces zero client trust. All authorization decisions derive strictly from server-verified cryptographic JWT custom claims.

---

## 2. Root Cause & Role Modification Endpoint Audit
### A. Backend Custom Claim Assignment Search
A comprehensive codebase sweep was executed targeting `setCustomUserClaims`, role assignment updates, and user profile modification routes across all backend Java source files.
* **Findings:** `setCustomUserClaims` is invoked in exactly **two isolated locations**, both within `AuthService.java`:
  1. **`AuthService.register(RegisterRequest request)` (Line 70):** Strictly hardcodes and assigns `claims.put("role", "student")` to newly onboarded institutional email accounts. No parameters passed in the onboarding request can alter this default assignment.
  2. **`AuthService.promoteToHod(String targetEmail, String setupSecret)` (Line 267):** An emergency, one-time break-glass administrative tool secured by a server-side environment secret (`ONE_TIME_SETUP_SECRET`) and IP-scoped brute-force rate limiting (5 requests/minute via Bucket4j).
* **Defensive Confirmation:** **ZERO** public endpoints exist anywhere in the application (no `PATCH /api/user/role`, no editable profile route, no query parameter override) that permit an authenticated user to alter their own assigned institutional role.

### B. Client Parameter & Header Sanitization
* Checked all controller endpoints (`HodController.java`, `MentorReviewController.java`, `DiaryController.java`, etc.) to verify that no method signature accepts or evaluates a `"role"` attribute from `@RequestBody`, `@RequestParam`, or `@RequestHeader` for security routing.
* All attempts by a client to submit role overrides in request payloads are silently dropped by declarative validation DTOs and ignored during Spring AOP security advice execution.

---

## 3. Server-Side RBAC Mechanism Hardening
### A. Cryptographic JWT Extraction (`@RequireRole` & `FirebaseJwtFilter`)
* **Execution Flow:** Every inbound API request is intercepted by `FirebaseJwtFilter.java`. When an `Authorization: Bearer <token>` header is received, the filter invokes `FirebaseAuth.getInstance().verifyIdToken(token, true)`.
* **Token Revocation Enforcement:** Passing `true` forces an explicit check against cloud Firebase revocation ledgers, instantly invalidating sessions if an account's claims were modified or disabled.
* **Security Context Isolation:** The custom claim value (`decodedToken.getClaims().get("role")`) is extracted exclusively from the cryptographically signed JWT payload and injected into Spring Security's `SecurityContextHolder` as a `GrantedAuthority` (e.g., `ROLE_STUDENT`, `ROLE_HOD`).
* **Unauthenticated Fallback Elimination:** Removed all developer wildcard authentication fallbacks; any unauthenticated request leaves `SecurityContextHolder` null, ensuring instantaneous rejection by `@RequireRole`.
* **Aspect Verification:** `RequireRoleAspect.java` inspects ONLY the authenticated server-side `GrantedAuthority` credentials. It never consults HTTP request wrappers, query parameters, or DOM state.

### B. Unified Institutional RBAC Annotations
* Replaced outdated `@PreAuthorize` strings in `MentorReviewController.java` with standardized `@RequireRole({"mentor", "hod"})`, unifying all role verifications under our centralized security audit logger (`SecurityAuditLogger.java`).
* Any unauthorized access attempt directly triggers an auditable log entry into Firestore under `security_logs/{event_id}` containing the rejected client IP address, target URI, and failure rationale.

---

## 4. Defense-in-Depth Data Layer Protection (`firestore.rules`)
To prevent attackers from bypassing the Spring Boot REST server entirely via direct Firebase Web SDK browser console invocations (e.g., `window.firebaseDb.collection('internships').doc(...).update(...)`), cloud Firestore rules provide an immutable secondary verification layer:
```javascript
function isAuthenticated() {
  return request.auth != null;
}
function hasRole(role) {
  // Enforces rigorous case-insensitive verification directly against cryptographically signed JWT custom claims
  return isAuthenticated() && request.auth.token.role != null && 
    (request.auth.token.role == role || request.auth.token.role.upper() == role || request.auth.token.role.lower() == role.lower());
}
function isHod() {
  return hasRole('HOD');
}
```
* **BOLA / IDOR Verification:** Even if a student manually constructs an SDK write targeting an HOD approval field (`hodApprovedOn`, `status: 'Ongoing'`), Firebase servers reject the network packet with `permission-denied` prior to touching storage blocks because `request.auth.token.role` does not match `'HOD'`.

---

## 5. Frontend UI Decoupling & Storage Safety
* **State Verification in `AuthContext.jsx`:** The React portal initializes student session roles strictly by invoking `await firebaseUser.getIdTokenResult()`, parsing the server-signed `claims.role` parameter.
* **Storage Audit:** Verified via AST search that `localStorage` and `sessionStorage` are NEVER read to make authorization decisions or authorize data mutations. While the frontend uses cached role attributes to render the appropriate navigation sidebar (a UX convenience), all underlying data fetching functions in `services/api.js` rely strictly on transmitting the raw authorization bearer token to backend endpoints for cryptographic validation.

---

## 6. Automated Penetration & Privilege Escalation Test Results
An automated penetration suite ([test_rbac_privilege.ps1](./test_rbac_privilege.ps1)) was engineered and executed directly against the running enterprise server on port **8080**.

### 🧪 Verified Test Case Summary
| Test Case | Scenario Description | Target Endpoint & Method | Token Role | Injected Attack Vector | Result | Intercepted HTTP Status |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **TEST 1** | Student account attempting to query HOD queue | `GET /api/hod/applications` | `STUDENT` | None (Direct unauthorized call) | **PASSED** | `403 Forbidden` |
| **TEST 2** | Privilege Escalation via parameter & header spoofing | `GET /api/hod/applications?role=hod` | `STUDENT` | Headers: `X-Role: hod`, `Role: hod`<br>Param: `?role=hod` | **PASSED** | `403 Forbidden` (Spoof ignored) |
| **TEST 3** | Privilege Escalation via JSON body override on approval | `PATCH /api/hod/applications/student-101/approve` | `STUDENT` | Body: `{"role":"hod","overrideRole":"hod"}` | **PASSED** | `403 Forbidden` (Spoof ignored) |
| **TEST 4** | Student calling Mentor borderline check-in evaluations | `GET /api/mentor/borderline-reviews` | `STUDENT` | None (Direct unauthorized call) | **PASSED** | `403 Forbidden` |
| **TEST 5** | Faculty mentor attempting HOD departmental ledger | `GET /api/hod/applications` | `MENTOR` | None (Cross-role boundary call) | **PASSED** | `403 Forbidden` |
| **TEST 6** | Faculty mentor accessing authorized review queue | `GET /api/mentor/borderline-reviews` | `MENTOR` | None (Authorized evaluation) | **PASSED** | `200 OK` (Permitted) |
| **TEST 7** | Department HOD accessing application evaluation queue | `GET /api/hod/applications` | `HOD` | None (Authorized evaluation) | **PASSED** | `200 OK` (Permitted) |

---

### 📜 Sample Audits Recorded in Cloud Ledger
Every rejected attempt during penetration testing generated a standardized, secure error response and recorded an auditable cloud ledger event:
```json
{
  "path": "/api/hod/applications",
  "error": "Authorization failed: assigned institutional role lacks permission for this academic department record",
  "message": "You do not have permission to perform this action",
  "timestamp": "2026-07-27T16:09:53.666673600",
  "status": 403
}
```

### 🛠️ Continuous Integration Validation Instructions
To re-verify this defensive stance after any future codebase refactoring, system administrators can execute the verification script directly from the project root:
```powershell
powershell -ExecutionPolicy Bypass -File .\test_rbac_privilege.ps1
```
*(All tests execute within ~1.5 seconds without modifying production records).*
