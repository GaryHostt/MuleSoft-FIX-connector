package org.mule.extension.fix.internal;

import org.mule.extension.fix.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.time.Instant;
import java.util.concurrent.*;

/**
 * Core FIX session manager that handles protocol logic, message routing, and session lifecycle.
 * This class orchestrates the stateful FIX conversation including:
 * - Sequence number validation and gap detection
 * - Heartbeat and test request handling
 * - Logon/Logout lifecycle management
 * - Message resend requests
 */
public class FIXSessionManager {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(FIXSessionManager.class);
    
    private final FIXSessionStateManager stateManager;
    private final String beginString;
    private Socket socket;
    private OutputStream outputStream;
    private InputStream inputStream;
    private volatile boolean running;
    
    // Background tasks
    private ScheduledExecutorService heartbeatScheduler;
    private ExecutorService messageProcessorExecutor;
    
    // Message handlers
    private MessageHandler messageHandler;
    
    public FIXSessionManager(String beginString) {
        this.beginString = beginString;
        this.stateManager = new FIXSessionStateManager();
        this.running = false;
    }
    
    /**
     * Connect to FIX server
     */
    public void connect(String host, int port, String senderCompId, String targetCompId, int heartbeatInterval) 
            throws IOException {
        
        LOGGER.info("Connecting to FIX server at {}:{}", host, port);
        
        socket = new Socket(host, port);
        outputStream = socket.getOutputStream();
        inputStream = socket.getInputStream();
        
        // Get or create session state
        FIXSessionState session = stateManager.getOrCreateSession(senderCompId, targetCompId);
        session.setHeartbeatInterval(heartbeatInterval);
        session.setStatus(FIXSessionState.SessionStatus.CONNECTING);
        
        // Set running flag BEFORE starting threads to avoid race condition
        running = true;
        
        // Start background services
        startHeartbeatService(session);
        startMessageProcessor(session);
        
        // Send Logon message
        sendLogon(session, heartbeatInterval);
        
        LOGGER.info("Connected to FIX server");
    }
    
    /**
     * Disconnect from FIX server
     */
    public void disconnect(String senderCompId, String targetCompId) throws IOException {
        FIXSessionState session = stateManager.getSession(senderCompId, targetCompId);
        if (session != null && session.isActive()) {
            sendLogout(session, "Normal disconnect");
            session.setStatus(FIXSessionState.SessionStatus.LOGGING_OUT);
        }
        
        stopBackgroundServices();
        
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
        
        running = false;
        LOGGER.info("Disconnected from FIX server");
    }
    
    /**
     * Send Logon message
     */
    private void sendLogon(FIXSessionState session, int heartbeatInterval) throws IOException {
        FIXMessage logonMsg = FIXMessageBuilder.logon(session.getNextOutgoingSeqNum(), heartbeatInterval)
            .build();
        
        String fixString = logonMsg.toFIXString(beginString, session.getSenderCompId(), session.getTargetCompId());
        LOGGER.info("Sending Logon message: {}", fixString.replace("\u0001", "|"));
        sendMessage(session, logonMsg);
        LOGGER.info("Sent Logon message");
    }
    
    /**
     * Send Logout message
     */
    private void sendLogout(FIXSessionState session, String reason) throws IOException {
        FIXMessage logoutMsg = FIXMessageBuilder.logout(session.getNextOutgoingSeqNum(), reason)
            .build();
        
        sendMessage(session, logoutMsg);
        LOGGER.info("Sent Logout message: {}", reason);
    }
    
    /**
     * Send a FIX message
     */
    public void sendMessage(FIXSessionState session, FIXMessage message) throws IOException {
        String fixString = message.toFIXString(beginString, session.getSenderCompId(), session.getTargetCompId());
        
        LOGGER.debug("Sending FIX message: {}", message);
        outputStream.write(fixString.getBytes());
        outputStream.flush();
        
        session.updateLastMessageSentTime();
        stateManager.saveSession(session);
    }
    
