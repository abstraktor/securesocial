/**
* Copyright 2011 Jorge Aliss (jaliss at gmail dot com) - twitter: @jaliss
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*     http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package controllers.securesocial;

import play.Logger;
import play.Play;
import play.i18n.Messages;
import play.libs.OAuth;
import play.mvc.Before;
import play.mvc.Controller;
import securesocial.provider.*;

import java.util.Collection;

/**
 * This is the main controller for the SecureSocial module.
 *
 */
public class SecureSocial extends Controller {

    private static final String USER_COOKIE = "securesocial.user";
    private static final String NETWORK_COOKIE = "securesocial.network";
    private static final String ORIGINAL_URL = "originalUrl";
    private static final String GET = "GET";
    private static final String ROOT = "/";
    static final String USER = "user";
    private static final String ERROR = "error";
    private static final String SECURESOCIAL_AUTH_ERROR = "securesocial.authError";
    private static final String SECURESOCIAL_LOGIN_REDIRECT = "securesocial.login.redirect";
    private static final String SECURESOCIAL_LOGOUT_REDIRECT = "securesocial.logout.redirect";
    private static final String SECURESOCIAL_SECURE_SOCIAL_LOGIN = "securesocial.SecureSocial.login";
    private static final String SECURESOCIAL_UNAUTHORIZED_ACTION = "securesocial.unauthorized.action";


    // Strings used from multiple places
    public static final String USERNAME = "username";
    public static final String EMAIL = "email";
    public static final String UUID = "uuid";
    public static final String NEW_PASSWORD = "newPassword";
    public static final String CONFIRM_PASSWORD = "confirmPassword";
    public static final String CURRENT_PASSWORD = "currentPassword";


    /**
     * Checks if there is a user logged in and redirects to the login page if not.
     */
    @Before(unless={"login", "authenticate", "authByPost", "authByPostWithFormat", "logout"})
    static void checkAccess() throws Throwable
    {
        final UserId userId = getUserId();

        if ( userId == null ) {
            final String originalUrl = request.method.equals(GET) ? request.url : ROOT;
            flash.put(ORIGINAL_URL, originalUrl);
	    unauthorizedAction();
        } else {
            final SocialUser user = loadCurrentUser(userId);
            if ( user == null ) {
               // the user had the cookies but the UserService can't find it ...
               // it must have been erased, redirect to login again.
               clearUserId();
               unauthorizedAction();
           }
        }
    }


    static void unauthorizedAction() {
        final String action = Play.configuration.getProperty(SECURESOCIAL_UNAUTHORIZED_ACTION);
        if("forbidden".equals(action)) {
        	forbidden();
        }
        login();
    }


    static SocialUser loadCurrentUser() {
        UserId id = getUserId();
        final SocialUser user = id != null ? loadCurrentUser(id) : null;
        return user;
    }

    private static SocialUser loadCurrentUser(UserId userId) {
        SocialUser user = UserService.find(userId);

        if ( user != null ) {
            // if the user is using OAUTH1 or OPENID HYBRID OAUTH set the ServiceInfo
            // so the app using this module can access it easily to invoke the APIs.
            if ( user.authMethod == AuthenticationMethod.OAUTH1 || user.authMethod == AuthenticationMethod.OPENID_OAUTH_HYBRID ) {
                final OAuth.ServiceInfo sinfo;
                IdentityProvider provider = ProviderRegistry.get(user.id.provider);
                if ( user.authMethod == AuthenticationMethod.OAUTH1 ) {
                    sinfo = ((OAuth1Provider)provider).getServiceInfo();
                } else {
                    sinfo = ((OpenIDOAuthHybridProvider)provider).getServiceInfo();
                }
                user.serviceInfo = sinfo;
            }
            // make the user available in templates
            renderArgs.put(USER, user);
        }
        return user;
    }

    /**
     * Returns the current user. This method can be called from secured and non-secured controllers giving you the
     * chance to retrieve the logged in user if there is one.
     *
     * @return SocialUser the current user or null if no user is logged in.
     */
    public static SocialUser getCurrentUser() {
        // first, try to get it from the renderArgs since it should be there on secured controllers.
        SocialUser currentUser =  (SocialUser) renderArgs.get(USER);

        if ( currentUser == null  ) {
            // the call is being made from an unsecured controller
            // try to provide a current user if there is one in the session
            currentUser = loadCurrentUser();
        }
        return currentUser;
    }

