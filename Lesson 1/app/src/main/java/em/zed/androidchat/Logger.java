/*
 * This file is a part of the Lesson 1 project.
 */

package em.zed.androidchat;

public interface Logger {
    boolean active(LogLevel level);
    void log(LogLevel level, String message);
    void log(LogLevel level, String message, Throwable e);
}
