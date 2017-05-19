/*
 * This file is a part of the Lesson 1 project.
 */

package em.zed.androidchat.main;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import edu.galileo.android.androidchat.contactlist.entities.User;
import em.zed.androidchat.backend.Auth;
import em.zed.androidchat.backend.Contacts;

public class AddController implements Add.Controller {

    private final ExecutorService io;
    private final Contacts.Service contacts;
    private final Auth.Session session;

    public AddController(ExecutorService io, Contacts.Service contacts, Auth.Session session) {
        this.io = io;
        this.contacts = contacts;
        this.session = session;
    }

    @Override
    public Add.Model addContact(CharSequence email) {
        String contact = email.toString();
        String error = check(contact);
        if (error != null) {
            return v -> v.invalid(error);
        }

        Future<Add.Model> f = io.submit(() -> {
            switch (session.check()) {
                case LOGGED_IN:
                    if (contacts.exists(contact)) {
                        User me = session.minimalProfile();
                        boolean online = contacts.addContact(me.getEmail(), contact);
                        return v -> v.added(contact, online);
                    } else {
                        return v -> v.addFailed("No such email!");
                    }
                case EXPIRED:
                    if (session.refresh()) {
                        return addContact(email);
                    }
                    // cascade intended
                case GUEST:
                    return v -> v.addFailed("Invalid session!");
                default:
                    return v -> v.error(new IllegalStateException("unhandled case"));
            }
        });
        return v -> v.adding(f);
    }

    static String check(String email) {
        if (email == null || email.isEmpty()) {
            return "Email cannot be empty!";
        }
        if (!email.contains("@") || email.lastIndexOf('.') < email.indexOf('@')) {
            return "Malformed email!";
        }
        return null;
    }

}
