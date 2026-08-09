# InternTrack AI — Authoritative Production API & Services Documentation

This document serves as the exhaustive, authoritative backend API specification for InternTrack AI across Modules 0 through 10 and 15. All route definitions, required authentication parameters, role checks (`@RequireRole` and `@PreAuthorize`), RequestBody Data Transfer Object (DTO) shapes, success responses, and runtime exception payloads directly reflect the compiled spring boot controllers running against our verified Google Cloud Firebase project.

---

## 🔐 Authentication & JWT Authorization
Every HTTP request directed to the InternTrack API (excluding `/api/auth/register`, `/api/health`, and `/api/health/firebase`) strictly mandates an OAuth2-style Bearer Authorization header:
```http
Authorization: Bearer {firebaseIdToken}
```
* **Token Acquisition:** Obtained directly from the client-side Firebase Authentication JavaScript SDK (`getIdToken()`) upon successful candidate or department faculty login.
* **Server-Side Interception:** Validated statelessly at runtime by our custom spring security JWT authorization filter (`JwtAuthenticationFilter`). The filter decodes token signatures against authentic public keys from the Firebase project (`interntrack-ai`) and maps embedded custom claims into Spring security roles (`STUDENT`, `MENTOR`, `HOD`).
* **Unauthorized Rejection:** Any request without a valid token (or expired JWT) is rejected immediately at the filter boundary with `401 Unauthorized`.

---

## 1. Auth/Registration (`AuthController.java`)

### Register Candidate Account & Submit Onboarding Documents
- **Method & Path:** `POST /api/auth/register` (Consumes `multipart/form-data`)
- **Role Required:** none (Public registration intake)
- **Request Body:** exact form-data parameter names matching `RegisterRequest.java`:
  ```json
  {
    "fullName": "Alex Vance",
    "collegeEmail": "alex.vance@cs.college.edu",
    "password": "SecurePassword123!",
    "branch": "Computer Science & Engineering",
    "mentorName": "Dr. Rajesh K.",
    "mentorEmail": "rajesh.k@cs.college.edu",
    "internshipDomain": "Cloud Infrastructure & DevOps",
    "joiningDate": "2026-07-01",
    "completionDate": "2026-12-31",
    "officeStartTime": "09:00",
    "officeEndTime": "17:00",
    "breakStartTime": "13:00",
    "breakEndTime": "14:00",
    "workingDays": "Monday,Tuesday,Wednesday,Thursday,Friday",
    "deviceType": "Laptop",
    "offerLetter": "(Binary MultipartFile < 5MB PDF/Image)",
    "approvalLetter": "(Binary MultipartFile < 5MB PDF/Image)",
    "referencePhoto": "(Binary MultipartFile < 5MB Portrait Image)",
    "consentGiven": "true"
  }
  ```
- **Response (Success):** Status Code `201 Created`
  ```json
  {
    "status": "SUCCESS",
    "uid": "dev-stud-107",
    "message": "Candidate profile created and submitted for Head of Department review."
  }
  ```
- **Response (Error):** Status Code `400 Bad Request`
  ```json
  {
    "status": "ERROR",
    "message": "Only official college email addresses are allowed (.college.edu domain mandated)"
  }
  ```
- **Notes:** Automatically verifies candidate consent (`consentGiven == true`) for live randomized biometric photo audits. Uploaded binary files are transferred directly to Cloud Storage buckets (`/reference-photos`, `/documents`) and referenced inside the generated Firestore `internships/{uid}` record.

---

## 2. HOD Approval (`HodController.java`)

### Get Pending Application Ledger
- **Method & Path:** `GET /api/hod/applications?page=1&status=PENDING`
- **Role Required:** `hod` (Enforced by `@RequireRole("hod")` class-level annotation)
- **Request Body:** None (Query params optional: `page` defaulted to 1, `status` defaulted to `"ALL"`)
- **Response (Success):** Status Code `200 OK`
  ```json
  {
    "applications": [
      {
        "uid": "dev-stud-107",
        "fullName": "Alex Vance",
        "branch": "Computer Science & Engineering",
        "status": "Applied",
        "createdAt": 1785120000000
      }
    ],
    "page": 1,
    "totalCount": 1
  }
  ```
- **Response (Error):** Status Code `403 Forbidden`
  ```json
  {
    "error": "Forbidden",
    "message": "You do not have permission to perform this action. HOD administrative role required."
  }
  ```
