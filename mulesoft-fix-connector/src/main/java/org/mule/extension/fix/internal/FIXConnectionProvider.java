package org.mule.extension.fix.internal;

import org.mule.runtime.api.connection.ConnectionException;
import org.mule.runtime.extension.api.annotation.param.Parameter;
import org.mule.runtime.extension.api.annotation.param.Optional;
import org.mule.runtime.api.connection.ConnectionValidationResult;
import org.mule.runtime.api.connection.CachedConnectionProvider;
import org.mule.runtime.extension.api.annotation.param.display.DisplayName;
import org.mule.runtime.extension.api.annotation.param.display.Summary;
import org.mule.runtime.extension.api.annotation.param.display.Placement;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Connection provider for FIX Protocol connections.
 * Manages connection lifecycle including connect, disconnect, and validation.
 * Uses CachedConnectionProvider to maintain a single connection per configuration.
 */
public class FIXConnectionProvider implements CachedConnectionProvider<FIXConnection> {

    private final Logger LOGGER = LoggerFactory.getLogger(FIXConnectionProvider.class);

    // Connection parameters
    @Parameter
    @DisplayName("Host")
    @Summary("FIX server hostname or IP address")
    @Placement(order = 1)
    private String host;

    @Parameter
    @DisplayName("Port")
    @Summary("FIX server port number")
    @Placement(order = 2)
    private int port;

    @Parameter
    @DisplayName("Connection Timeout")
    @Summary("Connection timeout in milliseconds")
    @Optional(defaultValue = "30000")
    @Placement(order = 3)
    private int connectionTimeout;
    
    // FIX Protocol Configuration parameters
    @Parameter
    @DisplayName("FIX Version")
    @Summary("FIX protocol version (e.g., FIX.4.2, FIX.4.4, FIXT.1.1)")
    @Optional(defaultValue = "FIX.4.4")
    @Placement(order = 4)
    private String beginString;

    @Parameter
    @DisplayName("Sender Comp ID")
    @Summary("Unique identifier for the message sender")
    @Placement(order = 5)
    private String senderCompId;

    @Parameter
    @DisplayName("Target Comp ID")
    @Summary("Unique identifier for the message recipient")
    @Placement(order = 6)
    private String targetCompId;

    @Parameter
    @DisplayName("Heartbeat Interval")
    @Summary("Heartbeat interval in seconds")
    @Optional(defaultValue = "30")
    @Placement(order = 7)
    private int heartbeatInterval;

    @Parameter
    @DisplayName("Reset Sequence on Logon")
    @Summary("Reset sequence numbers to 1 on logon")
    @Optional(defaultValue = "false")
    @Placement(order = 8)
    private boolean resetSequenceOnLogon;

    @Parameter
    @DisplayName("Validate Checksum")
    @Summary("Validate FIX message checksums")
    @Optional(defaultValue = "true")
    @Placement(order = 9)
    private boolean validateChecksum;

    /**
     * Establish FIX connection
     */
    @Override
    public FIXConnection connect() throws ConnectionException {
        try {
            LOGGER.info("Connecting to FIX server at {}:{}", host, port);
            
            // Manually construct the FIXConfiguration from parameters
            FIXConfiguration config = new FIXConfiguration();
            config.setBeginString(beginString);
            config.setSenderCompId(senderCompId);
            config.setTargetCompId(targetCompId);
            config.setHeartbeatInterval(heartbeatInterval);
            config.setResetSequenceOnLogon(resetSequenceOnLogon);
            config.setValidateChecksum(validateChecksum);
            
            FIXConnection connection = new FIXConnection(config, host, port);
            
            // Wait for logon to complete
            int waited = 0;
            int maxWait = connectionTimeout;
            LOGGER.info("Waiting for FIX session to become active (timeout: {}ms)", maxWait);
            while (waited < maxWait && !connection.isConnected()) {
                if (waited % 5000 == 0) {  // Log every 5 seconds
                    FIXSessionState state = connection.getSessionState();
                    LOGGER.info("Still waiting... {}ms elapsed. Session state: {}", 
                        waited, state != null ? state.getStatus() : "null");
                }
                Thread.sleep(100);
                waited += 100;
            }
            
            if (!connection.isConnected()) {
                FIXSessionState state = connection.getSessionState();
                LOGGER.error("Failed to establish FIX session. Final state: {}", 
                    state != null ? state.getStatus() : "null");
                throw new ConnectionException("Failed to establish FIX session within timeout period");
            }
            
            LOGGER.info("Successfully connected to FIX server");
            return connection;
            
        } catch (IOException e) {
            LOGGER.error("Failed to connect to FIX server at {}:{}", host, port, e);
            throw new ConnectionException("Failed to establish FIX connection: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ConnectionException("Connection interrupted", e);
        }
    }

    /**
     * Disconnect FIX connection
     */
    @Override
    public void disconnect(FIXConnection connection) {
        try {
            LOGGER.info("Disconnecting FIX connection: {}", connection.getId());
            connection.invalidate();
        } catch (Exception e) {
            LOGGER.error("Error while disconnecting [" + connection.getId() + "]: " + e.getMessage(), e);
        }
    }

    /**
     * Validate FIX connection
     */
    @Override
    public ConnectionValidationResult validate(FIXConnection connection) {
        try {
            if (connection.isConnected()) {
                FIXSessionState state = connection.getSessionState();
                if (state != null && state.isActive()) {
                    return ConnectionValidationResult.success();
                } else {
                    return ConnectionValidationResult.failure("FIX session is not active", null);
                }
            } else {
                return ConnectionValidationResult.failure("FIX connection is not established", null);
            }
        } catch (Exception e) {
            return ConnectionValidationResult.failure("Error validating connection: " + e.getMessage(), e);
        }
    }
}


