package com.rolandoislas.twitchunofficial.data.model;

import org.jetbrains.annotations.Nullable;

public class TwitchCredentials {
    private final String clientId;
    private final String clientSecret;
    private final String appToken;

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

    public String getAppToken() {
        return appToken;
    }
}