- **Notes:** Accessible exclusively by authenticated Department Head identities.

### Get Candidate Application Details
- **Method & Path:** `GET /api/hod/applications/{uid}`
- **Role Required:** `hod`
- **Request Body:** None
- **Response (Success):** Status Code `200 OK`
  ```json
  {
    "uid": "dev-stud-107",
    "fullName": "Alex Vance",
    "collegeEmail": "alex.vance@cs.college.edu",
    "internshipDomain": "Cloud Infrastructure & DevOps",
    "offerLetterUrl": "https://firebasestorage.googleapis.com/.../offer.pdf",
    "status": "Applied"
  }
  ```
- **Response (Error):** Status Code `404 Not Found`
  ```json
  {
    "error": "Candidate not found in application repository."
  }
  ```
- **Notes:** Merges parameters from both `users/{uid}` identity profile and `internships/{uid}` schedule record.

### Approve Internship Application
- **Method & Path:** `PATCH /api/hod/applications/{uid}/approve`
- **Role Required:** `hod`
- **Request Body:** None
- **Response (Success):** Status Code `200 OK`
  ```json
  {
    "status": "Approved",
    "approvedBy": "hod-directory-admin",
    "approvedAt": 1785125000000
  }
  ```
- **Response (Error):** Status Code `400 Bad Request`
  ```json
  {
    "message": "Application transition invalid from current operational state."
  }
  ```
- **Notes:** Records evaluating HOD principal directly into Firestore document attributes and enables active check-in schedulers for the candidate on their joining date.

### Reject Internship Application
- **Method & Path:** `PATCH /api/hod/applications/{uid}/reject`
- **Role Required:** `hod`
- **Request Body:** Optional JSON body:
  ```json
  {
    "reason": "Incomplete corporate offer letter documentation or unrecognized supervisor domain."
  }
  ```
- **Response (Success):** Status Code `200 OK`
  ```json
  {
    "status": "Rejected",
    "rejectionReason": "Incomplete corporate offer letter documentation or unrecognized supervisor domain.",
    "rejectedBy": "hod-directory-admin",
    "rejectedAt": 1785126000000
  }
  ```
- **Response (Error):** Status Code `403 Forbidden`
  ```json
  {
    "message": "Access Denied. RequireRole check failed for Principal."
  }
  ```
- **Notes:** Immediately moves application status to `Rejected` and records the explicit administrative justification required for student visibility.

---

## 3. Status (`StatusController.java`)

### Get Student Lifecycle & Attendance Status
- **Method & Path:** `GET /api/status/{uid}`
- **Role Required:** none explicitly on method (Protected by general JWT authentication filter; accessible by student owner, mentors, HODs)
- **Request Body:** None
- **Response (Success):** Status Code `200 OK`
  ```json
  {
    "uid": "dev-stud-107",
    "currentStatus": "Ongoing",
    "joiningDate": "2026-07-01",
    "completionDate": "2026-12-31",
    "lastEvaluated": 1785130000000
  }
  ```
- **Response (Error):** Status Code `404 Not Found`
  ```json
  {
    "error": "No status tracking ledger located for candidate ID."
  }
  ```
- **Notes:** Evaluates if the current server timestamp falls within active internship boundaries.

### Trigger Manual Status & Completion Transition Audit Job
- **Method & Path:** `POST /api/admin/run-status-job`
- **Role Required:** `hod` (`@RequireRole("hod")` annotation enforced)
- **Request Body:** None
- **Response (Success):** Status Code `200 OK`
  ```json
  {
    "status": "SUCCESS",
    "message": "Status and completion evaluation audit completed successfully",
    "transitionCount": 2,
    "completionCount": 1,
    "timestamp": "2026-07-27T15:45:00+05:30"
  }
  ```
- **Response (Error):** Status Code `403 Forbidden`
  ```json
  {
    "message": "Only authorized Head of Department administration accounts may invoke server scheduled crons."
  }
  ```
- **Notes:** On-demand administration capability allowing HODs to verify and promote records (e.g. automatically transitioning past-completion accounts to `Completed` status) immediately without waiting for automated hourly cron triggers.

---

## 4. Popups (`PopupController.java` & `MentorReviewController.java`)
*Note on architectural endpoint nomenclature:* To support clean resource mapping in REST conventions, working hour check-ins address the candidate directly at `/api/popups/{uid}/respond` while borderline evaluations match `/api/mentor/borderline-reviews`.

