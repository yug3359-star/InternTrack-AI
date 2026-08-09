# InternTrack AI — Institutional Production Codebase Architecture (Modules 0–10, 15)
**Document Version:** 1.0.0-PROD  
**Target Architecture:** Full-Stack Spring Boot 3 + React 18 + Firebase Admin/Firestore + AWS Rekognition & Consolidated LLM AI Pipelines

---

## 1. High-Level System Topology

InternTrack AI is an enterprise-grade AI-powered internship monitoring and institutional compliance platform designed for universities and engineering technical institutes. The architecture enforces three distinct departmental security boundaries:
1. **Student Execution Portal:** Biometric check-ins, automated daily work ledger evaluations, proctored AI examinations, and grievance escalations.
2. **Faculty Mentor Adjudication Workspace:** One-tap validation of borderline facial captures (40%–75% similarity), manual assessment of suspicious/rejected activity reports, and live student attendance ledgers.
3. **Head of Department (HOD) Governance Console:** University credential vetting, override approval chains, monthly escalation ledgers, system-wide analytics, and RBAC-protected manual AI pipeline triggers.

```
+---------------------------------------------------------------------------------------------+
|                                  CLIENT LAYER (React 18 / Vite)                            |
|  [Student Dashboard]       [Faculty Mentor Portal]       [HOD Governance & Analytics]       |
|  * Biometric Capture       * One-Tap Borderline Queue    * Credential Evaluation Ledgers    |
|  * Automated Diary Submit  * Suspicious Ledger Review    * System-Wide AI Diagnostics       |
|  * ProtectedRoute.jsx RBAC * Universal Search & Filters  * Real-Time CSV Export Engines     |
+---------------------------------------------------------------------------------------------+
                                                |
                   HTTPS (REST APIs / Signed Firebase ID Token JWT Ingestion)
                                                |
+---------------------------------------------------------------------------------------------+
|                             SECURITY & ROUTING LAYER (Spring Security)                      |
|  * SecurityDefensesFilter: HTTPS 301 Redirect Enforcer, HSTS, XSS & CSP HTTP Headers       |
|  * Bucket4j Token Bucket: Strict IP & Account Rate Limiter (5 req/min on Sensitive Paths)  |
|  * FirebaseJwtFilter: Cryptographic RSA Signature Verification & Role Claim Parsing        |
|  * @RequireRole AOP Aspect: Method-Level RBAC Enforcement ("student", "mentor", "hod")      |
+---------------------------------------------------------------------------------------------+
                                                |
+---------------------------------------------------------------------------------------------+
|                           CORE BUSINESS LOGIC LAYER (Spring Boot 3.2.5)                      |
|  [Auth & Onboarding]     [Biometric & Popups]     [AI Pipelines & Tests]  [Compliance Logs] |
|  * AuthService.java       * FaceMatchService.java * AiPipelineService     * DiaryService    |
|  * HodService.java        * PopupService.java     * TestQuestionService   * EscalationJob   |
|  * MentorReviewService    * QuotaService.java     * WeeklyQuestionGenJob  * DailyDigestJob  |
+---------------------------------------------------------------------------------------------+
                     /                          |                          \
     [Cloud Auth & Firestore]        [AWS Rekognition Biometric]    [OpenAI / Claude LLM]
     * Firebase Admin SDK             * CompareFaces SDK v2.25       * Strict JSON Schema Prompts
     * Custom Role JWT Claims         * 3x Exponential Backoff       * Defensive Regex Extraction
     * Real-time Ledger Synced        * Thresholds: <40% / >75%      * Domain Relevance Engine
```

---

## 2. Complete Project Directory Structure & Functional Traceability

