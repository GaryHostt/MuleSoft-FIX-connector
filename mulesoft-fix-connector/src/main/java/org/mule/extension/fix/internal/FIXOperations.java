package org.mule.extension.fix.internal;

import static org.mule.runtime.extension.api.annotation.param.MediaType.ANY;
import static org.mule.runtime.extension.api.annotation.param.MediaType.APPLICATION_JSON;

import org.mule.extension.fix.api.FIXMessage;
import org.mule.extension.fix.api.FIXMessageBuilder;
import org.mule.runtime.extension.api.annotation.param.MediaType;
import org.mule.runtime.extension.api.annotation.param.Config;
import org.mule.runtime.extension.api.annotation.param.Connection;
import org.mule.runtime.extension.api.annotation.param.Content;
import org.mule.runtime.extension.api.annotation.param.Parameter;
import org.mule.runtime.extension.api.annotation.param.Optional;
import org.mule.runtime.extension.api.annotation.param.display.DisplayName;
import org.mule.runtime.extension.api.annotation.param.display.Summary;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;
import java.util.HashMap;

/**
 * Operations for FIX Protocol Connector.
 * Provides operations to send various FIX message types and manage sessions.
 */
public class FIXOperations {

    private static final Logger LOGGER = LoggerFactory.getLogger(FIXOperations.class);
    
