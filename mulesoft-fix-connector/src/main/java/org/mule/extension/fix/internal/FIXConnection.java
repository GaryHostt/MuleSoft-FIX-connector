package org.mule.extension.fix.internal;

import org.mule.extension.fix.api.FIXMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Represents an active FIX protocol connection with session management.
 * This class wraps the FIXSessionManager and provides a clean interface for MuleSoft operations.
 */
public final class FIXConnection {

    private static final Logger LOGGER = LoggerFactory.getLogger(FIXConnection.class);

    private final String connectionId;
    private final FIXSessionManager sessionManager;
    private final FIXConfiguration config;
    private volatile boolean connected;

    public FIXConnection(FIXConfiguration config, String host, int port) throws IOException {
        this.config = config;
        this.connectionId = String.format("%s@%s:%d", config.getSenderCompId(), host, port);
        this.sessionManager = new FIXSessionManager(config.getBeginString());
        this.connected = false;

        LOGGER.info("Created FIX connection: {}", connectionId);

        // Connect to FIX server
        sessionManager.connect(host, port, 
                             config.getSenderCompId(), 
                             config.getTargetCompId(), 
                             config.getHeartbeatInterval());
        
        this.connected = true;
    }

    /**
     * Get the connection ID
     */
    public String getId() {
        return connectionId;
    }

    /**
     * Get the session manager
     */
    public FIXSessionManager getSessionManager() {
        return sessionManager;
    }

    /**
     * Get the configuration
     */
    public FIXConfiguration getConfig() {
        return config;
    }

    /**
     * Get session state
     */
    public FIXSessionState getSessionState() {
        return sessionManager.getStateManager()
            .getSession(config.getSenderCompId(), config.getTargetCompId());
    }

    /**
     * Send a FIX message
     */
    public void sendMessage(FIXMessage message) throws IOException {
        FIXSessionState state = getSessionState();
        if (state == null || !state.isActive()) {
            throw new IOException("FIX session is not active");
        }
        sessionManager.sendMessage(state, message);
    }

    /**
     * Check if connected
     */
    public boolean isConnected() {
        return connected && getSessionState() != null && getSessionState().isActive();
    }

    /**
     * Invalidate the connection
     */
    public void invalidate() {
        if (connected) {
            try {
                LOGGER.info("Invalidating FIX connection: {}", connectionId);
                sessionManager.disconnect(config.getSenderCompId(), config.getTargetCompId());
            } catch (IOException e) {
                LOGGER.error("Error disconnecting FIX session", e);
            } finally {
                connected = false;
            }
        }
    }

    @Override
    public String toString() {
        return String.format("FIXConnection{id='%s', connected=%s}", connectionId, connected);
    }
}

