# InternTrack AI — Step-by-Step Institutional Deployment Guide
**Document Purpose:** Complete operational blueprint for transitioning InternTrack AI (Modules 0–10, 15) from development into live, enterprise production for real university usage. Assumes zero prior deployment experience with target platforms.

---

## Section 1: Prerequisites & Required Enterprise Accounts

Before commencing deployment, ensure your institution has provisioned administrative access to the following platforms:

1. **GitHub Account:** For hosting the version-controlled private repository of the application codebase.
2. **Firebase Account & Google Cloud Platform (GCP) Project:** Provisioned with Firebase Authentication (Email/Password & Custom Custom Claims enabled), Cloud Firestore (Native mode), and Firebase Storage.
3. **Backend Hosting Provider (Render.com or Railway.app):** Recommended target for Spring Boot containerized deployment. This guide walks step-by-step through **Render.com (Web Service / Docker deployment)**.
4. **AWS Account (Amazon Web Services):** Provisioned with IAM programmatic credentials (`AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`) granted explicit authorization to invoke **Amazon Rekognition (`rekognition:CompareFaces`)**.
5. **LLM API Provider (OpenAI or Anthropic):** Active billing account with a live production secret API key (`OPENAI_API_KEY` or `ANTHROPIC_API_KEY`) for running automated compliance evaluations and examination generation.
6. **Uptime & Keep-Alive Monitor (UptimeRobot or Cron-Job.org):** Essential free utility to periodically ping the production heartbeat endpoint and prevent free-tier cloud containers from suspending scheduled cron background tasks.

---

## Section 2: Backend Deployment (Render.com)

### Step 2.1: Prepare Firebase Service Account Credentials
1. Navigate to the **Firebase Console** $\to$ **Project Settings** (gear icon) $\to$ **Service Accounts**.
2. Click **Generate new private key** and securely save the downloaded JSON file.
3. Rename the file to `firebase-service-account.json` and verify it is placed inside `backend/src/main/resources/` (Ensure your `.gitignore` contains this file so exposed private keys are never committed to public Git repos). For cloud deployment, you can also store the compact stringified JSON inside an environment variable and configure `FIREBASE_CREDENTIALS_PATH` accordingly.

### Step 2.2: Push Backend Code to GitHub
1. Open terminal inside the root directory and ensure all latest upgrades (AWS Rekognition SDK, consolidated AI pipeline, and Firestore mentor reviews) are committed:
   ```bash
   git add .
   git commit -m "feat(prod): hardening enterprise AI integrations and security guardrails for deployment"
   git push origin main
   ```

### Step 2.3: Provision Web Service on Render.com
1. Log into your **Render.com Dashboard** and select **New +** $\to$ **Web Service**.
2. Connect your GitHub repository containing the `InternTrack AI` repository.
3. Set the Root Directory to `backend`.
4. Select Environment: **Docker** (if utilizing Dockerfile) or **Java / Maven** runtime.
5. In the **Build Command** field, input:
   ```bash
   ./mvnw clean package -DskipTests
   ```
6. In the **Start Command** field, input:
   ```bash
   java -jar target/interntrack-ai-backend-1.0.0-SNAPSHOT.jar
   ```

### Step 2.4: Configure Environment Variables
Inside the Render **Environment Variables (Env Vars)** configuration tab, add the following production parameters:

| Variable Key | Sample / Institutional Target Value | Purpose |
| :--- | :--- | :--- |
| `SPRING_PROFILES_ACTIVE` | `prod` | Activates production security enforcement layers |
| `SERVER_PORT` | `10000` (or Render default) | Sets binding network port for Spring container |
| `FIREBASE_STORAGE_BUCKET` | `interntrack-ai-98f45.appspot.com` | Target Google Cloud storage repository |
| `CORS_ALLOWED_ORIGINS` | `https://interntrack-ai-98f45.web.app` | Restricts cross-origin requests exclusively to live web portal |
| `AWS_ACCESS_KEY_ID` | `AKIA... (Your AWS Key)` | Programmatic access identity for biometric evaluation |
| `AWS_SECRET_ACCESS_KEY` | `wJal... (Your AWS Secret)` | Cryptographic secret for Rekognition signatures |
| `AWS_REGION` | `us-east-1` (or your active AWS region) | Target region running Optical Face Comparison engine |
| `OPENAI_API_KEY` | `sk-... (Your OpenAI API Key)` | Powers AI weekly examination Bank & Diary evaluation |

7. Click **Create Web Service**. Render will initiate package compilation and publish your production endpoint (e.g., `https://interntrack-backend-prod.onrender.com`).

---

## Section 3: Frontend Deployment (Firebase Hosting)

### Step 3.1: Configure Backend Target Endpoint
1. In `frontend/src/`, verify or update your production API environment URL inside your network helper or environment properties (e.g., `.env.production`):
   ```ini
   VITE_API_BASE_URL=https://interntrack-backend-prod.onrender.com/api
   ```

### Step 3.2: Compile Production Bundle
1. Navigate to the frontend workspace and build the optimized production web assets:
   ```bash
   cd frontend
   npm install
   npm run build
   ```
2. This generates the minified production assets inside `frontend/dist/`.

### Step 3.3: Deploy to Live Firebase Domain
1. Ensure the Firebase CLI is installed globally on your machine (`npm install -g firebase-tools`) and authenticate via terminal:
   ```bash
   firebase login
   ```