### Submit Working Hour Engagement Check-in Response
- **Method & Path:** `POST /api/popups/{uid}/respond` *(Note: Acts as the production implementation of the originally prompted `POST /api/popup/{popupId}/respond` specification)*
- **Role Required:** `student` (Authenticated candidate account)
- **Request Body:** Real payload parameters exactly matching controller evaluation:
  ```json
  {
    "popupId": "dev-stud-107_1785128000",
    "action": "WORKING",
    "webcamPhotoUrl": "https://firebasestorage.googleapis.com/.../popup_capture.jpg"
  }
  ```
  *(Alternative Meeting Bypass Payload):*
  ```json
  {
    "popupId": "dev-stud-107_1785128000",
    "action": "IN_MEETING"
  }
  ```
- **Response (Success):** Status Code `200 OK`
  ```json
  {
    "status": "VERIFIED_FACE",
    "verificationSimilarity": 94.2,
    "respondedAt": 1785128015000,
    "message": "Attendance check verified via biometric face match (>75%)."
  }
  ```
- **Response (Error):** Status Code `400 Bad Request`
  ```json
  {
    "status": "REJECTED",
    "message": "Must be called within the active 2-minute popup check-in window. Window expired."
  }
  ```
- **Notes:** Must be submitted within exactly 120 seconds (2 minutes) of push alert trigger. When `IN_MEETING` is selected, the server verifies if monthly meeting passes exist in `quotas/{uid}_{month}`; if 0 remain, it throws an exception and logs a missed audit.

### Get Pending Borderline Mentor Review Queue
- **Method & Path:** `GET /api/mentor/borderline-reviews` *(Note: Acts as the active production route for prompted `GET /api/mentor/reviews`)*
- **Role Required:** `MENTOR` or `HOD` (`@PreAuthorize("hasAnyRole('MENTOR', 'HOD')")`)
- **Request Body:** None
- **Response (Success):** Status Code `200 OK`
  ```json
  {
    "reviews": [
      {
        "reviewId": "rev-107-alpha",
        "studentUid": "dev-stud-107",
        "studentName": "Alex Vance",
        "referencePhotoUrl": "https://firebasestorage.googleapis.com/.../ref.jpg",
        "capturePhotoUrl": "https://firebasestorage.googleapis.com/.../live.jpg",
        "similarityScore": 58.4,
        "status": "PENDING_REVIEW"
      }
    ],
    "totalPending": 1
  }
  ```
- **Response (Error):** Status Code `403 Forbidden`
  ```json
  {
    "error": "Forbidden",
    "message": "Access Denied. Mentor or HOD role credentials mandated."
  }
  ```
- **Notes:** Captures facial similarity snapshots evaluating between 40% and 75%, holding them in quarantine until faculty human-in-the-loop audit adjudication completes.

### Decide / Resolve Borderline Review
- **Method & Path:** `PATCH /api/mentor/borderline-reviews/{reviewId}/resolve` *(Note: Implements prompted `POST /api/mentor/reviews/{reviewId}/decide` functionality via semantic PATCH syntax)*
- **Role Required:** `MENTOR` or `HOD` (`@PreAuthorize("hasAnyRole('MENTOR', 'HOD')")`)
- **Request Body:** Real JSON payload exactly as read by method:
  ```json
  {
    "action": "APPROVE",
    "notes": "Student verified wearing optical reading glasses in poorly lit cubicle."
  }
  ```
- **Response (Success):** Status Code `200 OK`
  ```json
  {
    "reviewId": "rev-107-alpha",
    "status": "APPROVED_GENUINE",
    "resolvedBy": "FACULTY-MENTOR-01",
    "notes": "Student verified wearing optical reading glasses in poorly lit cubicle."
  }
  ```
- **Response (Error):** Status Code `404 Not Found`
  ```json
  {
    "error": "Target review document ID not located in Firestore store."
  }
  ```
- **Notes:** Accepts optional header `X-Mentor-UID` for logging auditing actions. Approving a review automatically removes the absence discrepancy flag from the student's daily record.

---

## 5. Attendance (`AttendanceController.java`)

### Respond to Daily Morning Attendance Window
- **Method & Path:** `POST /api/attendance/{attendanceId}/respond`
- **Role Required:** `student`
- **Request Body:** Exact DTO mapping from controller:
  ```json
  {
    "action": "present"
  }
  ```
  *(Or for exception passes):* `{"action": "meeting"}`
