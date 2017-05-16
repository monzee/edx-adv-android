/*
 * This file is a part of the Lesson 1 project.
 */

package em.zed.androidchat;

import android.util.Log;

import com.google.firebase.crash.FirebaseCrash;

public class FirebaseLog implements Logger {

    private final String tag;

    public FirebaseLog(String tag) {
        this.tag = tag;
    }

    @Override
    public boolean active(LogLevel level) {
        return true;
    }

    @Override
    public void log(LogLevel level, String message) {
        int l = Log.INFO;
        switch (level) {
            case D:
                l = Log.DEBUG;
                break;
            case I:
                break;
            case E:
                l = Log.ERROR;
                break;
        }
        FirebaseCrash.logcat(l, tag, message);
    }

    @Override
    public void log(LogLevel level, String message, Throwable e) {
        if (message != null && !message.isEmpty()) {
            log(level, message);
        }
        FirebaseCrash.report(e);
    }

}
