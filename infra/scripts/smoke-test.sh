#!/usr/bin/env bash
# ==============================================================================
# FinCore 360 — Automated Staging & Production Smoke Test Suite
# Used in CI/CD pipelines (Stage 6) to verify deployment health before traffic cutover.
# Returns 0 on success; non-zero triggers automated rollback (DEPLOYMENT.md §5).
# ==============================================================================
set -euo pipefail

BASE_URL="${1:-http://localhost:8080}"
echo "========================================================"
echo "FinCore 360 Smoke Test Suite: Verifying ${BASE_URL}"
echo "========================================================"

FAILED=0

# Helper function to test an endpoint with curl
test_endpoint() {
    local name="$1"
    local path="$2"
    local expected_substring="$3"

    echo -n "Checking ${name} [${path}]... "
    local response
    if response=$(curl -s -f -m 10 "${BASE_URL}${path}" 2>&1); then
        if [[ "${response}" == *"${expected_substring}"* ]]; then
            echo "PASSED"
        else
            echo "FAILED (Expected '${expected_substring}', got: ${response:0:100}...)"
            FAILED=1
        fi
    else
        echo "FAILED (HTTP request error: ${response})"
        FAILED=1
    fi
}

# 1. Verify Process Liveness Probe (Decoupled from external DB)
test_endpoint "Liveness Probe" "/actuator/health/liveness" '"status":"UP"'

# 2. Verify Readiness Probe (Confirms DB connectivity and schema availability)
test_endpoint "Readiness Probe" "/actuator/health/readiness" '"status":"UP"'

# 3. Verify OpenAPI Documentation Endpoint
test_endpoint "OpenAPI Spec" "/v3/api-docs" '"openapi"'

# 4. Verify Prometheus / Actuator Core Banking Metrics
test_endpoint "Transfer Failure Metric" "/actuator/metrics/fincore.transfers.failed" '"name":"fincore.transfers.failed"'

# 5. Verify Security Headers (OWASP Compliance)
echo -n "Checking Strict Security Headers... "
HEADERS=$(curl -s -I -m 10 "${BASE_URL}/actuator/health" || true)
if echo "${HEADERS}" | grep -iq "X-Content-Type-Options: nosniff" && echo "${HEADERS}" | grep -iq "X-Frame-Options: DENY"; then
    echo "PASSED"
else
    echo "WARNING / NON-BLOCKING: Some security headers omitted on actuator path."
fi

echo "========================================================"
if [ ${FAILED} -eq 0 ]; then
    echo "ALL SMOKE TESTS PASSED: Deployment healthy and verified!"
    exit 0
else
    echo "SMOKE TESTS FAILED: Immediate rollback required!"
    exit 1
fi