2. Initialize and publish the web application directly to global edge networks:
   ```bash
   firebase deploy --only hosting
   ```
3. Upon completion, Firebase CLI outputs your institutional portal deployment URL (e.g., `https://interntrack-ai-98f45.web.app`). Test navigating to this domain to verify smooth rendering and authentication!

---

## Section 4: Production Hardening — Uptime & Keep-Alive Monitor

### Why This is Essential (Known Cloud Limitation)
Free-tier hosting platforms (including Render.com and Railway.app) automatically put backend containers to sleep after 15 minutes of inbound inactivity. When the JVM container is dormant, **Spring `@Scheduled` cron jobs (such as `WeeklyQuestionGenJob`, `AttendancePopupJob`, and `DailyDigestJob`) will silently fail to fire on time**, jeopardizing institutional attendance reporting and examination synthesis.

### The Remediation Architecture
To guarantee 24/7 continuous operation without immediate hosting upgrades, InternTrack AI includes a lightweight production heartbeat endpoint:
* **Endpoint URL:** `GET https://interntrack-backend-prod.onrender.com/api/health`
* **Payload Returned:** `{ "status": "alive", "timestamp": 1785175600123 }`

### Step-by-Step Setup (UptimeRobot)
1. Register a free account at **UptimeRobot.com**.
2. Go to Dashboard and click **+ Add New Monitor**.
3. Set Monitor Type to **HTTP(s)**.
4. Name the monitor: `InternTrack-AI-KeepAlive-Beacon`.
5. In the URL box, paste your real backend health URL:
   `https://interntrack-backend-prod.onrender.com/api/health`
6. Set **Monitoring Interval to 10 Minutes** (This ensures an HTTP GET packet reaches the server before the 15-minute sleep timer hits zero).
7. Click **Create Monitor**. Your application background schedulers are now protected from sleep cycles!
*(Note: For live campus rollouts supporting thousands of concurrent check-ins, upgrading Render/Railway to a dedicated paid container is recommended to ensure high CPU throughput during examination start bursts).*

---

## Section 5: Post-Deployment Diagnostics & AI System Verification

After completing deployment, immediately perform this 3-point compliance verification checklist against your live environment:

### Checklist 1: Verify Live AWS Rekognition Face Match
* Log into the student dashboard on the production URL and launch an **Attendance Check-In** or **Proctored Exam**.
* Allow webcam access and submit your facial portrait.
* Inspect your Render backend logs: you should observe `[REAL AWS SDK SUCCESS] Verified optical biometric comparison score: [XX.X%]`.
* If a score between 40% and 75% occurs, log into the HOD/Mentor workspace and confirm the record is instantly visible on **Borderline Reviews (`/mentor/borderline-reviews`)** and persisted in Firestore `mentor_reviews`.

### Checklist 2: Verify AI Diary Review & Topic Extraction
* Submit a real engineering daily activity log on `/student/diaries/submit`.
* Verify that the UI renders **Automated AI Review Status: ACCEPTED** along with 2–4 specialized engineering topics (e.g., `Spring Boot REST APIs`).
* Submit an intentionally off-topic activity log (e.g., discussing baking recipes or video games). Confirm it transitions to **REJECTED** and routes to the faculty `Suspicious Reviews` console.

### Checklist 3: Verify Weekly AI Examination Bank Synthesis
* Using an admin token or via the HOD console, manually dispatch the HOD manual job trigger:
  `POST https://interntrack-backend-prod.onrender.com/api/hod/triggers/generate-weekly-questions/dev-stud-102`
* Check your **OpenAI / Anthropic developer API dashboard** to verify live tokens were utilized.
* Navigate to your **Firebase Firestore console** $\to$ Data $\to$ `test_questions`. Confirm a newly generated examination bank document (containing 5 challenging technical questions referencing student diary logs) exists and displays attribute `source: "REAL_AI_OPENAI"`.

---

## Section 6: Security Verification Summary
* **HTTPS Enforcement:** Try accessing your backend via `http://` instead of `https://`. You will observe an automatic 301 redirection or security rejection from `SecurityDefensesFilter`.
* **Rate Limit Auditing:** Refresh or repeatedly hit `/api/auth/register` more than 5 times within 60 seconds from the same network IP. The server will intercept and block requests with HTTP Status `429 Too Many Requests`.
* **RBAC Route Security:** Attempt typing `/hod/dashboard` into your web browser while authenticated as a Student account. You will be instantly redirected to the styled 403 Access Denied interface without exposing any institutional governance ledgers.

---

## Section 7: Administrative Maintenance Endpoints

### One-Time Legacy Student Backfill
If there are legacy student records generated prior to the enforcement of the newly expanded identity fields (Roll Number, Section, Semester, Mobile Number), Heads of Department can utilize an internal emergency cleanup endpoint to retroactively inject these identity attributes into an existing user's Firestore ledger.

* **Endpoint URL:** `POST /api/hod/backfill-student-details/{uid}`
* **Authorization:** Strict `HOD` Role Required (Bearer Token)
* **Payload Structure (JSON):**
  ```json
  {
    "rollNo": "2023ACSE1101169",
    "section": "A",
    "semester": 7,
    "mobileNumber": "9876543210"
  }
  ```
* **Disclaimer:** This is a designated internal cleanup endpoint, designed for one-time historical batch rectifications rather than daily operational usage.
