package org.mule.extension.fix.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages persistence of FIX session state.
 * In a real MuleSoft environment, this would integrate with ObjectStore API.
 * For now, we use in-memory storage with the same interface pattern.
 */
public class FIXSessionStateManager {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(FIXSessionStateManager.class);
    
    // In-memory session state store (in production, this would be ObjectStore)
    private final Map<String, FIXSessionState> sessionStore;
    
    public FIXSessionStateManager() {
        this.sessionStore = new ConcurrentHashMap<>();
    }
    
    /**
     * Get or create a session state
     */
    public FIXSessionState getOrCreateSession(String senderCompId, String targetCompId) {
        String sessionId = generateSessionId(senderCompId, targetCompId);
        return sessionStore.computeIfAbsent(sessionId, 
            key -> {
                LOGGER.info("Creating new session state for: {}", sessionId);
                return new FIXSessionState(senderCompId, targetCompId);
            });
    }
    
    /**
     * Get an existing session state
     */
    public FIXSessionState getSession(String sessionId) {
        return sessionStore.get(sessionId);
    }
    
    /**
     * Get an existing session state by comp IDs
     */
    public FIXSessionState getSession(String senderCompId, String targetCompId) {
        String sessionId = generateSessionId(senderCompId, targetCompId);
        return sessionStore.get(sessionId);
    }
    
    /**
     * Save/update session state
     */
    public void saveSession(FIXSessionState state) {
        LOGGER.debug("Saving session state: {}", state.getSessionId());
        sessionStore.put(state.getSessionId(), state);
    }
    
    /**
     * Remove session state
     */
    public void removeSession(String sessionId) {
        LOGGER.info("Removing session state: {}", sessionId);
        sessionStore.remove(sessionId);
    }
    
    /**
     * Check if session exists
     */
    public boolean hasSession(String sessionId) {
        return sessionStore.containsKey(sessionId);
    }
    
    /**
     * Clear all sessions
     */
    public void clearAllSessions() {
        LOGGER.info("Clearing all session states");
        sessionStore.clear();
    }
    
    /**
     * Get all session IDs
     */
    public Iterable<String> getAllSessionIds() {
        return sessionStore.keySet();
    }
    
    /**
     * Generate session ID from comp IDs
     */
    private static String generateSessionId(String senderCompId, String targetCompId) {
        return String.format("%s-%s", senderCompId, targetCompId);
    }
    
    /**
     * Validate and update incoming sequence number
     * Returns true if sequence is valid, false if gap detected
     */
    public SequenceValidationResult validateIncomingSequence(FIXSessionState state, int receivedSeqNum) {
        int expectedSeqNum = state.getIncomingSeqNum();
        
        if (receivedSeqNum == expectedSeqNum) {
            // Perfect sequence
            state.incrementIncomingSeqNum();
            saveSession(state);
            return new SequenceValidationResult(SequenceValidationResult.Status.VALID, expectedSeqNum, receivedSeqNum);
            
        } else if (receivedSeqNum > expectedSeqNum) {
            // Gap detected - message came too high
            LOGGER.warn("Sequence gap detected. Expected: {}, Received: {}", expectedSeqNum, receivedSeqNum);
            return new SequenceValidationResult(SequenceValidationResult.Status.GAP_DETECTED, expectedSeqNum, receivedSeqNum);
            
        } else {
            // Lower than expected - potential duplicate or resend
            LOGGER.warn("Lower sequence received. Expected: {}, Received: {}", expectedSeqNum, receivedSeqNum);
            return new SequenceValidationResult(SequenceValidationResult.Status.LOWER_THAN_EXPECTED, expectedSeqNum, receivedSeqNum);
        }
    }
    
    /**
     * Result of sequence validation
     */
    public static class SequenceValidationResult {
        public enum Status {
            VALID,
            GAP_DETECTED,
            LOWER_THAN_EXPECTED
        }
        
        private final Status status;
        private final int expectedSeqNum;
        private final int receivedSeqNum;
        
        public SequenceValidationResult(Status status, int expectedSeqNum, int receivedSeqNum) {
            this.status = status;
            this.expectedSeqNum = expectedSeqNum;
            this.receivedSeqNum = receivedSeqNum;
        }
        
        public Status getStatus() {
            return status;
        }
        
        public int getExpectedSeqNum() {
            return expectedSeqNum;
        }
        
        public int getReceivedSeqNum() {
            return receivedSeqNum;
        }
        
        public boolean isValid() {
            return status == Status.VALID;
        }
        
        public boolean isGapDetected() {
            return status == Status.GAP_DETECTED;
        }
        
        public boolean isLowerThanExpected() {
            return status == Status.LOWER_THAN_EXPECTED;
        }
    }
}

