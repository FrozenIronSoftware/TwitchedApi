package com.rolandoislas.twitchunofficial.util.twitch;

import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

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
    private List<String> scope;

    /**
     * Constructor for access token
     * @param accessToken access token
     * @param refreshToken refresh token
     */
    public AccessToken(String accessToken, @Nullable String refreshToken) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.expiresIn = 0;
        this.scope = new ArrayList<>();
    }

    private String getAccessToken() {
        return accessToken;
    }

    private String getRefreshToken() {
        return refreshToken;
    }

    private long getExpiresIn() {
        return expiresIn;
    }

    private List<String> getScope() {
        return scope;
    }

    public JsonObject toJsonObject() {
        JsonObject ret = new JsonObject();
        ret.addProperty("complete", true);
        ret.addProperty("token", getAccessToken());
        ret.addProperty("refresh_token", getRefreshToken());
        ret.addProperty("expires_in", getExpiresIn());
        StringBuilder scopes = new StringBuilder();
        for (String scope : getScope())
            scopes.append(scope).append(" ");
        int spaceIndex = scopes.lastIndexOf(" ");
        if (spaceIndex > -1)
            scopes.deleteCharAt(spaceIndex);
        ret.addProperty("scope", scopes.toString());
        return ret;
    }
}
