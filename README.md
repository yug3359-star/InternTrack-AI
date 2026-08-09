# InternTrack AI — Internship Monitoring & Compliance System

**InternTrack AI** is a data-dense, institutional academic internship monitoring and compliance system. Designed for university computer science departments, it streamlines weekly compliance logging by students, oversight verifications by faculty mentors, and analytics tracking by department Heads (HODs).

---

## Architecture Overview

- **Frontend**: React 18+ powered by Vite, utilizing plain CSS/CSS Modules for styling with a strict institutional design system (no generic frameworks like Tailwind or MUI). Features Axios for secure communication and Recharts for compliance analytics.
- **Backend**: Java 17+ with Spring Boot 3.x, featuring stateless Spring Security integration with Firebase Admin SDK for JWT verification and role-based access control.
- **Authentication & Data**: Firebase Authentication (ID Token verification with custom `"role"` claims), Firebase Firestore, and Firebase Storage.

---

## Design System & Institutional Aesthetics

The application follows a utilitarian, trustworthy university portal design (similar to modern campus ERPs and LMS platforms):
- **Palette**: `#F7F8FA` neutral grey background, `#FFFFFF` cards with thin 1px `#E2E5EA` borders, `#2B5C8A` institutional primary blue, and muted status indicators.
- **Typography**: *Source Sans 3* (denser 15px base size) for data tables and functional copy; *IBM Plex Serif* exclusively for academic dashboard headings.
- **Layout**: Persistent role-based left navigation, minimal decorative animations, solid 4px buttons, and direct, clear institutional microcopy.

---

## Repository Structure

```
├── frontend/                     # Vite + React 18 web client
│   ├── src/
│   │   ├── components/           # Navbar, Sidebar, and functional Loader
│   │   ├── context/              # AuthContext and role session state
│   │   ├── hooks/                # Custom hooks (useAuth)
│   │   ├── pages/                # Auth pages & role-scoped dashboards (Student, Mentor, HOD)
│   │   ├── services/             # Axios instance with Firebase Bearer token interceptor
│   │   ├── styles/               # Global design tokens (variables.css)
│   │   └── utils/                # Firebase SDK client config
│   └── package.json
│
├── backend/                      # Spring Boot 3 + Java 17 service
│   ├── src/main/java/com/interntrack/
│   │   ├── config/               # FirebaseAdmin, CORS, and Security configurations
│   │   ├── controller/           # Role-scoped endpoints & health diagnostics
│   │   ├── exception/            # Global exception handlers and institutional messaging
│   │   ├── model/                # JPA compliance entity models
│   │   ├── repository/           # Spring Data JPA repositories
│   │   ├── scheduler/            # Weekly automated compliance flagging scheduled jobs
│   │   ├── security/             # Firebase JWT auth filter & claim validators
│   │   └── service/              # Compliance verification and business logic
│   └── pom.xml
└── README.md
```

---

## Setup & Running Locally

### Prerequisites
- **Node.js** v18+ and **npm**
- **Java 17+** (JDK 17 or higher) and **Maven 3.8+**
- **Firebase Project** with Authentication and Admin SDK enabled

---

### 1. Backend Configuration (Spring Boot)

1. Navigate to the `backend/` directory:
   ```bash
   cd backend
   ```
2. Place your downloaded Firebase Admin SDK service account key JSON inside `src/main/resources/` named `firebase-service-account.json` (see `src/main/resources/firebase-service-account-example.json` for structure details).
3. Build and launch the Spring Boot service using the included self-contained Maven Wrapper (no separate Maven installation required in your PATH!):
   ```powershell
   # On Windows (PowerShell / CMD / VS Code):
   .\mvnw.cmd clean spring-boot:run

   # On macOS / Linux / Bash:
   ./mvnw clean spring-boot:run
   ```
4. The server will bind to `http://localhost:8080`.

---

### 2. Frontend Configuration (Vite + React)

1. Navigate to the `frontend/` directory:
   ```bash
   cd frontend
   ```
2. Create a `.env.local` file using your Firebase SDK configuration:
   ```env
   VITE_FIREBASE_API_KEY=your_api_key_here
   VITE_FIREBASE_AUTH_DOMAIN=your_project.firebaseapp.com
   VITE_FIREBASE_PROJECT_ID=your_project_id
   VITE_FIREBASE_STORAGE_BUCKET=your_project.appspot.com
   VITE_FIREBASE_MESSAGING_SENDER_ID=your_sender_id
   VITE_FIREBASE_APP_ID=your_app_id
   ```
3. Install dependencies and start the Vite dev server:
   ```bash
   npm install
   npm run dev
   ```
4. Open your browser and access the portal at `http://localhost:5173`.

---

## Default Roles & Access Claims

When interacting with the API, the backend inspects the custom `"role"` claim embedded within the Firebase JWT ID Token:
- **`STUDENT`**: Access to submit weekly compliance activity logs and review advisor feedback (`/api/student/**`).
- **`MENTOR`**: Faculty validation dashboard to approve/reject student logs and monitor cohort punctuality (`/api/mentor/**`).
- **`HOD`**: Department-wide oversight analytics, program compliance tracking, and automated reporting (`/api/hod/**`).