- **Response (Success):** Status Code `200 OK`
  ```json
  {
    "attendanceId": "dev-stud-107_2026-07-27",
    "uid": "dev-stud-107",
    "date": "2026-07-27",
    "status": "present",
    "respondedAt": 1785131000000,
    "message": "Attendance confirmed present successfully."
  }
  ```
- **Response (Error):** Status Code `400 Bad Request`
  ```json
  {
    "status": "missed",
    "error": "Monthly meeting override quota exhausted. Marked missed (counts as absence)."
  }
  ```
- **Notes:** Must be called within the mandated 1-hour window following registered office start times. If the attendance record is missing during testing, the server automatically provisions a real Firestore test document on-the-fly.

### Get Candidate Historical Attendance Roster
- **Method & Path:** `GET /api/attendance/history/{uid}`
- **Role Required:** none explicitly (Candidate owner, assigned supervisor, and HOD oversight allowed)
- **Request Body:** None
- **Response (Success):** Status Code `200 OK`
  ```json
  {
    "uid": "dev-stud-107",
    "records": [
      {
        "date": "2026-07-27",
        "status": "present",
        "respondedAt": 1785131000000
      }
    ],
    "totalDays": 1,
    "presentDays": 1,
    "excusedDays": 0,
    "missedDays": 0,
    "attendancePercentage": 100.0
  }
  ```
- **Response (Error):** Status Code `500 Internal Server Error`
  ```json
  {
    "error": "Failed to retrieve attendance ledger from cloud database."
  }
  ```
- **Notes:** Server computes absolute attendance percentage via formula `((presentDays + excusedDays) / totalDays) * 100` rounded to 1 decimal place.

---

## 6. Tests (`TestController.java`)

### Start Proctored Evaluation Session
- **Method & Path:** `POST /api/test/{testId}/start`
- **Role Required:** `student`
- **Request Body:** Optional JSON specifying captured start surveillance frame:
  ```json
  {
    "startPhotoUrl": "https://firebasestorage.googleapis.com/.../start_cam.jpg"
  }
  ```
- **Response (Success):** Status Code `200 OK`
  ```json
  {
    "testId": "dev-stud-107_2026-07-27-test",
    "status": "IN_PROGRESS",
    "actualStartTime": 1785135000000,
    "testDeadline": 1785136200000,
    "questions": [
      {
        "index": 0,
        "question": "Analyze the resiliency tradeoffs between asynchronous Kafka replication and synchronous JDBC commit logs."
      }
    ]
  }
  ```
- **Response (Error):** Status Code `400 Bad Request` (Throws `InvalidRegistrationException`)
  ```json
  {
    "error": "Biometric face verification failed. Reference photo mismatch (<40%).",
    "testId": "dev-stud-107_2026-07-27-test"
  }
  ```
- **Notes:** Server immutably sets `testDeadline = actualStartTime + (20 * 60 * 1000)` milliseconds. Client-side timers cannot override or lengthen this evaluation window.

### Submit Single Question Answer During Test
- **Method & Path:** `POST /api/test/{testId}/submit-answer`
- **Role Required:** `student`
- **Request Body:** Required exact JSON shape:
  ```json
  {
    "questionIndex": 0,
    "answer": "Asynchronous replication offers lower write latency but introduces RPO risk during broker failure..."
  }
  ```
- **Response (Success):** Status Code `200 OK`
  ```json
  {
    "testId": "dev-stud-107_2026-07-27-test",
    "status": "ANSWER_RECORDED",
    "questionIndex": 0
  }
  ```
- **Response (Error):** Status Code `400 Bad Request`
  ```json
  {
    "error": "Proctored testing window deadline exceeded. Additional answers rejected."
  }
  ```
- **Notes:** Rejects any answer payload immediately if current server clock exceeds `testDeadline`.

### Final Test Submission & Anti-Cheating Tab Audit
- **Method & Path:** `POST /api/test/{testId}/submit`
- **Role Required:** `student`
- **Request Body:** Exact DTO evaluated in method:
  ```json
  {
    "submitPhotoUrl": "https://firebasestorage.googleapis.com/.../submit_cam.jpg",
    "tabSwitchCount": 2,
    "answers": {
      "0": "Asynchronous replication analysis...",
      "1": "Container orchestrator scheduling logic..."
    }
  }
  ```
