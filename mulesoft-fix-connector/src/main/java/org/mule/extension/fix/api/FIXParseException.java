package org.mule.extension.fix.api;

/**
 * Exception thrown when FIX message parsing fails
 */
public class FIXParseException extends Exception {
    
    private static final long serialVersionUID = 1L;
    
    public FIXParseException(String message) {
        super(message);
    }
    
    public FIXParseException(String message, Throwable cause) {
        super(message, cause);
    }
}

