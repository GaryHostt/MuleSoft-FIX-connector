package com.fix.standalone;

import org.mule.extension.fix.api.*;
import org.mule.extension.fix.internal.*;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.Map;
import java.util.HashMap;

@RestController
@RequestMapping("/fix")
public class FIXTestController {

    private static final Logger logger = LoggerFactory.getLogger(FIXTestController.class);

    private FIXSessionManager sessionManager;
    private FIXSessionState sessionState;
    private java.net.Socket socket;
    private java.io.BufferedReader reader;
    private java.io.PrintWriter writer;

    @PostConstruct
    public void init() {
        try {
            // Initialize FIX session
            String sessionId = "CLIENT1-SERVER1";
            FIXSessionStateManager stateManager = new FIXSessionStateManager();
            
            sessionState = new FIXSessionState(sessionId, "CLIENT1", "SERVER1");
            sessionManager = new FIXSessionManager(sessionState, stateManager);
            
            logger.info("FIX Session initialized: {}", sessionId);
        } catch (Exception e) {
            logger.error("Failed to initialize FIX session", e);
        }
    }

    @PostMapping("/connect")
    public Map<String, Object> connect(@RequestParam(defaultValue = "localhost") String host,
                                       @RequestParam(defaultValue = "9876") int port) {
        Map<String, Object> response = new HashMap<>();
        try {
            socket = new java.net.Socket(host, port);
            reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(socket.getInputStream()));
            writer = new java.io.PrintWriter(socket.getOutputStream(), true);
            
            sessionState.setActive(true);
            
            response.put("success", true);
            response.put("message", "Connected to FIX server");
            response.put("host", host);
            response.put("port", port);
            
            logger.info("Connected to FIX server: {}:{}", host, port);
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
            logger.error("Failed to connect to FIX server", e);
        }
        return response;
    }

    @PostMapping("/logon")
    public Map<String, Object> logon() {
        Map<String, Object> response = new HashMap<>();
        try {
            if (socket == null || !socket.isConnected()) {
                response.put("success", false);
                response.put("error", "Not connected. Call /fix/connect first");
                return response;
            }

            // Build logon message
            FIXMessageBuilder builder = FIXMessageBuilder.logon(
                sessionState.getNextOutgoingSeqNum(), 30);
            FIXMessage logonMsg = builder.build();
            
            // Send message
            String rawMessage = logonMsg.toFIXString();
            writer.print(rawMessage);
            writer.flush();
            
            // Read response
            String responseLine = readFIXMessage();
            FIXMessage responseMsg = FIXMessageParser.parse(responseLine);
            
            response.put("success", true);
            response.put("sentMessage", logonMsg.toFIXString().replace("\u0001", "|"));
            response.put("receivedMessage", responseLine.replace("\u0001", "|"));
            response.put("msgType", responseMsg.getMsgType());
            response.put("seqNum", sessionState.getCurrentOutgoingSeqNum());
            
            logger.info("Logon successful");
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
            logger.error("Logon failed", e);
        }
        return response;
    }

    @PostMapping("/heartbeat")
    public Map<String, Object> sendHeartbeat() {
        Map<String, Object> response = new HashMap<>();
        try {
            if (socket == null || !socket.isConnected()) {
                response.put("success", false);
                response.put("error", "Not connected");
                return response;
            }

            FIXMessageBuilder builder = FIXMessageBuilder.heartbeat(
                sessionState.getNextOutgoingSeqNum());
            FIXMessage heartbeatMsg = builder.build();
            
            String rawMessage = heartbeatMsg.toFIXString();
            writer.print(rawMessage);
            writer.flush();
            
            response.put("success", true);
            response.put("sentMessage", rawMessage.replace("\u0001", "|"));
            response.put("seqNum", sessionState.getCurrentOutgoingSeqNum());
            
            logger.info("Heartbeat sent");
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
            logger.error("Heartbeat failed", e);
        }
        return response;
    }

    @PostMapping("/order")
    public Map<String, Object> sendNewOrderSingle(@RequestBody Map<String, String> orderDetails) {
        Map<String, Object> response = new HashMap<>();
        try {
            if (socket == null || !socket.isConnected()) {
                response.put("success", false);
                response.put("error", "Not connected");
                return response;
            }

            // Build New Order Single message
            FIXMessageBuilder builder = new FIXMessageBuilder("D")
                .withHeader(sessionState.getNextOutgoingSeqNum())
                .withField(11, orderDetails.getOrDefault("clOrdId", "ORDER" + System.currentTimeMillis()))
                .withField(55, orderDetails.getOrDefault("symbol", "AAPL"))
                .withField(54, orderDetails.getOrDefault("side", "1"))
                .withField(38, orderDetails.getOrDefault("orderQty", "100"))
                .withField(40, orderDetails.getOrDefault("ordType", "2"))
                .withField(44, orderDetails.getOrDefault("price", "150.50"))
                .withField(60, new java.text.SimpleDateFormat("yyyyMMdd-HH:mm:ss.SSS")
                    .format(new java.util.Date()));
            
            FIXMessage orderMsg = builder.build();
            String rawMessage = orderMsg.toFIXString();
            writer.print(rawMessage);
            writer.flush();
            
            // Read execution report
            String responseLine = readFIXMessage();
            FIXMessage responseMsg = FIXMessageParser.parse(responseLine);
            
            response.put("success", true);
            response.put("sentOrder", rawMessage.replace("\u0001", "|"));
            response.put("executionReport", responseLine.replace("\u0001", "|"));
            response.put("execType", responseMsg.getField(150));
            response.put("ordStatus", responseMsg.getField(39));
            
            logger.info("New Order Single sent successfully");
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
            logger.error("Order failed", e);
        }
        return response;
    }

    @GetMapping("/session")
    public Map<String, Object> getSessionInfo() {
        Map<String, Object> response = new HashMap<>();
        
        response.put("sessionId", sessionState.getSessionId());
        response.put("senderCompId", sessionState.getSenderCompId());
        response.put("targetCompId", sessionState.getTargetCompId());
        response.put("active", sessionState.isActive());
        response.put("connected", socket != null && socket.isConnected());
        response.put("incomingSeqNum", sessionState.getCurrentIncomingSeqNum());
        response.put("outgoingSeqNum", sessionState.getCurrentOutgoingSeqNum());
        response.put("lastSentTime", sessionState.getLastSentTime());
        response.put("lastReceivedTime", sessionState.getLastReceivedTime());
        
        return response;
    }

    @PostMapping("/disconnect")
    public Map<String, Object> disconnect() {
        Map<String, Object> response = new HashMap<>();
        try {
            if (socket != null && socket.isConnected()) {
                // Send logout
                FIXMessageBuilder builder = FIXMessageBuilder.logout(
                    sessionState.getNextOutgoingSeqNum());
                FIXMessage logoutMsg = builder.build();
                
                writer.print(logoutMsg.toFIXString());
                writer.flush();
                
                socket.close();
            }
            
            sessionState.setActive(false);
            response.put("success", true);
            response.put("message", "Disconnected from FIX server");
            
            logger.info("Disconnected from FIX server");
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
            logger.error("Disconnect failed", e);
        }
        return response;
    }

    @PreDestroy
    public void cleanup() {
        try {
            if (socket != null) {
                socket.close();
            }
            logger.info("FIX session cleaned up");
        } catch (Exception e) {
            logger.error("Cleanup failed", e);
        }
    }

    private String readFIXMessage() throws Exception {
        StringBuilder message = new StringBuilder();
        int ch;
        while ((ch = reader.read()) != -1) {
            message.append((char) ch);
            // Check if we've read a complete message (ends with checksum field 10=)
            String current = message.toString();
            if (current.contains("10=") && current.length() > current.indexOf("10=") + 10) {
                break;
            }
        }
        return message.toString();
    }
}

