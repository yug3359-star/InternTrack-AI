# InternTrack AI — Full Project Documentation (v7 — FINAL, Implemented)

### Internship Management, Monitoring & Compliance System

This version incorporates the final implemented technology stack and architecture. Format: Tech Stack → Software Specifications → Feature-by-Feature Deep Dive (Registration → Completion).

**Summary of Latest Implementation (v7):**

|#|Previous Concept|Final Implementation|
|-|-|-|
|1|Cloud-based Face Match (AWS/Azure)|Client-side Face-Match using `@vladmandic/face-api` (saves bandwidth/server load)|
|2|Strict LLM Dependency for Diary Review|Groq AI API without local fallback, enforcing industry-standard AI verification|
|3|Diary Review / Topic Extraction in 2 Steps|Merged API Pipeline: Single Groq AI call returns compliance decision + topics JSON|

---

# 1. TECHNOLOGY STACK

|Layer|Technology|
|-|-|
|Frontend|React.js (v18.2.0), Vite, HTML5, CSS3|
|Form Handling|React Hook Form|
|HTTP Client|Axios|
|State Management|React Context + `useReducer`|
|Backend|Java 21, Spring Boot (v3.2.5), Maven|
|API Style|REST (JSON)|
|Scheduling|Spring `@Scheduled` (cron-based)|
|Authentication|Firebase Authentication|
|Authorization|Role-Based Access Control (RBAC) + JWT (`JwtFilter`)|
|Rate Limiting|Bucket4j|
|Data Export|Apache POI (Excel)|
|Database|Firebase Firestore (Primary NoSQL) + H2/JPA (Relational Compliance Logging)|
|File Storage|Firebase Storage|
|Face Match (Client-Side)|`@vladmandic/face-api` (v1.7.15)|
|AI (Diary Review & Topic Extraction)|Groq AI API (no local fallback)|
|AI (Question Generation)|Groq AI API|
|Email Notifications|Brevo API|
|Webcam Capture|Browser-native `getUserMedia()`|
|Tab-Switch Detection|Browser-native Page Visibility API|
|Charts/Analytics|Recharts|
|Frontend Hosting|Firebase Hosting|
|Backend Hosting|Render.com / Railway.app|

---

# 2. SOFTWARE SPECIFICATIONS

### Development Environment

* **IDE (Frontend):** VS Code
* **IDE (Backend):** IntelliJ IDEA / Eclipse (Spring Boot support)
* **Version Control:** Git + GitHub
* **API Testing:** Postman

### Languages & Runtime Versions

* Java 21 (Spring Boot 3.2.x requirement)
* Node.js 18+ / npm (for React tooling via Vite)
* JavaScript / ES6+

### Core Frameworks & Libraries

* Spring Boot (Web, Security, Scheduling, Data JPA starters)
* React 18+
* Firebase Admin SDK (Java, backend-side)
* Firebase Client SDK (JavaScript, frontend-side)
* Bucket4j (Rate Limiting)
* Apache POI (Excel Generation)

### Cloud/External Services

* Firebase (Authentication, Firestore, Storage, Hosting)
* Groq AI API (LLM processing)
* Brevo API (Email Notifications)

### Database

* **Google Cloud Firestore** — Primary NoSQL document-oriented store, chosen over a relational DB for schema flexibility during active development and built-in real-time listeners.
* **Spring Data JPA & H2** — Used for structured relational compliance logging (`internship_logs`).

### Operating System Compatibility

* Web-based application — runs in any modern browser (Chrome, Firefox, Edge, Safari) on Windows, macOS, Linux, Android, or iOS. No installed client software required.

### Minimum Client-Side Requirements

* Modern browser with webcam/camera permission support (for test verification and pop-ups)
* Stable internet connection during office hours (required for real-time check-ins/tests)

---

# 3. FEATURES — REGISTRATION TO COMPLETION (IN DEPTH)

---

## FEATURE 1: Student Registration

**What it does:** Student creates an account and submits internship + company schedule details, restricted to official college email addresses. Student also uploads one clear **reference photo** at registration — this becomes the baseline for all future face-match verification.

**Fields captured:** Full Name, College Email, Branch, Mentor Name/Email, Internship Type/Domain, Joining Date, Completion Date, Office Start/End Time, Break Start/End Time, Working Days, Device Type (Desktop or Mobile — one only), Reference Photo.