### Backend Codebase (`backend/src/main/java/com/interntrack/`)
```
backend/
├── pom.xml                                     # Dependency definitions: Spring Boot 3.2.5, Firebase Admin, AWS Rekognition SDK, Bucket4j
└── src/main/
    ├── resources/
    │   ├── application.yml                     # Central production configuration binding CORS, Firebase credentials, AWS SDK keys, and LLM providers
    │   └── firebase-service-account.json       # Firebase Admin Service Account JSON key (Ignored in public Git; loaded via classpath/env)
    └── java/com/interntrack/
        ├── InternTrackApplication.java         # Spring Boot Server Entrypoint (@EnableScheduling, @EnableAsync)
        │
        ├── config/                             # Spring System Beans & Cloud Adapters
        │   ├── AsyncConfig.java                # Thread pool executor configuration for background AI task processing
        │   ├── CacheConfig.java                # ConcurrentMapCacheManager optimization for HOD analytics and repeated reads
        │   ├── CorsConfig.java                 # Universal origin authorization matching localized frontend dev servers and production host origins
        │   ├── FirebaseConfig.java             # Singleton initializes FirebaseAuth and Firestore serverless REST database beans
        │   ├── StorageConfig.java              # Cloud bucket connectivity for permanent webcam security portrait storage
        │   ├── TimezoneConfig.java             # Locks JVM clock strictly to Asia/Kolkata (UTC+5:30) institutional timezone
        │   └── WebConfig.java                  # Additional standard Spring MVC web filter mappings
        │
        ├── controller/                         # REST Controllers exposing protected departmental web service endpoints
        │   ├── AdminTriggerController.java     # [NEW] RBAC protected (@RequireRole("hod")) manual evaluation triggers for AI weekly question generation
        │   ├── AnalyticsController.java        # Exposes statistical breakdowns and CSV export stream endpoints for department heads
        │   ├── AttendanceController.java       # Queries chronological attendance status ledgers and monthly warning calculations
        │   ├── AuthController.java             # Secure student registration (@Valid enforced) with token bucket IP rate limit auditing
        │   ├── CompletionController.java       # Handles graduation evaluation and certificate credential issuance
        │   ├── DiaryController.java            # Ingests student activity logs for immediate AI semantic evaluation and topic extraction
        │   ├── EscalationController.java       # HOD interface for reviewing monthly cumulative student warnings and disciplinary notices
        │   ├── HealthCheckController.java      # Lightweight /api/health alive beacon preventing free cloud server sleep cycles (Production Hardening)
        │   ├── HodController.java              # Evaluates institutional credential registration forms (Approve/Reject student applications)
        │   ├── MentorReviewController.java     # Faculty one-tap validation endpoint for borderline biometric check-ins (40% - 75%)
        │   ├── MentorSuspiciousController.java # Faculty interface for grading rejected/off-topic diary submissions and escalating warnings
        │   ├── PopupController.java            # Serves working hour check-ins, webcam snapshot evaluations, and meeting override quotas
        │   ├── PortalController.java           # Universal application routing helper endpoint
        │   ├── StatusController.java           # Retrieves student status transitions (REGISTRATION -> AWESOME -> ONGOING -> TERMINATED)
        │   └── TestController.java             # Manages biometrically proctored examinations and score tabulation
        │
        ├── dto/
        │   └── SecurityValidationDtos.java     # Enterprise DTO classes utilizing strict @Valid annotations (@NotBlank, @Email, @Size, @Pattern)
        │
        ├── exception/
        │   ├── AccessDeniedException.java      # Runtime exception thrown during RBAC credential rejection
        │   ├── GlobalExceptionHandler.java     # Spring ControllerAdvice converting exceptions to clean JSON diagnostic responses (400, 401, 403, 500)
        │   └── InvalidRegistrationException.java # Thrown when student credential forms violate mandatory formatting requirements
        │
        ├── model/
        │   ├── Role.java                       # Enumeration of valid application roles (STUDENT, MENTOR, HOD)
        │   ├── StudentProfile.java             # Domain model mirroring Firestore user collection structure
        │   └── StudentStatus.java              # State transition enumerations governing student progression
        │
        ├── scheduler/                          # Automated Background Compliance Engines (@Scheduled Cron Jobs)
        │   ├── AttendancePopupJob.java         # Triggers random working hour check-in evaluations during scheduled internship windows
        │   ├── ComplianceScheduler.java        # Audits weekly student work logs for missing entries or unadjudicated faculty backlogs
        │   ├── DailyDigestJob.java             # Computes end-of-day attendance tallies, absence tracking, and error telemetry ledgers
        │   ├── DiaryRetentionJob.java          # Enforces archival storage cleaning policies across past academic semesters
        │   ├── EngagementPopupJob.java         # Manages active attendance popup expirations and increments missed check-in counters
        │   ├── EscalationJob.java              # Executes end-of-month tallying of biometric rejections, missed popups, and off-topic diaries
        │   ├── StatusTransitionJob.java        # Automatically shifts approved internships into active execution status at semester onset
        │   ├── TestSchedulerJob.java           # Opens and closes weekly AI proctored test windows
        │   └── WeeklyQuestionGenJob.java       # [NEW] Fires weekly at Sunday 02:00 AM UTC to synthesize custom exam banks from student diary logs
        │
        ├── security/                           # Multi-Tier Enterprise Security Guardrails
        │   ├── FirebaseJwtFilter.java          # Intercepts Bearer tokens, validates Firebase RSA signatures, and hydrates Spring Security Contexts
        │   ├── RateLimitService.java           # Token bucket enforcement via Bucket4j restricting registrations and login-adjacent endpoints
        │   ├── RequireRole.java                # Method and class-level annotation enforcing targeted departmental RBAC permissions
        │   ├── RequireRoleAspect.java          # Spring AOP aspect verifying user custom claims against @RequireRole requirements
        │   ├── SecurityAuditLogger.java        # Immutable system telemetry console logger recording sensitive access events and admin overrides
        │   ├── SecurityDefensesFilter.java     # HTTP enterprise filter enforcing HTTPS redirection, HSTS headers, CSP, X-Frame-Options, and X-XSS
        │   └── StudentSecurityFilter.java      # Additional defense filter preventing student cross-tenant data traversal
        │
        └── service/                            # Core Business Services & Cloud AI Adaptations
            ├── AiPipelineService.java          # Consolidated OpenAI/Claude REST connector with rate-limit backoff, JSON extraction, and topic generation
            ├── AnalyticsService.java           # Computes real-time institutional metrics and builds structured CSV data feeds
            ├── AuthService.java                # Handles onboarding validation and stamps mandatory role claims onto Firebase user identities
            ├── CompletionService.java          # Manages final performance evaluation and graduation clearance certificate generation
            ├── DailyStatusService.java         # Synchronizes real-time attendance attendance status across dashboards
            ├── DiaryService.java               # Directs diary logs through AI Pipeline; persists accepted to 'diaries' and rejected to 'suspicious_diaries'
            ├── FaceMatchService.java           # [UPGRADED] Real AWS Rekognition CompareFaces SDK integration with backoff retries and threshold routing
            ├── HodService.java                 # Executes HOD university application evaluations and status transitions
            ├── MentorReviewService.java        # [UPGRADED] Real Firestore integration saving borderline check-ins directly to 'mentor_reviews' collection
            ├── MentorSuspiciousService.java    # Manages faculty review ledgers for AI-flagged diary infractions
            ├── PopupService.java               # Evaluates attendance check-in photos against student reference portraits and manages meeting quotas
            ├── QuotaService.java               # Enforces maximum monthly meeting check-in overrides per institutional standards
            ├── TestQuestionService.java        # [UPGRADED] Queries live generated exam banks from Firestore 'test_questions' collection before fallback
            └── TestService.java                # [UPGRADED] Evaluates proctored exam biometric photos; routes borderline captures directly to mentor review
```

