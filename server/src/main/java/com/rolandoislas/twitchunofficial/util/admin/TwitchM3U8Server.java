package com.rolandoislas.twitchunofficial.util.admin;

import com.rolandoislas.twitchunofficial.TwitchUnofficialApi;
import com.rolandoislas.twitchunofficial.TwitchedApi;
import com.rolandoislas.twitchunofficial.data.model.Playlist;
import spark.ModelAndView;
import spark.Request;
import spark.Response;
import spark.template.handlebars.HandlebarsTemplateEngine;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static spark.Spark.halt;

public class TwitchM3U8Server {
    public static String getIndex(Request request, Response response) {
        if (!TwitchedApi.isDevApiEnabled())
            return null;
        Map<String, Object> model = new HashMap<>();
        return new HandlebarsTemplateEngine().render(new ModelAndView(model, "admin/m3u8.hbs"));
    }

    public static String postStream(Request request, Response response) {
        if (!TwitchedApi.isDevApiEnabled())
            return null;

        String login = request.queryParams("login");
        if (login == null || login.isEmpty())
            throw halt(400, "Missing login");

        Map<String, Object> model = new HashMap<>();
        List<Playlist> playlists = TwitchUnofficialApi.getHlsPlaylists(login);
        Map<String, String> generated = new HashMap<>();
        for (Playlist playlist : playlists) {
            generated.put(String.format("%d.%d", playlist.getQuality(), playlist.getFps()), playlist.getLines().get(2));
        }
        model.put("generated", generated);
        model.put("login", login);
        return new HandlebarsTemplateEngine().render(new ModelAndView(model, "admin/m3u8.hbs"));
    }
}