**Technology used:**

* **React Hook Form** — manages form state and validation with minimal re-renders.
* **Firebase Authentication** — creates the account, handles password hashing/verification.
* **Firestore** (`users`, `internships` collections) — stores the profile and internship metadata.
* **Firebase Storage** — stores the reference photo, referenced by URL in Firestore.

**Key details a panel may ask about:**

* *"Why Firebase Auth instead of writing your own login system?"* — Password hashing, token refresh, and email verification are security-sensitive to implement correctly; Firebase Auth is a managed, audited service that removes that risk from a student project.
* *"How do you restrict registration to college emails only?"* — Validated twice: client-side regex for instant feedback, and server-side in the Spring Boot registration endpoint, since client-only checks can be bypassed by calling the API directly.
* *"Why NoSQL (Firestore) and not MySQL/PostgreSQL?"* — At this project's scale (60–200 students), Firestore avoids schema migrations while the data model is still evolving, and its document model matches how the data is queried (mostly per-student lookups, not complex multi-table joins).

---

## FEATURE 2: Document Upload

**What it does:** Student uploads Offer Letter and Approval Letter as part of registration.

**Formats supported:** JPG, PNG, PDF.

**Technology used:**

* **Firebase Storage** — stores the binary files, returns a CDN-backed URL.
* **Firestore** — stores the resulting URL against the student's internship record (not the file itself).

**Key details a panel may ask about:**

* *"How do you prevent fake file uploads (e.g. renaming a virus to .pdf)?"* — Client-side `accept` attribute restricts the file picker, but the backend independently re-checks the actual MIME type of the uploaded bytes before accepting it.
* *"Why store the file separately from the database record?"* — Databases (including Firestore) are optimized for small structured records, not large binary blobs.

---

## FEATURE 3: HOD Approval

**What it does:** HOD reviews the submitted application and documents, approves or rejects.

**Technology used:**

* **Spring Boot REST endpoint**, protected by an RBAC check confirming the caller's role is `"hod"`.
* **Firestore** — `status` field updated to `approved` or `rejected`.

**Key details a panel may ask about:**

* *"What stops a student from calling the HOD-approve endpoint directly?"* — Every protected endpoint checks the authenticated user's role server-side before executing via the `JwtFilter`.
* *"What is RBAC and why use it here?"* — Permissions tied to a role (`student`/`mentor`/`hod`) rather than individual users.

---

## FEATURE 4: Internship Status Tracking

**What it does:** Tracks each student's internship through: `Applied → Pending Approval → Approved → Ongoing → Completed` (or `Rejected`).

**Technology used:**

* **Firestore status field**, read by both frontend and backend.
* **Spring Boot scheduled job** — auto-flips status to `Ongoing` once approved AND the joining date arrives.

**Key details a panel may ask about:**

* *"Why not let the frontend calculate status from dates?"* — Client device clocks can't be trusted; the backend job is the single source of truth.

---

## FEATURE 5: Working Hours Verification (Core Feature)

Runs during registered office hours (excluding break), powered by Spring Boot `@Scheduled` jobs.

### 5a. "Are You Working?" Engagement Pop-ups (5×/day)

**What it does:** 5 random pop-ups per day ask "Are you working?" with a ~2-minute response window. Clicking Yes captures a webcam photo. The system uses a Face-Match API to verify.

**Automated photo verification (Client-Side AI):**
The captured photo is automatically compared against the student's registration reference photo via `@vladmandic/face-api` executed in the browser.

|AI Confidence|Outcome|
|-|-|
|High match (>=75%)|Auto-approved. No human sees anything.|
|Low match (<40%)|Auto-rejected automatically. That day's **diary is rejected**.|
|Borderline (40–75%)|Sent to **mentor's dashboard** as a one-tap "Same person? Yes/No" comparison (reference photo vs. captured photo, side by side). Mentor's "No" → diary rejected. Mentor's "Yes" → proceeds normally.|

**Meeting Quota (handles genuine conflicts):**

* Each student gets a fixed monthly quota (e.g., 3 Whole Day Meeting Exemptions/month).
* If a student clicks **"I'm in a meeting"**, the system deducts 1 from their quota. This suppresses popups during legitimate corporate meetings.
* Once the quota reaches 0, further "meeting" claims automatically count as a missed pop-up (contributing to the daily absence count).