### Frontend Codebase (`frontend/src/`)
```
frontend/src/
├── App.jsx                                     # Primary application router wiring protected departmental paths and context providers
├── index.css                                   # Global Vanilla CSS styling tokens, color variables, and responsive grid layouts
├── main.jsx                                    # React DOM initialization and root hydration
│
├── components/
│   ├── AuthContext.jsx                         # Global React state providing verified Firebase Auth user and cryptographic custom role claims
│   ├── Navigation.jsx                          # Dynamic application top navigation bar rendering tabs based on user departmental roles
│   └── ProtectedRoute.jsx                      # [NEW] Enterprise route guard blocking typed URLs and directing unauthorized roles to 403 screen
│
└── pages/
    ├── AttendanceTracker.jsx                   # Student calendar view displaying daily check-in histories, test scores, and compliance metrics
    ├── BiometricCheckin.jsx                    # Interactive student camera capture modal capturing webcam streams for AWS Rekognition comparison
    ├── BorderlineReviews.jsx                   # Faculty mentor workspace for inspecting 40%–75% biometric match scans and adjudicating validity
    ├── Dashboard.jsx                           # Primary student overview showing active popups, pending diary entries, and overall attendance score
    ├── DiarySubmit.jsx                         # Student form for submitting daily engineering activity logs for automated LLM evaluation
    ├── HodDashboard.jsx                        # HOD governance suite for reviewing applicant credentials and executing institutional approvals
    ├── Login.jsx                               # Secure authentication screen supporting Firebase sign-in and development token evaluation
    ├── MentorDashboard.jsx                     # Faculty mentor management overview tracking assigned intern cohorts and compliance infractions
    ├── ProctoredTest.jsx                       # Examination portal capturing start and submit webcam snapshots to verify candidate presence
    ├── Register.jsx                            # Student registration onboarding form capturing domain specialization and initial reference photo
    ├── SuspiciousReviews.jsx                   # Faculty ledger displaying AI-rejected diary entries with highlighted model reasoning and topics
    ├── Unauthorized.jsx                        # [NEW] Enterprise 403 access denied interface ("You don't have permission to view this page.")
    └── Unauthorized.module.css                 # [NEW] Modular styling syntax for Unauthorized page aesthetics and micro-animations
```

