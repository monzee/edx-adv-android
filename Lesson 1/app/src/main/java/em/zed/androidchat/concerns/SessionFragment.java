/*
 * This file is a part of the Lesson 1 project.
 */

package em.zed.androidchat.concerns;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.concurrent.ExecutorService;

import em.zed.androidchat.LogLevel;
import em.zed.androidchat.Logger;
import em.zed.androidchat.backend.Auth;
import em.zed.androidchat.backend.Files;
import em.zed.androidchat.login.LoginActivity;

import static android.app.Activity.RESULT_OK;

public class SessionFragment extends Fragment {

    public interface Pipe {
        void loggedIn(Auth.Tokens tokens);
        void loginCancelled();
        void runOnUiThread(Runnable proc);
    }

    public static void attach(FragmentManager fm) {
        if (fm.findFragmentByTag(TAG) == null) {
            fm.beginTransaction()
                    .add(new SessionFragment(), TAG)
                    .commit();
        }
    }

    private static String TAG = SessionFragment.class.getSimpleName();
    private static String STORE = "cookie-jar";

    private ExecutorService io;
    private Files.Service files;
    private Logger log;
    private Pipe pipe;

    public SessionFragment inject(
            ExecutorService io,
            Files.Service files,
            Logger log,
            Pipe pipe) {
        this.io = io;
        this.files = files;
        this.log = log;
        this.pipe = pipe;
        return this;
    }

    @SuppressLint("NewApi")
    public void start(boolean forced) {
        LogLevel.I.to(log, "#start(%s)", forced);
        io.execute(() -> {
            try {
                files.read(STORE, (in, isNew) -> {
                    if (forced || isNew) {
                        // TODO: is it ok to call this in a non-main thread?
                        startActivityForResult(
                                new Intent(getActivity(), LoginActivity.class),
                                LoginActivity.RESULT);
                    } else try (ObjectInputStream file = new ObjectInputStream(in)) {
                        String auth = file.readUTF();
                        String refresh = file.readUTF();
                        pipe.runOnUiThread(() -> pipe.loggedIn(new Auth.Tokens(auth, refresh)));
                    }
                });
            } catch (IOException e) {
                LogLevel.E.to(log, e, "io error!");
                destroy();
            }
        });
    }

    @SuppressLint("NewApi")
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        LogLevel.I.to(log, "#onActivityResult");
        if (requestCode != LoginActivity.RESULT) {
            return;
        }
        if (resultCode != RESULT_OK) {
            destroy();
            pipe.loginCancelled();
        } else {
            String auth = data.getStringExtra(LoginActivity.TOKEN_AUTH);
            String refresh = data.getStringExtra(LoginActivity.TOKEN_REFRESH);
            io.execute(() -> {
                try {
                    files.write(STORE, Files.OVERWRITE, out -> {
                        try (ObjectOutputStream file = new ObjectOutputStream(out)) {
                            file.writeUTF(auth);
                            file.writeUTF(refresh);
                        }
                    });
                } catch (IOException e) {
                    LogLevel.E.to(log, e, "io error!");
                    destroy();
                }
            });
            pipe.loggedIn(new Auth.Tokens(auth, refresh));
        }
    }

    public void destroy() {
        LogLevel.I.to(log, "#destroy");
        io.execute(() -> {
            try {
                files.delete(STORE);
            } catch (IOException e) {
                LogLevel.E.to(log, e, "io error!");
            }
        });
    }

}