**Technology used:**

* `getUserMedia()` — single still frame capture.
* **`@vladmandic/face-api`** — Client-side facial landmark detection and similarity scoring.
* Spring Boot `@Scheduled` — random daily trigger timing + reschedule logic.
* Firestore `onSnapshot` — real-time pop-up delivery.
* Firestore counter field (`quotas` collection) — decrement logic, resets monthly.

**Key details a panel may ask about:**

* *"Why do face-matching on the client-side instead of the backend?"* — Doing it on the client-side using `@vladmandic/face-api` significantly reduces server overhead and bandwidth, as heavy video payload frames aren't constantly dispatched to the backend. The backend just receives the similarity score securely.
* *"How do you handle real meetings without giving students a blanket excuse?"* — The limited monthly quota allows for genuine corporate meetings. Abuse runs out the quota fast and gets exposed automatically.

### 5b. Formal Attendance Marking (1×/day)

**What it does:** One daily pop-up requiring the student to explicitly mark themselves present within a 1-hour window, or be marked absent for the day. Falls under the same Meeting Quota system.

**Technology used:** Same scheduling/notification stack as 5a, tracked in its own `attendance` Firestore collection by the `DailyStatusService`.

**Key details a panel may ask about:**

* *"Why have both 5a and 5b — isn't that redundant?"* — 5a is a high-frequency identity/engagement signal; 5b is one deliberate, low-noise daily record used for actual attendance percentage calculations.

### 5c. Personalized AI Test (Weekly)

**What it does:** A 1-hour window to start a test built from that week's typed diary content; once started, a fixed timer runs. Webcam captures snapshots; tab-switches are logged.

**Automated photo verification:**
Start/Submit photos are auto-compared to the reference photo via Face Match API using the same confidence thresholds. Borderline cases go to the mentor as a one-tap Yes/No. A confirmed mismatch → that day's **test is marked absent**.

**Scoring rule:**
* Never started / 0 answered → absence
* Started, 1–5 answered → scored `correct/5`, not an absence
* Mentor can override to absent after reviewing Start/Submit photos

**Technology used:**
* `getUserMedia()` for snapshots.
* `@vladmandic/face-api` for automated identity verification.
* Page Visibility API for tab-switch logging.
* Spring Boot server-side timestamp validation.

**Key details a panel may ask about:**
* *"Why replace static question banks with AI?"* — Static question banks can be shared among students. Dynamically generating 5 unique multiple-choice questions directly related to their week's diaries prevents cheating and verifies genuine learning.

---

## FEATURE 6: Daily Digital Diary System

**What it does:** Student **types** their daily diary entry directly into a text box in the app (no photo upload). This text feeds the AI pipeline that generates that week's test.

**AI Pipeline (Single Consolidated Call):**
1. **Compliance Decision + Topic Extraction** — typed diary text sent to an LLM (Groq API) via `AiPipelineService`, heavily prompted to return structured JSON. The JSON contains a binary decision (`accept` or `reject` based on domain relevance and continuity), reasoning, and an array of extracted technical topics.
2. **Strict Verification** — If the external LLM API rate-limits (HTTP 429), it uses exponential backoff retries. The local fallback was removed to enforce industry-standard AI verification; it throws an error if max retries are exceeded.

**Technology used:**
* Simple text input field (React).
* **Groq AI API** for evaluation and topic extraction.
* Spring Boot `AiPipelineService` with JSON defensive parsing and exponential backoff.

**Key details a panel may ask about:**
* *"Doesn't typed text make it easier to fake or copy-paste?"* — This is an accepted trade-off. If a student copy-pastes fake content, the AI generates test questions from that fake content, and the student will fail the proctored, face-verified test because they don't actually know the material.
* *"How do you handle LLM API rate limits?"* — Implemented an exponential backoff loop in Java (1.5s -> 3s -> 6s). If max retries are exceeded, it records a telemetry error and fails to ensure industry-standard verification is not bypassed.

---

## FEATURE 7: Fully Automated Diary Review

**What it does:** The mentor does nothing by default. When a student submits their diary, the AI automatically reviews it. 

**How the AI decides:**
* **Domain relevance:** Does the diary content match the student's declared internship domain?
* **Continuity:** Does today's entry logically follow from previous weeks without contradicting them?

If both checks pass → Auto-Accepted. If either fails → Auto-Rejected.

