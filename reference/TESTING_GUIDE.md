# FIX Protocol Connector - Setup and Testing Guide

This guide walks you through setting up and testing the complete FIX Protocol integration.

## Components

1. **FIX Server (Go)** - `fix-server-go/` - Simulates a FIX trading server
2. **FIX Connector (Java)** - `mulesoft-fix-connector/` - MuleSoft connector
3. **Sample App (MuleSoft)** - `fix-sample-app/` - Demonstrates connector usage
4. **Test Suite** - `test-connector.sh` - Automated tests

## Prerequisites

### Required Software

- **Java 17+** - For MuleSoft Runtime
- **Maven 3.6+** - For building connector and app
- **Go 1.21+** - For FIX server (install from https://go.dev/dl/)
- **MuleSoft Runtime 4.4+** or Anypoint Studio

### Installation Steps

#### 1. Install Go (if not installed)

**macOS:**
```bash
brew install go
```

**Linux:**
```bash
wget https://go.dev/dl/go1.21.5.linux-amd64.tar.gz
sudo tar -C /usr/local -xzf go1.21.5.linux-amd64.tar.gz
export PATH=$PATH:/usr/local/go/bin
```

**Verify:**
```bash
go version
```

#### 2. Build the FIX Connector

```bash
cd /Users/alex.macdonald/fix-connector/mulesoft-fix-connector
mvn clean install
```

**Expected Output:**
```
BUILD SUCCESS
Building jar: .../target/mulesoft-fix-connector-1.0.0.jar
```

## Quick Start - 3 Terminal Approach

### Terminal 1: Start FIX Server

```bash
cd /Users/alex.macdonald/fix-connector/fix-server-go

# Build (first time only)
go build -o fix-server

# Run
./fix-server
```

**Expected Output:**
```
2025/12/23 16:40:00 FIX Server started on port 9876
2025/12/23 16:40:00 Waiting for connections...
```

**Keep this terminal running.**

### Terminal 2: Start MuleSoft Application

#### Option A: Using Anypoint Studio

1. Import `fix-sample-app` project
2. Right-click → Run As → Mule Application
3. Wait for "DEPLOYED" message

#### Option B: Using Maven (Standalone)

```bash
cd /Users/alex.macdonald/fix-connector/fix-sample-app

# Package the application
mvn clean package

# Deploy to standalone Mule runtime
# (Assumes Mule runtime is installed)
cp target/fix-sample-app-1.0.0.jar $MULE_HOME/apps/
```

#### Option C: Using Embedded Mule (Development)

```bash
cd /Users/alex.macdonald/fix-connector/fix-sample-app
mvn mule:run
```

**Expected Output:**
```
INFO  ... Application deployment started
INFO  ... FIX connection established
INFO  ... Application started successfully
```

**Watch Terminal 1** - You should see:
```
2025/12/23 16:40:15 New connection from 127.0.0.1:54321
2025/12/23 16:40:15 Received: MsgType=A, SeqNum=1
2025/12/23 16:40:15 Logon from CLIENT1 to SERVER1, HeartbeatInterval=30
2025/12/23 16:40:15 Sent Logon response
```

**Keep this terminal running.**

### Terminal 3: Run Tests

```bash
cd /Users/alex.macdonald/fix-connector

# Run automated test suite
./test-connector.sh
```

## Manual Testing

### Test 1: Check Session Status

```bash
curl http://localhost:8081/fix/session | jq
```

**Expected Response:**
```json
{
  "sessionId": "CLIENT1-SERVER1",
  "senderCompId": "CLIENT1",
  "targetCompId": "SERVER1",
  "status": "LOGGED_IN",
  "incomingSeqNum": 2,
  "outgoingSeqNum": 2,
  "heartbeatInterval": 30,
  "lastMessageReceived": "2025-12-23T16:40:15.123Z",
  "lastMessageSent": "2025-12-23T16:40:15.456Z"
}
```

### Test 2: Send Heartbeat

```bash
curl -X POST http://localhost:8081/fix/heartbeat \
  -H "Content-Type: application/json" \
  -d '{}' | jq
```

**Check Terminal 1 (FIX Server):**
```
Received: MsgType=0, SeqNum=2
Received Heartbeat
```

### Test 3: Send Test Request

```bash
curl -X POST http://localhost:8081/fix/test-request \
  -H "Content-Type: application/json" \
  -d '{"testReqId": "TEST-123"}' | jq
```

**Check Terminal 1:**
```
Received: MsgType=1, SeqNum=3
TestRequest received: TEST-123
Sent Heartbeat response to TestRequest: TEST-123
```

### Test 4: Send New Order

```bash
curl -X POST http://localhost:8081/fix/order/new \
  -H "Content-Type: application/json" \
  -d '{
    "clOrdID": "ORD-001",
    "side": "1",
    "symbol": "EUR/USD",
    "orderQty": "1000000",
    "price": "1.1850"
  }' | jq
```

**Check Terminal 1:**
```
Received: MsgType=D, SeqNum=4
NewOrderSingle: ClOrdID=ORD-001, Symbol=EUR/USD, Side=1, Qty=1000000, Price=1.1850
Sent ExecutionReport for order ORD-001
```

**Check Terminal 2 (MuleSoft):**
You should see the ExecutionReport being received by the listener flow.

### Test 5: Send Multiple Orders

```bash
# Order 1
curl -X POST http://localhost:8081/fix/order/new \
  -H "Content-Type: application/json" \
  -d '{
    "clOrdID": "ORD-002",
    "side": "2",
    "symbol": "GBP/USD",
    "orderQty": "500000",
    "price": "1.2750"
  }'

# Order 2
curl -X POST http://localhost:8081/fix/order/new \
  -H "Content-Type: application/json" \
  -d '{
    "clOrdID": "ORD-003",
    "side": "1",
    "symbol": "USD/JPY",
    "orderQty": "2000000",
    "price": "110.50"
  }'
```

### Test 6: Verify Sequence Numbers

```bash
curl http://localhost:8081/fix/session | jq '.incomingSeqNum, .outgoingSeqNum'
```

Should show incremented sequence numbers.

## What to Observe

### In Terminal 1 (FIX Server):

✅ **Connection Established:**
```
New connection from 127.0.0.1:xxxxx
```

✅ **Logon Exchange:**
```
Received: MsgType=A, SeqNum=1
Logon from CLIENT1 to SERVER1, HeartbeatInterval=30
Sent Logon response
```

✅ **Message Processing:**
```
Received: MsgType=D, SeqNum=X
NewOrderSingle: ClOrdID=..., Symbol=..., Side=..., Qty=..., Price=...
Sent ExecutionReport for order ...
```

✅ **Heartbeats:**
```
Received: MsgType=0, SeqNum=X
Received Heartbeat
Sent scheduled Heartbeat
```

### In Terminal 2 (MuleSoft):

✅ **Application Started:**
```
Application deployment started
FIX connection established
```

✅ **Incoming Messages:**
```
Received FIX message: Type=8, SeqNum=X
Processing Execution Report
Message fields: {...}
```

✅ **Flow Execution:**
```
Sending FIX message: D
FIX message sent successfully
```

## Validation Checklist

### Session Layer

- [ ] Logon message sent and acknowledged
- [ ] Heartbeat interval configured (30 seconds)
- [ ] Automatic heartbeat generation
- [ ] TestRequest/Response working
- [ ] Sequence numbers incrementing correctly
- [ ] Logout handling

### Message Layer

- [ ] NewOrderSingle sent successfully
- [ ] ExecutionReport received
- [ ] Message checksum validation
- [ ] Field parsing correct
- [ ] Custom messages can be sent

### Application Layer

- [ ] HTTP endpoints responding
- [ ] JSON payloads processed
- [ ] FIX messages converted correctly
- [ ] Listener receiving messages
- [ ] Multiple concurrent orders work

## Troubleshooting

### Issue: FIX Server won't start

**Error:** `bind: address already in use`

**Solution:**
```bash
# Find and kill process on port 9876
lsof -ti:9876 | xargs kill -9

# Or use a different port
# Edit fix-server-go/main.go: change port from 9876 to 9877
# Edit fix-sample-app.xml: change port in fix:connection
```

### Issue: MuleSoft app can't connect

**Check:**
1. FIX server is running (Terminal 1 should show "Waiting for connections...")
2. Port is correct (9876)
3. No firewall blocking localhost

**Debug:**
```bash
# Test TCP connectivity
telnet localhost 9876
# Should connect (press Ctrl+] then 'quit' to exit)
```

### Issue: Sequence number mismatch

**Solution:**
Restart both server and app to reset sequences.

### Issue: No ExecutionReports received

**Check:**
1. Listener flow is active (check MuleSoft logs)
2. Message filter is set to "ALL" or "8"
3. Orders are being sent successfully

### Issue: Checksum errors

**Check server logs** for checksum validation messages. This usually indicates:
- Network issues
- Message corruption
- Implementation bug

## Performance Testing

### Load Test: Send 100 Orders

```bash
for i in {1..100}; do
  curl -X POST http://localhost:8081/fix/order/new \
    -H "Content-Type: application/json" \
    -d "{
      \"clOrdID\": \"ORD-$i\",
      \"side\": \"1\",
      \"symbol\": \"EUR/USD\",
      \"orderQty\": \"1000000\",
      \"price\": \"1.1850\"
    }" &
done
wait

# Check final sequence numbers
curl http://localhost:8081/fix/session | jq '.outgoingSeqNum'
```

### Monitor Heartbeats

```bash
# Leave running for 2 minutes to observe automatic heartbeats
watch -n 5 'curl -s http://localhost:8081/fix/session | jq ".lastMessageSent, .lastMessageReceived"'
```

## Clean Shutdown

1. **Stop MuleSoft App** (Terminal 2): `Ctrl+C`
2. **Stop FIX Server** (Terminal 1): `Ctrl+C`

You should see graceful Logout messages exchanged.

## Next Steps

### 1. Extend FIX Server
- Add support for order cancellation (MsgType F)
- Implement market data feeds (MsgType W)
- Add order status requests (MsgType H)

### 2. Enhance MuleSoft App
- Add database integration for order storage
- Implement JMS for async processing
- Add error handling and retry logic
- Create monitoring dashboard

### 3. Production Readiness
- Add TLS/SSL encryption
- Implement message persistence
- Add comprehensive logging
- Set up monitoring and alerts

## Test Results Documentation

After running tests, document:
- All tests passed? ✅/✗
- Average response time
- Sequence number progression
- Any errors observed
- Performance under load

## Support

If you encounter issues:
1. Check all three terminals for error messages
2. Review server logs: `fix-server-go` output
3. Review MuleSoft logs: Check console/log files
4. Verify network connectivity: `telnet localhost 9876`
5. Check sequence numbers match
6. Validate message format

---

**Happy Testing! 🚀**

