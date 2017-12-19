/*
 * Copyright (c) 2017 Rolando Islas. All Rights Reserved
 *
 */

package com.rolandoislas.twitchunofficial;

import com.rolandoislas.twitchunofficial.data.annotation.NotCached;
import spark.ModelAndView;
import spark.Request;
import spark.Response;
import spark.template.handlebars.HandlebarsTemplateEngine;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

class TwitchUnofficialServer {
    /**
     * Get the website root
     * @param request request
     * @param response response
     * @return root html
     */
    @NotCached
    static String getIndex(Request request, Response response) {
        Map<String, Object> model = new HashMap<>();
        return new HandlebarsTemplateEngine().render(new ModelAndView(model, "index.hbs"));
    }

    /**
     * Get link page root
     * @param request request
     * @param response response
     * @return html
     */
    @NotCached
    static String getLinkIndex(Request request, Response response) {
        String linkId = request.queryParams("link_id");
        // Show default page
        if (linkId == null) {
            Map<String, Object> model = new HashMap<>();
            return new HandlebarsTemplateEngine().render(new ModelAndView(model, "link.hbs"));
        }
        // Check if id is valid
        if (!TwitchedApi.isLinkCodeValid(linkId)) {
            Map<String, Object> model = new HashMap<>();
            model.put("link_invalid", true);
            return new HandlebarsTemplateEngine().render(new ModelAndView(model, "link.hbs"));
        }
        // Redirect
        return TwitchedApi.redirectToTwitchOauth(linkId, request, response);
    }

    /**
     * Handle a callback from an Oauth endpoint
     * @param request request
     * @param response response
     * @return html
     */
    @NotCached
    static String getLinkCallback(Request request, Response response) {
        // Params
        String error = request.queryParams("error");
        // Return
        Map<String, Object> model = new HashMap<>();
        if (error != null)
            model.put("error", error.equals("access_denied") ? "Twitch login was canceled." : "Twitch login failed.");
        return new HandlebarsTemplateEngine().render(new ModelAndView(model, "link_complete.hbs"));
    }
}