- **Response (Success):** Status Code `200 OK`
  ```json
  {
    "testId": "dev-stud-107_2026-07-27-test",
    "status": "COMPLETED",
    "actualSubmitTime": 1785136000000,
    "score": 4,
    "scorePercentage": 80.0,
    "tabSwitchCount": 2
  }
  ```
- **Response (Error):** Status Code `500 Internal Server Error`
  ```json
  {
    "error": "Failed to execute AI automated grading score evaluation."
  }
  ```
- **Notes:** Automatically calculates integer score out of 5 (or percentage / 100). Submitting with 0 answers recorded triggers automatic promotion to `absent` disciplinary classification.

### Reschedule Proctored Exam via Meeting Quota
- **Method & Path:** `POST /api/test/{testId}/reschedule`
- **Role Required:** `student`
- **Request Body:** Optional JSON:
  ```json
  {
    "reason": "Emergency production database failover meeting with supervisor"
  }
  ```
- **Response (Success):** Status Code `200 OK`
  ```json
  {
    "testId": "dev-stud-107_2026-07-27-test",
    "status": "RESCHEDULED",
    "message": "Meeting quota pass consumed (1 pass deducted). Exam moved to next available cycle."
  }
  ```
- **Response (Error):** Status Code `400 Bad Request`
  ```json
  {
    "error": "Exam document already rescheduled once. Subsequent reschedule overrides forbidden."
  }
  ```
- **Notes:** Only permitted exactly once per proctored test document; requires at least 1 remaining meeting pass in monthly student quota.

### Mentor Override Exam Status to Absent
- **Method & Path:** `POST /api/mentor/test/{testId}/override-absent`
- **Role Required:** `MENTOR` or `HOD`
- **Request Body:** Required JSON containing explicit justification remarks:
  ```json
  {
    "reason": "Candidate left webcam optical surveillance frame for over 12 minutes during testing."
  }
  ```
- **Response (Success):** Status Code `200 OK`
  ```json
  {
    "testId": "dev-stud-107_2026-07-27-test",
    "status": "ABSENT",
    "overrideReason": "Candidate left webcam optical surveillance frame for over 12 minutes during testing."
  }
  ```
- **Response (Error):** Status Code `400 Bad Request`
  ```json
  {
    "error": "Missing required field: reason. Faculty override requires mandatory descriptive justification."
  }
  ```
- **Notes:** Faculty RBAC endpoint allowing supervisors to nullify compromised examination results and record formal absence infractions.

---

## 7. Diary (`DiaryController.java`)
*Note on architectural endpoint nomenclature:* To support structured module grouping, work logs map directly to `/api/diaries/submit` and `/api/diaries/student/{uid}` in the active compiled code.

### Submit Daily AI Work Activity Diary
- **Method & Path:** `POST /api/diaries/submit` *(Note: Acts as the production implementation for prompted `POST /api/diary`)*
- **Role Required:** `student`
- **Request Body:** Real DTO evaluated by method:
  ```json
  {
    "uid": "dev-stud-107",
    "date": "2026-07-27",
    "entryText": "Implement Spring Security JWT authentication filter and Redis 5-minute TTL caching configuration for Module 10 analytics endpoints.",
    "studentName": "Alex Vance (2023CSB104)",
    "rejectedDueToFaceMismatch": false
  }
  ```
- **Response (Success):** Status Code `200 OK`
  ```json
  {
    "docId": "dev-stud-107_2026-07-27",
    "verificationStatus": "VERIFIED",
    "reviewReason": "Accepted. Semantic analysis matched registered domain: [Cloud Infrastructure & DevOps].",
    "topics": ["Spring Security", "JWT", "Redis Caching", "Analytics"],
    "submittedAt": 1785140000000
  }
  ```
- **Response (Error):** Status Code `400 Bad Request`
  ```json
  {
    "error": true,
    "message": "Diary log rejected. Entry text is too vague or fails semantic domain relevancy verification."
  }
  ```
- **Notes:** Once committed to Firestore `diaries/{uid}_{date}`, security rules strip candidate update rights, ensuring the record serves as an immutable academic trail.