    /**
     * Helper method to convert Map to simple JSON string
     */
    private String mapToJson(Map<String, Object> map) {
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) json.append(",");
            json.append("\"").append(entry.getKey()).append("\":\"")
                .append(entry.getValue()).append("\"");
            first = false;
        }
        json.append("}");
        return json.toString();
    }

    /**
     * Send a custom FIX message
     * 
     * @param connection The FIX connection
     * @param msgType FIX message type (e.g., "D" for NewOrderSingle, "8" for ExecutionReport)
     * @param fields Map of tag-value pairs to include in the message
     * @return Result of the send operation as JSON string
     */
    @MediaType(value = APPLICATION_JSON, strict = false)
    @DisplayName("Send FIX Message")
    @Summary("Send a custom FIX message with specified message type and fields")
    public String sendMessage(
            @Connection FIXConnection connection,
            @DisplayName("Message Type") @Summary("FIX message type code") String msgType,
            @DisplayName("Message Fields") @Summary("JSON string of FIX tag numbers to values") String fields) {
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            FIXSessionState state = connection.getSessionState();
            if (state == null || !state.isActive()) {
                result.put("success", false);
                result.put("error", "FIX session is not active");
                return mapToJson(result);
            }
            
            // Build message
            FIXMessageBuilder builder = new FIXMessageBuilder(msgType)
                .withHeader(state.getNextOutgoingSeqNum());
            
            // Add custom fields (expecting JSON or comma-separated format)
            if (fields != null && !fields.isEmpty()) {
                // Simple parsing - expecting format like: "11=ORDER123,55=AAPL,54=1"
                String[] fieldPairs = fields.split(",");
                for (String pair : fieldPairs) {
                    String[] parts = pair.split("=");
                    if (parts.length == 2) {
                        try {
                            int tag = Integer.parseInt(parts[0].trim());
                            builder.withField(tag, parts[1].trim());
                        } catch (NumberFormatException e) {
                            LOGGER.warn("Invalid tag number: {}", parts[0]);
                        }
                    }
                }
            }
            
            FIXMessage message = builder.build();
            connection.sendMessage(message);
            
            result.put("success", true);
            result.put("msgType", msgType);
            result.put("seqNum", state.getCurrentOutgoingSeqNum());
            
            LOGGER.info("Sent FIX message: type={}, seqNum={}", msgType, state.getCurrentOutgoingSeqNum());
            
        } catch (IOException e) {
            LOGGER.error("Failed to send FIX message", e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        
        return mapToJson(result);
    }

    /**
     * Send a heartbeat message
     * 
     * @param connection The FIX connection
     * @param testReqId Optional TestReqID if responding to a TestRequest
     * @return Result of the send operation as JSON string
     */
    @MediaType(value = APPLICATION_JSON, strict = false)
    @DisplayName("Send Heartbeat")
    @Summary("Send a FIX Heartbeat message (MsgType 0)")
    public String sendHeartbeat(
            @Connection FIXConnection connection,
            @Optional @DisplayName("Test Request ID") @Summary("TestReqID if responding to TestRequest") String testReqId) {
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            FIXSessionState state = connection.getSessionState();
            if (state == null || !state.isActive()) {
                result.put("success", false);
                result.put("error", "FIX session is not active");
                return mapToJson(result);
            }
            
            FIXMessageBuilder builder = FIXMessageBuilder.heartbeat(state.getNextOutgoingSeqNum());
            
            if (testReqId != null && !testReqId.isEmpty()) {
                builder.withField(FIXMessage.TAG_TEST_REQ_ID, testReqId);
            }
            
            connection.sendMessage(builder.build());
            
            result.put("success", true);
            result.put("msgType", FIXMessage.MSG_TYPE_HEARTBEAT);
            result.put("seqNum", state.getCurrentOutgoingSeqNum());
            
            LOGGER.info("Sent Heartbeat message");
            
        } catch (IOException e) {
            LOGGER.error("Failed to send Heartbeat", e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        
        return mapToJson(result);
    }

    /**
     * Send a test request
     * 
     * @param connection The FIX connection
     * @param testReqId Test request identifier
     * @return Result of the send operation
     */
    @MediaType(value = APPLICATION_JSON, strict = false)
    @DisplayName("Send Test Request")
    @Summary("Send a FIX Test Request message (MsgType 1)")
    public String sendTestRequest(
            @Connection FIXConnection connection,
            @DisplayName("Test Request ID") @Summary("Unique identifier for this test request") String testReqId) {
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            FIXSessionState state = connection.getSessionState();
            if (state == null || !state.isActive()) {
                result.put("success", false);
                result.put("error", "FIX session is not active");
                return mapToJson(result);
            }
            
            FIXMessage message = FIXMessageBuilder
                .testRequest(state.getNextOutgoingSeqNum(), testReqId)
                .build();
            
            connection.sendMessage(message);
            
            result.put("success", true);
            result.put("msgType", FIXMessage.MSG_TYPE_TEST_REQUEST);
            result.put("testReqId", testReqId);
            result.put("seqNum", state.getCurrentOutgoingSeqNum());
            
            LOGGER.info("Sent Test Request: {}", testReqId);
            
        } catch (IOException e) {
            LOGGER.error("Failed to send Test Request", e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        
        return mapToJson(result);
    }

    /**
     * Request resend of messages
     * 
     * @param connection The FIX connection
     * @param beginSeqNo Beginning sequence number
     * @param endSeqNo Ending sequence number (0 for infinity)
     * @return Result of the send operation
     */
    @MediaType(value = APPLICATION_JSON, strict = false)
    @DisplayName("Request Resend")
    @Summary("Send a FIX Resend Request message (MsgType 2)")
    public String requestResend(
            @Connection FIXConnection connection,
            @DisplayName("Begin Sequence Number") int beginSeqNo,
            @Optional(defaultValue = "0") @DisplayName("End Sequence Number") @Summary("0 means all messages to current") int endSeqNo) {
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            FIXSessionState state = connection.getSessionState();
            if (state == null || !state.isActive()) {
                result.put("success", false);
                result.put("error", "FIX session is not active");
                return mapToJson(result);
            }
            
            FIXMessage message = FIXMessageBuilder
                .resendRequest(state.getNextOutgoingSeqNum(), beginSeqNo, endSeqNo)
                .build();
            
            connection.sendMessage(message);
            
            result.put("success", true);
            result.put("msgType", FIXMessage.MSG_TYPE_RESEND_REQUEST);
            result.put("beginSeqNo", beginSeqNo);
            result.put("endSeqNo", endSeqNo);
            result.put("seqNum", state.getCurrentOutgoingSeqNum());
            
            LOGGER.info("Sent Resend Request: {} to {}", beginSeqNo, endSeqNo);
            
        } catch (IOException e) {
            LOGGER.error("Failed to send Resend Request", e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        
        return mapToJson(result);
    }

    /**
     * Get current session information
     * 
     * @param connection The FIX connection
     * @return Session information including sequence numbers and status
     */
    @MediaType(value = APPLICATION_JSON, strict = false)
    @DisplayName("Get Session Info")
    @Summary("Retrieve current FIX session information and status")
    public String getSessionInfo(@Connection FIXConnection connection) {
        Map<String, Object> result = new HashMap<>();
        
        FIXSessionState state = connection.getSessionState();
        if (state == null) {
            result.put("error", "No session state available");
            return mapToJson(result);
        }
        
        result.put("sessionId", state.getSessionId());
        result.put("senderCompId", state.getSenderCompId());
        result.put("targetCompId", state.getTargetCompId());
        result.put("status", state.getStatus().toString());
        result.put("incomingSeqNum", state.getIncomingSeqNum());
        result.put("outgoingSeqNum", state.getCurrentOutgoingSeqNum());
        result.put("heartbeatInterval", state.getHeartbeatInterval());
        result.put("lastMessageReceived", state.getLastMessageReceivedTime() != null ? 
                   state.getLastMessageReceivedTime().toString() : null);
        result.put("lastMessageSent", state.getLastMessageSentTime() != null ? 
                   state.getLastMessageSentTime().toString() : null);
        result.put("logonTime", state.getLogonTime() != null ? 
                   state.getLogonTime().toString() : null);
        result.put("bufferedMessageCount", state.getBufferedMessageCount());
        
        return mapToJson(result);
    }

    /**
     * Reset sequence numbers
     * 
     * @param connection The FIX connection
     * @return Result of the operation
     */
    @MediaType(value = APPLICATION_JSON, strict = false)
    @DisplayName("Reset Sequence Numbers")
    @Summary("Reset both incoming and outgoing sequence numbers to 1")
    public String resetSequenceNumbers(@Connection FIXConnection connection) {
        Map<String, Object> result = new HashMap<>();
        
        FIXSessionState state = connection.getSessionState();
        if (state == null) {
            result.put("success", false);
            result.put("error", "No session state available");
            return mapToJson(result);
        }
        
        state.resetSequenceNumbers();
        connection.getSessionManager().getStateManager().saveSession(state);
        
        result.put("success", true);
        result.put("message", "Sequence numbers reset to 1");
        
        LOGGER.info("Sequence numbers reset for session: {}", state.getSessionId());
        
        return mapToJson(result);
    }
}

