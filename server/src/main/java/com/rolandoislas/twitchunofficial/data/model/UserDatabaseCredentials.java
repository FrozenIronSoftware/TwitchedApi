package com.rolandoislas.twitchunofficial.data.model;

/**
 * Database stored user credentials
 */
public class UserDatabaseCredentials {
    private long id;
    private String username;
    private String hash;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getHash() {
        return hash;
    }

    public void setHash(String hash) {
        this.hash = hash;
    }
}