### Get Candidate Work Diary Timeline
- **Method & Path:** `GET /api/diaries/student/{uid}` *(Note: Implements prompted `GET /api/diary/history/{uid}`)*
- **Role Required:** none explicitly (Candidate owner, assigned mentor, HOD read oversight allowed)
- **Request Body:** None
- **Response (Success):** Status Code `200 OK`
  ```json
  [
    {
      "docId": "dev-stud-107_2026-07-27",
      "date": "2026-07-27",
      "entryText": "Implement Spring Security JWT...",
      "verificationStatus": "VERIFIED",
      "topics": ["Spring Security", "JWT", "Redis Caching"],
      "submittedAt": 1785140000000
    }
  ]
  ```
- **Response (Error):** Status Code `500 Internal Server Error`
  ```json
  {
    "error": "System fault querying daily diary history ledgers from cloud store."
  }
  ```
- **Notes:** Returns chronological history sorted by submission date descending.

---

## 8. Mentor Review / Suspicious AI Diaries (`MentorSuspiciousController.java`)

### Get Suspicious Diary Queue
- **Method & Path:** `GET /api/mentor/suspicious?mentorEmail=rajesh.k@cs.college.edu` *(Also exposed on HOD mirror route `GET /api/hod/suspicious`)*
- **Role Required:** `MENTOR` or `HOD`
- **Request Body:** None (Optional parameter `mentorEmail` to filter by supervisor)
- **Response (Success):** Status Code `200 OK`
  ```json
  [
    {
      "docId": "dev-stud-107_2026-07-25",
      "uid": "dev-stud-107",
      "studentName": "Alex Vance",
      "entryText": "did stuff on the computer all day and checked emails.",
      "reviewReason": "Anomalous AI entry: Vague descriptions and lack of technical engineering specifics.",
      "submittedAt": 1785000000000
    }
  ]
  ```
- **Response (Error):** Status Code `403 Forbidden`
  ```json
  {
    "message": "Faculty authorization required to access suspicious anomaly queue."
  }
  ```
- **Notes:** Isolates diaries automatically flagged by the natural language semantic review pipeline for manual human inspection.

### Execute Decision Override on Suspicious Log
- **Method & Path:** `POST /api/mentor/suspicious/{id}/override` *(Also mirrored on `POST /api/hod/suspicious/{id}/override`)*
- **Role Required:** `MENTOR` or `HOD`
- **Request Body:** Real DTO evaluated by method:
  ```json
  {
    "action": "accept",
    "mentorUid": "Dr. Rajesh K. (Faculty Supervisor)"
  }
  ```
  *(Alternative Reject/Delete Action):* `{"action": "delete"}`
- **Response (Success):** Status Code `200 OK`
  ```json
  {
    "docId": "dev-stud-107_2026-07-25",
    "status": "VERIFIED",
    "overriddenBy": "Dr. Rajesh K. (Faculty Supervisor)",
    "message": "Suspicious AI anomaly manually accepted by faculty override."
  }
  ```
- **Response (Error):** Status Code `400 Bad Request`
  ```json
  {
    "error": true,
    "message": "Invalid override action specified. Must be 'accept' or 'delete'."
  }
  ```
- **Notes:** Manually approving an entry updates the underlying document in `diaries` to `VERIFIED` status and clears the disciplinary infraction count from the candidate's monthly escalation profile.

---

## 9. Escalation & Highlighting (`EscalationController.java`)

### Get Highlighted Students Ledger
- **Method & Path:** `GET /api/hod/highlighted`
- **Role Required:** `hod` (`@RequireRole("hod")`)
- **Request Body:** None
- **Response (Success):** Status Code `200 OK`
  ```json
  [
    {
      "uid": "dev-stud-107",
      "fullName": "Alex Vance",
      "branch": "Computer Science & Engineering",
      "diaryRejectionCount": 3,
      "absenceCount": 2,
      "excuseAbusePercentage": 75.0,
      "highlighted": true,
      "reasons": ["Exceeded monthly maximum AI work diary rejections (>=3)"]
    }
  ]
  ```
- **Response (Error):** Status Code `403 Forbidden`
  ```json
  {
    "message": "Access Denied. RequireRole check failed for Principal."
  }
  ```
- **Notes:** Auto-flags students whose real monthly infraction counters exceed strict institutional safety thresholds.

### Get Student Escalation & Warning History
- **Method & Path:** `GET /api/hod/highlighted/{uid}/history`
- **Role Required:** `hod`
- **Request Body:** None
- **Response (Success):** Status Code `200 OK`
  ```json
  [
    {
      "month": "2026-06",
      "diaryRejectionCount": 1,
      "absenceCount": 0,
      "highlighted": false,
      "reasons": []
    },
    {
      "month": "2026-07",
      "diaryRejectionCount": 3,
      "absenceCount": 2,
      "highlighted": true,
      "reasons": ["Exceeded monthly maximum AI work diary rejections (>=3)"]
    }
  ]
  ```
