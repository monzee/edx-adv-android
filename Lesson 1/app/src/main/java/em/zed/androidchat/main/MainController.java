/*
 * This file is a part of the Lesson 1 project.
 */

package em.zed.androidchat.main;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import edu.galileo.android.androidchat.contactlist.entities.User;
import em.zed.androidchat.Globals;
import em.zed.androidchat.LogLevel;
import em.zed.androidchat.Logger;
import em.zed.androidchat.backend.Auth;
import em.zed.androidchat.backend.Contacts;
import em.zed.androidchat.backend.UserRepository;

public class MainController implements Main.SourcePort {

    public static class Builder {
        private final UserRepository users;
        private final Contacts.Service contacts;
        private ExecutorService bg = Executors.newSingleThreadExecutor();
        private Logger log;

        public Builder(UserRepository users, Contacts.Service contacts) {
            this.users = users;
            this.contacts = contacts;
        }

        public Builder withExecutorService(ExecutorService bg) {
            this.bg = bg;
            return this;
        }

        public Builder withLogger(Logger log) {
            this.log = log;
            return this;
        }

        public MainController build(Auth.Session session) {
            return new MainController(this, session);
        }
    }

    private final Builder my;
    private final Auth.Session session;
    private String userEmail;

    private MainController(Builder my, Auth.Session session) {
        this.my = my;
        this.session = session;
    }

    @Override
    public Runnable observe(UserRepository.OnContactsUpdate listener) {
        if (userEmail == null) {
            return Globals.NOOP;
        }
        return my.users.onUpdate(userEmail, contacts -> {
            LogLevel.I.to(my.log, "#observe");
            listener.updated(contacts);
        });
    }

    @Override
    public Main.Model loadContacts() {
        LogLevel.I.to(my.log, "#loadContacts");
        Future<Main.Model> f = my.bg.submit(() -> {
            switch (session.check()) {
                case LOGGED_IN:
                    User user = session.minimalProfile();
                    userEmail = user.getEmail();
                    my.contacts.fillContacts(user);
                    List<User> userContacts = new ArrayList<>();
                    Map<String, Boolean> data = user.getContacts();
                    for (String email : data.keySet()) {
                        userContacts.add(new User(email, data.get(email), null));
                    }
                    return v -> v.loaded(userEmail, userContacts);
                case EXPIRED:
                    LogLevel.D.to(my.log, "EXPIRED");
                    return session.refresh() ? loadContacts() : Main.View::loggedOut;
                case GUEST:
                    LogLevel.D.to(my.log, "GUEST");
                default:
                    return Main.View::loggedOut;
            }
        });
        return v -> v.loading(f);
    }

    @Override
    public Main.Model removeContact(String email) {
        LogLevel.I.to(my.log, "#removeContact(%s)", email);
        Future<Main.Model> f = my.bg.submit(() -> {
            switch (session.check()) {
                case LOGGED_IN:
                    boolean online = my.contacts.removeContact(userEmail, email);
                    return v -> v.removed(new User(email, online, null));
                case EXPIRED:
                    LogLevel.D.to(my.log, "EXPIRED");
                    return session.refresh() ? removeContact(email) : Main.View::loggedOut;
                case GUEST:
                    LogLevel.D.to(my.log, "GUEST");
                default:
                    return Main.View::loggedOut;
            }
        });
        return v -> v.removing(f);
    }

    @Override
    public Main.Model logout() {
        LogLevel.I.to(my.log, "#logout");
        Future<Main.Model> f = my.bg.<Main.Model>submit(() -> {
            session.logout();
            return Main.View::loggedOut;
        });
        return v -> v.loggingOut(f);
    }

}
