/*
 * Copyright (c) 2017 Rolando Islas. All Rights Reserved
 *
 */

package com.rolandoislas.twitchunofficial.util.twitch.helix;

import com.google.gson.JsonObject;
import org.jetbrains.annotations.Nullable;

import java.util.Base64;

public class Pagination {
    private String cursor;

    public Pagination(@Nullable Long before, long after) {
        JsonObject afterJson = null;
        JsonObject beforeJson = null;
        if (before != null && before >= 0) {
            beforeJson = new JsonObject();
            beforeJson.addProperty("Offset", before);
            afterJson = new JsonObject();
            afterJson.addProperty("Offset", after);
        }
        JsonObject cursorJson = new JsonObject();
        cursorJson.add("a", afterJson);
        cursorJson.add("b", beforeJson);
        if (beforeJson != null)
            cursor = Base64.getEncoder().withoutPadding().encodeToString(cursorJson.toString().getBytes());
    }

    @Override
    public String toString() {
        if (cursor != null)
            return new String(Base64.getDecoder().decode(cursor));
        else
            return "";
    }

    @Nullable
    public String getCursor() {
        return cursor;
    }
}