- **Response (Error):** Status Code `404 Not Found`
  ```json
  {
    "error": "No historical disciplinary warnings located for target candidate."
  }
  ```
- **Notes:** Compiles longitudinal month-over-month disciplinary warning ledgers from `warnings/{uid}_{yyyy-MM}`.

### Trigger Manual Escalation & Warning Audit Job
- **Method & Path:** `POST /api/admin/run-escalation-job?month=2026-07`
- **Role Required:** `hod` (`@RequireRole("hod")`)
- **Request Body:** None (Optional query parameter `month` formatted as `yyyy-MM`)
- **Response (Success):** Status Code `200 OK`
  ```json
  {
    "status": "SUCCESS",
    "message": "Escalation audit completed successfully.",
    "flaggedCount": 1,
    "timestamp": "2026-07-27T15:45:10+05:30"
  }
  ```
- **Response (Error):** Status Code `403 Forbidden`
  ```json
  {
    "message": "Only authorized Department Head principals may execute admin scheduled crons."
  }
  ```
- **Notes:** Allows administrators to compute and apply disciplinary escalation flags immediately without awaiting automated scheduled cron worker execution.

---

## 10. Completion Certification (`CompletionController.java`)

### Get Student Final Completion Summary Certificate
- **Method & Path:** `GET /api/completion/{uid}`
- **Role Required:** none explicitly (Candidate owner, assigned faculty mentor, HOD access allowed)
- **Request Body:** None
- **Response (Success):** Status Code `200 OK`
  ```json
  {
    "uid": "dev-stud-107",
    "fullName": "Alex Vance",
    "internshipDomain": "Cloud Infrastructure & DevOps",
    "attendancePercentage": 96.5,
    "diaryCompliancePercentage": 94.0,
    "averageTestScore": 84.2,
    "excuseUsagePercentage": 12.0,
    "riskLevel": "Low",
    "certifiedAt": 1785145000000
  }
  ```
- **Response (Error):** Status Code `404 Not Found`
  ```json
  null
  ```
  *(Returns explicit HTTP 404 Not Found with null body when student has not yet crossed their completion date boundary or has not completed)*
- **Notes:** Server-validates candidate completion dates via backend database timestamps regardless of client system clock input.

### Get All Completed Roster Roster
- **Method & Path:** `GET /api/hod/completed`
- **Role Required:** `hod` (`@RequireRole("hod")`)
- **Request Body:** None
- **Response (Success):** Status Code `200 OK`
  ```json
  [
    {
      "uid": "dev-stud-107",
      "fullName": "Alex Vance",
      "branch": "Computer Science & Engineering",
      "attendancePercentage": 96.5,
      "riskLevel": "Low",
      "completedAt": 1785145000000
    }
  ]
  ```
- **Response (Error):** Status Code `403 Forbidden`
  ```json
  {
    "error": "Forbidden",
    "message": "Department Head authorization required."
  }
  ```
- **Notes:** Returns institutional certification ledgers for all graduating internship candidates across the program.

---

## 11. Academic Analytics (`AnalyticsController.java` — Module 10)

### Get Student Longitudinal Academic Analytics
- **Method & Path:** `GET /api/analytics/student/{uid}`
- **Role Required:** none explicitly (Candidate owner, assigned mentor, HOD access allowed)
- **Request Body:** None
- **Response (Success):** Status Code `200 OK`
  ```json
  {
    "hasData": true,
    "attendanceOverTime": [
      {"date": "2026-07-20", "value": 100.0},
      {"date": "2026-07-27", "value": 96.5}
    ],
    "diaryComplianceOverTime": [
      {"date": "2026-07-20", "value": 90.0},
      {"date": "2026-07-27", "value": 94.0}
    ],
    "testScoresOverTime": [
      {"date": "2026-07-22", "value": 80.0},
      {"date": "2026-07-25", "value": 88.4}
    ],
    "excuseUsagePercentage": 12.0
  }
  ```
- **Response (Error):** Status Code `200 OK` (Empty Fallback Payload)
  ```json
  {
    "hasData": false,
    "message": "Not enough data to display yet"
  }
  ```
