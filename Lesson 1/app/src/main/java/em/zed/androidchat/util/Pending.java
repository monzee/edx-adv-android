/*
 * This file is a part of the Lesson 1 project.
 */

package em.zed.androidchat.util;

import java.util.concurrent.Future;

public class Pending<T> {

    private final T producer;
    private final Future<?> join;

    public Pending(T producer, Future<?> join) {
        this.producer = producer;
        this.join = join;
    }

    public T cancel() {
        join.cancel(true);
        return producer;
    }

}
