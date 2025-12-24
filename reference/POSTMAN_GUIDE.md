# Postman Collection for FIX Protocol Connector

## Overview
This Postman collection provides comprehensive testing for the MuleSoft FIX Protocol Connector through its HTTP REST API.

## Files Included
- **`FIX_Connector_Tests.postman_collection.json`** - Main test collection
- **`FIX_Connector_Local.postman_environment.json`** - Local environment variables

## Quick Start

### 1. Import into Postman

**Option A: Via Postman App**
1. Open Postman
2. Click "Import" button
3. Select both JSON files:
   - `FIX_Connector_Tests.postman_collection.json`
   - `FIX_Connector_Local.postman_environment.json`
4. Click "Import"

**Option B: Via Command Line**
```bash
# Copy files to a convenient location
cp FIX_Connector_Tests.postman_collection.json ~/Downloads/
cp FIX_Connector_Local.postman_environment.json ~/Downloads/
```

### 2. Set Environment
1. Click on environment dropdown (top right)
2. Select "FIX Connector - Local Environment"

### 3. Prerequisites
Ensure these services are running:

```bash
# Terminal 1: Start FIX Server
cd fix-server-go
python3 fix_server.py

# Terminal 2: Start MuleSoft App
cd fix-sample-app
mvn mule:run
```

Wait for:
- FIX server: "FIX Server started on port 9876"
- MuleSoft app: "Application started successfully"

### 4. Run Tests
Click "Send" on any request, or use Collection Runner for automated testing.

---

## Collection Structure

### 📁 **Session Management** (2 requests)
Test session information and sequence control.

#### 1. Get Session Info
- **Method:** GET
- **URL:** `{{baseUrl}}/fix/session`
- **Description:** Retrieves current session status, sequence numbers, and connection details
- **Tests:**
  - ✓ Status code is 200
  - ✓ Session is logged in
  - ✓ Correct SenderCompID and TargetCompID
  - ✓ Sequence numbers are valid

**Expected Response:**
```json
{
  "sessionId": "CLIENT1-SERVER1",
  "senderCompId": "CLIENT1",
  "targetCompId": "SERVER1",
  "status": "LOGGED_IN",
  "incomingSeqNum": 2,
  "outgoingSeqNum": 3,
  "heartbeatInterval": 30,
  "lastMessageReceived": "2025-12-23T16:45:30.123Z",
  "lastMessageSent": "2025-12-23T16:45:35.456Z"
}
```

#### 2. Reset Sequence Numbers
- **Method:** POST
- **URL:** `{{baseUrl}}/fix/reset-sequence`
- **Body:** `{}`
- **Description:** Resets both sequences to 1
- **⚠️ Warning:** Use with caution in production

---

### 📁 **Heartbeat & Connection** (3 requests)
Test connection health monitoring features.

#### 1. Send Heartbeat
- **Method:** POST
- **URL:** `{{baseUrl}}/fix/heartbeat`
- **Body:** `{}`
- **Description:** Sends FIX Heartbeat (MsgType 0)
- **Tests:**
  - ✓ Heartbeat sent successfully
  - ✓ Message type is "0"
  - ✓ Sequence number incremented

#### 2. Send Heartbeat with TestReqID
- **Method:** POST
- **URL:** `{{baseUrl}}/fix/heartbeat`
- **Body:**
```json
{
  "testReqId": "TEST-POSTMAN-001"
}
```
- **Description:** Responds to TestRequest with Heartbeat

#### 3. Send Test Request
- **Method:** POST
- **URL:** `{{baseUrl}}/fix/test-request`
- **Body:**
```json
{
  "testReqId": "POSTMAN-TEST-{{$timestamp}}"
}
```
- **Description:** Tests connection health (MsgType 1)
- **Expected:** Server responds with Heartbeat

---

### 📁 **Order Management** (3 requests)
Test order submission functionality.

#### 1. Send New Order - Market Buy EUR/USD
- **Method:** POST
- **URL:** `{{baseUrl}}/fix/order/new`
- **Body:**
```json
{
  "clOrdID": "ORD-POSTMAN-001",
  "side": "1",
  "symbol": "EUR/USD",
  "orderQty": "1000000",
  "price": "1.1850",
  "ordType": "2",
  "timeInForce": "0"
}
```
- **Description:** Buy 1M EUR/USD at 1.1850

