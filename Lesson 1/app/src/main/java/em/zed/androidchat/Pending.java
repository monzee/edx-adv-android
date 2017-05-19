/*
 * This file is a part of the Lesson 1 project.
 */

package em.zed.androidchat;

import java.util.concurrent.Future;

public class Pending<T> {

    private final T snapshot;
    private final Future<?> join;

    public Pending(T snapshot, Future<?> join) {
        this.snapshot = snapshot;
        this.join = join;
    }

    public T cancel() {
        join.cancel(true);
        return snapshot;
    }

}
