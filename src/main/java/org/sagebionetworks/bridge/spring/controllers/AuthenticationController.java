package org.sagebionetworks.bridge.spring.controllers;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.sagebionetworks.bridge.BridgeConstants.CLEAR_SITE_DATA_HEADER;
import static org.sagebionetworks.bridge.BridgeConstants.CLEAR_SITE_DATA_VALUE;
import static org.sagebionetworks.bridge.BridgeConstants.APP_ACCESS_EXCEPTION_MSG;
import static org.sagebionetworks.bridge.BridgeConstants.APP_ID_PROPERTY;
import static org.sagebionetworks.bridge.BridgeConstants.STUDY_PROPERTY;
import static org.sagebionetworks.bridge.Roles.SUPERADMIN;

import javax.servlet.http.Cookie;

import com.fasterxml.jackson.databind.JsonNode;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.ConsentRequiredException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.json.JsonUtils;
import org.sagebionetworks.bridge.models.CriteriaContext;
import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.accounts.PasswordReset;
import org.sagebionetworks.bridge.models.accounts.SignIn;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.accounts.UserSessionInfo;
import org.sagebionetworks.bridge.models.accounts.Verification;
import org.sagebionetworks.bridge.models.apps.App;
import org.sagebionetworks.bridge.models.oauth.OAuthAuthorizationToken;
import org.sagebionetworks.bridge.services.AccountWorkflowService;
import org.sagebionetworks.bridge.services.AuthenticationService.ChannelType;

@CrossOrigin
@RestController
public class AuthenticationController extends BaseController {

    private AccountWorkflowService accountWorkflowService;
    
    @Autowired
    final void setAccountWorkflowService(AccountWorkflowService accountWorkflowService) {
        this.accountWorkflowService = accountWorkflowService;
    }

    @PostMapping("/v3/auth/email")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public StatusMessage requestEmailSignIn() { 
        SignIn signInRequest = parseJson(SignIn.class);
        getMetrics().setAppId(signInRequest.getAppId());

        String userId = accountWorkflowService.requestEmailSignIn(signInRequest);
        if (userId != null) {
            getMetrics().setUserId(userId);
        }

        return new StatusMessage("Email sent.");
    }

    @PostMapping("/v3/auth/email/signIn")
    public JsonNode emailSignIn() { 
        SignIn signInRequest = parseJson(SignIn.class);

        if (isBlank(signInRequest.getAppId())) {
            throw new BadRequestException("App ID is required.");
        }
        getMetrics().setAppId(signInRequest.getAppId());

        App app = appService.getApp(signInRequest.getAppId());
        verifySupportedVersionOrThrowException(app);
        
        CriteriaContext context = getCriteriaContext(app.getIdentifier());
        UserSession session = null;
        try {
            session = authenticationService.emailSignIn(context, signInRequest);
        } catch(ConsentRequiredException e) {
            setCookieAndRecordMetrics(e.getUserSession());
            throw e;
        }
        setCookieAndRecordMetrics(session);

        return UserSessionInfo.toJSON(session);
    }
    
    @PostMapping("/v3/auth/phone")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public StatusMessage requestPhoneSignIn() {
        SignIn signInRequest = parseJson(SignIn.class);
        getMetrics().setAppId(signInRequest.getAppId());

        String userId = accountWorkflowService.requestPhoneSignIn(signInRequest);
        if (userId != null) {
            getMetrics().setUserId(userId);
        }

        return new StatusMessage("Message sent.");
    }

    @PostMapping("/v3/auth/phone/signIn")
    public JsonNode phoneSignIn() {
        SignIn signInRequest = parseJson(SignIn.class);

        if (isBlank(signInRequest.getAppId())) {
            throw new BadRequestException("App ID is required.");
        }
        getMetrics().setAppId(signInRequest.getAppId());

        App app = appService.getApp(signInRequest.getAppId());
        verifySupportedVersionOrThrowException(app);
        
        CriteriaContext context = getCriteriaContext(app.getIdentifier());
        
        UserSession session = null;
        try {
            session = authenticationService.phoneSignIn(context, signInRequest);
        } catch(ConsentRequiredException e) {
            setCookieAndRecordMetrics(e.getUserSession());
            throw e;
        }
        setCookieAndRecordMetrics(session);

        return UserSessionInfo.toJSON(session);
    }
    
