package com.hhclicker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "security.encryption")
public class EncryptionProperties {
    private String hhSessionKey;

    public String getHhSessionKey() { return hhSessionKey; }
    public void setHhSessionKey(String hhSessionKey) { this.hhSessionKey = hhSessionKey; }
}
