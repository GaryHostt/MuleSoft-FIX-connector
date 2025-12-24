package org.mule.extension.fix.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Manages FIX session state including sequence numbers, session status, and timing.
 * This class handles the stateful nature of FIX protocol conversations.
 */
public class FIXSessionState implements Serializable {
    
    private static final long serialVersionUID = 1L;
    private static final Logger LOGGER = LoggerFactory.getLogger(FIXSessionState.class);
    
    // Session identifiers
    private final String sessionId;
    private final String senderCompId;
    private final String targetCompId;
    
    // Sequence numbers
    private volatile int incomingSeqNum;  // Next expected incoming sequence number
    private volatile int outgoingSeqNum;  // Next outgoing sequence number
    
    // Session state
    private volatile SessionStatus status;
    private volatile Instant lastMessageReceivedTime;
    private volatile Instant lastMessageSentTime;
    private volatile Instant logonTime;
    
    // Heartbeat interval (in seconds)
    private volatile int heartbeatInterval;
    
    // Out-of-order message buffer (for gap fill scenarios)
    private final Map<Integer, String> messageBuffer;
    
    public enum SessionStatus {
        DISCONNECTED,
        CONNECTING,
        LOGGED_IN,
        LOGGING_OUT,
        AWAITING_RESEND,
        ERROR
    }
    
    public FIXSessionState(String senderCompId, String targetCompId) {
        this.sessionId = generateSessionId(senderCompId, targetCompId);
        this.senderCompId = senderCompId;
        this.targetCompId = targetCompId;
        this.incomingSeqNum = 1;
        this.outgoingSeqNum = 1;
        this.status = SessionStatus.DISCONNECTED;
        this.messageBuffer = new ConcurrentHashMap<>();
        this.heartbeatInterval = 30; // Default 30 seconds
    }
    
    /**
     * Generate a unique session identifier
     */
    private static String generateSessionId(String senderCompId, String targetCompId) {
        return String.format("%s-%s", senderCompId, targetCompId);
    }
    
    /**
     * Get the session ID
     */
    public String getSessionId() {
        return sessionId;
    }
    
    /**
     * Get sender comp ID
     */
    public String getSenderCompId() {
        return senderCompId;
    }
    
    /**
     * Get target comp ID
     */
    public String getTargetCompId() {
        return targetCompId;
    }
    
    /**
     * Get next expected incoming sequence number
     */
    public synchronized int getIncomingSeqNum() {
        return incomingSeqNum;
    }
    
    /**
     * Increment and get the next incoming sequence number
     */
    public synchronized int incrementIncomingSeqNum() {
        return ++incomingSeqNum;
    }
    
    /**
     * Set incoming sequence number (used for resets)
     */
    public synchronized void setIncomingSeqNum(int seqNum) {
        LOGGER.info("Resetting incoming sequence number from {} to {}", incomingSeqNum, seqNum);
        this.incomingSeqNum = seqNum;
    }
    
    /**
     * Get next outgoing sequence number (and increment)
     */
    public synchronized int getNextOutgoingSeqNum() {
        return outgoingSeqNum++;
    }
    
    /**
     * Get current outgoing sequence number (without incrementing)
     */
    public synchronized int getCurrentOutgoingSeqNum() {
        return outgoingSeqNum;
    }
    
    /**
     * Set outgoing sequence number (used for resets)
     */
    public synchronized void setOutgoingSeqNum(int seqNum) {
        LOGGER.info("Resetting outgoing sequence number from {} to {}", outgoingSeqNum, seqNum);
        this.outgoingSeqNum = seqNum;
    }
    
    /**
     * Reset both sequence numbers to 1
     */
    public synchronized void resetSequenceNumbers() {
        LOGGER.info("Resetting all sequence numbers to 1");
        this.incomingSeqNum = 1;
        this.outgoingSeqNum = 1;
        this.messageBuffer.clear();
    }
    
    /**
     * Get session status
     */
    public SessionStatus getStatus() {
        return status;
    }
    
