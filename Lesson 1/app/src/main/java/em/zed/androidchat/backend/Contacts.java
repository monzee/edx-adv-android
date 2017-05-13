/*
 * This file is a part of the Lesson 1 project.
 */

package em.zed.androidchat.backend;

import edu.galileo.android.androidchat.contactlist.entities.User;

public interface Contacts {

    interface Service {
        /**
         * Synchronously fetches the user's contacts and mutates the entity.
         */
        void fillContacts(User user) throws InterruptedException;

        /**
         * @return the online status of the target
         */
        boolean addContact(String adder, String addee) throws InterruptedException;

        /**
         * @return the online status of the target
         */
        boolean removeContact(String source, String target) throws InterruptedException;

        /**
         * Synchronizes this user's online status with all its contacts'
         * contacts field.
         * <p>
         * Fire and forget; doesn't wait for all updates to complete, but might
         * still block if the contacts need to be fetched.
         *
         * @param user the row containing the value to propagate
         * @throws InterruptedException may be thrown if the user's contacts
         * field needs to be filled and was interrupted while doing so. If
         * the argument has a non-null contacts field, this can be safely caught
         * and ignored.
         */
        void broadcastStatus(User user) throws InterruptedException;
    }

}
