# FIX Connector - Postman Testing Guide

## Overview
This guide provides the recommended order and best practices for testing the FIX Protocol Connector using Postman.

## Prerequisites

### 1. Start the FIX Server
Before running any Postman tests, you must start the FIX server:

```bash
cd fix-server-go
python3 fix_server.py
```

**Expected Output:**
```
FIX 4.4 Server starting on localhost:9876
Server ready - waiting for connections...
```

### 2. Start the Mule Application
The Mule application should be running in Anypoint Studio or standalone:
- HTTP Listener: `http://localhost:8081`
- FIX Connection: Auto-connects to `localhost:9876`

### 3. Import Postman Collection
1. Open Postman
2. Click **Import**
3. Select `FIX_Connector_Tests.postman_collection.json`
4. Import `FIX_Connector_Local.postman_environment.json`
5. Select the "FIX Connector - Local" environment

---

## Testing Order

### ⚠️ Important Note About Session Management
The FIX connector automatically handles **Logon** when the connection is established. There is no explicit "Logon" operation in Postman. The connector uses a `CachedConnectionProvider` which:
- Establishes the FIX session on first use
- Sends Logon (MsgType A) automatically
- Maintains the connection across multiple operations
- Reconnects automatically if disconnected

Similarly, **Logout** happens automatically when the Mule app shuts down or the connection is released.

---

## Recommended Test Sequence

### Phase 1: Verify Session (Start Here)

#### 1. **GET Session Info**
- **Folder:** Session Management
- **Purpose:** Verify the FIX session is established
- **Expected Result:** 
  ```json
  {
    "status": "LOGGED_IN",
    "senderCompId": "CLIENT1",
    "targetCompId": "SERVER1",
    "incomingSeqNum": 1,
    "outgoingSeqNum": 2
  }
  ```
- **What It Does:** 
  - Triggers automatic logon if not already logged in
  - Returns current session state
  - Shows sequence numbers

**✅ Success Criteria:**
- Status code: 200
- `status` is "LOGGED_IN"
- Sequence numbers > 0

---

### Phase 2: Basic Message Flow

#### 2. **POST Send Heartbeat**
- **Folder:** Heartbeat & Connection
- **Purpose:** Test basic session maintenance
- **Body:** `{}`
- **Expected Result:**
  ```json
  {
    "success": true,
    "msgType": "0",
    "seqNum": 3
  }
  ```

#### 3. **POST Send Test Request**
- **Folder:** Heartbeat & Connection
- **Purpose:** Verify bidirectional communication
- **Body:**
  ```json
  {
    "testReqId": "POSTMAN-TEST-001"
  }
  ```
- **Expected Result:** Server responds with Heartbeat containing TestReqID

---

### Phase 3: Order Submission

#### 4. **POST Send New Order - Market Buy EUR/USD**
- **Folder:** Order Management
- **Purpose:** Test primary business functionality
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
- **Expected Result:**
  ```json
  {
    "success": true,
    "msgType": "D",
    "seqNum": 4
  }
  ```

#### 5. **POST Send New Order - Market Sell GBP/USD** (Optional)
- Test selling orders
- Different symbol

#### 6. **POST Send New Order - Limit Buy USD/JPY** (Optional)
- Test different order types
- Good-Till-Cancel (GTC) time in force

---

### Phase 4: Custom Messages

#### 7. **POST Send Custom FIX Message - NewOrderSingle**
- **Folder:** Custom FIX Messages
- **Purpose:** Test manual field specification
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

---

### Phase 5: Recovery Operations (Advanced)

#### 8. **POST Request Message Resend**
- **Folder:** Gap Recovery
- **Purpose:** Test sequence number recovery
- **Body:**
  ```json
  {
    "beginSeqNo": 1,
    "endSeqNo": 5
  }
  ```
- **When to Use:** After detecting message gaps

#### 9. **POST Reset Sequence Numbers** ⚠️
- **Folder:** Session Management
- **Purpose:** Reset both sides to sequence 1
- **Body:** `{}`
- **⚠️ WARNING:** Only use when troubleshooting - requires session restart

---

### Phase 6: Verify Final State

#### 10. **GET Session Info** (Again)
- Verify all messages were tracked
- Check final sequence numbers
- Confirm session is still active

---

## Quick Test Flow (Minimum Viable)

If you just want to verify the connector works:

1. **GET Session Info** - Establishes session
2. **POST Send New Order** - Tests core functionality  
3. **GET Session Info** - Verifies state updated

**Total Time:** ~30 seconds

---

## Advanced Testing Scenarios

### Load Testing
Use the **"Send Multiple Orders (Batch)"** request with Postman Runner:
1. Select the "Load Testing" folder
2. Click **Run**
3. Set iterations (e.g., 100)
4. Set delay (e.g., 100ms)
5. Run the collection

