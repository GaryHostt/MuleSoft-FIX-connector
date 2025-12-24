# FIX Protocol Connector for MuleSoft

A comprehensive MuleSoft connector implementing the Financial Information eXchange (FIX) Protocol with full support for session management, sequence tracking, heartbeat mechanisms, and stateful conversations.

## Overview

The FIX Protocol Connector enables MuleSoft applications to communicate with FIX-compliant trading systems, exchanges, and financial institutions. It handles all the complexities of the FIX protocol including:

- **Stateful Session Management:** Maintains active FIX sessions with proper logon/logout handling
- **Sequence Number Tracking:** Validates and manages incoming/outgoing message sequences with gap detection
- **Automatic Heartbeat:** Keeps connections alive with configurable heartbeat intervals
- **Message Validation:** Parses FIX messages with checksum validation and required field verification
- **Connection Recovery:** Handles connection failures, resend requests, and sequence resets
- **Out-of-Order Message Buffering:** Queues messages received out of sequence for proper processing

## Features

### FIX Protocol Compliance

- **FIX Versions Supported:** FIX.4.2, FIX.4.4, FIXT.1.1
- **Message Types:** Logon (A), Logout (5), Heartbeat (0), TestRequest (1), ResendRequest (2), SequenceReset (4), Reject (3)
- **Application Messages:** NewOrderSingle (D), ExecutionReport (8), and custom message types
- **Transport Framing:** Proper BodyLength and CheckSum validation

### Session Layer

The connector implements robust session management:

1. **Logon Sequence:**
   - Establishes TCP connection
   - Sends Logon message with encryption method and heartbeat interval
   - Validates server Logon response
   - Transitions to LOGGED_IN state

2. **Heartbeat Mechanism:**
   - Sends Heartbeat (MsgType 0) when no message sent within heartbeat interval
   - Monitors incoming messages and sends TestRequest if no message received
   - Detects dead connections (2x heartbeat interval without messages)

3. **Sequence Number Management:**
   - Tracks next expected incoming sequence number
   - Increments outgoing sequence number for each message
   - Detects gaps and automatically sends ResendRequest
   - Handles PossDupFlag for duplicate message detection
   - Supports sequence reset with ResetSeqNumFlag

4. **Logout Handling:**
   - Graceful session termination
   - Sends Logout message with optional reason
   - Cleans up resources

### Presentation Layer

Message parsing and formatting:

- **Parser:** Converts raw FIX strings to structured message objects
- **Builder:** Creates properly formatted FIX messages with correct framing
- **Checksum Validation:** Verifies message integrity (sum of bytes modulo 256)
- **Field Validation:** Ensures required fields are present (MsgType, MsgSeqNum, SenderCompID, TargetCompID, SendingTime)

### Application Layer

Business logic handling:

- **Message Routing:** Directs messages to appropriate handlers based on MsgType
- **Custom Operations:** Send business messages through connector operations
- **Message Listener:** Source component for receiving and processing incoming messages
- **Field Mapping:** Converts between FIX tag=value format and MuleSoft data structures

## Installation

### Build from Source

```bash
cd mulesoft-fix-connector
mvn clean install
```

This generates `mulesoft-fix-connector-1.0.0.jar` in the `target` directory.

### Add to Mule Application

Add the dependency to your Mule application's `pom.xml`:

```xml
<dependency>
    <groupId>com.fix.muleConnector</groupId>
    <artifactId>mulesoft-fix-connector</artifactId>
    <version>1.0.0</version>
    <classifier>mule-plugin</classifier>
</dependency>
```

## Configuration

### Basic Configuration

```xml
<fix:config name="FIX_Config">
    <fix:connection host="fix.server.com" port="9876" connectionTimeout="30000" />
    <fix:begin-string>FIX.4.4</fix:begin-string>
    <fix:sender-comp-id>CLIENT1</fix:sender-comp-id>
    <fix:target-comp-id>SERVER1</fix:target-comp-id>
    <fix:heartbeat-interval>30</fix:heartbeat-interval>
    <fix:reset-sequence-on-logon>false</fix:reset-sequence-on-logon>
    <fix:validate-checksum>true</fix:validate-checksum>
</fix:config>
```