---

## Deploying Firebase Production Security Rules

To enforce defense-in-depth Role-Based Access Control (RBAC) at the database and storage layers, deploy the custom rules from the root workspace using the **Firebase CLI**:

1. If you haven't installed the Firebase CLI yet, execute:
   ```bash
   npm install -g firebase-tools
   firebase login
   ```
2. Update `.firebaserc` with your exact live project ID (or select it via `firebase use --add`).
3. Deploy both Firestore and Storage security rules simultaneously:
   ```bash
   firebase deploy --only firestore:rules,storage:rules
   ```

---

## End-to-End Real Firebase Verification Checklist

When validating the system against your live production Firebase architecture (no emulators/mocks), follow this systematic verification checklist:

### 1. Confirm Cloud Connection Health Diagnostics
- Open your browser or Terminal and execute a request to the backend diagnostics endpoint:
  ```bash
  curl http://localhost:8080/api/health/firebase
  ```
- **Expected Output**: A JSON payload returning `"status": "connected"`, confirming Spring Boot executed a trivial live read (`firestore.getCollections()`) against your cloud Firestore database.

### 2. Check Client SDK Startup Initialization
- Launch the React web portal at `http://localhost:5173` and open your Browser Developer Tools console (`F12`).
- **Expected Output**: Confirm the diagnostic log appears on app initialization:
  ```
  [InternTrack SDK Diagnostic] Firebase Client SDK connected successfully. Project ID: [your-project-id]
  ```

### 3. Real Student Registration & Live Console Auditing
- Register one real student through the multi-step UI (`http://localhost:5173/student/register`), uploading a baseline Reference Photo (`.jpg`) and required PDFs.
- **Firestore Console Check**: Navigate to your real Firebase console under **Firestore Database** → verify a new document has been generated inside the `users` and `internships` collections keyed by the student's authenticated `uid`.
- **Storage Console Check**: Open **Firebase Storage** → verify the uploaded portrait appears inside `reference-photos/{uid}.jpg` and verification letters appear under `documents/{uid}/`.

### 4. Defense-in-Depth Security Rules Validation
- Open Browser DevTools on any public webpage or console session and attempt an unauthorized direct Firestore fetch bypassing our application SDK:
  ```javascript
  // Try reading another student's uid record directly without valid authentication/custom role claims
  await db.collection('internships').doc('another-student-uid').get();
  ```
- **Expected Outcome**: The request is instantly blocked by Firestore Security Rules with an explicit `FirebaseError: Missing or insufficient permissions`, confirming that direct API bypass attempts are neutralized at the database level!

---

## Production Hardening, Load Safety & Environment Governance

To ensure the system remains dependable under real campus loads (60–200 simultaneous student check-ins) and adheres to institutional reliability policies, InternTrack AI incorporates enterprise production-hardening infrastructure:

### ⚡ Exponential Backoff Rate-Limit Handling & Real Load Verification
Both biometric facial verification (`FaceMatchService`) and NLP activity evaluation (`AiPipelineService`) implement automatic exponential backoff retry loops (up to 3 attempts with increasing pauses: 1.5s → 3s → 6s) whenever cloud API endpoints return HTTP 429 throttling errors or throughput exhaustion during peak classroom usage spikes.
- **Mandatory Real Load Verification Before Rollout:** Before onboarding a real university cohort, manually calculate how many concurrent scheduled engagement popups and test sessions your expected student count (e.g., 100 students) generates around high-traffic trigger times (e.g., 9:00 AM work start). Check your active AWS Rekognition and OpenAI / Claude API account billing tiers and **manually verify that your assigned concurrent Request-Per-Second (RPS) rate limit comfortably exceeds this real traffic volume**. Do not assume default sandbox tiers are sufficient; execute this verification with your actual expected college student count!

### 📊 Nightly Operational Telemetry & Daily Digest Emails
To ensure system stability anomalies are actually observed by administrators rather than sitting unchecked in ephemeral server logs, `DailyDigestJob` executes an automated health audit every evening at **11:00 PM** (`0 0 23 * * *`):
- Aggregates daily counts of: cron scheduled worker exceptions, face-match cloud timeouts, AI pipeline evaluation errors, and accounts hitting zero meeting quota allowance.
- Commits an immutable daily summary report directly into Cloud Firestore at `system_logs/{date}`.
- Dispatches a concise automated notification email to the department head (`ADMIN_EMAIL`), providing instantaneous transparency into platform operational health!

### 🛡️ Mandatory Environment Separation (Development vs. Live Production)
To protect confidential student identity records, biometric portraits, and activity logs:
- **Never perform feature experimentation, refactoring testing, or database migrations directly against the live production Firebase project (`interntrack-ai-98f45`)**.
- Always maintain a distinct, isolated developer project (e.g., `interntrack-dev-sandbox`) configured in `.env.local` during continued enhancement development after rollout, ensuring live academic data remains mathematically insulated and pristine!

