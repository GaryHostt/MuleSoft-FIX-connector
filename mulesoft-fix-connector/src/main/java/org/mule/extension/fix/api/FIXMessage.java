package org.mule.extension.fix.api;

import java.util.LinkedHashMap;
import java.util.Map;
import java.io.Serializable;

/**
 * Represents a FIX Protocol message with proper field ordering and checksum calculation.
 * FIX messages use tag=value pairs separated by SOH (Start of Header, ASCII 0x01).
 */
public class FIXMessage implements Serializable {
    
    private static final long serialVersionUID = 1L;
    private static final char SOH = '\u0001'; // Start of Header delimiter
    
    // Standard FIX Tags
    public static final int TAG_BEGIN_STRING = 8;
    public static final int TAG_BODY_LENGTH = 9;
    public static final int TAG_MSG_TYPE = 35;
    public static final int TAG_MSG_SEQ_NUM = 34;
    public static final int TAG_SENDER_COMP_ID = 49;
    public static final int TAG_TARGET_COMP_ID = 56;
    public static final int TAG_SENDING_TIME = 52;
    public static final int TAG_CHECKSUM = 10;
    public static final int TAG_POSS_DUP_FLAG = 43;
    public static final int TAG_POSS_RESEND = 97;
    public static final int TAG_ORIG_SENDING_TIME = 122;
    public static final int TAG_HEARTBEAT_INTERVAL = 108;
    public static final int TAG_TEST_REQ_ID = 112;
    public static final int TAG_BEGIN_SEQ_NO = 7;
    public static final int TAG_END_SEQ_NO = 16;
    public static final int TAG_RESET_SEQ_NUM_FLAG = 141;
    public static final int TAG_TEXT = 58;
    public static final int TAG_ENCRYPT_METHOD = 98;
    
    // Message Types
    public static final String MSG_TYPE_HEARTBEAT = "0";
    public static final String MSG_TYPE_TEST_REQUEST = "1";
    public static final String MSG_TYPE_RESEND_REQUEST = "2";
    public static final String MSG_TYPE_REJECT = "3";
    public static final String MSG_TYPE_SEQUENCE_RESET = "4";
    public static final String MSG_TYPE_LOGOUT = "5";
    public static final String MSG_TYPE_LOGON = "A";
    
    private final LinkedHashMap<Integer, String> fields = new LinkedHashMap<>();
    
    public FIXMessage() {
    }
    
    public FIXMessage(String msgType) {
        setField(TAG_MSG_TYPE, msgType);
    }
    
    /**
     * Set a field value
     */
    public void setField(int tag, String value) {
        fields.put(tag, value);
    }
    
    /**
     * Set a field value (integer overload)
     */
    public void setField(int tag, int value) {
        fields.put(tag, String.valueOf(value));
    }
    
    /**
     * Get a field value
     */
    public String getField(int tag) {
        return fields.get(tag);
    }
    
    /**
     * Get a field value as integer
     */
    public Integer getFieldAsInt(int tag) {
        String value = fields.get(tag);
        return value != null ? Integer.parseInt(value) : null;
    }
    
    /**
     * Check if field exists
     */
    public boolean hasField(int tag) {
        return fields.containsKey(tag);
    }
    
    /**
     * Get message type
     */
    public String getMsgType() {
        return getField(TAG_MSG_TYPE);
    }
    
    /**
     * Get sequence number
     */
    public Integer getMsgSeqNum() {
        return getFieldAsInt(TAG_MSG_SEQ_NUM);
    }
    
    /**
     * Check if this is a duplicate message (PossDupFlag = Y)
     */
    public boolean isPossDup() {
        return "Y".equals(getField(TAG_POSS_DUP_FLAG));
    }
    
    /**
     * Get all fields
     */
    public Map<Integer, String> getFields() {
        return new LinkedHashMap<>(fields);
    }
    
    /**
     * Calculate checksum for FIX message
     * Checksum is sum of all bytes (modulo 256) in the message up to but not including the checksum field
     */
    public static String calculateChecksum(String message) {
        int checksum = 0;
        for (byte b : message.getBytes()) {
            checksum += b;
        }
        checksum = checksum % 256;
        return String.format("%03d", checksum);
    }
    
    /**
     * Build the complete FIX message string with proper framing
     * Message structure: BeginString(8) | BodyLength(9) | [Header + Body] | Checksum(10)
     */
    public String toFIXString(String beginString, String senderCompId, String targetCompId) {
        // Build body (everything between BodyLength and Checksum)
        StringBuilder body = new StringBuilder();
        
        // Message Type (required in header)
        body.append(TAG_MSG_TYPE).append('=').append(getField(TAG_MSG_TYPE)).append(SOH);
        
        // Sender and Target (required in header)
        body.append(TAG_SENDER_COMP_ID).append('=').append(senderCompId).append(SOH);
        body.append(TAG_TARGET_COMP_ID).append('=').append(targetCompId).append(SOH);
        
        // Add remaining fields (except BeginString, BodyLength, MsgType, and Checksum which are handled separately)
        for (Map.Entry<Integer, String> entry : fields.entrySet()) {
            int tag = entry.getKey();
            if (tag != TAG_BEGIN_STRING && tag != TAG_BODY_LENGTH && 
                tag != TAG_MSG_TYPE && tag != TAG_CHECKSUM &&
                tag != TAG_SENDER_COMP_ID && tag != TAG_TARGET_COMP_ID) {
                body.append(tag).append('=').append(entry.getValue()).append(SOH);
            }
        }
        
        // Calculate body length
        int bodyLength = body.length();
        
        // Build header
        StringBuilder header = new StringBuilder();
        header.append(TAG_BEGIN_STRING).append('=').append(beginString).append(SOH);
        header.append(TAG_BODY_LENGTH).append('=').append(bodyLength).append(SOH);
        
        // Combine header and body for checksum calculation
        String messageForChecksum = header.toString() + body.toString();
        String checksum = calculateChecksum(messageForChecksum);
        
        // Build complete message
        StringBuilder completeMessage = new StringBuilder(messageForChecksum);
        completeMessage.append(TAG_CHECKSUM).append('=').append(checksum).append(SOH);
        
        return completeMessage.toString();
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("FIXMessage{");
        sb.append("MsgType=").append(getField(TAG_MSG_TYPE));
        sb.append(", SeqNum=").append(getField(TAG_MSG_SEQ_NUM));
        sb.append(", fields=").append(fields);
        sb.append('}');
        return sb.toString();
    }
}