### Sequence Recovery Testing
1. Send several orders
2. Stop the FIX server
3. Send more orders (they'll queue)
4. Restart FIX server
5. Use "Request Message Resend" to recover

### Heartbeat Testing
1. **GET Session Info** - Note last heartbeat time
2. Wait 30+ seconds (heartbeat interval)
3. **GET Session Info** - Verify automatic heartbeat sent
4. **POST Send Test Request** - Test manual health check

---

## Monitoring During Tests

### FIX Server Console
Watch the Python server output:
```
Client connected from ('127.0.0.1', 52849)
Received: 8=FIX.4.4|9=95|35=A|49=CLIENT1|56=SERVER1|...
Sent response: 8=FIX.4.4|9=95|35=A|49=SERVER1|56=CLIENT1|...
```

### Mule Application Console
Watch for log messages:
```
INFO  Received request to send FIX message: {...}
INFO  FIX message sent successfully: {...}
```

### Postman Tests Tab
All requests include automated tests that verify:
- Status code (200)
- Response structure
- Message type
- Success flags

---

## Common Issues & Solutions

### Issue: "Session is not active"
**Solution:** The connector hasn't established connection yet
- Run "GET Session Info" first
- Check FIX server is running
- Verify port 9876 is not blocked

### Issue: "Connection refused"
**Solution:** FIX server not running
- Start `fix_server.py`
- Check it's listening on port 9876

### Issue: Sequence number mismatch
**Solution:** 
- Use "POST Reset Sequence Numbers"
- Restart both FIX server and Mule app
- Session should re-sync

### Issue: No response from server
**Solution:**
- Check FIX server console for errors
- Verify message format in Postman request
- Use "POST Send Test Request" to test connection

---

## FIX Field Reference

### Common FIX Tag Numbers (for Custom Messages)

| Tag | Field Name | Description | Example |
|-----|------------|-------------|---------|
| 11 | ClOrdID | Client Order ID | "ORD-001" |
| 21 | HandlInst | Handling Instructions | "1" (Automated) |
| 35 | MsgType | Message Type | "D" (NewOrderSingle) |
| 38 | OrderQty | Order Quantity | "1000000" |
| 40 | OrdType | Order Type | "1" (Market), "2" (Limit) |
| 44 | Price | Price | "1.1850" |
| 54 | Side | Side | "1" (Buy), "2" (Sell) |
| 55 | Symbol | Symbol | "EUR/USD" |
| 59 | TimeInForce | Time in Force | "0" (Day), "1" (GTC) |
| 60 | TransactTime | Transaction Time | "20251223-16:00:00.000" |

### Message Types (MsgType=35)

| Code | Name | Description |
|------|------|-------------|
| 0 | Heartbeat | Session keep-alive |
| 1 | TestRequest | Request heartbeat |
| 2 | ResendRequest | Request message replay |
| 3 | Reject | Message rejected |
| 4 | SequenceReset | Reset sequence number |
| 5 | Logout | End session |
| A | Logon | Start session |
| D | NewOrderSingle | Submit new order |
| 8 | ExecutionReport | Order status update |

---

## Best Practices

1. **Always start with Session Info** - Establishes connection
2. **Use unique Order IDs** - Postman variables help: `{{$timestamp}}`
3. **Monitor both consoles** - Server + Mule app
4. **Wait between tests** - Let server process (1-2 seconds)
5. **Reset sequences carefully** - Can desync session
6. **Save successful responses** - For comparison later
7. **Use Collection Runner** - For batch/load testing
8. **Check test results** - All requests have automated tests

---

## Troubleshooting Checklist

- [ ] FIX server is running on port 9876
- [ ] Mule app is running (check Studio console)
- [ ] HTTP listener is active on port 8081
- [ ] Postman environment is selected
- [ ] baseUrl variable is set to `http://localhost:8081`
- [ ] First request is "GET Session Info"
- [ ] Session status shows "LOGGED_IN"

---

## Next Steps

After successful Postman testing:
1. Review `TEST_RESULTS.md` for automated test results
2. Check `FIX_PROTOCOL_VALIDATION.md` for protocol compliance
3. Run MUnit tests: `mvn test -f fix-sample-app/pom.xml`
4. Review connector code in `mulesoft-fix-connector/`

---

## Additional Resources

- **Postman Guide:** `POSTMAN_GUIDE.md`
- **Connector Documentation:** `CONNECTOR_README.md`
- **Testing Guide:** `TESTING_GUIDE.md`
- **FIX Protocol Validation:** `FIX_PROTOCOL_VALIDATION.md`
- **Quick Reference:** `QUICK_REFERENCE.md`

