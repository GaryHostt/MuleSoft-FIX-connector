# FIX Sample Application

This is a sample MuleSoft application demonstrating the usage of the FIX Protocol Connector.

## Prerequisites

- MuleSoft Runtime 4.4.0 or higher
- Java 17
- FIX Protocol server running on localhost:9876 (or configure your own)

## Features Demonstrated

### 1. Send Custom FIX Messages
**Endpoint:** `POST http://localhost:8081/fix/send`
```json
{
  "msgType": "D",
  "fields": {
    "11": "ORDER123",
    "21": "1",
    "55": "EUR/USD"
  }
}
```

### 2. Send Heartbeat
**Endpoint:** `POST http://localhost:8081/fix/heartbeat`
```json
{
  "testReqId": "TEST-12345"
}
```

### 3. Send Test Request
**Endpoint:** `POST http://localhost:8081/fix/test-request`
```json
{
  "testReqId": "TEST-12345"
}
```

### 4. Request Message Resend
**Endpoint:** `POST http://localhost:8081/fix/resend`
```json
{
  "beginSeqNo": 10,
  "endSeqNo": 20
}
```

### 5. Get Session Information
**Endpoint:** `GET http://localhost:8081/fix/session`

Returns current session status, sequence numbers, and connection details.

### 6. Reset Sequence Numbers
**Endpoint:** `POST http://localhost:8081/fix/reset-sequence`

Resets both incoming and outgoing sequence numbers to 1.

### 7. Send New Order Single
**Endpoint:** `POST http://localhost:8081/fix/order/new`
```json
{
  "clOrdID": "ORD-001",
  "side": "1",
  "symbol": "EUR/USD",
  "orderQty": "1000000",
  "price": "1.1850",
  "ordType": "2",
  "timeInForce": "0"
}
```

### 8. Listen for Incoming FIX Messages
The `fix-message-listener-flow` automatically processes all incoming FIX messages and logs them.

## Configuration

Edit `fix-sample-app.xml` to configure:

- **Host:** FIX server hostname
- **Port:** FIX server port
- **Sender Comp ID:** Your client identifier
- **Target Comp ID:** Server identifier
- **Heartbeat Interval:** Time between heartbeats (seconds)
- **FIX Version:** FIX protocol version (e.g., FIX.4.4)

## Running the Application

1. Build the connector:
```bash
cd ../mulesoft-fix-connector
mvn clean install
```

2. Deploy the application:
```bash
cd ../fix-sample-app
mvn clean package
```

3. Deploy to Anypoint Studio or CloudHub

4. Test endpoints using curl or Postman

## Example curl Commands

### Send New Order
```bash
curl -X POST http://localhost:8081/fix/order/new \
  -H "Content-Type: application/json" \
  -d '{
    "clOrdID": "ORD-001",
    "side": "1",
    "symbol": "EUR/USD",
    "orderQty": "1000000",
    "price": "1.1850"
  }'
```

### Get Session Info
```bash
curl -X GET http://localhost:8081/fix/session
```

### Send Heartbeat
```bash
curl -X POST http://localhost:8081/fix/heartbeat \
  -H "Content-Type: application/json" \
  -d '{}'
```

## Architecture

The application demonstrates:

1. **Session Management:** Automatic logon, heartbeat, and session recovery
2. **Sequence Tracking:** Validates incoming sequence numbers and handles gaps
3. **Message Processing:** Parses and validates FIX messages with checksum verification
4. **Connection Monitoring:** Detects connection issues and triggers recovery
5. **Stateful Conversation:** Maintains session state across messages

## FIX Protocol Features

- **Logon/Logout:** Automatic session establishment and termination
- **Heartbeat:** Keeps connection alive
- **Test Request:** Validates connection health
- **Resend Request:** Recovers missing messages
- **Sequence Reset:** Resets sequence numbers when needed
- **PossDup Handling:** Detects and handles duplicate messages

## Troubleshooting

### Connection Issues
- Verify FIX server is running and accessible
- Check firewall rules for the configured port
- Verify SenderCompID and TargetCompID match server configuration

### Sequence Number Mismatch
- Use the reset sequence endpoint to resynchronize
- Check for PossDupFlag in rejected messages
- Review sequence number logs

### Message Parsing Errors
- Verify FIX version matches server expectations
- Check required fields are present (BeginString, BodyLength, MsgType, etc.)
- Validate checksum calculation

## Support

For issues or questions about the FIX Protocol Connector, please refer to the connector documentation.