#### 2. Send New Order - Market Sell GBP/USD
- **Side:** "2" (Sell)
- **Symbol:** GBP/USD
- **Quantity:** 500,000

#### 3. Send New Order - Limit Buy USD/JPY
- **Side:** "1" (Buy)
- **Symbol:** USD/JPY
- **Quantity:** 2,000,000
- **TimeInForce:** "1" (GTC)

**Field Reference:**
- **side:** 1=Buy, 2=Sell
- **ordType:** 1=Market, 2=Limit
- **timeInForce:** 0=Day, 1=GTC, 3=IOC

---

### 📁 **Custom FIX Messages** (2 requests)
Test custom message functionality with manual fields.

#### 1. Send Custom FIX Message - NewOrderSingle
- **Method:** POST
- **URL:** `{{baseUrl}}/fix/send`
- **Body:**
```json
{
  "msgType": "D",
  "fields": {
    "11": "CUSTOM-ORD-001",
    "21": "1",
    "55": "EUR/GBP",
    "54": "1",
    "38": "750000",
    "44": "0.8550",
    "40": "2",
    "59": "0",
    "60": "20251223-16:00:00.000"
  }
}
```
- **Description:** Manually specify all FIX tags

#### 2. Send Custom FIX Message - With All Tags
- **Includes:** Account, Exchange, and additional optional fields

---

### 📁 **Gap Recovery** (2 requests)
Test sequence gap detection and recovery.

#### 1. Request Message Resend
- **Method:** POST
- **URL:** `{{baseUrl}}/fix/resend`
- **Body:**
```json
{
  "beginSeqNo": 1,
  "endSeqNo": 5
}
```
- **Description:** Request messages 1-5 (MsgType 2)

#### 2. Request Resend All Missing
- **endSeqNo:** 0 (means all remaining messages)

---

### 📁 **Load Testing** (1 request)
Use Collection Runner for high-volume testing.

#### Send Multiple Orders (Batch)
- **Dynamic Variables:** Uses Postman's random data
- **Usage:** Collection Runner → Set iterations (10, 100, etc.)
- **Pre-request Script:** Generates unique order ID per request

---

## Environment Variables

| Variable | Value | Description |
|----------|-------|-------------|
| `baseUrl` | http://localhost:8081 | MuleSoft app base URL |
| `fixServerHost` | localhost | FIX server hostname |
| `fixServerPort` | 9876 | FIX server port |
| `senderCompId` | CLIENT1 | Client identifier |
| `targetCompId` | SERVER1 | Server identifier |
| `lastSeqNum` | 0 | Last sequence number (auto-updated) |
| `orderId` | - | Generated order ID |

---

## Test Scenarios

### Scenario 1: Basic Connectivity Test
1. **Get Session Info** - Verify connection
2. **Send Heartbeat** - Test message exchange
3. **Send Test Request** - Validate health check

**Expected:** All tests pass, sequence numbers increment

### Scenario 2: Order Submission Flow
1. **Get Session Info** - Check initial state
2. **Send New Order - EUR/USD** - Submit buy order
3. **Send New Order - GBP/USD** - Submit sell order
4. **Get Session Info** - Verify sequences incremented

**Expected:** Orders accepted, ExecutionReports received

### Scenario 3: Sequence Management
1. **Get Session Info** - Note current sequences
2. **Send Heartbeat** (3 times)
3. **Get Session Info** - Verify +3 on outgoing sequence
4. **Reset Sequence Numbers** - Reset to 1
5. **Get Session Info** - Verify reset

**Expected:** Sequences track correctly

### Scenario 4: Gap Recovery Test
1. **Request Resend (1-5)** - Test ResendRequest
2. **Get Session Info** - Check for AWAITING_RESEND status

**Expected:** ResendRequest sent, gap handling active

### Scenario 5: Load Test
1. Select **Send Multiple Orders (Batch)**
2. Click "Run" → "Run Collection"
3. Set iterations: 10, 50, or 100
4. Run collection

**Expected:** All orders accepted, no failures

---

## Automated Testing with Collection Runner

### Run All Tests
1. Click "..." next to collection name
2. Select "Run collection"
3. Choose requests to run
4. Click "Run FIX Connector Tests"

### Run with Delays
- Set delay between requests: 100ms - 1000ms
- Prevents overwhelming the server

