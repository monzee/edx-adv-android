/*
 * This file is a part of the Lesson 1 project.
 */

package em.zed.androidchat.login;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.MainThread;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.Toast;

import java.util.EnumSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnClick;
import edu.galileo.android.androidchat.R;
import em.zed.androidchat.backend.Auth;

import static edu.galileo.android.androidchat.AndroidChatApplication.GLOBALS;

public class LoginActivity extends AppCompatActivity implements Login.View, Login.Model.Case {

    public static final int RESULT = 1;
    public static final String TOKEN_AUTH = "key-auth-token";
    public static final String TOKEN_REFRESH = "key-refresh-token";

    private static class Retained {
        final ExecutorService bg = GLOBALS.io();
        final Login.Controller will = new LoginController(bg, GLOBALS.auth(), GLOBALS.logger());
        Login.Model state = Login.Model.Case::idle;
    }

    @Bind(R.id.btnSignin) Button btnSignIn;
    @Bind(R.id.btnSignup) Button btnSignUp;
    @Bind(R.id.editTxtEmail) EditText inputEmail;
    @Bind(R.id.editTxtPassword) EditText inputPassword;
    @Bind(R.id.progressBar) ProgressBar progressBar;
    @Bind(R.id.layoutMainContainer) RelativeLayout container;

    private Retained scope;
    private Future<?> pending;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        scope = (Retained) getLastCustomNonConfigurationInstance();
        if (scope == null) {
            scope = new Retained();
        }
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        ButterKnife.bind(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        scope.state.match(this);
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
        return scope;
    }

    @Override
    public void apply(Login.Model newState) {
        runOnUiThread(() -> {
            scope.state = newState;
            newState.match(this);
        });
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
        pending = scope.bg.submit(() -> {
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
        say(reason.toString());
        apply(Login.Model.Case::idle);
    }

    @Override
    public void signingUp(Future<Login.Model> result) {
        spin(true);
        pending = scope.bg.submit(() -> {
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
        say("Account created");
        apply(loggingIn);
    }

    @Override
    public void signUpFailed(EnumSet<Login.Invalid> rejected) {
        if (rejected.isEmpty()) {
            say("Email already taken. Did you forget your password?");
            apply(Login.Model.Case::idle);
        } else {
            say("Signup failed");
            rejected.add(Login.Invalid.REJECTED);
            apply(of -> of.invalid(rejected));
        }
    }

    @Override
    public void error(Throwable e) {
        throw new RuntimeException(e);
    }

    @OnClick(R.id.btnSignin)
    void signIn() {
        String email = inputEmail.getText().toString();
        String password = inputPassword.getText().toString();
        apply(scope.will.login(email, password));
    }

    @OnClick(R.id.btnSignup)
    void signUp() {
        String email = inputEmail.getText().toString();
        String password = inputPassword.getText().toString();
        apply(scope.will.signUp(email, password));
    }

    void say(String message, Object... fmtArgs) {
        Toast.makeText(this, String.format(message, fmtArgs), Toast.LENGTH_SHORT)
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
