/*
 * This file is a part of the Lesson 1 project.
 */

package em.zed.androidchat.backend.fake;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import edu.galileo.android.androidchat.contactlist.entities.User;
import em.zed.androidchat.backend.Auth;

public class FakeAuth implements Auth.Service {

    private final ReadWriteLock locks = new ReentrantReadWriteLock();
    private final Map<String, String> accounts = new HashMap<>();
    private final FakeUserRepository users;
    private String lastChecked;

    public FakeAuth() {
        this(Collections.emptyMap());
    }

    public FakeAuth(FakeUserRepository users) {
        this(users, Collections.emptyMap());
    }

    public FakeAuth(Map<String, String> accounts) {
        this(new FakeUserRepository(), accounts);
    }

    public FakeAuth(FakeUserRepository users, Map<String, String> accounts) {
        this.users = users;
        this.accounts.put("foo@example.com", "hunter2");
        this.accounts.put("bar@example.com", "foobar95");
        this.accounts.putAll(accounts);
    }

    @Override
    public boolean signUp(String email, String password) {
        if (accounts.containsKey(email)) {
            return false;
        }
        Lock write = locks.writeLock();
        try {
            write.lock();
            accounts.put(email, password);
            users.put(new User(email, true, null));
            return true;
        } finally {
            write.unlock();
        }
    }

    @Override
    public Auth.Tokens login(String email, String password) throws Auth.AuthError {
        Lock read = locks.readLock();
        try {
            read.lock();
            if (!accounts.containsKey(email)) {
                throw new Auth.UnknownEmail(email);
            }
            if (!accounts.get(email).equals(password)) {
                throw new Auth.BadPassword(email);
            }
            User u = users.getByEmail(email);
            if (u != null) {
                u.setOnline(User.ONLINE);
            } else {
                u = new User(email, true, null);
            }
            users.put(u);
            return new Auth.Tokens(email, null);
        } finally {
            read.unlock();
        }
    }

    @Override
    public Auth.Tokens refresh(String token) throws Auth.AuthError {
        throw new Auth.CannotRefresh();
    }

    @Override
    public Auth.Status check(String token) {
        lastChecked = token;
        if (accounts.containsKey(token)) {
            return Auth.Status.LOGGED_IN;
        }
        return Auth.Status.GUEST;
    }

    /**
     * Needs to do a {@link #check(String)} first before this could work.
     *
     * @return the user row of the last token checked
     */
    @Override
    public User minimalProfile() {
        if (lastChecked == null || !accounts.containsKey(lastChecked)) {
            return null;
        }
        return users.getByEmail(lastChecked);
    }

    @Override
    public void logout(String token) {
        User u = users.getByEmail(token);
        u.setOnline(User.OFFLINE);
        users.put(u);
        lastChecked = null;
    }

}
