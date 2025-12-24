package org.mule.extension.fix.internal;

import org.mule.extension.fix.api.FIXMessage;
import org.mule.runtime.api.connection.ConnectionException;
import org.mule.runtime.api.connection.ConnectionProvider;
import org.mule.runtime.extension.api.annotation.Alias;
import org.mule.runtime.extension.api.annotation.param.Connection;
import org.mule.runtime.extension.api.annotation.param.MediaType;
import org.mule.runtime.extension.api.annotation.param.Optional;
import org.mule.runtime.extension.api.annotation.param.Parameter;
import org.mule.runtime.extension.api.annotation.param.display.DisplayName;
import org.mule.runtime.extension.api.annotation.param.display.Summary;
import org.mule.runtime.extension.api.runtime.source.Source;
import org.mule.runtime.extension.api.runtime.source.SourceCallback;
import org.mule.runtime.extension.api.runtime.operation.Result;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

import static org.mule.runtime.extension.api.annotation.param.MediaType.APPLICATION_JSON;

/**
 * Message source that listens for incoming FIX messages.
 * This source will trigger flows when FIX messages are received.
 */
@Alias("listener")
@DisplayName("FIX Message Listener")
@MediaType(value = APPLICATION_JSON, strict = false)
public class FIXMessageListener extends Source<String, Void> {

    private static final Logger LOGGER = LoggerFactory.getLogger(FIXMessageListener.class);

    @Connection
    private ConnectionProvider<FIXConnection> connectionProvider;

    @Parameter
    @Optional(defaultValue = "ALL")
    @DisplayName("Message Type Filter")
    @Summary("Filter messages by type (e.g., 'D', '8', 'ALL' for all messages)")
    private String messageTypeFilter;

    @Parameter
    @Optional(defaultValue = "false")
    @DisplayName("Include Admin Messages")
    @Summary("Include administrative messages (Heartbeat, TestRequest, etc.)")
    private boolean includeAdminMessages;

    private volatile boolean started = false;
    private FIXConnection connection;

    @Override
    public void onStart(SourceCallback<String, Void> sourceCallback) {
        LOGGER.info("Starting FIX Message Listener");
        
        try {
            connection = connectionProvider.connect();
            
            // Register message handler
            connection.getSessionManager().setMessageHandler(new FIXSessionManager.MessageHandler() {
                @Override
                public void onLogon(FIXSessionState session) {
                    LOGGER.info("Session logged on: {}", session.getSessionId());
                    if (includeAdminMessages) {
                        String message = String.format("{\"messageType\":\"LOGON\",\"sessionId\":\"%s\",\"timestamp\":%d}",
                            session.getSessionId(), System.currentTimeMillis());
                        
                        sourceCallback.handle(Result.<String, Void>builder()
                            .output(message)
                            .build());
                    }
                }

                @Override
                public void onLogout(FIXSessionState session, String reason) {
                    LOGGER.info("Session logged out: {}, reason: {}", session.getSessionId(), reason);
                    if (includeAdminMessages) {
                        String message = String.format("{\"messageType\":\"LOGOUT\",\"sessionId\":\"%s\",\"reason\":\"%s\",\"timestamp\":%d}",
                            session.getSessionId(), reason != null ? reason : "", System.currentTimeMillis());
                        
                        sourceCallback.handle(Result.<String, Void>builder()
                            .output(message)
                            .build());
                    }
                }

                @Override
                public void onApplicationMessage(FIXSessionState session, FIXMessage fixMessage) {
                    String msgType = fixMessage.getMsgType();
                    
                    // Check if message type matches filter
                    if (!messageTypeFilter.equals("ALL") && !messageTypeFilter.equals(msgType)) {
                        return;
                    }
                    
                    LOGGER.debug("Received FIX message: type={}, seqNum={}", 
                               msgType, fixMessage.getMsgSeqNum());
                    
                    // Convert FIX message to JSON string
                    StringBuilder jsonBuilder = new StringBuilder("{");
                    jsonBuilder.append("\"messageType\":\"").append(msgType).append("\",");
                    jsonBuilder.append("\"seqNum\":").append(fixMessage.getMsgSeqNum()).append(",");
                    jsonBuilder.append("\"senderCompId\":\"").append(fixMessage.getField(FIXMessage.TAG_SENDER_COMP_ID)).append("\",");
                    jsonBuilder.append("\"targetCompId\":\"").append(fixMessage.getField(FIXMessage.TAG_TARGET_COMP_ID)).append("\",");
                    jsonBuilder.append("\"sendingTime\":\"").append(fixMessage.getField(FIXMessage.TAG_SENDING_TIME)).append("\",");
                    
                    // Add all fields
                    jsonBuilder.append("\"fields\":{");
                    boolean first = true;
                    for (Map.Entry<Integer, String> entry : fixMessage.getFields().entrySet()) {
                        if (!first) jsonBuilder.append(",");
                        jsonBuilder.append("\"").append(entry.getKey()).append("\":\"").append(entry.getValue()).append("\"");
                        first = false;
                    }
                    jsonBuilder.append("},");
                    jsonBuilder.append("\"timestamp\":").append(System.currentTimeMillis());
                    jsonBuilder.append("}");
                    
                    // Trigger flow
                    sourceCallback.handle(Result.<String, Void>builder()
                            .output(jsonBuilder.toString())
                            .build());
                }
            });
            
            started = true;
            LOGGER.info("FIX Message Listener started successfully");
            
        } catch (Exception e) {
            LOGGER.error("Failed to start FIX Message Listener", e);
            throw new RuntimeException("Failed to start FIX Message Listener", e);
        }
    }

    @Override
    public void onStop() {
        LOGGER.info("Stopping FIX Message Listener");
        started = false;
        
        if (connection != null) {
            try {
                connectionProvider.disconnect(connection);
            } catch (Exception e) {
                LOGGER.error("Error disconnecting during stop", e);
            }
        }
        
        LOGGER.info("FIX Message Listener stopped");
    }
}