    @PostMapping("/v4/auth/signIn")
    public JsonNode signIn() {
        SignIn signIn = parseJson(SignIn.class);
        getMetrics().setAppId(signIn.getAppId());

        App app = appService.getApp(signIn.getAppId());
        verifySupportedVersionOrThrowException(app);

        CriteriaContext context = getCriteriaContext(app.getIdentifier());

        UserSession session;
        try {
            session = authenticationService.signIn(app, context, signIn);
        } catch (ConsentRequiredException e) {
            setCookieAndRecordMetrics(e.getUserSession());
            throw e;
        }

        setCookieAndRecordMetrics(session);
        return UserSessionInfo.toJSON(session);
    }

    @PostMapping("/v3/auth/reauth")
    public JsonNode reauthenticate() {
        SignIn signInRequest = parseJson(SignIn.class);

        if (isBlank(signInRequest.getAppId())) {
            throw new BadRequestException("App ID is required.");
        }
        getMetrics().setAppId(signInRequest.getAppId());

        App app = appService.getApp(signInRequest.getAppId());
        verifySupportedVersionOrThrowException(app);
        
        CriteriaContext context = getCriteriaContext(app.getIdentifier());
        UserSession session;
        try {
            session = authenticationService.reauthenticate(app, context, signInRequest);
        } catch (ConsentRequiredException e) {
            setCookieAndRecordMetrics(e.getUserSession());
            throw e;
        }

        setCookieAndRecordMetrics(session);
        
        return UserSessionInfo.toJSON(session);
    }

    @Deprecated
    @PostMapping({"/v3/auth/signIn", "/api/v1/auth/signIn"})
    public JsonNode signInV3() {
        // Email based sign in with throw UnauthorizedException if email-only sign in is disabled.
        // This maintains backwards compatibility for older clients.
        try {
            return signIn();
        } catch(UnauthorizedException e) {
            throw new EntityNotFoundException(Account.class);
        }
    }

    @Deprecated
    @PostMapping("/v3/auth/signOut")
    public StatusMessage signOut() {
        final UserSession session = getSessionIfItExists();
        // Always set, even if we eventually decide to return an error code when there's no session
        if (session != null) {
            authenticationService.signOut(session);
        }
        // Servlet API has no way to delete cookies. strange but true. Set it "blank" to remove
        Cookie cookie = makeSessionCookie("", 0);
        response().addCookie(cookie);
        return new StatusMessage("Signed out.");
    }

    @Deprecated
    @GetMapping("/api/v1/auth/signOut")
    public StatusMessage signOutGet() {
        return signOut();
    }
    
    @PostMapping("/v4/auth/signOut")
    public StatusMessage signOutV4() {
        final UserSession session = getSessionIfItExists();
        // Always set, even if we eventually decide to return an error code when there's no session
        Cookie cookie = makeSessionCookie("", 0);
        response().addCookie(cookie);
        response().setHeader(CLEAR_SITE_DATA_HEADER, CLEAR_SITE_DATA_VALUE);
        if (session != null) {
            authenticationService.signOut(session);
        } else {
            throw new BadRequestException("Not signed in");
        }
        return new StatusMessage("Signed out.");
    }
    
    @PostMapping({"/v3/auth/signUp", "/api/v1/auth/signUp"})
    @ResponseStatus(HttpStatus.CREATED)
    public StatusMessage signUp() {
        JsonNode node = parseJson(JsonNode.class);
        StudyParticipant participant = parseJson(node, StudyParticipant.class);
        
        String appId = JsonUtils.asText(node, APP_ID_PROPERTY, STUDY_PROPERTY);
        getMetrics().setAppId(appId);
        App app = getAppOrThrowException(appId);
        authenticationService.signUp(app, participant);
        return new StatusMessage("Signed up.");
    }

    @PostMapping({"/v3/auth/verifyEmail", "/api/v1/auth/verifyEmail"})
    public StatusMessage verifyEmail() {
        Verification verification = parseJson(Verification.class);

        authenticationService.verifyChannel(ChannelType.EMAIL, verification);
        
        return new StatusMessage("Email address verified.");
    }

    @PostMapping({"/v3/auth/resendEmailVerification", "/api/v1/auth/resendEmailVerification"})
    public StatusMessage resendEmailVerification() {
        AccountId accountId = parseJson(AccountId.class);
        getAppOrThrowException(accountId.getUnguardedAccountId().getAppId());
        
        authenticationService.resendVerification(ChannelType.EMAIL, accountId);
        return new StatusMessage("If registered with the app, we'll email you instructions on how to verify your account.");
    }

