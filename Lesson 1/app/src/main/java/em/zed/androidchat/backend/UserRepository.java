/*
 * This file is a part of the Lesson 1 project.
 */

package em.zed.androidchat.backend;

import java.util.List;
import java.util.concurrent.Executor;

import edu.galileo.android.androidchat.contactlist.entities.User;

public interface UserRepository {

    interface OnUserUpdate {
        /**
         * @param user the updated profile object
         */
        void updated(User user);
    }

    interface OnContactsUpdate {
        void updated(List<User> contacts);
    }

    User getByEmail(String email) throws InterruptedException;
    void put(User user) throws InterruptedException;
    void put(User user, boolean fireAndForget) throws InterruptedException;

    /**
     * @return stops the listener when run
     */
    Runnable onUpdate(String email, OnContactsUpdate listener);

    /**
     * @return stops the listener when run
     */
    Runnable onUpdate(Executor ex, String email, OnContactsUpdate listener);

}
