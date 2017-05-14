/*
 * This file is a part of the Lesson 1 project.
 */

package em.zed.androidchat.backend.fake;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import edu.galileo.android.androidchat.contactlist.entities.User;
import em.zed.androidchat.Globals;
import em.zed.androidchat.backend.UserRepository;

public class FakeUserRepository implements UserRepository {

    private static class Watcher {
        final Executor ex;
        final OnUserUpdate listener;

        Watcher(Executor ex, OnUserUpdate listener) {
            this.ex = ex;
            this.listener = listener;
        }
    }

    private final ReadWriteLock locks = new ReentrantReadWriteLock();
    private final Map<String, Integer> byEmail = new HashMap<>();
    private final List<User> data = new ArrayList<>();
    private final Map<String, List<Watcher>> watchersByEmail = new HashMap<>();
    private final Executor bg;

    public FakeUserRepository() {
        this(null);
    }

    public FakeUserRepository(Executor bg) {
        this.bg = bg;
    }

    @Override
    public User getByEmail(String email) {
        Lock read = locks.readLock();
        try {
            read.lock();
            Integer i = byEmail.get(email);
            if (i != null) {
                return data.get(i);
            }
            return null;
        } finally {
            read.unlock();
        }
    }

    @Override
    public void put(User user) {
        String email = user.getEmail();
        Map<String, Boolean> contacts = user.getContacts();
        Lock write = locks.writeLock();
        try {
            write.lock();
            Integer i = byEmail.get(email);
            if (i != null) {
                User u = data.get(i);
                u.setEmail(email);
                u.setOnline(user.isOnline());
                if (contacts != null) {
                    u.setContacts(contacts);
                }
            } else {
                byEmail.put(email, data.size());
                data.add(user);
            }
        } finally {
            write.unlock();
        }
        if (bg != null) {
            bg.execute(() -> notifyUpdate(email));
        }
    }

    @Override
    public void put(User user, boolean fireAndForget) {
        put(user);
    }

    @Override
    public Canceller onUpdate(String email, OnUserUpdate listener) {
        return onUpdate(Globals.IMMEDIATE, email, listener);
    }

    @Override
    public Canceller onUpdate(Executor ex, String email, OnUserUpdate listener) {
        if (bg == null) {
            return Canceller.NOOP;
        }
        synchronized (watchersByEmail) {
            if (!watchersByEmail.containsKey(email)) {
                watchersByEmail.put(email, new ArrayList<>());
            }
            List<Watcher> watchers = watchersByEmail.get(email);
            Watcher w = new Watcher(ex, listener);
            watchers.add(w);
            return () -> {
                watchers.remove(w);
                if (watchers.isEmpty()) {
                    watchersByEmail.remove(email);
                }
            };
        }
    }

    private void notifyUpdate(String key) {
        synchronized (watchersByEmail) {
            List<Watcher> watchers = watchersByEmail.get(key);
            if (watchers == null) {
                return;
            }
            User user = getByEmail(key);
            for (Watcher w : watchers) {
                OnUserUpdate listener = w.listener;
                w.ex.execute(() -> listener.updated(user, false));
            }
        }
    }

}
