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
                return v -> v.loggedIn(tokens);
            } catch (Auth.UnknownEmail e) {
                return v -> v.loginFailed(Login.LoginFailure.UNKNOWN_EMAIL);
            } catch (Auth.BadPassword e) {
                return v -> v.loginFailed(Login.LoginFailure.BAD_PASSWORD);
            }
        });
        return v -> v.loggingIn(result);
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
                    return v -> v.signedUp(login(email, password));
                } else {
                    return v -> v.signUpFailed(setOf());
                }
            } catch (Auth.EmailRejected e) {
                return v -> v.signUpFailed(setOf(
                        Login.Invalid.EMAIL,
                        Login.Invalid.REJECTED));
            } catch (Auth.PasswordRejected e) {
                return v -> v.signUpFailed(setOf(
                        Login.Invalid.PASSWORD,
                        Login.Invalid.REJECTED));
            }
        });
        return v -> v.signingUp(result);
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
            return v -> v.invalid(errors);
        }
        return null;
    }

    static EnumSet<Login.Invalid> setOf(Login.Invalid... e) {
        EnumSet<Login.Invalid> flags = EnumSet.noneOf(Login.Invalid.class);
        Collections.addAll(flags, e);
        return flags;
    }

}
