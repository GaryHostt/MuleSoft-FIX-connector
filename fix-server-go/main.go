package main

import (
	"bufio"
	"fmt"
	"log"
	"net"
	"strconv"
	"strings"
	"sync"
	"time"
)

const (
	SOH = "\x01" // Start of Header delimiter
)

// FIXMessage represents a parsed FIX message
type FIXMessage struct {
	Fields map[int]string
}

// FIXSession represents a FIX session with a client
type FIXSession struct {
	conn              net.Conn
	senderCompID      string
	targetCompID      string
	incomingSeqNum    int
	outgoingSeqNum    int
	heartbeatInterval int
	lastMessageTime   time.Time
	loggedIn          bool
	mu                sync.Mutex
}

func main() {
	listener, err := net.Listen("tcp", ":9876")
	if err != nil {
		log.Fatal("Failed to start server:", err)
	}
	defer listener.Close()

	log.Println("FIX Server started on port 9876")
	log.Println("Waiting for connections...")

	for {
		conn, err := listener.Accept()
		if err != nil {
			log.Println("Error accepting connection:", err)
			continue
		}

		log.Printf("New connection from %s\n", conn.RemoteAddr())
		go handleConnection(conn)
	}
}

func handleConnection(conn net.Conn) {
	defer conn.Close()

	session := &FIXSession{
		conn:           conn,
		incomingSeqNum: 1,
		outgoingSeqNum: 1,
		loggedIn:       false,
	}

	// Start heartbeat monitor
	go session.heartbeatMonitor()

	reader := bufio.NewReader(conn)
	buffer := ""

	for {
		data, err := reader.ReadString('\x01')
		if err != nil {
			log.Println("Connection closed:", err)
			return
		}

		buffer += data

		// Check if we have a complete message (ends with checksum)
		if strings.Contains(buffer, SOH+"10=") {
			// Extract complete message
			checksumIndex := strings.LastIndex(buffer, SOH+"10=")
			if checksumIndex != -1 {
				// Find the SOH after checksum value
				afterChecksum := buffer[checksumIndex+4:]
				sohIndex := strings.Index(afterChecksum, SOH)
				if sohIndex != -1 {
					completeMessage := buffer[:checksumIndex+4+sohIndex+1]
					session.processMessage(completeMessage)
					buffer = buffer[checksumIndex+4+sohIndex+1:]
				}
			}
		}
	}
}

func (s *FIXSession) processMessage(rawMessage string) {
	s.mu.Lock()
	s.lastMessageTime = time.Now()
	s.mu.Unlock()

	msg := parseMessage(rawMessage)
	if msg == nil {
		log.Println("Failed to parse message")
		return
	}

	// Validate checksum
	checksumIndex := strings.LastIndex(rawMessage, SOH+"10=")
	if checksumIndex != -1 {
		messageForChecksum := rawMessage[:checksumIndex+1]
		expectedChecksum := calculateChecksum(messageForChecksum)
		receivedChecksum := msg.Fields[10]
		if expectedChecksum != receivedChecksum {
			log.Printf("Checksum mismatch: expected=%s, received=%s\n", expectedChecksum, receivedChecksum)
		}
	}

	msgType := msg.Fields[35]
	seqNum, _ := strconv.Atoi(msg.Fields[34])

	log.Printf("Received: MsgType=%s, SeqNum=%d\n", msgType, seqNum)

	// Validate sequence number
	if seqNum != s.incomingSeqNum {
		log.Printf("Sequence number mismatch: expected=%d, received=%d\n", s.incomingSeqNum, seqNum)
		// In production, send ResendRequest here
	}
	s.incomingSeqNum++

	// Handle message by type
	switch msgType {
	case "A": // Logon
		s.handleLogon(msg)
	case "5": // Logout
		s.handleLogout(msg)
	case "0": // Heartbeat
		log.Println("Received Heartbeat")
	case "1": // TestRequest
		s.handleTestRequest(msg)
	case "D": // NewOrderSingle
		s.handleNewOrder(msg)
	default:
		log.Printf("Unhandled message type: %s\n", msgType)
	}
}

func (s *FIXSession) handleLogon(msg *FIXMessage) {
	s.senderCompID = msg.Fields[56] // Their target is our sender
	s.targetCompID = msg.Fields[49] // Their sender is our target

	if interval, err := strconv.Atoi(msg.Fields[108]); err == nil {
		s.heartbeatInterval = interval
	}

	log.Printf("Logon from %s to %s, HeartbeatInterval=%d\n",
		s.targetCompID, s.senderCompID, s.heartbeatInterval)

	s.loggedIn = true

	// Send Logon response
	response := s.buildMessage("A", map[int]string{
		98:  "0", // EncryptMethod (None)
		108: strconv.Itoa(s.heartbeatInterval),
	})

	s.sendMessage(response)
	log.Println("Sent Logon response")
}