### Configuration Parameters

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `host` | String | Required | FIX server hostname or IP address |
| `port` | Integer | Required | FIX server port number |
| `connectionTimeout` | Integer | 30000 | Connection timeout in milliseconds |
| `beginString` | String | FIX.4.4 | FIX protocol version |
| `senderCompId` | String | Required | Unique identifier for message sender |
| `targetCompId` | String | Required | Unique identifier for message recipient |
| `heartbeatInterval` | Integer | 30 | Heartbeat interval in seconds |
| `resetSequenceOnLogon` | Boolean | false | Reset sequence numbers to 1 on logon |
| `validateChecksum` | Boolean | true | Validate FIX message checksums |

## Operations

### Send FIX Message

Send a custom FIX message with specified message type and fields.

```xml
<fix:send-message config-ref="FIX_Config">
    <fix:msg-type>D</fix:msg-type>
    <fix:fields>{
        "11": "ORDER123",
        "21": "1",
        "55": "EUR/USD",
        "54": "1",
        "38": "1000000",
        "44": "1.1850",
        "40": "2",
        "59": "0"
    }</fix:fields>
</fix:send-message>
```

**Parameters:**
- `msgType`: FIX message type code
- `fields`: Map of FIX tag numbers to values

**Returns:**
```json
{
  "success": true,
  "msgType": "D",
  "seqNum": 42
}
```

### Send Heartbeat

Send a Heartbeat message to keep the connection alive.

```xml
<fix:send-heartbeat config-ref="FIX_Config" testReqId="TEST-123" />
```

**Parameters:**
- `testReqId` (optional): TestReqID if responding to a TestRequest

### Send Test Request

Send a TestRequest to validate connection health.

```xml
<fix:send-test-request config-ref="FIX_Config" testReqId="TEST-123" />
```

### Request Resend

Request retransmission of messages due to sequence gap.

```xml
<fix:request-resend config-ref="FIX_Config" beginSeqNo="10" endSeqNo="20" />
```

**Parameters:**
- `beginSeqNo`: First sequence number to resend
- `endSeqNo`: Last sequence number (0 = all messages to current)

### Get Session Info

Retrieve current session information and status.

```xml
<fix:get-session-info config-ref="FIX_Config" />
```

**Returns:**
```json
{
  "sessionId": "CLIENT1-SERVER1",
  "senderCompId": "CLIENT1",
  "targetCompId": "SERVER1",
  "status": "LOGGED_IN",
  "incomingSeqNum": 15,
  "outgoingSeqNum": 23,
  "heartbeatInterval": 30,
  "lastMessageReceived": "2025-12-23T16:30:45.123Z",
  "lastMessageSent": "2025-12-23T16:30:50.456Z",
  "logonTime": "2025-12-23T16:00:00.000Z",
  "bufferedMessageCount": 0
}
```

### Reset Sequence Numbers

Reset both incoming and outgoing sequence numbers to 1.

```xml
<fix:reset-sequence-numbers config-ref="FIX_Config" />
```

**Use with caution:** Should only be used when coordinating a sequence reset with the counterparty.

## Message Listener (Source)

Listen for incoming FIX messages and trigger flows.

```xml
<flow name="fix-message-listener-flow">
    <fix:listener config-ref="FIX_Config" 
                  messageTypeFilter="ALL" 
                  includeAdminMessages="false" />
    
    <logger level="INFO" message="Received: #[payload.messageType]" />
    
    <!-- Process message -->
</flow>
```

**Parameters:**
- `messageTypeFilter`: Filter by message type (e.g., "D", "8", or "ALL")
- `includeAdminMessages`: Include admin messages (Logon, Logout, Heartbeat, etc.)

**Message Payload:**
```json
{
  "messageType": "D",
  "seqNum": 42,
  "senderCompId": "SERVER1",
  "targetCompId": "CLIENT1",
  "sendingTime": "20251223-16:30:45.123",
  "fields": {
    "35": "D",
    "49": "SERVER1",
    "56": "CLIENT1",
    "11": "ORDER123",
    "55": "EUR/USD"
  },
  "timestamp": 1703351445123
}
```

## Architecture

### Component Structure

