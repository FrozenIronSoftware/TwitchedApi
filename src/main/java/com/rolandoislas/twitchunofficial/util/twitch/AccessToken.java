package com.rolandoislas.twitchunofficial.util.twitch;

import com.google.gson.annotations.SerializedName;
import org.jetbrains.annotations.Nullable;

/**
 * Class representing the JSON access token response from twitch
 */
public class AccessToken {
    @SerializedName(value = "access_token", alternate = {"token"})
    private String accessToken;
    @SerializedName("refresh_token")
    private String refreshToken;
    @SerializedName("expires_in")
    private long expiresIn;
    @SerializedName("scope")
    private String scope;

    /**
     * Constructor for access token
     * @param accessToken access token
     * @param refreshToken refresh token
     */
    public AccessToken(String accessToken, @Nullable String refreshToken) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.expiresIn = 0;
        this.scope = "";
    }

    public String getAccessToken() {
        return accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public long getExpiresIn() {
        return expiresIn;
    }

    public String getScope() {
        return scope;
    }
}