    @PostMapping("/v3/auth/verifyPhone")
    public StatusMessage verifyPhone() {
        Verification verification = parseJson(Verification.class);

        authenticationService.verifyChannel(ChannelType.PHONE, verification);
        
        return new StatusMessage("Phone number verified.");
    }

    @PostMapping("/v3/auth/resendPhoneVerification")
    public StatusMessage resendPhoneVerification() {
        AccountId accountId = parseJson(AccountId.class);
        
        // Must be here to get the correct exception if app property is missing
        getAppOrThrowException(accountId.getUnguardedAccountId().getAppId());
        
        authenticationService.resendVerification(ChannelType.PHONE, accountId);
        return new StatusMessage("If registered with the app, we'll send an SMS message to your phone.");
    }

    @PostMapping({"/v3/auth/requestResetPassword", "/api/v1/auth/requestResetPassword"})
    public StatusMessage requestResetPassword() {
        SignIn signIn = parseJson(SignIn.class);
        
        App app = appService.getApp(signIn.getAppId());
        verifySupportedVersionOrThrowException(app);
        
        authenticationService.requestResetPassword(app, false, signIn);

        return new StatusMessage("If registered with the app, we'll send you instructions on how to change your password.");
    }
    
    @PostMapping({"/v3/auth/resetPassword", "/api/v1/auth/resetPassword"})
    public StatusMessage resetPassword() {
        PasswordReset passwordReset = parseJson(PasswordReset.class);
        getAppOrThrowException(passwordReset.getAppId());
        authenticationService.resetPassword(passwordReset);
        return new StatusMessage("Password has been changed.");
    }
    
    @PostMapping(path = {"/v3/auth/app", "/v3/auth/study"})
    public JsonNode changeApp() {
        UserSession session = getAuthenticatedSession();
        
        // To switch apps, the account must be an administrative account with a Synapse User ID
        StudyParticipant participant = session.getParticipant(); 
        if (participant.getRoles().isEmpty()) {
            throw new UnauthorizedException(APP_ACCESS_EXCEPTION_MSG);
        }
        
        // Retrieve the desired app
        SignIn signIn = parseJson(SignIn.class);
        String targetAppId = signIn.getAppId();
        App targetApp = appService.getApp(targetAppId);

        // Cross app administrator can switch to any app. Implement this here because clients 
        // cannot tell who is a cross-app administrator once they've switched apps.
        if (session.isInRole(SUPERADMIN)) {
            sessionUpdateService.updateApp(session, targetApp.getIdentifier());
            return UserSessionInfo.toJSON(session);
        }
        // Otherwise, verify the user has access to this app
        if (participant.getSynapseUserId() == null) {
            throw new BadRequestException("Account has not been assigned a Synapse user ID");
        }
        AccountId accountId = AccountId.forSynapseUserId(targetAppId, participant.getSynapseUserId());
        Account account = accountService.getAccount(accountId);
        if (account == null) {
            throw new UnauthorizedException(APP_ACCESS_EXCEPTION_MSG);
        }
        
        // Make the switch
        authenticationService.signOut(session);
        
        // RequestContext reqContext = BridgeUtils.getRequestContext();
        CriteriaContext context = new CriteriaContext.Builder()
            .withUserId(account.getId())
            .withAppId(targetApp.getIdentifier())
            .build();
        
        UserSession newSession = authenticationService.getSessionFromAccount(targetApp, context, account);
        cacheProvider.setUserSession(newSession);
        
        return UserSessionInfo.toJSON(newSession);
    }
        
    @PostMapping("/v3/auth/oauth/signIn")
    public JsonNode oauthSignIn() {
        OAuthAuthorizationToken token = parseJson(OAuthAuthorizationToken.class);
        
        App app = appService.getApp(token.getAppId());
        CriteriaContext context = getCriteriaContext(app.getIdentifier());
        
        UserSession session = authenticationService.oauthSignIn(context, token);
        setCookieAndRecordMetrics(session);
        
        return UserSessionInfo.toJSON(session);
    }
    
    private App getAppOrThrowException(String appId) {
        App app = appService.getApp(appId);
        verifySupportedVersionOrThrowException(app);
        return app;
    }
}
