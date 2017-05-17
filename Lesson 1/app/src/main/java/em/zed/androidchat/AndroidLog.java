/*
 * This file is a part of the Lesson 1 project.
 */

package em.zed.androidchat;

import android.util.Log;

import edu.galileo.android.androidchat.BuildConfig;

public class AndroidLog implements Logger {

    private final String tag;
    private final boolean isDebug = BuildConfig.BUILD_TYPE.equals("debug");

    public AndroidLog(String tag) {
        this.tag = tag;
    }

    @Override
    public boolean active(LogLevel level) {
        return isDebug;
    }

    @Override
    public void log(LogLevel level, String message) {
        switch (level) {
            case D:
                Log.d(tag, message);
                break;
            case I:
                Log.i(tag, message);
                break;
            case E:
                Log.e(tag, message);
                break;
        }
    }

    @Override
    public void log(LogLevel level, String message, Throwable e) {
        switch (level) {
            case D:
                Log.d(tag, message, e);
                break;
            case I:
                Log.i(tag, message, e);
                break;
            case E:
                Log.e(tag, message, e);
                break;
        }
    }
}
