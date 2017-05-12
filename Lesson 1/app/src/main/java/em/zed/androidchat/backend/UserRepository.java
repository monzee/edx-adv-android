/*
 * This file is a part of the Lesson 1 project.
 */

package em.zed.androidchat.backend;

import java.util.concurrent.Executor;

import edu.galileo.android.androidchat.contactlist.entities.User;

public interface UserRepository {

    interface OnUserUpdate {
        void updated(User user);
    }

    interface Canceller {
        Canceller NOOP = () -> {};
        void cancel();
    }

    User getByEmail(String email) throws InterruptedException;
    void put(User user) throws InterruptedException;
    Canceller onUpdate(String email, OnUserUpdate listener);
    Canceller onUpdate(Executor ex, String email, OnUserUpdate listener);

}
