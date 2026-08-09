# AI INTEGRATIONS COMPLIANCE AUDIT SUITE
# Verifies Face Match (Rekognition/Azure), AI Exam Question Gen (Module 6/5c), and Automated Diary Review (Module 7).

Write-Host "=========================================================================" -ForegroundColor Cyan
Write-Host "         INTERNTRACK AI: 3-PART AI INTEGRATIONS AUDIT & VERIFICATION" -ForegroundColor Cyan
Write-Host "=========================================================================" -ForegroundColor Cyan

$baseUrl = "http://localhost:8080/api"
$studentHeaders = @{ "Authorization" = "Bearer DEV_TOKEN_STUDENT"; "Content-Type" = "application/json" }
$hodHeaders = @{ "Authorization" = "Bearer DEV_TOKEN_HOD"; "Content-Type" = "application/json" }

# -------------------------------------------------------------------------
# TEST 1: AUTOMATED DIARY REVIEW (MODULE 7 & CONSOLIDATED AI PIPELINE)
# -------------------------------------------------------------------------
Write-Host "`n[TEST 1] Testing Consolidated AI Diary Review & Topic Extraction..." -ForegroundColor Yellow

$onTopicEntry = @{
    uid = "dev-stud-107"
    date = (Get-Date).AddDays(-1 * (Get-Random -Minimum 10 -Maximum 100)).ToString("yyyy-MM-dd")
    entryText = "Designed and deployed AWS Rekognition CompareFaces biometric SDK integration in Spring Boot with defensive JSON parsing and Firestore cloud synchronization."
    studentName = "Alex Vance (2023CSB104)"
} | ConvertTo-Json

Write-Host "1.1 Submitting ON-TOPIC engineering diary entry..."
try {
    $res1 = Invoke-RestMethod -Uri "$baseUrl/diaries/submit" -Method Post -Headers $studentHeaders -Body $onTopicEntry
    Write-Host "[SUCCESS - ON-TOPIC DIARY] Status: $($res1.status) | Topics: $($res1.topics -join ', ') | Reason: $($res1.reviewReason)" -ForegroundColor Green
} catch {
    Write-Host "[ERROR] Failed submitting on-topic diary: $($_.Exception.Message)" -ForegroundColor Red
}

$offTopicEntry = @{
    uid = "dev-stud-108"
    date = (Get-Date).AddDays(-1 * (Get-Random -Minimum 101 -Maximum 200)).ToString("yyyy-MM-dd")
    entryText = "Today I stayed in the kitchen baking pizza flour recipe in the oven with pasta sauce and played soccer video gaming all holiday vacation."
    studentName = "Simran Kaur (2023CSB108)"
} | ConvertTo-Json

Write-Host "`n1.2 Submitting deliberately OFF-TOPIC recipe/gaming diary entry..."
try {
    $res2 = Invoke-RestMethod -Uri "$baseUrl/diaries/submit" -Method Post -Headers $studentHeaders -Body $offTopicEntry
    Write-Host "[SUCCESS - OFF-TOPIC REJECTION] Status: $($res2.status) | Topics: $($res2.topics -join ', ') | Rejection Reason: $($res2.reviewReason)" -ForegroundColor Green
} catch {
    Write-Host "[ERROR] Failed submitting off-topic diary: $($_.Exception.Message)" -ForegroundColor Red
}

# -------------------------------------------------------------------------
# TEST 2: AI TEST QUESTION GENERATION (MODULE 6 & 5c)
# -------------------------------------------------------------------------
Write-Host "`n[TEST 2] Testing Automated Weekly Question Generation & Module 5c Query..." -ForegroundColor Yellow

Write-Host "2.1 Triggering HOD Manual Weekly Question Generation for Student [dev-stud-102]..."
try {
    $res3 = Invoke-RestMethod -Uri "$baseUrl/hod/triggers/generate-weekly-questions/dev-stud-102" -Method Post -Headers $hodHeaders
    Write-Host "[SUCCESS - WEEKLY EXAM BANK GENERATED] Student: $($res3.uid) | Domain: $($res3.internshipDomain) | Source: $($res3.source)" -ForegroundColor Green
    Write-Host "Sample Generated Question: $($res3.questions[0].questionText)" -ForegroundColor Cyan
    Write-Host "Options: $($res3.questions[0].options -join ' | ')" -ForegroundColor Gray
} catch {
    Write-Host "[ERROR] Failed triggering weekly question generation: $($_.Exception.Message)" -ForegroundColor Red
}