- **Notes:** Aggregates data from `attendance`, `diaries`, and `tests` collections, pre-formatting time series arrays explicitly for instantaneous Recharts frontend rendering without client math requirements.

### Get HOD Institutional Program Overview
- **Method & Path:** `GET /api/analytics/hod/overview`
- **Role Required:** `hod`
- **Request Body:** None
- **Response (Success):** Status Code `200 OK`
  ```json
  {
    "totalOngoing": 42,
    "totalCompleted": 18,
    "totalHighlighted": 3,
    "suspiciousDiariesThisMonth": 2,
    "warningTrends": [
      {"month": "2026-06", "count": 1},
      {"month": "2026-07", "count": 3}
    ]
  }
  ```
- **Response (Error):** Status Code `500 Internal Server Error`
  ```json
  {
    "error": "Failed to assemble cached institutional analytics aggregates."
  }
  ```
- **Notes:** Aggregated results are cached server-side in Spring multi-line memory via `@Cacheable` with a strict **5-Minute Time-To-Live (TTL)** to prevent excessive Firestore database read billing during heavy HOD traffic.

### Get Departmental Branch Comparison Ledgers
- **Method & Path:** `GET /api/analytics/hod/branch-comparison`
- **Role Required:** `hod`
- **Request Body:** None
- **Response (Success):** Status Code `200 OK`
  ```json
  [
    {
      "branch": "Computer Science & Engineering",
      "attendance": 94.2,
      "testScore": 86.1,
      "hasData": true
    },
    {
      "branch": "Electronics & Communication",
      "attendance": 89.5,
      "testScore": 81.0,
      "hasData": true
    }
  ]
  ```
- **Response (Error):** Status Code `200 OK` (Empty list when no students enrolled)
  ```json
  []
  ```
- **Notes:** Groups all active identities by department faculty and averages their longitudinal performance for executive comparative charting.

---

## 12. Health & Diagnostic Utilities (`HealthCheckController.java`)

### System Health & Operational State Check
- **Method & Path:** `GET /api/health`
- **Role Required:** none (Public unauthenticated diagnostic route)
- **Request Body:** None
- **Response (Success):** Status Code `200 OK`
  ```json
  {
    "service": "InternTrack AI — Institutional Academic Portal Backend",
    "operationalState": "ONLINE_HEALTHY",
    "serverTimestamp": "2026-07-27T15:45:20.123",
    "databaseMode": "CLOUD_FIRESTORE_PRODUCTION",
    "securityFilter": "STATELESS_JWT_VERIFICATION_ENABLED"
  }
  ```
- **Response (Error):** Status Code `503 Service Unavailable` (When application contexts fail initialization)
- **Notes:** Useful for load balancers and automated monitoring probes to confirm operational viability.

### Live Cloud Firestore & Firebase Auth Connection Audit
- **Method & Path:** `GET /api/health/firebase`
- **Role Required:** none (Public diagnostic verification route)
- **Request Body:** None
- **Response (Success):** Status Code `200 OK`
  ```json
  {
    "timestamp": "2026-07-27T15:45:20.456",
    "status": "connected",
    "service": "Firebase Firestore & Admin SDK",
    "collectionsDetected": 14,
    "authBeanActive": true
  }
  ```
- **Response (Error):** Status Code `503 Service Unavailable`
  ```json
  {
    "timestamp": "2026-07-27T15:45:20.456",
    "status": "disconnected",
    "message": "Firebase service account JSON not found or credentials unauthenticated in backend resources."
  }
  ```
- **Notes:** Executes an active query against Cloud Firestore (`listCollections()`) to verify production network connectivity and confirm that all 14 schema collections are reachable.

### Session Token & Principal Authorization Diagnostics
- **Method & Path:** `GET /api/auth/status`
- **Role Required:** none (Returns session status whether authenticated or anonymous)
- **Request Body:** None
- **Response (Success - Authenticated):** Status Code `200 OK`
  ```json
  {
    "authenticated": true,
    "principalId": "dev-stud-107",
    "email": "alex.vance@cs.college.edu",
    "grantedAuthorities": [{"authority": "ROLE_STUDENT"}]
  }
  ```
- **Response (Success - Unauthenticated):** Status Code `200 OK`
  ```json
  {
    "authenticated": false,
    "message": "No active institutional session token present in authorization header."
  }
  ```
- **Notes:** Allows client frontends to verify active session roles and debug token claim bindings at runtime.
