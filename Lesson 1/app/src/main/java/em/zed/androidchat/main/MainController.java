/*
 * This file is a part of the Lesson 1 project.
 */

package em.zed.androidchat.main;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
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

    private final Auth.Service auth;
    private final UserRepository users;
    private final Contacts.Service contacts;
    private final Logger log;
    private ExecutorService bg;
    private Auth.Tokens tokens;
    private String userEmail;

    public MainController(
            Auth.Service auth,
            UserRepository users,
            Contacts.Service contacts) {
        this(Executors.newSingleThreadExecutor(), auth, users, contacts, null);
    }

    public MainController(
            ExecutorService bg,
            Auth.Service auth,
            UserRepository users,
            Contacts.Service contacts,
            Logger log) {
        this.bg = bg;
        this.auth = auth;
        this.users = users;
        this.contacts = contacts;
        this.log = log;
    }

    @Override
    public UserRepository.Canceller observe(UserRepository.OnUserUpdate listener) {
        if (userEmail == null) {
            return UserRepository.Canceller.NOOP;
        }
        return users.onUpdate(userEmail, listener);
    }

    @Override
    public Main.Model loadContacts(Auth.Tokens tokens) {
        LogLevel.I.to(log, "#loadContacts(%s)", tokens);
        if (tokens == null) {
            LogLevel.D.to(log, "not authenticated");
            return Main.Model.Case::loggedOut;
        }
        this.tokens = tokens;
        Future<Main.Model> f = bg.submit(() -> {
            switch (auth.check(tokens.auth)) {
                case LOGGED_IN:
                    User user = auth.minimalProfile();
                    userEmail = user.getEmail();
                    contacts.fillContacts(user);
                    List<User> contacts = new ArrayList<>();
                    Map<String, Boolean> data = user.getContacts();
                    for (String email : data.keySet()) {
                        contacts.add(new User(email, data.get(email), null));
                    }
                    LogLevel.D.to(log, "%d contacts", contacts.size());
                    return of_ -> of_.loaded(userEmail, contacts);
                case EXPIRED:
                    LogLevel.D.to(log, "EXPIRED");
                    try {
                        return loadContacts(auth.refresh(tokens.refresh));
                    } catch (Auth.CannotRefresh e) {
                        return Main.Model.Case::loggedOut;
                    }
                case GUEST:
                    LogLevel.D.to(log, "GUEST");
                default:
                    return Main.Model.Case::loggedOut;
            }
        });
        return of -> of.loading(f);
    }

    @Override
    public Main.Model addContact(String email) {
        LogLevel.I.to(log, "#addContact(%s)", email);
        if (tokens == null) {
            LogLevel.D.to(log, "not authenticated");
            return Main.Model.Case::loggedOut;
        }
        Future<Main.Model> f = bg.submit(() -> {
            switch (auth.check(tokens.auth)) {
                case LOGGED_IN:
                    boolean online = contacts.addContact(userEmail, email);
                    LogLevel.D.to(log, "added");
                    return of_ -> of_.added(new User(email, online, null));
                case EXPIRED:
                    LogLevel.D.to(log, "EXPIRED");
                    try {
                        tokens = auth.refresh(tokens.refresh);
                        return addContact(email);
                    } catch (Auth.CannotRefresh e) {
                        return Main.Model.Case::loggedOut;
                    }
                case GUEST:
                    LogLevel.D.to(log, "GUEST");
                default:
                    return Main.Model.Case::loggedOut;
            }
        });
        return of -> of.adding(f);
    }

    @Override
    public Main.Model removeContact(String email) {
        LogLevel.I.to(log, "#removeContact(%s)", email);
        if (tokens == null) {
            LogLevel.D.to(log, "not authenticated");
            return Main.Model.Case::loggedOut;
        }
        Future<Main.Model> f = bg.submit(() -> {
            switch (auth.check(tokens.auth)) {
                case LOGGED_IN:
                    boolean online = contacts.removeContact(userEmail, email);
                    LogLevel.D.to(log, "removed");
                    return of_ -> of_.removed(new User(email, online, null));
                case EXPIRED:
                    LogLevel.D.to(log, "EXPIRED");
                    try {
                        tokens = auth.refresh(tokens.refresh);
                        return addContact(email);
                    } catch (Auth.CannotRefresh e) {
                        return Main.Model.Case::loggedOut;
                    }
                case GUEST:
                    LogLevel.D.to(log, "GUEST");
                default:
                    return Main.Model.Case::loggedOut;
            }
        });
        return of -> of.removing(f);
    }

    @Override
    public Main.Model logout() {
        LogLevel.I.to(log, "#logout");
        if (tokens == null) {
            LogLevel.D.to(log, "not authenticated");
            return Main.Model.Case::loggedOut;
        }
        Future<Main.Model> f = bg.<Main.Model>submit(() -> {
            auth.logout(tokens.auth);
            LogLevel.D.to(log, "ok");
            return Main.Model.Case::loggedOut;
        });
        return of -> of.loggingOut(f);
    }

}
