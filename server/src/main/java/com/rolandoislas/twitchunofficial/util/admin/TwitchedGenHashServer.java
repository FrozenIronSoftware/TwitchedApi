package com.rolandoislas.twitchunofficial.util.admin;

import com.rolandoislas.twitchunofficial.TwitchedApi;
import com.rolandoislas.twitchunofficial.util.AuthUtil;
import org.eclipse.jetty.http.HttpStatus;
import org.mindrot.jbcrypt.BCrypt;
import spark.ModelAndView;
import spark.Request;
import spark.Response;
import spark.template.handlebars.HandlebarsTemplateEngine;

import java.util.HashMap;
import java.util.Map;

import static spark.Spark.halt;

public class TwitchedGenHashServer {
    /**
     * Return the index page
     * @param request request
     * @param response response
     * @return html
     */
    public static String getIndex(Request request, Response response) {
        if (!TwitchedApi.isDevApiEnabled())
            return null;
        Map<String, Object> model = new HashMap<>();
        return new HandlebarsTemplateEngine().render(new ModelAndView(model, "admin/genhash.hbs"));
    }

    /**
     * Handle user name and password post
     * @param request request
     * @param response response
     * @return html
     */
    public static String postCredentials(Request request, Response response) {
        if (!TwitchedApi.isDevApiEnabled())
            return null;
        if (request.body() == null || request.body().isEmpty())
            throw halt(HttpStatus.BAD_REQUEST_400);
        String[] params = request.body().split("&");
        String password = null;
        for (String param : params) {
            String[] split = param.split("=");
            if (split.length != 2)
                throw halt(HttpStatus.BAD_REQUEST_400);
            String key = split[0];
            String value = split[1];
            if (key.equals("password"))
                password = value;
        }
        if (password == null || password.isEmpty() || password.length() > 500)
            throw halt(HttpStatus.BAD_REQUEST_400);
        String hash = BCrypt.hashpw(password, BCrypt.gensalt(AuthUtil.BCRYPT_ROUNDS));
        Map<String, Object> model = new HashMap<>();
        model.put("hash", hash);
        model.put("generated", true);
        return new HandlebarsTemplateEngine().render(new ModelAndView(model, "admin/genhash.hbs"));
    }
}
