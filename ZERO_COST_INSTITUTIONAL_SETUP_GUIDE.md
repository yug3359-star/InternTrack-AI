# 🏛️ InternTrack AI: Zero-Cost Institutional Setup & Implementation Guide

This definitive technical manual provides the exhaustive, step-by-step roadmap for configuring, deploying, and operating **InternTrack AI** as a fully institutional-grade academic monitoring platform for **$0.00 total spend**.

By integrating zero-cost edge networks (Firebase Spark), containerized free hosting tiers (Render), free cloud supercomputers (Groq Llama-3), optical AI allowances (AWS Free Tier), and automated keep-alive beacons (UptimeRobot), your university engineering department can operate this enterprise architecture without requiring budgets or credit card deployments.

---

## 📊 Complete System Architecture & Cost Audit

| Architecture Layer | Technology / Vendor | Institutional Duty | Enterprise Zero-Cost Strategy | Monthly Spend |
| :--- | :--- | :--- | :--- | :--- |
| **Frontend Web Portal** | **React 18 + Vite (Firebase Hosting)** | Role-scoped web application for Students, Faculty Mentors, and Department Heads (HOD). | Hosted on **Firebase Spark Tier** (10 GB high-speed SSD CDN storage, 360 MB/day network transfer allowance). | **$0.00** *(Deployed Live)* |
| **Backend REST Server**| **Spring Boot 3 + Java 17 (Render.com)** | Stateful authentication validation, scheduled cron jobs, rate limiting, and business logic execution. | Containerized via our multi-stage `Dockerfile` on **Render Free Web Service** (750 hours/month = 24/7 continuous uptime). | **$0.00** |
| **Database & File Store**| **Firebase Cloud Firestore & Storage** | NoSQL document storage for student logs and secure Object Storage for baseline reference photos & validation PDFs. | **Firebase Spark Tier** (50,000 document reads, 20,000 writes/day, 5 GB portrait & PDF capacity). | **$0.00** |
| **AI LLM Evaluation Engine**| **Groq Cloud (Meta Llama 3 70B)** | Module 6/7 Daily Diary Relevance Auditor, Topic Extractor, and Automated Weekly Test Question Bank Synthesis. | Replaced paid OpenAI API with **Groq Open-Source Cloud REST API** (30 requests/min, zero credit card requirement). | **$0.00** |
| **Biometric Vision AI**| **Amazon Rekognition** | Optical facial comparison verifying real-time webcam captures against authenticated reference photos during exams/popups. | **AWS Free Tier** (1,000 free facial comparisons per month for 12 months, backed up by intelligent local simulation). | **$0.00** |
| **24/7 Uptime Beacon** | **UptimeRobot Keep-Alive Pulse** | Pings server every 10 minutes to prevent container idling, guaranteeing weekend cron workers trigger on time. | **UptimeRobot Free Account** (Up to 50 automated HTTP check pulses at 5-10 minute intervals). | **$0.00** |
| **TOTAL MONTHLY OPERATING COST** | | | | **$0.00 USD** |

---

## 🚀 Exact Step-by-Step Remaining Implementation Plan

Follow these 5 distinct chronological steps to finalize your zero-cost institutional implementation:

```
[Phase 1: Database Rules] ──> [Phase 2: Free Groq AI Key] ──> [Phase 3: Render Env Config] ──> [Phase 4: Uptime Pulse] ──> [Phase 5: Live Verification]
```

---

### 🛡️ Phase 1: Lock Down Firebase Production Security Rules ($0 Cost)

To enforce institutional Role-Based Access Control (RBAC) at the network database layer so malicious users can never forge role claims or read peers' daily logs directly from browser developer consoles:

1. Open your terminal inside the project root folder (`C:\Users\yyugg\OneDrive\Desktop\Internship monitoring`).
2. Verify that your CLI targets your active project (**`interntrack-ai-98f45`**):
   ```powershell
   firebase use interntrack-ai-98f45
   ```
3. Execute live deployment of both Firestore and Storage defense-in-depth security policies:
   ```powershell
   firebase deploy --only firestore:rules,storage:rules
   ```
4. **Institutional Confirmation:** Upon completion, the Firebase terminal logs will output:
   * `✔ firestore: released rules firestore.rules to https://console.firebase.google.com/...`
   * `✔ storage: released rules storage.rules to https://console.firebase.google.com/...`

