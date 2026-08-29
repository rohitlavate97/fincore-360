# ==============================================================================
# FinCore 360 — Automated Staging & Production Smoke Test Suite (PowerShell)
# ==============================================================================
param(
    [string]$BaseUrl = "http://localhost:8080"
)

$ErrorActionPreference = "Stop"
Write-Host "========================================================" -ForegroundColor Cyan
Write-Host "FinCore 360 Smoke Test Suite: Verifying $BaseUrl" -ForegroundColor Cyan
Write-Host "========================================================" -ForegroundColor Cyan

$FailedCount = 0

function Test-Endpoint {
    param(
        [string]$Name,
        [string]$Path,
        [string]$ExpectedSubstring
    )

    Write-Host -NoNewline "Checking $Name [$Path]... "
    try {
        $response = Invoke-RestMethod -Uri "$BaseUrl$Path" -TimeoutSec 10 -Method Get
        $rawJson = $response | ConvertTo-Json -Compress
        if ($rawJson -like "*$ExpectedSubstring*") {
            Write-Host "PASSED" -ForegroundColor Green
        } else {
            Write-Host "FAILED (Expected '$ExpectedSubstring')" -ForegroundColor Red
            $script:FailedCount++
        }
    } catch {
        Write-Host "FAILED ($($_.Exception.Message))" -ForegroundColor Red
        $script:FailedCount++
    }
}

# 1. Liveness Probe
Test-Endpoint -Name "Liveness Probe" -Path "/actuator/health/liveness" -ExpectedSubstring "UP"

# 2. Readiness Probe
Test-Endpoint -Name "Readiness Probe" -Path "/actuator/health/readiness" -ExpectedSubstring "UP"

# 3. OpenAPI Spec
Test-Endpoint -Name "OpenAPI Spec" -Path "/v3/api-docs" -ExpectedSubstring "openapi"

# 4. Actuator Metric
Test-Endpoint -Name "Transfer Failure Metric" -Path "/actuator/metrics/fincore.transfers.failed" -ExpectedSubstring "fincore.transfers.failed"

Write-Host "========================================================" -ForegroundColor Cyan
if ($FailedCount -eq 0) {
    Write-Host "ALL SMOKE TESTS PASSED: Deployment healthy and verified!" -ForegroundColor Green
    exit 0
} else {
    Write-Host "SMOKE TESTS FAILED: Immediate rollback required!" -ForegroundColor Red
    exit 1
}