    /**
     * Returns true if there is a user logged in or false otherwise.
     * @return a boolean
     */
    public static boolean isUserLoggedIn() {
        return getUserId() != null;
    }

    /*
     * Removes the SecureSocial cookies from the session.
     */
    private static void clearUserId() {
        session.remove(USER_COOKIE);
        session.remove(NETWORK_COOKIE);
    }

    /*
     * Sets the SecureSocial cookies in the session.
     */
    private static void setUserId(SocialUser user) {
        session.put(USER_COOKIE, user.id.id);
        session.put(NETWORK_COOKIE, user.id.provider.toString());
    }

    /*
     * Creates a UserId object from the values stored in the session.
     *
     * @see UserId
     * @returns  UserId the user id
     */
    private static UserId getUserId() {
        final String userId = session.get(USER_COOKIE);
        final String networkId = session.get(NETWORK_COOKIE);

        UserId id = null;

        if ( userId != null && networkId != null ) {
            id = new UserId();
            id.id = userId;
            id.provider = ProviderType.valueOf(networkId);
        }
        return id;
    }

    /**
     * The action for the login page.
     */
    public static void login() {
        if(params.get("origin") != null && flash.get("originalUrl") == null)
            flash.put("originalUrl", params.get("origin"));

        final Collection providers = ProviderRegistry.all();
        flash.keep(ORIGINAL_URL);
        boolean userPassEnabled = ProviderRegistry.get(ProviderType.userpass) != null;
        render(providers, userPassEnabled);
    }

    /**
     * The logout action.
     */
    public static void logout() {
        clearUserId();
        final String redirectTo = Play.configuration.getProperty(SECURESOCIAL_LOGOUT_REDIRECT, SECURESOCIAL_SECURE_SOCIAL_LOGIN);
        redirect(redirectTo);
    }

    /**
     * This is the entry point for all authentication requests from the login page.
     * The type is used to invoke the right provider.
     *
     * @param type   The provider type as selected by the user in the login page
     * @see ProviderType
     * @see IdentityProvider
     */
    public static void authenticate(ProviderType type) {
        doAuthenticate(type);
    }

    public static void authByPost(ProviderType type) {
        doAuthenticate(type);
    }

    public static void authByPostWithFormat(ProviderType type) {
        doAuthenticate(type);
    }

    private static void doAuthenticate(ProviderType type) {
        if ( type == null ) {
            Logger.error("Provider type was missing in request");
            // just throw a 404 error
            notFound();
        }
        flash.keep(ORIGINAL_URL);

        IdentityProvider provider = ProviderRegistry.get(type);
        String originalUrl = null;

        try {
            SocialUser user = provider.authenticate();
            setUserId(user);
            originalUrl = flash.get(ORIGINAL_URL);
        } catch ( Exception e ) {
            if("json".equals(request.format)){
                forbidden("");
            } else {
                e.printStackTrace();
                if ( flash.get(ERROR) == null ) {
                    flash.error(Messages.get(SECURESOCIAL_AUTH_ERROR));
                }
                flash.keep(ORIGINAL_URL);
                login();
            }
        }

        if("json".equals(request.format)){
            ok();
        } else {
            final String redirectTo = Play.configuration.getProperty(SECURESOCIAL_LOGIN_REDIRECT, ROOT);
            redirect( originalUrl != null ? originalUrl : redirectTo);
        }
    }

    /**
     * A helper class to integrate SecureSocial with the Deadbolt module.
     *
     * Basically the integration is done by calling SecureSocial.Deadbolt.beforeRoleCheck()
     * within the DeadboltHandler.beforeRoleCheck implementation.
     *
     * Eg:
     *
     * public class MyDeadboltHandler extends Controller implements DeadboltHandler
     * {
     *      try {
     *          SecureSocial.DeadboltHelper.beforeRoleCheck();
     *      } catch ( Throwable t) {
     *          // handle the exception in an application specific way
     *      }
     * }
     */
    public static class DeadboltHelper {
        public static void beforeRoleCheck() throws Throwable {
            checkAccess();
        }
    }
}