    /**
     * Set session status
     */
    public void setStatus(SessionStatus status) {
        LOGGER.info("Session status changed: {} -> {}", this.status, status);
        this.status = status;
        if (status == SessionStatus.LOGGED_IN && logonTime == null) {
            this.logonTime = Instant.now();
        }
    }
    
    /**
     * Check if session is logged in and active
     */
    public boolean isActive() {
        return status == SessionStatus.LOGGED_IN;
    }
    
    /**
     * Update last message received time
     */
    public void updateLastMessageReceivedTime() {
        this.lastMessageReceivedTime = Instant.now();
    }
    
    /**
     * Update last message sent time
     */
    public void updateLastMessageSentTime() {
        this.lastMessageSentTime = Instant.now();
    }
    
    /**
     * Get last message received time
     */
    public Instant getLastMessageReceivedTime() {
        return lastMessageReceivedTime;
    }
    
    /**
     * Get last message sent time
     */
    public Instant getLastMessageSentTime() {
        return lastMessageSentTime;
    }
    
    /**
     * Get logon time
     */
    public Instant getLogonTime() {
        return logonTime;
    }
    
    /**
     * Set heartbeat interval
     */
    public void setHeartbeatInterval(int seconds) {
        this.heartbeatInterval = seconds;
        LOGGER.info("Heartbeat interval set to {} seconds", seconds);
    }
    
    /**
     * Get heartbeat interval
     */
    public int getHeartbeatInterval() {
        return heartbeatInterval;
    }
    
    /**
     * Check if heartbeat is needed (no message sent within heartbeat interval)
     */
    public boolean isHeartbeatNeeded() {
        if (lastMessageSentTime == null) {
            return false;
        }
        long secondsSinceLastSent = Instant.now().getEpochSecond() - lastMessageSentTime.getEpochSecond();
        return secondsSinceLastSent >= heartbeatInterval;
    }
    
    /**
     * Check if test request should be sent (no message received within heartbeat interval + tolerance)
     */
    public boolean isTestRequestNeeded() {
        if (lastMessageReceivedTime == null) {
            return false;
        }
        long secondsSinceLastReceived = Instant.now().getEpochSecond() - lastMessageReceivedTime.getEpochSecond();
        // Send test request if no message received for 1.2x heartbeat interval
        return secondsSinceLastReceived >= (heartbeatInterval * 1.2);
    }
    
    /**
     * Check if connection should be considered dead
     */
    public boolean isConnectionDead() {
        if (lastMessageReceivedTime == null) {
            return false;
        }
        long secondsSinceLastReceived = Instant.now().getEpochSecond() - lastMessageReceivedTime.getEpochSecond();
        // Consider dead if no message received for 2x heartbeat interval
        return secondsSinceLastReceived >= (heartbeatInterval * 2);
    }
    
    /**
     * Buffer an out-of-order message
     */
    public void bufferMessage(int seqNum, String rawMessage) {
        LOGGER.info("Buffering out-of-order message with seqNum: {}", seqNum);
        messageBuffer.put(seqNum, rawMessage);
    }
    
    /**
     * Get buffered message by sequence number
     */
    public String getBufferedMessage(int seqNum) {
        return messageBuffer.remove(seqNum);
    }
    
    /**
     * Check if message is buffered
     */
    public boolean hasBufferedMessage(int seqNum) {
        return messageBuffer.containsKey(seqNum);
    }
    
    /**
     * Get count of buffered messages
     */
    public int getBufferedMessageCount() {
        return messageBuffer.size();
    }
    
    /**
     * Clear message buffer
     */
    public void clearMessageBuffer() {
        messageBuffer.clear();
    }
    
    @Override
    public String toString() {
        return String.format("FIXSessionState{sessionId='%s', status=%s, inSeq=%d, outSeq=%d, heartbeat=%ds}",
                sessionId, status, incomingSeqNum, outgoingSeqNum, heartbeatInterval);
    }
}