### Run with Data File
Create CSV with test data:
```csv
clOrdID,symbol,side,orderQty,price
ORD-001,EUR/USD,1,1000000,1.1850
ORD-002,GBP/USD,2,500000,1.2750
ORD-003,USD/JPY,1,2000000,110.50
```

Use in Collection Runner for data-driven testing.

---

## Test Assertions

Each request includes automated tests:

### Common Assertions
```javascript
pm.test("Status code is 200", function () {
    pm.response.to.have.status(200);
});

pm.test("Response has success field", function () {
    var jsonData = pm.response.json();
    pm.expect(jsonData.success).to.be.true;
});

pm.test("Sequence number is valid", function () {
    var jsonData = pm.response.json();
    pm.expect(jsonData.seqNum).to.be.above(0);
});
```

### Session-Specific Assertions
```javascript
pm.test("Session is logged in", function () {
    var jsonData = pm.response.json();
    pm.expect(jsonData.status).to.eql("LOGGED_IN");
});
```

---

## Tips & Tricks

### 1. Monitor Sequence Numbers
The "Get Session Info" request automatically saves `lastSeqNum` to environment.
Use it in other tests:
```javascript
pm.expect(jsonData.seqNum).to.be.above(pm.environment.get("lastSeqNum"));
```

### 2. Dynamic Order IDs
Use Postman dynamic variables:
- `{{$timestamp}}` - Unix timestamp
- `{{$randomInt}}` - Random integer
- `{{$guid}}` - UUID

### 3. View Console Output
Open Postman Console (View → Show Postman Console) to see:
- Request/response details
- Test results
- Console logs

### 4. Export Test Results
After running collection:
1. Click "Export Results"
2. Choose JSON or HTML format
3. Share with team

---

## Troubleshooting

### Issue: All Requests Fail with Connection Error

**Solution:**
```bash
# Check if MuleSoft app is running
curl http://localhost:8081/fix/session

# If not, start it:
cd fix-sample-app
mvn mule:run
```

### Issue: "Session Not Active" Error

**Solution:**
```bash
# Check FIX server is running
lsof -i :9876

# If not, start it:
cd fix-server-go
python3 fix_server.py
```

### Issue: Sequence Number Errors

**Solution:**
- Run "Reset Sequence Numbers" request
- Restart both FIX server and MuleSoft app

### Issue: Timeout on Requests

**Solution:**
- Increase timeout in Postman Settings → General → Request timeout
- Check server logs for errors

---

## Advanced Usage

### Newman (CLI Testing)

Run collection from command line:

```bash
# Install Newman
npm install -g newman

# Run collection
newman run FIX_Connector_Tests.postman_collection.json \
  -e FIX_Connector_Local.postman_environment.json \
  --reporters cli,json

# Run with iterations
newman run FIX_Connector_Tests.postman_collection.json \
  -e FIX_Connector_Local.postman_environment.json \
  -n 10 \
  --delay-request 100
```

### CI/CD Integration

Add to your CI pipeline:

```yaml
# .github/workflows/test.yml
- name: Run Postman Tests
  run: |
    newman run FIX_Connector_Tests.postman_collection.json \
      -e FIX_Connector_Local.postman_environment.json \
      --reporters cli,junit \
      --reporter-junit-export results.xml
```

---

## Expected Results Summary

| Request | Expected Status | Expected MsgType | Notes |
|---------|----------------|------------------|-------|
| Get Session Info | 200 | N/A | Status: LOGGED_IN |
| Send Heartbeat | 200 | 0 | Success: true |
| Send Test Request | 200 | 1 | Includes TestReqID |
| Send New Order | 200 | D | Order accepted |
| Send Custom Message | 200 | Varies | Fields validated |
| Request Resend | 200 | 2 | ResendRequest sent |
| Reset Sequence | 200 | N/A | Sequences reset to 1 |

---

## Support

For issues or questions:
1. Check `TESTING_GUIDE.md` for setup instructions
2. Review `TEST_RESULTS.md` for validation details
3. Check `TROUBLESHOOTING.md` for common issues
4. Review server logs in fix-server-go output

---

**Happy Testing! 🚀**

The Postman collection provides comprehensive coverage of all FIX Protocol Connector operations with automated test assertions and detailed documentation.

