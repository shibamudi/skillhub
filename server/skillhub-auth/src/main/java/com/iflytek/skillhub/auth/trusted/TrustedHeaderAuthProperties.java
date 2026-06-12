package com.iflytek.skillhub.auth.trusted;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for trusted header authentication.
 *
 * <p>When enabled, the system trusts user identity information from HTTP headers
 * set by a reverse proxy (e.g., Traefik ForwardAuth).
 */
@ConfigurationProperties(prefix = "skillhub.auth.trusted-header")
public class TrustedHeaderAuthProperties {

    private boolean enabled = false;
    private String userIdHeader = "X-Forwarded-User";
    private String emailHeader = "X-Forwarded-Email";
    private String displayNameHeader = "X-Forwarded-Name";
    private boolean autoProvision = true;
    private String logoutUrl;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getUserIdHeader() {
        return userIdHeader;
    }

    public void setUserIdHeader(String userIdHeader) {
        this.userIdHeader = userIdHeader;
    }

    public String getEmailHeader() {
        return emailHeader;
    }

    public void setEmailHeader(String emailHeader) {
        this.emailHeader = emailHeader;
    }

    public String getDisplayNameHeader() {
        return displayNameHeader;
    }

    public void setDisplayNameHeader(String displayNameHeader) {
        this.displayNameHeader = displayNameHeader;
    }

    public boolean isAutoProvision() {
        return autoProvision;
    }

    public void setAutoProvision(boolean autoProvision) {
        this.autoProvision = autoProvision;
    }

    public String getLogoutUrl() {
        return logoutUrl;
    }

    public void setLogoutUrl(String logoutUrl) {
        this.logoutUrl = logoutUrl;
    }
}