**Suspicious Section:**
Any diary the AI rejects is moved into a permanent `suspicious_diaries` collection. These stay as untouched, timestamped evidence until a mentor or HOD manually overrides the decision. Rejections feed into the escalation counter.

**Technology used:**
* One combined LLM API call per diary via Spring Boot `@Async`.
* Firestore — two collections: `diaries` (normal, accepted) and `suspicious_diaries` (permanent, rejected).

**Key details a panel may ask about:**
* *"Isn't relying on AI for accept/reject risky?"* — That's why rejected diaries land in the Suspicious section where a human can review and reverse it if they choose to. It acts as a safety net.
* *"Doesn't this double your AI costs?"* — No, we merged two jobs into one call. The API call that decides accept/reject also extracts the topics needed for test generation.

---

## FEATURE 8: Rejection & Absence Escalation

**What it does:** Tracks monthly counters — diary rejections, absences, and excuse-abuse (percentage of quotas used unnecessarily). Reaching a threshold flags the student as "Highlighted" on the HOD dashboard.

**Technology used:**
* **Spring Boot `EscalationService` & `@Scheduled` monthly job** — tallies counters, writes to a `warnings` collection, and updates analytics.

**Key details a panel may ask about:**
* *"Why separate counters instead of one overall score?"* — Rejections, absences, and excuse-abuse are conceptually different signals. Separating them gives the HOD clearer diagnostic information about *why* a student is flagged.

---

## FEATURE 10: Data Export & Reporting

**What it does:** Allows the HOD to export aggregated student compliance and completion data to an Excel spreadsheet for offline university records.

**Technology used:**
* **Apache POI** (Java backend) to generate `.xlsx` files dynamically.
* **Spring Boot `ExcelDataService`** aggregates data from Firestore (users, internships, attendance, and completion_summaries) to construct the report.

---

## FEATURE 9: Internship Completion

**What it does:** Once a student passes their registered Completion Date, status auto-updates to `Completed`, and a final compliance summary (attendance %, diary compliance %, average test score, excuse-usage %) is generated.

**Technology used:**
* **Spring Boot `@Scheduled` daily job** — checks dates, updates status, computes `completion_summaries`.

**Key details a panel may ask about:**
* *"Why compute a final summary instead of leaving raw historical data?"* — Makes it fast to review a completed student's record for grading/certification without re-aggregating months of raw records. Future Scope includes an ERP webhook to push this summary directly to the university's grading system.

---

# 4. SUPPORTING SYSTEMS (Cross-Cutting)

|System|Technology|Purpose|
|-|-|-|
|Notifications|Firestore `onSnapshot` listeners|Real-time in-app alerts|
|Security|Firebase Auth + RBAC (`JwtFilter`) + Firestore Security Rules|Defense-in-depth: role checks in backend AND database-level rules|
|Rate Limiting|Bucket4j|Protects APIs against brute-force attacks and abuse|
|Identity Verification|`@vladmandic/face-api`|Client-side automated confidence-scored face matching|
|Reporting|Apache POI|Excel generation for offline record keeping|
|Analytics|Firestore aggregation queries (`AnalyticsService`) + Recharts|Attendance %, diary compliance %, test scores, risk level|
|Hosting|Firebase Hosting (frontend) + Render/Railway (backend)|Free/low-cost, minimal DevOps overhead|

**Key detail a panel may ask about:** *"Why do you need both backend RBAC checks AND Firestore Security Rules?"* — Backend checks protect your intended API flow; Firestore Security Rules protect against someone bypassing your backend entirely and calling Firestore directly. Together: defense in depth.

---

# 5. EXPECTED OUTPUT WHEN BUILDING

Complete React Frontend • Java Spring Boot Backend • Firebase Integration (Auth/Firestore/Storage) • Relational Logging (Spring Data JPA) • Firestore Schema & Security Rules • Client-Side Face-Match API Integration (`@vladmandic/face-api`) • AI Topic Extraction & Diary Evaluator Pipeline (Single Groq API Call, no fallback) • Meeting Quota Logic • Scheduled Jobs (pop-ups, attendance, test windows, warnings, completion checks) • Webcam & Tab-Switch Logic • Mentor & HOD Dashboards (with analytics via Recharts, Excel Export via POI) • Bucket4j Rate Limiting • Database Design • API Documentation • Folder Structure • Deployment Guide
