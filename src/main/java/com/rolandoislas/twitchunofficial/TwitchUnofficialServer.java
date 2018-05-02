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

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

class TwitchUnofficialServer {

    /**
     * Get text from a partial file and convert line breaks to HTML line breaks
     * This only replaces UNIX line breaks (\n)
     * @param fileName name of partial
     * @param replaceOnlyDouble only replace double line breaks "\n\n"
     * @return text with html break tags
     */
    private static String getPartialWithHtmlLineBreaks(String fileName,
                                                       @SuppressWarnings("SameParameterValue")
                                                               boolean replaceOnlyDouble) {
        InputStream fileStream = ClassLoader.getSystemResourceAsStream("templates/partial/" + fileName);
        String fileString = new BufferedReader(new InputStreamReader(fileStream))
                .lines().collect(Collectors.joining("\n"));
        fileString = fileString
                .replaceAll("<", "&lt;")
                .replaceAll(replaceOnlyDouble ? "\n\n" : "\n", replaceOnlyDouble ? "<br><br>" : "<br>");
        return fileString;
    }

    /**
     * Get the website root
     * @param request request
     * @param response response
     * @return root html
     */
    @NotCached
    static String getIndex(@SuppressWarnings("unused") Request request, @SuppressWarnings("unused") Response response) {
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
    static String getLinkCallback(Request request, @SuppressWarnings("unused") Response response) {
        // Check error
        String error = request.queryParams("error");
        Map<String, Object> model = new HashMap<>();
        if (error != null)
            model.put("error", error.equals("access_denied") ? "Twitch login was canceled." : "Twitch login failed.");
        // Check for authorization code flow
        String authCode = request.queryParams("code");
        String state = request.queryParams("state");
        if (authCode != null && state != null) {
            if (!TwitchedApi.requestAccessToken(request, authCode, state))
                model.put("error", "The link code has expired.");
        }
        else
            model.put("send_token_xhr", true);
        return new HandlebarsTemplateEngine().render(new ModelAndView(model, "link_complete.hbs"));
    }

    /**
     * Get the info index page
     * @param request request
     * @param response response
     * @return html
     */
    static String getInfoIndex(@SuppressWarnings("unused") Request request,
                               @SuppressWarnings("unused") Response response) {
        Map<String, Object> model = new HashMap<>();
        model.put("text", getPartialWithHtmlLineBreaks("license.txt", true));
        return new HandlebarsTemplateEngine().render(new ModelAndView(model, "info.hbs"));
    }

    /**
     * Get OSS info page
     * @param request request
     * @param response response
     * @return html
     */
    static String getInfoOss(@SuppressWarnings("unused") Request request,
                             @SuppressWarnings("unused") Response response) {
        Map<String, Object> model = new HashMap<>();
        model.put("text_website", getPartialWithHtmlLineBreaks("third_party.txt", true));
        model.put("text_roku", getPartialWithHtmlLineBreaks("third_party_roku.txt", true));
        model.put("text_apple_tv", getPartialWithHtmlLineBreaks("third_party_apple_tv.txt", true));
        return new HandlebarsTemplateEngine().render(new ModelAndView(model, "info_oss.hbs"));
    }

    /**
     * Get privacy info page
     * @param request request
     * @param response response
     * @return html
     */
    static String getInfoPrivacy(@SuppressWarnings("unused") Request request,
                                 @SuppressWarnings("unused") Response response) {
        Map<String, Object> model = new HashMap<>();
        return new HandlebarsTemplateEngine().render(new ModelAndView(model, "info_privacy.hbs"));
    }

    /**
     * Get the extension page
     * @param request request
     * @param response response
     * @return html
     */
    static String getExtensionIndex(@SuppressWarnings("unused") Request request,
                                    @SuppressWarnings("unused") Response response) {
        Map<String, Object> model = new HashMap<>();
        return new HandlebarsTemplateEngine().render(new ModelAndView(model, "extension.hbs"));
    }

    /**
     * Get the support page
     * @param request request
     * @param response response
     * @return html
     */
    static String getSupportIndex(@SuppressWarnings("unused") Request request,
                                  @SuppressWarnings("unused") Response response) {
        Map<String, Object> model = new HashMap<>();
        return new HandlebarsTemplateEngine().render(new ModelAndView(model, "support.hbs"));
    }

    /**
     * Get the mobile app page
     * @param request request
     * @param response response
     * @return html
     */
    static String getAppIndex(@SuppressWarnings("unused") Request request,
                              @SuppressWarnings("unused") Response response) {
        Map<String, Object> model = new HashMap<>();
        return new HandlebarsTemplateEngine().render(new ModelAndView(model, "app.hbs"));
    }
}
