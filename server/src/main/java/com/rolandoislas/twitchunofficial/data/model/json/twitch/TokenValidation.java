package com.rolandoislas.twitchunofficial.data.model.json.twitch;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Structure for token validation responses
 */
public class TokenValidation {
    @SerializedName("client_id")
    private String clientId;
    private String login;
    private List<String> scopes;
    @SerializedName("user_id")
    private String userId;

    public String getUserId() {
        return userId;
    }

    public String getLogin() {
        return login;
    }
}
