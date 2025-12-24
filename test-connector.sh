#!/bin/bash

# FIX Protocol Connector - Test Suite
# This script tests the MuleSoft FIX connector against the Go FIX server

echo "=================================="
echo "FIX Connector Test Suite"
echo "=================================="
echo ""

# Base URL for MuleSoft app
BASE_URL="http://localhost:8081"

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Test counter
TESTS_PASSED=0
TESTS_FAILED=0

# Function to run a test
run_test() {
    local test_name=$1
    local endpoint=$2
    local method=$3
    local data=$4
    
    echo -e "${YELLOW}Testing: $test_name${NC}"
    
    if [ "$method" == "GET" ]; then
        response=$(curl -s -w "\n%{http_code}" "$BASE_URL$endpoint")
    else
        response=$(curl -s -w "\n%{http_code}" -X "$method" "$BASE_URL$endpoint" \
            -H "Content-Type: application/json" \
            -d "$data")
    fi
    
    http_code=$(echo "$response" | tail -n1)
    body=$(echo "$response" | sed '$d')
    
    if [ "$http_code" == "200" ] || [ "$http_code" == "201" ]; then
        echo -e "${GREEN}✓ PASSED${NC}"
        echo "Response: $body"
        TESTS_PASSED=$((TESTS_PASSED + 1))
    else
        echo -e "${RED}✗ FAILED (HTTP $http_code)${NC}"
        echo "Response: $body"
        TESTS_FAILED=$((TESTS_FAILED + 1))
    fi
    echo ""
    sleep 1
}

# Check if MuleSoft app is running
echo "Checking if MuleSoft app is running..."
if ! curl -s -o /dev/null -w "%{http_code}" "$BASE_URL/fix/session" | grep -q "200\|500"; then
    echo -e "${RED}Error: MuleSoft app is not running on port 8081${NC}"
    echo "Please start the MuleSoft application first"
    exit 1
fi
echo -e "${GREEN}✓ MuleSoft app is running${NC}"
echo ""

# Wait a moment for connection to establish
echo "Waiting for FIX session to establish..."
sleep 3
echo ""

# Test 1: Get Session Info
run_test "Get Session Info" \
    "/fix/session" \
    "GET" \
    ""

# Test 2: Send Heartbeat
run_test "Send Heartbeat" \
    "/fix/heartbeat" \
    "POST" \
    '{}'

# Test 3: Send Test Request
run_test "Send Test Request" \
    "/fix/test-request" \
    "POST" \
    '{"testReqId": "TEST-123"}'

# Test 4: Send New Order Single
run_test "Send New Order Single" \
    "/fix/order/new" \
    "POST" \
    '{
        "clOrdID": "ORD-001",
        "side": "1",
        "symbol": "EUR/USD",
        "orderQty": "1000000",
        "price": "1.1850",
        "ordType": "2",
        "timeInForce": "0"
    }'

# Test 5: Send Another Order
run_test "Send Another Order" \
    "/fix/order/new" \
    "POST" \
    '{
        "clOrdID": "ORD-002",
        "side": "2",
        "symbol": "GBP/USD",
        "orderQty": "500000",
        "price": "1.2750"
    }'

# Test 6: Send Custom FIX Message
run_test "Send Custom FIX Message" \
    "/fix/send" \
    "POST" \
    '{
        "msgType": "D",
        "fields": {
            "11": "ORD-003",
            "21": "1",
            "55": "USD/JPY",
            "54": "1",
            "38": "2000000",
            "44": "110.50",
            "40": "2",
            "59": "0"
        }
    }'

# Test 7: Get Session Info Again (check sequence numbers)
run_test "Get Updated Session Info" \
    "/fix/session" \
    "GET" \
    ""

# Test 8: Request Resend (testing gap recovery)
run_test "Request Message Resend" \
    "/fix/resend" \
    "POST" \
    '{
        "beginSeqNo": 1,
        "endSeqNo": 5
    }'

# Summary
echo "=================================="
echo "Test Summary"
echo "=================================="
echo -e "Tests Passed: ${GREEN}$TESTS_PASSED${NC}"
echo -e "Tests Failed: ${RED}$TESTS_FAILED${NC}"
echo -e "Total Tests: $((TESTS_PASSED + TESTS_FAILED))"
echo ""

if [ $TESTS_FAILED -eq 0 ]; then
    echo -e "${GREEN}✓ All tests passed!${NC}"
    exit 0
else
    echo -e "${RED}✗ Some tests failed${NC}"
    exit 1
fi

