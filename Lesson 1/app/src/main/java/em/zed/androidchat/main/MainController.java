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
    public UserRepository.Canceller observe(
            List<User> userContacts,
            UserRepository.OnUserUpdate listener) {
        if (userEmail == null) {
            return UserRepository.Canceller.NOOP;
        }
        return my.users.onUpdate(userEmail, user -> {
            LogLevel.I.to(my.log, "#observe");
            Map<String, Boolean> freshContacts = user.getContacts();
            if (freshContacts != null) for (User c : userContacts) {
                boolean online = my.contacts.isOnline(c.getEmail(), freshContacts);
                if (c.isOnline() != online) {
                    LogLevel.D.to(my.log, "updated %s: %s", c.getEmail(), online);
                    c.setOnline(online);
                    listener.updated(c);
                }
            }
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
                    LogLevel.D.to(my.log, "%d contacts", userContacts.size());
                    return of -> of.loaded(userEmail, userContacts);
                case EXPIRED:
                    LogLevel.D.to(my.log, "EXPIRED");
                    return session.refresh() ? loadContacts() : Main.Model.Case::loggedOut;
                case GUEST:
                    LogLevel.D.to(my.log, "GUEST");
                default:
                    return Main.Model.Case::loggedOut;
            }
        });
        return of -> of.loading(f);
    }

    @Override
    public Main.Model addContact(String email) {
        LogLevel.I.to(my.log, "#addContact(%s)", email);
        Future<Main.Model> f = my.bg.submit(() -> {
            switch (session.check()) {
                case LOGGED_IN:
                    boolean online = my.contacts.addContact(userEmail, email);
                    LogLevel.D.to(my.log, "added");
                    return of -> of.added(new User(email, online, null));
                case EXPIRED:
                    LogLevel.D.to(my.log, "EXPIRED");
                    return session.refresh() ? addContact(email) : Main.Model.Case::loggedOut;
                case GUEST:
                    LogLevel.D.to(my.log, "GUEST");
                default:
                    return Main.Model.Case::loggedOut;
            }
        });
        return of -> of.adding(f);
    }

    @Override
    public Main.Model removeContact(String email) {
        LogLevel.I.to(my.log, "#removeContact(%s)", email);
        Future<Main.Model> f = my.bg.submit(() -> {
            switch (session.check()) {
                case LOGGED_IN:
                    boolean online = my.contacts.removeContact(userEmail, email);
                    LogLevel.D.to(my.log, "removed");
                    return of -> of.removed(new User(email, online, null));
                case EXPIRED:
                    LogLevel.D.to(my.log, "EXPIRED");
                    return session.refresh() ? removeContact(email) : Main.Model.Case::loggedOut;
                case GUEST:
                    LogLevel.D.to(my.log, "GUEST");
                default:
                    return Main.Model.Case::loggedOut;
            }
        });
        return of -> of.removing(f);
    }

    @Override
    public Main.Model logout() {
        LogLevel.I.to(my.log, "#logout");
        Future<Main.Model> f = my.bg.<Main.Model>submit(() -> {
            session.logout();
            LogLevel.D.to(my.log, "ok");
            return Main.Model.Case::loggedOut;
        });
        return of -> of.loggingOut(f);
    }

}
