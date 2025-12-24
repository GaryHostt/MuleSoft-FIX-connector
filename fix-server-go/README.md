# FIX Server in Go

A simple FIX Protocol server implementation in Go for testing the MuleSoft FIX Connector.

## Features

- **FIX 4.4 Protocol Support**
- **Session Management:** Handles Logon/Logout
- **Heartbeat Mechanism:** Automatic heartbeat generation
- **Sequence Tracking:** Validates incoming sequence numbers
- **Message Handling:**
  - Logon (MsgType A)
  - Logout (MsgType 5)
  - Heartbeat (MsgType 0)
  - TestRequest (MsgType 1)
  - NewOrderSingle (MsgType D) - Simulates order execution
  - ExecutionReport (MsgType 8) - Sends back simulated fills

## Prerequisites

- Go 1.21 or higher

## Build and Run

```bash
# Build
cd fix-server-go
go build -o fix-server

# Run
./fix-server
```

The server will start on port **9876** and wait for connections.

## Configuration

The server is configured with:
- **Port:** 9876
- **FIX Version:** FIX.4.4
- **Supported CompIDs:** Any (accepts all)
- **Heartbeat:** Configurable per session (from client's Logon message)

## Testing with MuleSoft Connector

1. **Start the FIX server:**
   ```bash
   cd fix-server-go
   go run main.go
   ```

2. **Configure the MuleSoft app** to connect to:
   - Host: `localhost`
   - Port: `9876`
   - SenderCompID: `CLIENT1`
   - TargetCompID: `SERVER1`

3. **Start the MuleSoft application**

4. **Test the connection:**
   - The connector will automatically send a Logon message
   - The server will respond with Logon
   - Heartbeats will be exchanged automatically

## Example Messages

### Logon Request (from Client)
```
8=FIX.4.4|9=...|35=A|49=CLIENT1|56=SERVER1|34=1|52=...|98=0|108=30|10=...|
```

### Logon Response (from Server)
```
8=FIX.4.4|9=...|35=A|49=SERVER1|56=CLIENT1|34=1|52=...|98=0|108=30|10=...|
```

### NewOrderSingle Request
```
8=FIX.4.4|9=...|35=D|49=CLIENT1|56=SERVER1|34=2|...|11=ORD-001|55=EUR/USD|54=1|38=1000000|44=1.1850|...|10=...|
```

### ExecutionReport Response
```
8=FIX.4.4|9=...|35=8|49=SERVER1|56=CLIENT1|34=2|...|37=EXECORD-001|39=2|150=2|...|10=...|
```

## Server Logs

The server logs all activity:
```
2025/12/23 16:40:00 FIX Server started on port 9876
2025/12/23 16:40:00 Waiting for connections...
2025/12/23 16:40:15 New connection from 127.0.0.1:54321
2025/12/23 16:40:15 Received: MsgType=A, SeqNum=1
2025/12/23 16:40:15 Logon from CLIENT1 to SERVER1, HeartbeatInterval=30
2025/12/23 16:40:15 Sent Logon response
2025/12/23 16:40:20 Received: MsgType=D, SeqNum=2
2025/12/23 16:40:20 NewOrderSingle: ClOrdID=ORD-001, Symbol=EUR/USD, Side=1, Qty=1000000, Price=1.1850
2025/12/23 16:40:20 Sent ExecutionReport for order ORD-001
2025/12/23 16:40:45 Received: MsgType=0, SeqNum=3
2025/12/23 16:40:45 Received Heartbeat
2025/12/23 16:41:00 Sent scheduled Heartbeat
```

## Message Flow

```
Client (MuleSoft)          Server (Go)
      │                         │
      ├──── Logon (A) ─────────>│
      │<──── Logon (A) ──────────┤
      │                         │
      ├──── NewOrderSingle (D) ─>│
      │<──── ExecutionReport (8)─┤
      │                         │
      ├──── Heartbeat (0) ──────>│
      │<──── Heartbeat (0) ───────┤
      │                         │
      ├──── TestRequest (1) ────>│
      │<──── Heartbeat (0) ───────┤ (with TestReqID)
      │                         │
      ├──── Logout (5) ─────────>│
      │<──── Logout (5) ──────────┤
```

## Implementation Details

### Session State
- **Sequence Numbers:** Tracked per session (incoming/outgoing)
- **Session Status:** Logged in/out
- **Last Message Time:** For heartbeat monitoring
- **Thread Safety:** Mutex protection for concurrent access

### Heartbeat Monitor
- Runs every 5 seconds
- Sends heartbeat if no message sent within heartbeat interval
- Monitors connection health

### Message Processing
1. Read from TCP socket
2. Parse FIX message (SOH-delimited)
3. Validate checksum
4. Validate sequence number
5. Route by MsgType
6. Generate response
7. Send response

### Order Simulation
When receiving NewOrderSingle (D):
1. Parse order details
2. Log order information
3. Immediately send ExecutionReport (8) with:
   - ExecType = Fill (2)
   - OrdStatus = Filled (2)
   - Fill price = Order price
   - Fill quantity = Order quantity

## Troubleshooting

### Server won't start
- **Port already in use:** Kill process using port 9876
  ```bash
  lsof -ti:9876 | xargs kill -9
  ```

### Connection drops immediately
- Check sequence numbers in logs
- Verify checksum calculations
- Review heartbeat interval settings

### Messages not being processed
- Check FIX message format (SOH delimiters)
- Verify required fields are present
- Review server logs for errors

## Extending the Server

To add support for new message types:

1. Add handler function:
```go
func (s *FIXSession) handleYourMessage(msg *FIXMessage) {
    // Your logic here
}
```

2. Add case in `processMessage`:
```go
case "YOUR_MSG_TYPE":
    s.handleYourMessage(msg)
```

## Testing Commands

```bash
# Build
go build -o fix-server

# Run
./fix-server

# Run with race detection
go run -race main.go

# Test with telnet (not recommended, just for testing connectivity)
telnet localhost 9876
```

## Production Considerations

This is a **test server** for development. For production:
- Add proper error recovery
- Implement message store for resends
- Add authentication
- Support TLS/SSL
- Add comprehensive logging
- Implement proper session management
- Add metrics and monitoring
- Handle edge cases and protocol violations

## License

For testing and development purposes only.

