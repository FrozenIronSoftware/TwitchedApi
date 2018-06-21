package com.rolandoislas.twitchunofficial.data.model;

import org.jetbrains.annotations.Nullable;

public class TwitchCredentials {
    private final String clientId;
    private final String clientSecret;
    @Nullable private String appToken;

    public TwitchCredentials(String clientId, String clientSecret, @Nullable String appToken) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.appToken = appToken;
    }

    public String getClientId() {
        return clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    @Nullable
    public String getAppToken() {
        return appToken;
    }

    public void setAppToken(@Nullable String appToken) {
        this.appToken = appToken;
    }
}
