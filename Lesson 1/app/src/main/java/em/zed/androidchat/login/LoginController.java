/*
 * This file is a part of the Lesson 1 project.
 */

package em.zed.androidchat.login;

import java.util.Collections;
import java.util.EnumSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import em.zed.androidchat.LogLevel;
import em.zed.androidchat.Logger;
import em.zed.androidchat.backend.Auth;

public class LoginController implements Login.Controller {

    private final ExecutorService bg;
    private final Auth.Service auth;
    private final Logger log;

    public LoginController(Auth.Service auth) {
        this(auth, null);
    }

    public LoginController(ExecutorService bg, Auth.Service auth) {
        this(bg, auth, null);
    }

    public LoginController(Auth.Service auth, Logger log) {
        this(Executors.newSingleThreadExecutor(), auth, log);
    }

    public LoginController(ExecutorService bg, Auth.Service auth, Logger log) {
        this.bg = bg;
        this.auth = auth;
        this.log = log;
    }

    @Override
    public Login.Model login(String email, String password) {
        LogLevel.I.to(log, "#login");
        Login.Model invalid = validate(email, password);
        if (invalid != null) {
            return invalid;
        }

        Future<Login.Model> result = bg.submit(() -> {
            try {
                Auth.Tokens tokens = auth.login(email, password);
                return of -> of.loggedIn(tokens);
            } catch (Auth.UnknownEmail e) {
                LogLevel.E.to(log, e);
                return of -> of.loginFailed(Login.Reason.UNKNOWN_EMAIL);
            } catch (Auth.BadPassword e) {
                LogLevel.E.to(log, e);
                return of -> of.loginFailed(Login.Reason.BAD_PASSWORD);
            }
        });
        return of -> of.loggingIn(result);
    }

    @Override
    public Login.Model signUp(String email, String password) {
        LogLevel.I.to(log, "#signUp");
        Login.Model invalid = validate(email, password);
        if (invalid != null) {
            return invalid;
        }

        Future<Login.Model> result = bg.submit(() -> {
            try {
                if (auth.signUp(email, password)) {
                    return of -> of.signedUp(login(email, password));
                } else {
                    return of -> of.signUpFailed(setOf());
                }
            } catch (Auth.EmailRejected e) {
                LogLevel.E.to(log, e);
                return of -> of.signUpFailed(setOf(
                        Login.Invalid.EMAIL,
                        Login.Invalid.REJECTED));
            } catch (Auth.PasswordRejected e) {
                LogLevel.E.to(log, e);
                return of -> of.signUpFailed(setOf(
                        Login.Invalid.PASSWORD,
                        Login.Invalid.REJECTED));
            }
        });
        return of -> of.signingUp(result);
    }

    static Login.Model validate(String email, String password) {
        EnumSet<Login.Invalid> errors = setOf();
        if (email == null || !email.contains("@")) {
            errors.add(Login.Invalid.EMAIL);
        }
        if (password == null || password.isEmpty()) {
            errors.add(Login.Invalid.PASSWORD);
        }
        if (!errors.isEmpty()) {
            return of -> of.invalid(errors);
        }
        return null;
    }

    static EnumSet<Login.Invalid> setOf(Login.Invalid... e) {
        EnumSet<Login.Invalid> flags = EnumSet.noneOf(Login.Invalid.class);
        Collections.addAll(flags, e);
        return flags;
    }

}
