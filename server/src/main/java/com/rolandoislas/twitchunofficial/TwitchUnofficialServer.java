/*
 * Copyright (c) 2017 Rolando Islas. All Rights Reserved
 *
 */

package com.rolandoislas.twitchunofficial;

import com.google.common.collect.Lists;
import com.google.gson.JsonSyntaxException;
import com.rolandoislas.twitchunofficial.data.annotation.NotCached;
import org.jetbrains.annotations.Nullable;
import spark.ModelAndView;
import spark.Request;
import spark.Response;
import spark.template.handlebars.HandlebarsTemplateEngine;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.List;
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
                                                               boolean replaceOnlyDouble, boolean keepOriginalBreaks) {
        InputStream fileStream = ClassLoader.getSystemResourceAsStream("templates/partial/" + fileName);
        String fileString = new BufferedReader(new InputStreamReader(fileStream))
                .lines().collect(Collectors.joining("\n"));
        fileString = fileString
                .replaceAll("<", "&lt;")
                .replaceAll(replaceOnlyDouble ? "\n\n" : "\n",
                        replaceOnlyDouble ?
                                keepOriginalBreaks ? "\n\n<br><br>" : "<br><br>" :
                                keepOriginalBreaks ? "\n<br>" : "<br>");
        return fileString;
    }

    /**
     * @see #getPartialWithHtmlLineBreaks(String, boolean, boolean)
     */
    private static String getPartialWithHtmlLineBreaks(String fileName,
                                                       @SuppressWarnings("SameParameterValue")
                                                               boolean replaceOnlyDouble) {
        return getPartialWithHtmlLineBreaks(fileName, replaceOnlyDouble, false);
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
        // License
        String[] license = getPartialWithHtmlLineBreaks("license.txt", true,
                true).split("\n");
        StringBuilder licenseStringBuilder = new StringBuilder();
        int line = 0;
        for (String licenseString : license) {
            if (line == 0)
                licenseStringBuilder.append(String.format("[%s]\n", licenseString));
            else
                licenseStringBuilder.append(String.format("%s\n", licenseString));
            line++;
        }
        model.put("text_license", parseBrackets(licenseStringBuilder.toString()));
        // Release Notes
        model.put("text_release_notes", parseBrackets(getPartialWithHtmlLineBreaks("release_notes.txt",
                false, true)));
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
        model.put("text_website",
                parseBrackets(getPartialWithHtmlLineBreaks("third_party.txt", true,
                        true)));
        model.put("text_roku",
                parseBrackets(getPartialWithHtmlLineBreaks("third_party_roku.txt", true,
                        true)));
        model.put("text_apple_tv",
                parseBrackets(getPartialWithHtmlLineBreaks("third_party_apple_tv.txt", true,
                        true)));
        return new HandlebarsTemplateEngine().render(new ModelAndView(model, "info_oss.hbs"));
    }

    /**
     * Parse text as a list of items. Each item should start with a title wrapped in brackets (e.g. [foo bar])
     * @param text text
     * @return html
     */
    private static String parseBrackets(String text) {
        StringBuilder html = new StringBuilder();
        String[] lines = text.split("(?<=\n|<br>)");
        StringBuilder item = null;
        for (int lineIndex = 0; lineIndex < lines.length; lineIndex++) {
            String line = lines[lineIndex];
            if (line.startsWith("[") || lineIndex == lines.length - 1) {
                if (item != null) {
                    item.append("</p>");
                    item.append("</div>");
                    html.append(item);
                }
                item = new StringBuilder();
                item.append("<div class=\"dep-item dep-closed\">");
                item.append("<h4 class=\"dep-header\">");
                item.append(line.replace("[", "").replace("]", ""));
                item.append("</h4>");
                item.append("<p class=\"dep-text\">");
            }
            else if (item != null) {
                item.append(line);
            }
        }
        return html.toString();
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

    /**
     * Handle global page rules
     * @param request request
     * @param response response
     */
    static void handleGlobalPageRules(Request request, Response response) {
        // Root to WWW
        if (request.host() != null) {
            String[] colonSplit = request.host().split(":");
            if (colonSplit.length > 0) {
                String[] host = colonSplit[0].split("\\.");
                // Exactly 2 for TLD
                if (host.length == 2) {
                    String port = null;
                    if (colonSplit.length > 1)
                        port = colonSplit[1];
                    List<String> hostArray = Lists.newArrayList(host);
                    hostArray.add(0, "www");
                    response.redirect(constructUrl(request.scheme(), hostArray.toArray(new String[0]), port,
                            request.pathInfo(), request.queryString()));
                    return;
                }
            }
        }
        // HTTP to HTTPS
        if (request.scheme().equalsIgnoreCase("http") && TwitchUnofficial.redirectToHttps) {
            String redirect = request.url() + (request.queryString() != null ? "?" + request.queryString() : "");
            response.redirect(redirect.replace("http://", "https://"));
            return;
        }
        // Redirect paths with a trailing slash
        if (request.pathInfo().endsWith("/") && !request.pathInfo().equals("/"))
            response.redirect(request.pathInfo().substring(0, request.pathInfo().length() - 1));
    }

    /**
     * Construct a url from its pars
     * @param scheme url scheme e.g https
     * @param host array of host parts not including dots, colons or ports
     * @param port Port to use
     * @param path path beginning with a forward slash
     * @param queryString query string excluding a question mark
     */
    private static String constructUrl(String scheme, String[] host, @Nullable String port,
                                     @Nullable String path, @Nullable String queryString) {
        StringBuilder hostString = new StringBuilder();
        for (String hostPart : host)
            hostString.append(hostPart).append(".");
        hostString.deleteCharAt(hostString.lastIndexOf("."));
        String portString = port != null ? ":" + port : "";
        String queryAppend = queryString != null ? "?" + queryString : "";
        String pathString = path != null ? path : "";
        return String.format("%s://%s%s%s%s", scheme, hostString.toString(), portString, pathString, queryAppend);
    }

    /**
     * Get the stream quality page
     * @param request request
     * @param response response
     * @return stream quality page
     */
    static String getInfoStreamQuality(Request request, Response response) {
        Map<String, Object> model = new HashMap<>();
        try {
            String qualities = TwitchUnofficialApi.gson.toJson(TwitchedApi.getStreamQualities());
            model.put("qualities", qualities);
        }
        catch (JsonSyntaxException ignore) {}
        return new HandlebarsTemplateEngine().render(new ModelAndView(model, "info_streamquality.hbs"));
    }
}
