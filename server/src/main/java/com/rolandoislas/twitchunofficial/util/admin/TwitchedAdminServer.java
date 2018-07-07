package com.rolandoislas.twitchunofficial.util.admin;

import com.google.common.hash.Hashing;
import com.google.gson.JsonSyntaxException;
import com.rolandoislas.twitchunofficial.data.model.UserDatabaseCredentials;
import com.rolandoislas.twitchunofficial.data.model.json.twitched.admin.AuthenticationData;
import com.rolandoislas.twitchunofficial.util.AuthUtil;
import com.rolandoislas.twitchunofficial.util.DatabaseUtil;
import org.eclipse.jetty.http.HttpStatus;
import org.mindrot.jbcrypt.BCrypt;
import spark.ModelAndView;
import spark.Request;
import spark.Response;
import spark.Session;
import spark.template.handlebars.HandlebarsTemplateEngine;

import java.util.HashMap;
import java.util.Map;

import static com.rolandoislas.twitchunofficial.TwitchUnofficialApi.gson;
import static spark.Spark.halt;

public class TwitchedAdminServer {
    /**
     * Return the login page
     * @param request request
     * @param response response
     * @return login page
     */
    public static String getLoginPage(Request request, Response response) {
        Map<String, Object> model = new HashMap<>();
        Session session = request.session(false);
        if (session != null) {
            boolean authenticatedAdmin = session.attribute("admin");
            if (authenticatedAdmin)
                model.put("admin", true);
        }
        return new HandlebarsTemplateEngine().render(new ModelAndView(model, "admin/login.hbs"));
    }

    /**
     * Return the logout page
     * @param request request
     * @param response response
     * @return logout page
     */
    public static String getLogoutPage(Request request, Response response) {
        Session session = request.session(false);
        if (session != null)
            session.invalidate();
        Map<String, Object> model = new HashMap<>();
        model.put("logout", true);
        return new HandlebarsTemplateEngine().render(new ModelAndView(model, "admin/login.hbs"));
    }

    /**
     * Log in with username and password
     * @param request request
     * @param response response
     * @return empty 200 response on success
     */
    public static String postLoginPage(Request request, Response response) {
        // Parse passed data
        AuthenticationData authenticationData;
        try {
            authenticationData = gson.fromJson(request.body(), AuthenticationData.class);
        }
        catch (JsonSyntaxException e) {
            throw halt(HttpStatus.BAD_REQUEST_400);
        }
        if (authenticationData.getUsername() == null || authenticationData.getUsername().isEmpty() ||
                authenticationData.getPassword() == null || authenticationData.getPassword().isEmpty())
            throw halt(HttpStatus.BAD_REQUEST_400);
        // Get data from database
        UserDatabaseCredentials userDatabaseCredentials = DatabaseUtil.getAdminUserData(authenticationData.getUsername());
        if (userDatabaseCredentials == null)
            throw halt(HttpStatus.UNAUTHORIZED_401);
        if (userDatabaseCredentials.getHash() == null || userDatabaseCredentials.getHash().isEmpty() ||
                userDatabaseCredentials.getUsername() == null || userDatabaseCredentials.getUsername().isEmpty())
            throw halt(HttpStatus.INTERNAL_SERVER_ERROR_500);
        // Compare data
        if (!BCrypt.checkpw(Hashing.sha256().hashString(authenticationData.getPassword()).toString(),
                userDatabaseCredentials.getHash()))
            throw halt(HttpStatus.UNAUTHORIZED_401);
        // Set authenticated session
        Session session = request.session();
        session.attribute("admin", true);
        return "";
    }

    /**
     * Check authentication and redirect to login page if not
     * @param request request
     * @param error if true, throw a 401 error instead of redirecting to the login page
     */
    public static void checkAuth(Request request, Response response, boolean error) {
        if (!AuthUtil.shouldAuthenticate())
            return;
        Session session = request.session(false);
        if (session == null || !((boolean) session.attribute("admin"))) {
            if (!error) {
                response.redirect(String.format("/admin/login?completion=%s", request.pathInfo()), HttpStatus.FOUND_302);
                throw halt(HttpStatus.FOUND_302);
            }
            else
                throw halt(HttpStatus.UNAUTHORIZED_401);
        }
    }

    /**
     * @see #checkAuth(Request, Response, boolean)
     */
    static void checkAuth(Request request, Response response) {
        checkAuth(request, response, false);
    }
}
