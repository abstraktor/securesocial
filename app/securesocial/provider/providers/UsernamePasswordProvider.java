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
 *
 */
package securesocial.provider.providers;

import controllers.securesocial.SecureSocial;
import play.data.validation.Validation;
import play.i18n.Messages;
import play.mvc.Scope;
import securesocial.provider.*;
import securesocial.utils.SecureSocialPasswordHasher;

import java.util.Map;

/**
 * A provider for username and password authentication
 */
public class UsernamePasswordProvider extends IdentityProvider
{
    private static final String USER_NAME = "userName";
    private static final String PASSWORD = "password";
    private static final String SECURESOCIAL_REQUIRED = "securesocial.required";
    private static final String SECURESOCIAL_ACCOUNT_NOT_ACTIVE = "securesocial.accountNotActive";
    private static final String SECURESOCIAL_WRONG_USER_PASS = "securesocial.wrongUserPass";

    public UsernamePasswordProvider() {
        super(ProviderType.userpass, AuthenticationMethod.USER_PASSWORD);
    }

    @Override
    protected SocialUser doAuth(Map<String, Object> authContext) {
        //
        final String userName = Scope.Params.current().get(USER_NAME);
        final String password = Scope.Params.current().get(PASSWORD);

        boolean hasErrors = false;
        Validation validation = Validation.current();
        if ( userName == null || userName.trim().length() == 0 ) {
            validation.addError(USER_NAME, Messages.get(SECURESOCIAL_REQUIRED));
            hasErrors = true;
        }

        if ( password == null || password.trim().length() == 0 ) {
            validation.addError(PASSWORD, Messages.get(SECURESOCIAL_REQUIRED));
            hasErrors = true;
        }

        //
        UserId id = new UserId();
        id.id = Scope.Params.current().get(USER_NAME);
        id.provider = ProviderType.userpass;
        SocialUser user = UserService.find(id);

        // Deactivated because
        // for timum, we verify this in
        // the AuthenticationAuthorisationInterceptor
        // if ( !user.isEmailVerified) {
        //     flash.error(Messages.get(SECURESOCIAL_ACCOUNT_NOT_ACTIVE));
        //     SecureSocial.login();
        // }
        
        if (!hasErrors  && (user == null || !passwordMatches(Scope.Params.current().get(PASSWORD), user.password))) {
            validation.addError(USER_NAME, Messages.get(SECURESOCIAL_REQUIRED));
            validation.addError(PASSWORD, Messages.get(SECURESOCIAL_REQUIRED));
            hasErrors = true;
        }

        if ( hasErrors ) {
            Scope.Flash.current().put(USER_NAME, userName);
            validation.keep();
            Scope.Flash.current().error(Messages.get(SECURESOCIAL_WRONG_USER_PASS));
            throw new AuthenticationException();
        }
        return user;
    }

    private boolean passwordMatches(String password, String userPassword) {
        return SecureSocialPasswordHasher.verifyPasswordHash(password, userPassword);
    }

    @Override
    protected void fillProfile(SocialUser user, Map<String, Object> authContext) {
        // there's nothing to do here, since the user is being loaded from the DB it should already have
        // all the required fields set.
    }
}