    /**
     * Process incoming message
     */
    public void processIncomingMessage(FIXSessionState session, String rawMessage) {
        try {
            FIXMessage message = FIXMessageParser.parse(rawMessage);
            session.updateLastMessageReceivedTime();
            
            LOGGER.debug("Received FIX message: {}", message);
            
            // Validate sequence number
            Integer receivedSeqNum = message.getMsgSeqNum();
            if (receivedSeqNum == null) {
                LOGGER.error("Message missing sequence number");
                return;
            }
            
            FIXSessionStateManager.SequenceValidationResult validation = 
                stateManager.validateIncomingSequence(session, receivedSeqNum);
            
            if (validation.isGapDetected()) {
                handleSequenceGap(session, validation.getExpectedSeqNum(), receivedSeqNum);
                // Buffer the out-of-order message
                session.bufferMessage(receivedSeqNum, rawMessage);
                return;
            } else if (validation.isLowerThanExpected()) {
                handleLowerSequence(session, message, receivedSeqNum);
                return;
            }
            
            // Process message by type
            String msgType = message.getMsgType();
            switch (msgType) {
                case FIXMessage.MSG_TYPE_LOGON:
                    handleLogon(session, message);
                    break;
                    
                case FIXMessage.MSG_TYPE_LOGOUT:
                    handleLogout(session, message);
                    break;
                    
                case FIXMessage.MSG_TYPE_HEARTBEAT:
                    handleHeartbeat(session, message);
                    break;
                    
                case FIXMessage.MSG_TYPE_TEST_REQUEST:
                    handleTestRequest(session, message);
                    break;
                    
                case FIXMessage.MSG_TYPE_RESEND_REQUEST:
                    handleResendRequest(session, message);
                    break;
                    
                case FIXMessage.MSG_TYPE_SEQUENCE_RESET:
                    handleSequenceReset(session, message);
                    break;
                    
                default:
                    // Application message - pass to handler
                    if (messageHandler != null) {
                        messageHandler.onApplicationMessage(session, message);
                    }
                    break;
            }
            
            // Check for buffered messages that can now be processed
            processBufferedMessages(session);
            
        } catch (FIXParseException e) {
            LOGGER.error("Failed to parse FIX message", e);
        }
    }
    
    /**
     * Handle sequence gap - send ResendRequest
     */
    private void handleSequenceGap(FIXSessionState session, int expectedSeqNum, int receivedSeqNum) {
        LOGGER.warn("Sequence gap detected. Expected: {}, Received: {}. Requesting resend.", 
                    expectedSeqNum, receivedSeqNum);
        
        try {
            FIXMessage resendRequest = FIXMessageBuilder
                .resendRequest(session.getNextOutgoingSeqNum(), expectedSeqNum, receivedSeqNum - 1)
                .build();
            
            sendMessage(session, resendRequest);
            session.setStatus(FIXSessionState.SessionStatus.AWAITING_RESEND);
        } catch (IOException e) {
            LOGGER.error("Failed to send ResendRequest", e);
        }
    }
    
    /**
     * Handle lower sequence number
     */
    private void handleLowerSequence(FIXSessionState session, FIXMessage message, int receivedSeqNum) {
        // Check if it's marked as PossDup
        if (message.isPossDup()) {
            LOGGER.info("Received duplicate message (PossDupFlag=Y) with seqNum: {}. Ignoring.", receivedSeqNum);
        } else {
            LOGGER.error("Received lower sequence without PossDupFlag. Expected: {}, Received: {}. Potential fatal error.",
                        session.getIncomingSeqNum(), receivedSeqNum);
            // In production, this might trigger disconnect
        }
    }
    
    /**
     * Process buffered messages in sequence
     */
    private void processBufferedMessages(FIXSessionState session) {
        int expectedSeqNum = session.getIncomingSeqNum();
        
        while (session.hasBufferedMessage(expectedSeqNum)) {
            String bufferedMessage = session.getBufferedMessage(expectedSeqNum);
            LOGGER.info("Processing buffered message with seqNum: {}", expectedSeqNum);
            processIncomingMessage(session, bufferedMessage);
            expectedSeqNum = session.getIncomingSeqNum();
        }
    }
    