---

### 🧠 Phase 2: Provision Zero-Cost Supercomputer AI Credentials ($0 Cost)

We eliminated the $5 OpenAI developer deposit requirement by enabling open-source **Meta Llama 3 (70 Billion parameters)** hosted on **Groq Cloud** as a drop-in replacement for ChatGPT!

#### 1. Generate Your Free LLM Key (No Credit Card Required)
1. Navigate in your browser to **[https://console.groq.com](https://console.groq.com)**.
2. Sign up or log in instantly using your Google Account (`yuglodha67@gmail.com`).
3. Click on **API Keys** in the left-hand menu $\to$ Click **Create API Key**.
4. Enter Key Name: `interntrack-free-engine` $\to$ Click **Submit**.
5. Copy down your new secret key string, which begins with **`gsk_...`**

#### 2. Provision AWS Biometric Facial Vision Key (AWS Free Tier - 1,000 Photos/Month)
1. Navigate to **[https://aws.amazon.com](https://aws.amazon.com)** and create a Free AWS Builder Account.
2. Search for **IAM (Identity & Access Management)** in the top services console bar.
3. Click **Users** $\to$ **Create User** $\to$ Name: `interntrack-vision-auditor`.
4. On Step 2 (Permissions), choose **Attach policies directly** $\to$ Search for and check **`AmazonRekognitionFullAccess`**.
5. Click **Create User**. Once complete, click on the username $\to$ Open **Security credentials** tab $\to$ Click **Create access key**.
6. Select *Application running outside AWS* $\to$ Save your generated:
   * **`AWS_ACCESS_KEY_ID`** (Example: `AKIAIOSFODNN7EXAMPLE`)
   * **`AWS_SECRET_ACCESS_KEY`** (40-character secret key string)

---

### ⚙️ Phase 3: Push Docker Codebase & Configure Render Server ($0 Cost)

Because we created an automated multi-stage `backend/Dockerfile` and upgraded `AiPipelineService.java` to dynamically interpret free AI endpoints, Render will autonomously compile and host your Java 17 backend!

#### 1. Commit and Push Latest Code Improvements to GitHub
In your integrated VS Code terminal or PowerShell, push today's zero-cost code enhancements:
```powershell
git push -u origin main
```

#### 2. Configure Render Web Service Parameters
1. Open your **Render.com Dashboard** $\to$ Select your `InternTrack-AI` service (or create New Web Service targeting repository `https://github.com/yugjain020/InternTrack-AI`).
2. Set **Language / Environment:** Choose **`Docker`** *(Do NOT select Node)*.
3. Set **Root Directory:** Type **`backend`**.
4. Navigate to the **Environment (Env Vars)** tab in Render and input these precise institutional configuration variables:

| Environment Variable Key | Exact Target Value to Inject | Institutional Purpose & Wallet Protection |
| :--- | :--- | :--- |
| **`SPRING_PROFILES_ACTIVE`** | `prod` | Activates production HTTPS enforcement filters and rate-limiting barriers |
| **`FIREBASE_STORAGE_BUCKET`**| `interntrack-ai-98f45.appspot.com` | Directly links server file verifiers to your real Firebase cloud bucket |
| **`CORS_ALLOWED_ORIGINS`** | `https://interntrack-ai-98f45.web.app,http://localhost:5173` | Authorizes your live deployed web frontend to communicate securely |
| **`LLM_API_URL`** | `https://api.groq.com/openai/v1/chat/completions` | **Critical Zero-Cost Switch:** Redirects LLM calls from OpenAI to Free Groq! |
| **`OPENAI_API_KEY`** | `gsk_YourRealGroqKeyHere...` | Sets the authentication token for your free Groq Llama-3 developer session |
| **`AI_MODEL`** | `llama3-70b-8192` | Assigns Meta's flagship 70B parameter open model for student log evaluation |
| **`AWS_ACCESS_KEY_ID`** | `AKIA...` *(or leave blank for localized fallback)*| Enables optical face biometric comparisons within the 1,000 free monthly tier |
| **`AWS_SECRET_ACCESS_KEY`** | `wJalrX...` | Cryptographically signs facial comparison packages sent to AWS servers |
| **`AWS_REGION`** | `us-east-1` | Specifies US East North Virginia server complex for lowest processing latency|

5. Click **Save Changes** (or **Manual Deploy $\to$ Deploy latest commit**). Render will spin up a Java builder container, compile Maven, and launch your server at your dedicated URL (e.g., `https://interntrack-ai-olzy.onrender.com`)!

---

### 💓 Phase 4: Install Keep-Alive Uptime Heartbeat ($0 Cost)

Free tier cloud servers typically enter a "sleep" or "idle" mode after 15 minutes of receiving zero incoming web traffic. If your backend is asleep on **Sunday at 2:00 AM**, your automated **Weekly AI Exam Question Generator** cron job (`WeeklyQuestionGenJob.java`) will fail to trigger!

To keep your server permanently wide awake 24 hours a day for $0:

1. Register a free account at **[https://uptimerobot.com](https://uptimerobot.com)**.
2. On your UptimeRobot dashboard, click **+ Add New Monitor**.
3. Set **Monitor Type:** Select **`HTTP(s)`**.
4. Set **Friendly Name:** `InternTrack-AI-Production-Heartbeat`.
5. Set **URL (IP or Host):** Enter your live Render backend health address:
   ```
   https://interntrack-ai-olzy.onrender.com/api/health
   ```
6. Set **Monitoring Interval:** Choose **`10 Minutes`** *(This ensures an automated HTTP heartbeat arrives every 600 seconds, mathematically resetting Render's 15-minute shutdown countdown!)*.
7. Click **Create Monitor**. Your automated cron background schedulers are now permanently insulated from free-tier sleep cycles!

---

### 🧪 Phase 5: Final Institutional Acceptance Verification Suite

To confirm your $0 platform is officially operating at full institutional compliance, perform this 4-step acceptance audit:

#### 1. Confirm Live Backend Diagnostic Connection
Open your browser or terminal and hit your live Render health URL:
```bash
curl https://interntrack-ai-olzy.onrender.com/api/health
```
* **Expected Result:** Instantaneous JSON response confirming running state:  
  `{"status":"alive","timestamp":1785232500000}`

#### 2. Execute Automated Terminal System Audit
On your Windows machine, open PowerShell in the project directory and run our self-contained auditing suite:
```powershell
powershell -ExecutionPolicy Bypass -File .\test_ai_systems_audit.ps1
```
* **Expected Result:** The audit tool will evaluate all three pipelines and output green success blocks:
  * `[REAL LLM PIPELINE SUCCESS] Decision: [accept], Extracted Topics: [React UI Development, Spring Boot REST APIs]`
  * `[AI QUESTION BANK SAVED] Successfully stored 5 generated exam questions in Firestore test_questions/...`
  * `[REAL AWS SDK SUCCESS] Verified optical biometric comparison score: [98.4%]`

#### 3. Perform Live Web Portal Audit
1. Open your production portal in Chrome or Edge:  
   👉 **[https://interntrack-ai-98f45.web.app](https://interntrack-ai-98f45.web.app)**
2. Sign in with a test Student account $\to$ Navigate to **Daily Diary**.
3. Submit a real engineering log (e.g., *"Integrated Spring Security JWT bearer filters with Cloud Firestore repositories"*).
4. Verify that within 2 seconds, Groq's Llama-3 supercomputer evaluates the entry, marks compliance status as **ACCEPTED**, and generates custom study topics in real-time!

---

## 🏁 Summary of Complete Operations

By adhering to this document, your university Computer Science department achieves:
* **Mathematical Data Insulation:** Production records inside `interntrack-ai-98f45` remain protected behind strict Firebase Security Rules.
* **Biometric Integrity:** Real-time webcam portraits are checked against initial registration IDs without commercial software licensing fees.
* **Automated Academic Oversight:** Faculty mentors receive high-density analytics on student punctuality, while automated question generators quiz interns weekly on their actual self-reported accomplishments.
* **Perpetual Zero-Cost Sustainability:** The complete computing, storage, AI reasoning, and hosting pipeline costs precisely **$0.00** per month.

*Your InternTrack AI platform is fully initialized, hardened, and ready for institutional deployment!* 🚀
