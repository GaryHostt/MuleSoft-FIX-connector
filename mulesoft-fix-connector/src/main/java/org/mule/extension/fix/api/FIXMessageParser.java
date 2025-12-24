package org.mule.extension.fix.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Parser for FIX Protocol messages with checksum validation.
 * Handles the parsing of raw FIX message strings into FIXMessage objects.
 */
public class FIXMessageParser {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(FIXMessageParser.class);
    private static final char SOH = '\u0001'; // Start of Header delimiter
    
    /**
     * Parse a raw FIX message string into a FIXMessage object
     * 
     * @param rawMessage The raw FIX message string
     * @return Parsed FIXMessage object
     * @throws FIXParseException if the message is invalid or checksum fails
     */
    public static FIXMessage parse(String rawMessage) throws FIXParseException {
        if (rawMessage == null || rawMessage.isEmpty()) {
            throw new FIXParseException("Message is null or empty");
        }
        
        FIXMessage message = new FIXMessage();
        
        // Split by SOH delimiter
        String[] fields = rawMessage.split(String.valueOf(SOH));
        
        String checksumField = null;
        String messageWithoutChecksum = null;
        
        // Parse all fields
        for (int i = 0; i < fields.length; i++) {
            String field = fields[i];
            if (field.isEmpty()) {
                continue;
            }
            
            int equalsIndex = field.indexOf('=');
            if (equalsIndex == -1) {
                LOGGER.warn("Invalid field format (no '=' found): {}", field);
                continue;
            }
            
            try {
                int tag = Integer.parseInt(field.substring(0, equalsIndex));
                String value = field.substring(equalsIndex + 1);
                
                // Store checksum separately for validation
                if (tag == FIXMessage.TAG_CHECKSUM) {
                    checksumField = value;
                    // Calculate where checksum starts for validation
                    int checksumStart = rawMessage.lastIndexOf(SOH + FIXMessage.TAG_CHECKSUM + "=");
                    if (checksumStart != -1) {
                        messageWithoutChecksum = rawMessage.substring(0, checksumStart + 1);
                    }
                } else {
                    message.setField(tag, value);
                }
            } catch (NumberFormatException e) {
                LOGGER.warn("Invalid tag number in field: {}", field, e);
            }
        }
        
        // Validate checksum
        if (checksumField != null && messageWithoutChecksum != null) {
            String calculatedChecksum = FIXMessage.calculateChecksum(messageWithoutChecksum);
            if (!checksumField.equals(calculatedChecksum)) {
                throw new FIXParseException(
                    String.format("Checksum validation failed. Expected: %s, Calculated: %s", 
                                  checksumField, calculatedChecksum));
            }
        } else {
            LOGGER.warn("Checksum field not found in message");
        }
        
        // Validate required fields
        validateRequiredFields(message);
        
        return message;
    }
    
    /**
     * Validate that required FIX fields are present
     */
    private static void validateRequiredFields(FIXMessage message) throws FIXParseException {
        if (!message.hasField(FIXMessage.TAG_MSG_TYPE)) {
            throw new FIXParseException("Missing required field: MsgType (35)");
        }
        if (!message.hasField(FIXMessage.TAG_MSG_SEQ_NUM)) {
            throw new FIXParseException("Missing required field: MsgSeqNum (34)");
        }
        if (!message.hasField(FIXMessage.TAG_SENDER_COMP_ID)) {
            throw new FIXParseException("Missing required field: SenderCompID (49)");
        }
        if (!message.hasField(FIXMessage.TAG_TARGET_COMP_ID)) {
            throw new FIXParseException("Missing required field: TargetCompID (56)");
        }
        if (!message.hasField(FIXMessage.TAG_SENDING_TIME)) {
            throw new FIXParseException("Missing required field: SendingTime (52)");
        }
    }
    
    /**
     * Extract BeginString from raw message (needed before full parsing)
     */
    public static String extractBeginString(String rawMessage) {
        if (rawMessage == null) {
            return null;
        }
        
        int beginStringStart = rawMessage.indexOf("8=");
        if (beginStringStart == -1) {
            return null;
        }
        
        int sohIndex = rawMessage.indexOf(SOH, beginStringStart);
        if (sohIndex == -1) {
            return null;
        }
        
        return rawMessage.substring(beginStringStart + 2, sohIndex);
    }
    
    /**
     * Extract message type from raw message (for quick filtering)
     */
    public static String extractMsgType(String rawMessage) {
        if (rawMessage == null) {
            return null;
        }
        
        int msgTypeStart = rawMessage.indexOf("35=");
        if (msgTypeStart == -1) {
            return null;
        }
        
        int sohIndex = rawMessage.indexOf(SOH, msgTypeStart);
        if (sohIndex == -1) {
            return null;
        }
        
        return rawMessage.substring(msgTypeStart + 3, sohIndex);
    }
}