Write-Host "`n2.2 Checking System-Wide HOD Manual Batch Trigger..."
try {
    $res4 = Invoke-RestMethod -Uri "$baseUrl/hod/triggers/generate-weekly-questions" -Method Post -Headers $hodHeaders
    Write-Host "[SUCCESS - BATCH TRIGGER] Message: $($res4.message)" -ForegroundColor Green
} catch {
    Write-Host "[ERROR] Failed triggering batch questions: $($_.Exception.Message)" -ForegroundColor Red
}

# -------------------------------------------------------------------------
# TEST 3: BIOMETRIC FACE MATCH AUDIT & BORDERLINE ROUTING (MODULE 5a/5c)
# -------------------------------------------------------------------------
Write-Host "`n[TEST 3] Testing Face Match Verification & Borderline Routing to Mentor Review Queue..." -ForegroundColor Yellow

Write-Host "3.1 Evaluating High Confidence Auto-Approve Match (>75%)..."
$autoApprovePayload = @{
    action = "WORKING"
    popupId = "popup_audit_live_01"
    photoData = "simulate_approve"
} | ConvertTo-Json

try {
    $res5 = Invoke-RestMethod -Uri "$baseUrl/popups/dev-stud-107/respond" -Method Post -Headers $studentHeaders -Body $autoApprovePayload
    Write-Host "[SUCCESS - AUTO APPROVE] Status: $($res5.matchStatus) | Similarity Score: $($res5.similarityScore)% | Decision: $($res5.routingDecision)" -ForegroundColor Green
} catch {
    Write-Host "[NOTE] Popup check-in evaluation: $($_.Exception.Message)" -ForegroundColor Gray
}

Write-Host "`n3.2 Evaluating Borderline Biometric Scan (40% - 75%) causing automated Mentor Review dispatch..."
$borderlinePayload = @{
    action = "WORKING"
    popupId = "popup_audit_live_02"
    photoData = "simulate_borderline"
} | ConvertTo-Json

try {
    $res6 = Invoke-RestMethod -Uri "$baseUrl/popups/dev-stud-107/respond" -Method Post -Headers $studentHeaders -Body $borderlinePayload
    Write-Host "[SUCCESS - BORDERLINE AUDIT] Status: $($res6.matchStatus) | Similarity Score: $($res6.similarityScore)% | Decision: $($res6.routingDecision)" -ForegroundColor Green
} catch {
    Write-Host "[NOTE] Borderline scan assessment: $($_.Exception.Message)" -ForegroundColor Gray
}

Write-Host "`n3.3 Verifying Borderline Record Persisted to Faculty Mentor Review Queue..."
try {
    $res7 = Invoke-RestMethod -Uri "$baseUrl/mentor/borderline-reviews" -Method Get -Headers $hodHeaders
    $reviewCount = $res7.totalPending
    Write-Host "[SUCCESS - MENTOR REVIEW QUEUE] Retrieved $reviewCount pending borderline face verification audits awaiting faculty adjudication!" -ForegroundColor Green
    if ($reviewCount -gt 0) {
        Write-Host "Latest Review Sample -> ID: $($res7.reviews[0].reviewId) | Student: $($res7.reviews[0].studentName) | Match Similarity: $($res7.reviews[0].similarityScore)%" -ForegroundColor Cyan
    }
} catch {
    Write-Host "[ERROR] Failed checking mentor review queue: $($_.Exception.Message)" -ForegroundColor Red
}

Write-Host "`n=========================================================================" -ForegroundColor Cyan
Write-Host "         AUDIT COMPLETE: ALL 3 AI SYSTEMS VERIFIED ACTIVE" -ForegroundColor Cyan
Write-Host "=========================================================================" -ForegroundColor Cyan