```
FIXExtension
├── FIXConfiguration
│   ├── Connection Parameters
│   └── Session Settings
├── FIXConnectionProvider
│   └── Connection Lifecycle
├── FIXConnection
│   └── Session Manager Wrapper
├── FIXSessionManager
│   ├── Message Processing
│   ├── Heartbeat Service
│   └── Connection Monitor
├── FIXSessionState
│   ├── Sequence Tracking
│   └── Message Buffer
├── FIXSessionStateManager
│   └── State Persistence
├── FIXMessage
│   └── Message Model
├── FIXMessageParser
│   └── Parsing Logic
└── FIXMessageBuilder
    └── Message Construction
```

### Session State Machine

```
DISCONNECTED -> CONNECTING -> LOGGED_IN <-> AWAITING_RESEND
                                  ↓
                           LOGGING_OUT
                                  ↓
                             DISCONNECTED
```

### Sequence Number Validation Flow

1. Message arrives with MsgSeqNum (Tag 34)
2. Compare with expected sequence number
3. If equal: Process message, increment expected
4. If greater: Gap detected, send ResendRequest, buffer message
5. If lower: Check PossDupFlag
   - If set: Log and ignore (valid duplicate)
   - If not set: Log error (potential protocol violation)

## Best Practices

### 1. Sequence Number Management

- **Never manually modify** sequence numbers during an active session
- **Use ResetSeqNumFlag** only during logon with counterparty agreement
- **Monitor sequence gaps** and handle ResendRequests promptly
- **Store sent messages** for potential resend requests

### 2. Heartbeat Configuration

- Set heartbeat interval based on network latency (typically 30-60 seconds)
- Ensure your application can process messages faster than heartbeat interval
- Monitor connection health through session info endpoint

### 3. Error Handling

- Implement retry logic for connection failures
- Log all protocol violations for debugging
- Handle Reject messages appropriately
- Validate field values before sending

### 4. Testing

- Test with FIX simulator before production
- Verify sequence recovery scenarios
- Test reconnection and failover
- Validate message formatting and checksums

### 5. Security

- Use encrypted connections (TLS/SSL) when available
- Protect SenderCompID and TargetCompID credentials
- Implement authentication at application layer if required
- Monitor for unusual message patterns

## FIX Message Examples

### New Order Single (MsgType D)

```json
{
  "msgType": "D",
  "fields": {
    "11": "ORDER-001",
    "21": "1",
    "55": "EUR/USD",
    "54": "1",
    "38": "1000000",
    "44": "1.1850",
    "40": "2",
    "59": "0",
    "60": "20251223-16:30:45.123"
  }
}
```

**Field Reference:**
- 11: ClOrdID (Client Order ID)
- 21: HandlInst (Handling Instructions)
- 55: Symbol
- 54: Side (1=Buy, 2=Sell)
- 38: OrderQty
- 44: Price
- 40: OrdType (1=Market, 2=Limit)
- 59: TimeInForce (0=Day, 1=GTC)
- 60: TransactTime

## Troubleshooting

### Connection Refused
- Verify host and port are correct
- Check firewall rules
- Ensure FIX server is running
- Verify network connectivity

### Sequence Number Mismatch
```
Expected: 45, Received: 47
```
- Gap detected - connector automatically sends ResendRequest
- Check logs for missing messages
- May indicate message loss or network issue

### Checksum Validation Failed
```
Checksum validation failed. Expected: 123, Calculated: 124
```
- Message corruption in transit
- Check network quality
- Verify no middleware is modifying messages

### Session Not Active
```
FIX session is not active
```
- Session not logged in yet
- Check logon was successful
- Verify heartbeats are being exchanged
- Check connection status

## Supported MuleSoft Versions

- **MuleSoft Runtime:** 4.4.0+
- **Java:** 17+
- **Maven:** 3.6+

## License

This connector is provided as-is for integration with FIX protocol systems.

## Support

For issues, questions, or contributions:
1. Check the troubleshooting section
2. Review FIX protocol specification (www.fixtrading.org)
3. Examine connector logs for detailed error messages
4. Test with FIX simulator for isolation

## References

- [FIX Protocol Official Site](https://www.fixtrading.org)
- [FIX 4.4 Specification](https://www.fixtrading.org/standards/fix-4-4/)
- [MuleSoft Connector Development](https://docs.mulesoft.com/mule-sdk/)

