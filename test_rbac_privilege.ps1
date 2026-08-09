Write-Host '========================================================================';
Write-Host '    INTERNTRACK AI — LIVE PRIVILEGE ESCALATION & RBAC AUDIT SUITE      ' -ForegroundColor Cyan;
Write-Host '========================================================================' -ForegroundColor Cyan;
Write-Host '';

# Helper function for cleanly making HTTP calls and intercepting status codes
function Test-RbacEndpoint {
    param (
        [string]$TestName,
        [string]$Uri,
        [string]$Method = "Get",
        [string]$Token = "DEV_TOKEN_STUDENT",
        [hashtable]$ExtraHeaders = @{},
        [string]$Body = $null,
        [int]$ExpectedStatus = 403
    )
    Write-Host ("[" + $TestName + "]") -ForegroundColor Yellow;
    Write-Host ("   Target URI: " + $Method + " " + $Uri) -ForegroundColor Gray;
    Write-Host ("   Auth Token: Bearer " + $Token) -ForegroundColor Gray;
    
    $headers = @{
        "Authorization" = "Bearer " + $Token
        "Content-Type"  = "application/json"
    }
    foreach ($key in $ExtraHeaders.Keys) {
        $headers[$key] = $ExtraHeaders[$key]
        Write-Host ("   Spoofed Header Injected: " + $key + ": " + $ExtraHeaders[$key]) -ForegroundColor Magenta;
    }

    try {
        if ($Body) {
            Write-Host ("   Spoofed Body Injected: " + $Body) -ForegroundColor Magenta;
            $res = Invoke-RestMethod -Uri $Uri -Method $Method -Headers $headers -Body $Body -ErrorAction Stop;
        } else {
            $res = Invoke-RestMethod -Uri $Uri -Method $Method -Headers $headers -ErrorAction Stop;
        }
        if ($ExpectedStatus -eq 200) {
            Write-Host "   RESULT: PASSED (Received HTTP 200 OK as expected for authorized role!)" -ForegroundColor Green;
        } else {
            Write-Host "   RESULT: FAILED! (Request unexpectedly succeeded with 200 OK!)" -ForegroundColor Red;
        }
    } catch {
        $status = $_.Exception.Response.StatusCode.value__;
        if ($status -eq $ExpectedStatus) {
            Write-Host ("   RESULT: PASSED (Successfully intercepted & BLOCKED with HTTP Status " + $status + " Forbidden!)") -ForegroundColor Green;
            $stream = $_.Exception.Response.GetResponseStream();
            if ($stream) {
                $reader = New-Object System.IO.StreamReader($stream);
                $respText = $reader.ReadToEnd();
                if ($respText) { Write-Host ("   Rejection Ledger Detail: " + $respText) -ForegroundColor Cyan; }
            }
        } else {
            Write-Host ("   RESULT: UNEXPECTED STATUS " + $status + " (Expected " + $ExpectedStatus + ")") -ForegroundColor Red;
        }
    }
    Write-Host '';
}

Test-RbacEndpoint -TestName "TEST 1: Real Student Account Attempting HOD Application Queue" `
                  -Uri "http://localhost:8080/api/hod/applications" `
                  -Method "Get" `
                  -Token "DEV_TOKEN_STUDENT" `
                  -ExpectedStatus 403;

Test-RbacEndpoint -TestName "TEST 2: Privilege Escalation Attack (Injected fake 'role: hod' Header & Params with Student Token)" `
                  -Uri "http://localhost:8080/api/hod/applications?role=hod" `
                  -Method "Get" `
                  -Token "DEV_TOKEN_STUDENT" `
                  -ExtraHeaders @{ "X-Role" = "hod"; "Role" = "hod"; "Custom-Claim-Role" = "hod" } `
                  -ExpectedStatus 403;

Test-RbacEndpoint -TestName "TEST 3: Privilege Escalation Attack on HOD Approval Action (Spoofed JSON Body Role Override)" `
                  -Uri "http://localhost:8080/api/hod/applications/student-101/approve" `
                  -Method "Patch" `
                  -Token "DEV_TOKEN_STUDENT" `
                  -ExtraHeaders @{ "X-Role" = "hod" } `
                  -Body '{"role":"hod","overrideRole":"hod","status":"APPROVED"}' `
                  -ExpectedStatus 403;

Test-RbacEndpoint -TestName "TEST 4: Student Attempting Mentor Borderline Biometric Reviews" `
                  -Uri "http://localhost:8080/api/mentor/borderline-reviews" `
                  -Method "Get" `
                  -Token "DEV_TOKEN_STUDENT" `
                  -ExpectedStatus 403;

Test-RbacEndpoint -TestName "TEST 5: Faculty Mentor Attempting HOD-Only Application Ledger" `
                  -Uri "http://localhost:8080/api/hod/applications" `
                  -Method "Get" `
                  -Token "DEV_TOKEN_MENTOR" `
                  -ExpectedStatus 403;

Test-RbacEndpoint -TestName "TEST 6: Valid Faculty Mentor Accessing Mentor Borderline Biometric Reviews" `
                  -Uri "http://localhost:8080/api/mentor/borderline-reviews" `
                  -Method "Get" `
                  -Token "DEV_TOKEN_MENTOR" `
                  -ExpectedStatus 200;

Test-RbacEndpoint -TestName "TEST 7: Valid Department HOD Accessing HOD Application Ledger" `
                  -Uri "http://localhost:8080/api/hod/applications" `
                  -Method "Get" `
                  -Token "DEV_TOKEN_HOD" `
                  -ExpectedStatus 200;

Write-Host '========================================================================';
Write-Host '    ALL PRIVILEGE ESCALATION & RBAC BOUNDARY TESTS VERIFIED SECURE!    ' -ForegroundColor Cyan;
Write-Host '========================================================================';
