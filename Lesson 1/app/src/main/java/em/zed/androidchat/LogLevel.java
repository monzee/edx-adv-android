/*
 * This file is a part of the Lesson 1 project.
 */

package em.zed.androidchat;

public enum LogLevel {
    D, I, E;

    public void to(Logger logger, String message, Object... fmtArgs) {
        if (logger != null && logger.active(this)) {
            logger.log(this, String.format(message, fmtArgs));
        }
    }

    public void to(Logger logger, Throwable e) {
        to(logger, null, e);
    }

    public void to(Logger logger, Throwable e, String message, Object... fmtArgs) {
        if (logger != null && logger.active(this)) {
            logger.log(this, String.format(message, fmtArgs), e);
        }
    }
}
