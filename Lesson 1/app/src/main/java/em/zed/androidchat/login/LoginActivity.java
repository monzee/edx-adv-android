/*
 * This file is a part of the Lesson 1 project.
 */

package em.zed.androidchat.login;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;

import java.util.EnumSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnClick;
import edu.galileo.android.androidchat.R;
import em.zed.androidchat.backend.Auth;

import static edu.galileo.android.androidchat.AndroidChatApplication.GLOBALS;

public class LoginActivity extends AppCompatActivity implements Login.Model.Case {

    public static final int RESULT = 1;
    public static final String TOKEN_AUTH = "key-auth-token";
    public static final String TOKEN_REFRESH = "key-refresh-token";

    private static class Scope {
        final ExecutorService bg = GLOBALS.io();
        final Login.Controller actions = new LoginController(bg, GLOBALS.auth(), GLOBALS.logger());
        Login.Model state = Login.Model.Case::idle;
    }

    @Bind(R.id.btnSignin) Button btnSignIn;
    @Bind(R.id.btnSignup) Button btnSignUp;
    @Bind(R.id.editTxtEmail) EditText inputEmail;
    @Bind(R.id.editTxtPassword) EditText inputPassword;
    @Bind(R.id.progressBar) ProgressBar progressBar;
    @Bind(R.id.layoutMainContainer) RelativeLayout container;

    private Scope my;
    private Future<?> pending;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        my = (Scope) getLastCustomNonConfigurationInstance();
        if (my == null) {
            my = new Scope();
        }
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        ButterKnife.bind(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        my.state.match(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (pending != null) {
            pending.cancel(true);
            pending = null;
        }
    }

    @Override
    public Object onRetainCustomNonConfigurationInstance() {
        return my;
    }

    @Override
    public void idle() {
        spin(false);
    }

    @Override
    public void invalid(EnumSet<Login.Invalid> errors) {
        spin(false);
        if (errors.contains(Login.Invalid.EMAIL)) {
            inputEmail.setError("Invalid email address");
        }
        if (errors.contains(Login.Invalid.PASSWORD)) {
            if (errors.contains(Login.Invalid.REJECTED)) {
                inputPassword.setError("Password too simple");
            } else {
                inputPassword.setError("Password cannot be empty");
            }
        }
    }

    @Override
    public void loggingIn(Future<Login.Model> result) {
        spin(true);
        pending = my.bg.submit(() -> {
            try {
                apply(result.get());
            } catch (ExecutionException e) {
                apply(of -> of.error(e));
            } catch (InterruptedException ignored) {
            }
        });
    }

    @Override
    public void loggedIn(Auth.Tokens tokens) {
        spin(false);
        setResult(RESULT_OK, new Intent()
                .putExtra(TOKEN_AUTH, tokens.auth)
                .putExtra(TOKEN_REFRESH, tokens.refresh));
        ActivityCompat.finishAfterTransition(this);
    }

    @Override
    public void loginFailed(Login.Reason reason) {
        say(R.string.login_error_message_signin, reason);
        apply(Login.Model.Case::idle);
    }

    @Override
    public void signingUp(Future<Login.Model> result) {
        spin(true);
        pending = my.bg.submit(() -> {
            try {
                apply(result.get());
            } catch (ExecutionException e) {
                apply(of -> of.error(e));
            } catch (InterruptedException ignored) {
            }
        });
    }

    @Override
    public void signedUp(Login.Model loggingIn) {
        say(R.string.login_notice_message_useradded);
        apply(loggingIn);
    }

    @Override
    public void signUpFailed(EnumSet<Login.Invalid> rejected) {
        if (rejected.isEmpty()) {
            say("Email already taken. Did you forget your password?");
            apply(Login.Model.Case::idle);
        } else {
            say(R.string.login_error_message_signup, "; some field was rejected by the service.");
            rejected.add(Login.Invalid.REJECTED);
            apply(of -> of.invalid(rejected));
        }
    }

    @Override
    public void error(Throwable e) {
        if (e instanceof TimeoutException) {
            say("Request has taken too long. Try again later.");
            apply(Login.Model.Case::idle);
            return;
        }
        throw new RuntimeException(e);
    }

    @OnClick(R.id.btnSignin)
    void signIn() {
        String email = inputEmail.getText().toString();
        String password = inputPassword.getText().toString();
        apply(my.actions.login(email, password));
    }

    @OnClick(R.id.btnSignup)
    void signUp() {
        String email = inputEmail.getText().toString();
        String password = inputPassword.getText().toString();
        apply(my.actions.signUp(email, password));
    }

    void apply(Login.Model newState) {
        runOnUiThread(() -> {
            my.state = newState;
            newState.match(this);
        });
    }

    void say(@StringRes int message, Object... fmtArgs) {
        say(getString(message, fmtArgs));
    }

    void say(String message, Object... fmtArgs) {
        Snackbar.make(container, String.format(message, fmtArgs), Snackbar.LENGTH_LONG)
                .show();
    }

    void spin(boolean busy) {
        inputEmail.setError(null);
        inputPassword.setError(null);
        if (busy) {
            inputEmail.setEnabled(false);
            inputPassword.setEnabled(false);
            btnSignIn.setEnabled(false);
            btnSignUp.setEnabled(false);
            progressBar.setVisibility(View.VISIBLE);
        } else {
            pending = null;
            inputEmail.setEnabled(true);
            inputPassword.setEnabled(true);
            btnSignIn.setEnabled(true);
            btnSignUp.setEnabled(true);
            progressBar.setVisibility(View.GONE);
        }
    }

}
