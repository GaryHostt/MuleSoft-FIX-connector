package org.mule.extension.fix.internal;

import org.mule.runtime.extension.api.annotation.Operations;
import org.mule.runtime.extension.api.annotation.Sources;
import org.mule.runtime.extension.api.annotation.connectivity.ConnectionProviders;

/**
 * Configuration for FIX Protocol Connector.
 * This class holds configuration data but parameters are defined in the ConnectionProvider.
 * This approach prevents duplicate parameter definitions in the SDK metadata.
 */
@Operations(FIXOperations.class)
@Sources(FIXMessageListener.class)
@ConnectionProviders(FIXConnectionProvider.class)
public class FIXConfiguration {

    // Note: No @Parameter annotations here to avoid duplicate SDK metadata
    // Parameters are defined in FIXConnectionProvider
    
    private String beginString;
    private String senderCompId;
    private String targetCompId;
    private int heartbeatInterval;
    private boolean resetSequenceOnLogon;
    private boolean validateChecksum;

    public String getBeginString() {
        return beginString;
    }
    
    public void setBeginString(String beginString) {
        this.beginString = beginString;
    }

    public String getSenderCompId() {
        return senderCompId;
    }
    
    public void setSenderCompId(String senderCompId) {
        this.senderCompId = senderCompId;
    }

    public String getTargetCompId() {
        return targetCompId;
    }
    
    public void setTargetCompId(String targetCompId) {
        this.targetCompId = targetCompId;
    }

    public int getHeartbeatInterval() {
        return heartbeatInterval;
    }
    
    public void setHeartbeatInterval(int heartbeatInterval) {
        this.heartbeatInterval = heartbeatInterval;
    }

    public boolean isResetSequenceOnLogon() {
        return resetSequenceOnLogon;
    }
    
    public void setResetSequenceOnLogon(boolean resetSequenceOnLogon) {
        this.resetSequenceOnLogon = resetSequenceOnLogon;
    }

    public boolean isValidateChecksum() {
        return validateChecksum;
    }
    
    public void setValidateChecksum(boolean validateChecksum) {
        this.validateChecksum = validateChecksum;
    }
}
