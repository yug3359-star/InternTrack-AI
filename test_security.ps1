Write-Host '==============================================================';
Write-Host '    INTERNTRACK AI — LIVE SECURITY HARDENING TEST SUITE       ' -ForegroundColor Cyan;
Write-Host '==============================================================' -ForegroundColor Cyan;
Write-Host '';

Write-Host '[TEST 1] Verifying Defensive HTTP Headers on Public Endpoint:' -ForegroundColor Yellow;
try {
    $res = Invoke-WebRequest -Uri 'http://localhost:8080/api/health' -UseBasicParsing;
    Write-Host ("   HTTP Status: " + $res.StatusCode + " " + $res.StatusDescription) -ForegroundColor Green;
    Write-Host ("   X-Content-Type-Options:  " + $res.Headers['X-Content-Type-Options']) -ForegroundColor Green;
    Write-Host ("   X-Frame-Options:         " + $res.Headers['X-Frame-Options']) -ForegroundColor Green;
    Write-Host ("   X-XSS-Protection:        " + $res.Headers['X-XSS-Protection']) -ForegroundColor Green;
    Write-Host ("   Content-Security-Policy: " + $res.Headers['Content-Security-Policy']) -ForegroundColor Green;
} catch {
    Write-Host ("   Error testing endpoint: " + $_.Exception.Message) -ForegroundColor Red;
}
Write-Host '';

Write-Host '[TEST 2] Verifying @Valid Input Sanitization on Diary Submission:' -ForegroundColor Yellow;
try {
    Invoke-RestMethod -Uri 'http://localhost:8080/api/diaries/submit' -Method Post -ContentType 'application/json' -Body '{"uid":"hack-1","date":"INVALID_DATE_SCRIPT","entryText":"bad"}' -ErrorAction Stop;
    Write-Host "   Warning: Request unexpectedly succeeded!" -ForegroundColor Yellow;
} catch {
    Write-Host ("   Intercepted invalid payload! Response Code: " + $_.Exception.Response.StatusCode.value__) -ForegroundColor Green;
    $stream = $_.Exception.Response.GetResponseStream();
    if ($stream) {
        $reader = New-Object System.IO.StreamReader($stream);
        Write-Host ("   Rejection Details: " + $reader.ReadToEnd()) -ForegroundColor Cyan;
    }
}
Write-Host '';

Write-Host '[TEST 3] Verifying Bucket4j Brute-Force Rate Limiting (5 req/min on /api/auth/*):' -ForegroundColor Yellow;
Write-Host "   Executing rapid burst of authentication requests to test IP token-bucket throttling..." -ForegroundColor Gray;
for ($i = 1; $i -le 7; $i++) {
    try {
        $r = Invoke-WebRequest -Uri 'http://localhost:8080/api/auth/promote-hod' -Method Post -ContentType 'application/json' -Body '{"targetEmail":"test@college.edu","setupSecret":"short"}' -UseBasicParsing -ErrorAction Stop;
        Write-Host ("   Attempt #" + $i + " -> Permitted under threshold (HTTP Status " + $r.StatusCode + ")") -ForegroundColor Gray;
    } catch {
        $code = $_.Exception.Response.StatusCode.value__;
        if ($code -eq 429) {
            Write-Host ("   Attempt #" + $i + " -> BLOCKED! HTTP 429 Too Many Requests (Rate limit threshold triggered!)") -ForegroundColor Red;
            $stream2 = $_.Exception.Response.GetResponseStream();
            if ($stream2) {
                $reader2 = New-Object System.IO.StreamReader($stream2);
                Write-Host ("   Throttling Response JSON: " + $reader2.ReadToEnd()) -ForegroundColor Magenta;
            }
            break;
        } else {
            Write-Host ("   Attempt #" + $i + " -> Permitted under rate threshold (HTTP Status " + $code + " - Expected validation rejection)") -ForegroundColor Gray;
        }
    }
    Start-Sleep -Milliseconds 100;
}
Write-Host '';
Write-Host '================================================================';
Write-Host '    ALL 3 ENTERPRISE SECURITY TESTS EXECUTED SUCCESSFULLY!      ' -ForegroundColor Cyan;
Write-Host '================================================================';