func (s *FIXSession) handleLogout(msg *FIXMessage) {
	text := msg.Fields[58]
	log.Printf("Logout received: %s\n", text)

	// Send Logout response
	response := s.buildMessage("5", map[int]string{
		58: "Goodbye",
	})

	s.sendMessage(response)
	s.loggedIn = false
	log.Println("Sent Logout response")
}

func (s *FIXSession) handleTestRequest(msg *FIXMessage) {
	testReqID := msg.Fields[112]
	log.Printf("TestRequest received: %s\n", testReqID)

	// Respond with Heartbeat containing TestReqID
	response := s.buildMessage("0", map[int]string{
		112: testReqID,
	})

	s.sendMessage(response)
	log.Printf("Sent Heartbeat response to TestRequest: %s\n", testReqID)
}

func (s *FIXSession) handleNewOrder(msg *FIXMessage) {
	clOrdID := msg.Fields[11]
	symbol := msg.Fields[55]
	side := msg.Fields[54]
	orderQty := msg.Fields[38]
	price := msg.Fields[44]

	log.Printf("NewOrderSingle: ClOrdID=%s, Symbol=%s, Side=%s, Qty=%s, Price=%s\n",
		clOrdID, symbol, side, orderQty, price)

	// Send ExecutionReport (simulated fill)
	execReport := s.buildMessage("8", map[int]string{
		37:  "EXEC" + clOrdID,                     // OrderID
		11:  clOrdID,                              // ClOrdID
		17:  "EXEC" + clOrdID + "001",            // ExecID
		150: "2",                                  // ExecType (Fill)
		39:  "2",                                  // OrdStatus (Filled)
		55:  symbol,                               // Symbol
		54:  side,                                 // Side
		38:  orderQty,                             // OrderQty
		44:  price,                                // Price
		32:  orderQty,                             // LastQty (filled qty)
		31:  price,                                // LastPx (fill price)
		151: "0",                                  // LeavesQty
		14:  orderQty,                             // CumQty
		6:   price,                                // AvgPx
		60:  time.Now().Format("20060102-15:04:05.000"), // TransactTime
	})

	s.sendMessage(execReport)
	log.Printf("Sent ExecutionReport for order %s\n", clOrdID)
}

func (s *FIXSession) heartbeatMonitor() {
	ticker := time.NewTicker(5 * time.Second)
	defer ticker.Stop()

	for range ticker.C {
		if !s.loggedIn {
			continue
		}

		s.mu.Lock()
		timeSinceLastMessage := time.Since(s.lastMessageTime).Seconds()
		s.mu.Unlock()

		// Send heartbeat if needed
		if timeSinceLastMessage >= float64(s.heartbeatInterval) {
			heartbeat := s.buildMessage("0", map[int]string{})
			s.sendMessage(heartbeat)
			log.Println("Sent scheduled Heartbeat")
		}
	}
}

func (s *FIXSession) buildMessage(msgType string, fields map[int]string) string {
	s.mu.Lock()
	defer s.mu.Unlock()

	// Build body
	body := ""
	body += "35=" + msgType + SOH
	body += "49=" + s.senderCompID + SOH
	body += "56=" + s.targetCompID + SOH
	body += "34=" + strconv.Itoa(s.outgoingSeqNum) + SOH
	body += "52=" + time.Now().Format("20060102-15:04:05.000") + SOH

	// Add custom fields
	for tag, value := range fields {
		body += strconv.Itoa(tag) + "=" + value + SOH
	}

	// Calculate body length
	bodyLength := len(body)

	// Build header
	header := "8=FIX.4.4" + SOH
	header += "9=" + strconv.Itoa(bodyLength) + SOH

	// Calculate checksum
	messageForChecksum := header + body
	checksum := calculateChecksum(messageForChecksum)

	// Build complete message
	completeMessage := messageForChecksum + "10=" + checksum + SOH

	s.outgoingSeqNum++

	return completeMessage
}

func (s *FIXSession) sendMessage(message string) {
	_, err := s.conn.Write([]byte(message))
	if err != nil {
		log.Println("Error sending message:", err)
	}

	s.mu.Lock()
	s.lastMessageTime = time.Now()
	s.mu.Unlock()
}

func parseMessage(rawMessage string) *FIXMessage {
	msg := &FIXMessage{
		Fields: make(map[int]string),
	}

	fields := strings.Split(rawMessage, SOH)
	for _, field := range fields {
		if field == "" {
			continue
		}

		parts := strings.SplitN(field, "=", 2)
		if len(parts) != 2 {
			continue
		}

		tag, err := strconv.Atoi(parts[0])
		if err != nil {
			continue
		}

		msg.Fields[tag] = parts[1]
	}

	return msg
}

func calculateChecksum(message string) string {
	sum := 0
	for _, b := range []byte(message) {
		sum += int(b)
	}
	checksum := sum % 256
	return fmt.Sprintf("%03d", checksum)
}