---

## 3. Real-World AI Integrations & Routing Proof

### 1. Optical Biometric Identity Validation (AWS Rekognition)
* **Execution Path:** `FaceMatchService.java` invokes `RekognitionClient.compareFaces(CompareFacesRequest)` over live AWS REST endpoints.
* **Image Hydration:** Automatically extracts raw byte arrays from Base64 Data URL streams or HTTP Firebase Storage links using `SdkBytes.fromByteArray(...)`.
* **Institutional Thresholds:**
  * **Score $\ge$ 75.0%:** Auto-Approved (`matchStatus = "APPROVED"`). Attendance ledger instantly synchronized.
  * **Score $\le$ 40.0%:** Auto-Rejected (`matchStatus = "REJECTED"`). Marked as absence; warning counter incremented in monthly escalation table.
  * **Score 40.1% – 74.9%:** Borderline Alert (`matchStatus = "BORDERLINE"`). Instantly routed via `MentorReviewService` into Firestore collection `mentor_reviews`, appearing on the faculty `BorderlineReviews.jsx` workspace for manual visual adjudication.

### 2. Consolidated AI Diary Review & Topic Extraction (OpenAI / Claude LLM)
* **Execution Path:** `DiaryService.java` submits activity text into `AiPipelineService.reviewAndExtractTopics()`, performing a singular consolidated network request.
* **Defensive JSON Extraction:** Immune to markdown formatting artifacts (` ```json ` wrappers). Uses explicit structural character indexing (`content.indexOf('{')` and `lastIndexOf('}')`) to guarantee accurate deserialization.
* **Automated Decision & Escalation Routing:**
  * **On-Topic Acceptance:** Returns `{ "decision": "accept", "topics": ["Spring Boot REST APIs", "Database Schema Architecture"] }`. Saved directly to Firestore `diaries` collection.
  * **Off-Topic Rejection:** Returns `{ "decision": "reject", "reason": "Entry discusses culinary recipes or non-engineering hobbies...", "topics": ["Off-Topic Submission"] }`. Instantly diverted to Firestore `suspicious_diaries` collection, flagging student for faculty audit on `SuspiciousReviews.jsx`.

### 3. Automated Weekly Exam Question Generation
* **Execution Path:** `WeeklyQuestionGenJob.java` fires via cron schedule (`0 0 2 * * SUN`) or manual HOD trigger (`POST /api/hod/triggers/generate-weekly-questions`).
* **Synthesis Engine:** Reads past 14 days of submitted student activity logs and crafts 5 challenging multiple-choice examination questions directly testing the technical concepts reported by the student.
* **Storage & Consumption:** Stored in Firestore collection `test_questions` under ID `{uid}_WEEKLY_BANK`. When a student launches an examination via `ProctoredTest.jsx`, `TestQuestionService.java` extracts this custom bank, falling back to localized domain baseline questions only with an explicit `WARN`-level system log if genuinely zero custom tests exist.

---

## 4. Security Hardening Layer Summary
* **Privilege Escalation Prevention:** User custom claims (`role`) are stamped strictly inside `AuthService.register()`. Zero endpoints exist anywhere across controllers to modify user roles post-creation. Frontend components read custom claims directly from signed JWT tokens via `AuthContext.jsx`.
* **URL Protection:** All administrative and mentor routing in `App.jsx` is securely wrapped inside `<ProtectedRoute allowedRoles={[...]}>`. Direct manual address bar entry by unauthorized users instantly triggers a redirect to the styled 403 Unauthorized page.
* **Brute-Force & Denial of Service Defense:** Sensitive authentication and onboarding endpoints are wrapped in a lightweight Bucket4j token bucket filter restricting IP request velocity to 5 attempts per minute.
* **HTTPS & Header Enforcement:** All production interactions pass through `SecurityDefensesFilter`, enforcing explicit HTTPS upgrades, HTTP Strict Transport Security (HSTS), Content Security Policy (CSP) headers, and XSS protection frames.
