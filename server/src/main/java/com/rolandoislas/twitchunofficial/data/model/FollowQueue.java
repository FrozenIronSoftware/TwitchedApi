package com.rolandoislas.twitchunofficial.data.model;

import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class FollowQueue {
    private final FollowType type;
    private String userId;

    public FollowQueue(String userId, FollowType type) {
        this.userId = userId;
        this.type = type;
    }

    @Nullable
    public String getUserId() {
        return userId;
    }

    @Nullable
    public FollowType getFollowType() {
        return type;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof FollowQueue) {
            FollowQueue compare = (FollowQueue) obj;
            return  Objects.equals(compare.getUserId(), getUserId()) &&
                    Objects.equals(compare.getFollowType(), getFollowType());
        }
        else
            return false;
    }

    public enum FollowType {
        CHANNEL, GAME
    }
}
