package com.rolandoislas.twitchunofficial.data.model;

public class StreamStatusQueue implements QueueItem {
    private final String userIdentifier;
    private final Type type;

    public String getUserIdentifier() {
        return userIdentifier;
    }

    public Type getType() {
        return type;
    }

    public StreamStatusQueue(String userIdentifier, Type type) {
        this.userIdentifier = userIdentifier;
        this.type = type;
    }

    public enum Type {
        ID, LOGIN
    }
}
