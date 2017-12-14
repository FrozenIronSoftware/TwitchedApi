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
        JsonObject afterJson = new JsonObject();
        afterJson.addProperty("Offset", after);
        JsonObject beforeJson = null;
        if (before != null) {
            beforeJson = new JsonObject();
            beforeJson.addProperty("Offset", before);
        }
        JsonObject cursorJson = new JsonObject();
        cursorJson.add("a", afterJson);
        cursorJson.add("b", beforeJson);
        cursor = Base64.getEncoder().withoutPadding().encodeToString(cursorJson.toString().getBytes());
    }

    @Override
    public String toString() {
        return new String(Base64.getDecoder().decode(cursor));
    }

    public String getCursor() {
        return cursor;
    }
}