    /**
     * Handle Logon message
     */
    private void handleLogon(FIXSessionState session, FIXMessage message) {
        String fixString = message.toFIXString(beginString, session.getTargetCompId(), session.getSenderCompId());
        LOGGER.info("Received Logon response: {}", fixString.replace("\u0001", "|"));
        
        // Check if ResetSeqNumFlag is set
        if ("Y".equals(message.getField(FIXMessage.TAG_RESET_SEQ_NUM_FLAG))) {
            session.resetSequenceNumbers();
        }
        
        // Update heartbeat interval if provided
        Integer heartbeatInterval = message.getFieldAsInt(FIXMessage.TAG_HEARTBEAT_INTERVAL);
        if (heartbeatInterval != null) {
            session.setHeartbeatInterval(heartbeatInterval);
        }
        
        LOGGER.info("Setting session status to LOGGED_IN");
        session.setStatus(FIXSessionState.SessionStatus.LOGGED_IN);
        stateManager.saveSession(session);
        
        if (messageHandler != null) {
            messageHandler.onLogon(session);
        }
    }
    
    /**
     * Handle Logout message
     */
    private void handleLogout(FIXSessionState session, FIXMessage message) {
        String reason = message.getField(FIXMessage.TAG_TEXT);
        LOGGER.info("Received Logout message. Reason: {}", reason);
        
        session.setStatus(FIXSessionState.SessionStatus.DISCONNECTED);
        
        if (messageHandler != null) {
            messageHandler.onLogout(session, reason);
        }
    }
    
    /**
     * Handle Heartbeat message
     */
    private void handleHeartbeat(FIXSessionState session, FIXMessage message) {
        LOGGER.debug("Received Heartbeat message");
        // Heartbeat received - connection is alive
    }
    
    /**
     * Handle TestRequest message
     */
    private void handleTestRequest(FIXSessionState session, FIXMessage message) {
        String testReqId = message.getField(FIXMessage.TAG_TEST_REQ_ID);
        LOGGER.info("Received TestRequest with ID: {}", testReqId);
        
        try {
            // Respond with Heartbeat containing the TestReqID
            FIXMessage heartbeat = FIXMessageBuilder
                .heartbeat(session.getNextOutgoingSeqNum(), testReqId)
                .build();
            
            sendMessage(session, heartbeat);
        } catch (IOException e) {
            LOGGER.error("Failed to send Heartbeat response to TestRequest", e);
        }
    }
    
    /**
     * Handle ResendRequest message
     */
    private void handleResendRequest(FIXSessionState session, FIXMessage message) {
        Integer beginSeqNo = message.getFieldAsInt(FIXMessage.TAG_BEGIN_SEQ_NO);
        Integer endSeqNo = message.getFieldAsInt(FIXMessage.TAG_END_SEQ_NO);
        
        LOGGER.info("Received ResendRequest from {} to {}", beginSeqNo, endSeqNo);
        
        // In a production implementation, this would retrieve and resend messages from message store
        // For now, we'll send a SequenceReset-GapFill
        try {
            int currentSeqNum = session.getCurrentOutgoingSeqNum();
            FIXMessage seqReset = FIXMessageBuilder
                .sequenceReset(beginSeqNo, currentSeqNum, true)
                .build();
            
            sendMessage(session, seqReset);
        } catch (IOException e) {
            LOGGER.error("Failed to send SequenceReset", e);
        }
    }
    
    /**
     * Handle SequenceReset message
     */
    private void handleSequenceReset(FIXSessionState session, FIXMessage message) {
        Integer newSeqNo = message.getFieldAsInt(36); // NewSeqNo tag
        
        if (newSeqNo != null) {
            LOGGER.info("Received SequenceReset. Resetting incoming sequence to: {}", newSeqNo);
            session.setIncomingSeqNum(newSeqNo);
            stateManager.saveSession(session);
            
            if (session.getStatus() == FIXSessionState.SessionStatus.AWAITING_RESEND) {
                session.setStatus(FIXSessionState.SessionStatus.LOGGED_IN);
            }
        }
    }
    
