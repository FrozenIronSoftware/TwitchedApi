package com.rolandoislas.twitchunofficial.util.admin;

import spark.ModelAndView;
import spark.Request;
import spark.Response;
import spark.template.handlebars.HandlebarsTemplateEngine;

import java.util.HashMap;
import java.util.Map;

public class TwitchedStreamQualityServer {
    /**
     * Get stream quality index
     * @param request request
     * @param response response
     * @return html
     */
    public static String getIndex(Request request, Response response) {
        TwitchedAdminServer.checkAuth(request, response);
        Map<String, Object> model = new HashMap<>();
        model.put("admin", true);
        // TODO move client id to env var
        model.put("client_id",
                "admin-FnGbFbZprhDmYvw2tGU4ispyyMGgq2D6nEKVVBmzfY6uma4CkdMydz0TsAOScKrYpgFCram4Zgx5svVk7HLnihBEbj7uEN");
        return new HandlebarsTemplateEngine().render(new ModelAndView(model, "admin/streamquality.hbs"));
    }
}
