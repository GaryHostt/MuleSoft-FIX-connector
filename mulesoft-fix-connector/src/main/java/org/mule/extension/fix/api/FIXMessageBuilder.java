package org.mule.extension.fix.api;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Builder for constructing FIX protocol messages with proper formatting.
 * Provides convenient methods for creating standard FIX message types.
 */
public class FIXMessageBuilder {
    
    private static final DateTimeFormatter FIX_TIMESTAMP_FORMAT = 
        DateTimeFormatter.ofPattern("yyyyMMdd-HH:mm:ss.SSS");
    
    private final FIXMessage message;
    
    public FIXMessageBuilder(String msgType) {
        this.message = new FIXMessage(msgType);
    }
    
    /**
     * Add standard header fields
     */
    public FIXMessageBuilder withHeader(int seqNum, String sendingTime) {
        message.setField(FIXMessage.TAG_MSG_SEQ_NUM, seqNum);
        message.setField(FIXMessage.TAG_SENDING_TIME, sendingTime);
        return this;
    }
    
    /**
     * Add standard header fields with current timestamp
     */
    public FIXMessageBuilder withHeader(int seqNum) {
        return withHeader(seqNum, getCurrentFIXTimestamp());
    }
    
    /**
     * Add a field
     */
    public FIXMessageBuilder withField(int tag, String value) {
        message.setField(tag, value);
        return this;
    }
    
    /**
     * Add a field (integer)
     */
    public FIXMessageBuilder withField(int tag, int value) {
        message.setField(tag, value);
        return this;
    }
    
    /**
     * Mark as possible duplicate
     */
    public FIXMessageBuilder withPossDup(String origSendingTime) {
        message.setField(FIXMessage.TAG_POSS_DUP_FLAG, "Y");
        message.setField(FIXMessage.TAG_ORIG_SENDING_TIME, origSendingTime);
        return this;
    }
    
    /**
     * Build the message
     */
    public FIXMessage build() {
        return message;
    }
    
    /**
     * Create a Logon message (MsgType A)
     */
    public static FIXMessageBuilder logon(int seqNum, int heartbeatInterval) {
        return new FIXMessageBuilder(FIXMessage.MSG_TYPE_LOGON)
            .withHeader(seqNum)
            .withField(FIXMessage.TAG_ENCRYPT_METHOD, 0) // No encryption
            .withField(FIXMessage.TAG_HEARTBEAT_INTERVAL, heartbeatInterval);
    }
    
    /**
     * Create a Logon message with ResetSeqNumFlag
     */
    public static FIXMessageBuilder logonWithReset(int heartbeatInterval) {
        return new FIXMessageBuilder(FIXMessage.MSG_TYPE_LOGON)
            .withHeader(1) // Start at 1
            .withField(FIXMessage.TAG_ENCRYPT_METHOD, 0)
            .withField(FIXMessage.TAG_HEARTBEAT_INTERVAL, heartbeatInterval)
            .withField(FIXMessage.TAG_RESET_SEQ_NUM_FLAG, "Y");
    }
    
    /**
     * Create a Logout message (MsgType 5)
     */
    public static FIXMessageBuilder logout(int seqNum) {
        return new FIXMessageBuilder(FIXMessage.MSG_TYPE_LOGOUT)
            .withHeader(seqNum);
    }
    
    /**
     * Create a Logout message with text reason
     */
    public static FIXMessageBuilder logout(int seqNum, String reason) {
        return logout(seqNum)
            .withField(FIXMessage.TAG_TEXT, reason);
    }
    
    /**
     * Create a Heartbeat message (MsgType 0)
     */
    public static FIXMessageBuilder heartbeat(int seqNum) {
        return new FIXMessageBuilder(FIXMessage.MSG_TYPE_HEARTBEAT)
            .withHeader(seqNum);
    }
    
    /**
     * Create a Heartbeat response to TestRequest (includes TestReqID)
     */
    public static FIXMessageBuilder heartbeat(int seqNum, String testReqId) {
        return heartbeat(seqNum)
            .withField(FIXMessage.TAG_TEST_REQ_ID, testReqId);
    }
    
    /**
     * Create a TestRequest message (MsgType 1)
     */
    public static FIXMessageBuilder testRequest(int seqNum, String testReqId) {
        return new FIXMessageBuilder(FIXMessage.MSG_TYPE_TEST_REQUEST)
            .withHeader(seqNum)
            .withField(FIXMessage.TAG_TEST_REQ_ID, testReqId);
    }
    
    /**
     * Create a ResendRequest message (MsgType 2)
     */
    public static FIXMessageBuilder resendRequest(int seqNum, int beginSeqNo, int endSeqNo) {
        return new FIXMessageBuilder(FIXMessage.MSG_TYPE_RESEND_REQUEST)
            .withHeader(seqNum)
            .withField(FIXMessage.TAG_BEGIN_SEQ_NO, beginSeqNo)
            .withField(FIXMessage.TAG_END_SEQ_NO, endSeqNo);
    }
    
    /**
     * Create a ResendRequest for all missing messages (EndSeqNo = 0 means infinity)
     */
    public static FIXMessageBuilder resendRequest(int seqNum, int beginSeqNo) {
        return resendRequest(seqNum, beginSeqNo, 0);
    }
    
    /**
     * Create a Reject message (MsgType 3)
     */
    public static FIXMessageBuilder reject(int seqNum, int refSeqNum, String reason) {
        return new FIXMessageBuilder(FIXMessage.MSG_TYPE_REJECT)
            .withHeader(seqNum)
            .withField(45, refSeqNum) // RefSeqNum
            .withField(FIXMessage.TAG_TEXT, reason);
    }
    
    /**
     * Create a SequenceReset-GapFill message (MsgType 4)
     */
    public static FIXMessageBuilder sequenceReset(int seqNum, int newSeqNo, boolean gapFillFlag) {
        FIXMessageBuilder builder = new FIXMessageBuilder(FIXMessage.MSG_TYPE_SEQUENCE_RESET)
            .withHeader(seqNum)
            .withField(36, newSeqNo); // NewSeqNo
        
        if (gapFillFlag) {
            builder.withField(123, "Y"); // GapFillFlag
        }
        
        return builder;
    }
    
    /**
     * Get current timestamp in FIX format (yyyyMMdd-HH:mm:ss.SSS)
     */
    public static String getCurrentFIXTimestamp() {
        return ZonedDateTime.now().format(FIX_TIMESTAMP_FORMAT);
    }
    
    /**
     * Format a ZonedDateTime to FIX timestamp
     */
    public static String toFIXTimestamp(ZonedDateTime dateTime) {
        return dateTime.format(FIX_TIMESTAMP_FORMAT);
    }
}