    /**
     * Start heartbeat service
     */
    private void startHeartbeatService(FIXSessionState session) {
        heartbeatScheduler = Executors.newSingleThreadScheduledExecutor();
        
        heartbeatScheduler.scheduleAtFixedRate(() -> {
            try {
                if (session.isActive()) {
                    // Check if we need to send heartbeat
                    if (session.isHeartbeatNeeded()) {
                        LOGGER.debug("Sending scheduled heartbeat");
                        FIXMessage heartbeat = FIXMessageBuilder
                            .heartbeat(session.getNextOutgoingSeqNum())
                            .build();
                        sendMessage(session, heartbeat);
                    }
                    
                    // Check if we need to send test request
                    if (session.isTestRequestNeeded()) {
                        LOGGER.warn("No message received. Sending TestRequest");
                        String testReqId = "TR-" + Instant.now().toEpochMilli();
                        FIXMessage testRequest = FIXMessageBuilder
                            .testRequest(session.getNextOutgoingSeqNum(), testReqId)
                            .build();
                        sendMessage(session, testRequest);
                    }
                    
                    // Check if connection is dead
                    if (session.isConnectionDead()) {
                        LOGGER.error("Connection appears dead. No messages received in {} seconds", 
                                    session.getHeartbeatInterval() * 2);
                        session.setStatus(FIXSessionState.SessionStatus.ERROR);
                        disconnect(session.getSenderCompId(), session.getTargetCompId());
                    }
                }
            } catch (Exception e) {
                LOGGER.error("Error in heartbeat service", e);
            }
        }, 5, 5, TimeUnit.SECONDS); // Check every 5 seconds
    }
    
    /**
     * Start message processor
     */
    private void startMessageProcessor(FIXSessionState session) {
        messageProcessorExecutor = Executors.newSingleThreadExecutor();
        LOGGER.info("Starting message processor thread");
        
        messageProcessorExecutor.submit(() -> {
            LOGGER.info("Message processor thread started, waiting for messages...");
            byte[] buffer = new byte[8192];
            StringBuilder messageBuffer = new StringBuilder();
            
            while (running && !Thread.currentThread().isInterrupted()) {
                try {
                    int bytesRead = inputStream.read(buffer);
                    if (bytesRead == -1) {
                        LOGGER.warn("Connection closed by server");
                        break;
                    }
                    
                    LOGGER.debug("Read {} bytes from socket", bytesRead);
                    String data = new String(buffer, 0, bytesRead);
                    messageBuffer.append(data);
                    
                    LOGGER.debug("Message buffer now contains: {}", 
                        messageBuffer.toString().replace("\u0001", "|"));
                    
                    // Extract complete messages (ending with checksum + SOH)
                    String messages = messageBuffer.toString();
                    int lastChecksumEnd = messages.lastIndexOf("\u000110=");
                    
                    if (lastChecksumEnd != -1) {
                        // Find the SOH after checksum value
                        int messageEnd = messages.indexOf('\u0001', lastChecksumEnd + 4);
                        if (messageEnd != -1) {
                            String completeMessage = messages.substring(0, messageEnd + 1);
                            LOGGER.debug("Processing complete message: {}", 
                                completeMessage.replace("\u0001", "|"));
                            processIncomingMessage(session, completeMessage);
                            messageBuffer = new StringBuilder(messages.substring(messageEnd + 1));
                        }
                    }
                    
                } catch (IOException e) {
                    if (running) {
                        LOGGER.error("Error reading from socket", e);
                    }
                    break;
                }
            }
            LOGGER.info("Message processor thread stopped");
        });
    }
    
    /**
     * Stop background services
     */
    private void stopBackgroundServices() {
        if (heartbeatScheduler != null) {
            heartbeatScheduler.shutdown();
        }
        if (messageProcessorExecutor != null) {
            messageProcessorExecutor.shutdown();
        }
    }
    
    /**
     * Set message handler
     */
    public void setMessageHandler(MessageHandler handler) {
        this.messageHandler = handler;
    }
    
    /**
     * Get session state manager
     */
    public FIXSessionStateManager getStateManager() {
        return stateManager;
    }
    
    /**
     * Interface for handling FIX messages
     */
    public interface MessageHandler {
        void onLogon(FIXSessionState session);
        void onLogout(FIXSessionState session, String reason);
        void onApplicationMessage(FIXSessionState session, FIXMessage message);
    }
}

