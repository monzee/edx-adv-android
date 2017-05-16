package edu.galileo.android.androidchat;

import android.app.Application;
import android.os.AsyncTask;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.concurrent.ExecutorService;

import edu.galileo.android.androidchat.lib.GlideImageLoader;
import edu.galileo.android.androidchat.lib.ImageLoader;
import em.zed.androidchat.AndroidLog;
import em.zed.androidchat.Globals;
import em.zed.androidchat.Lazy;
import em.zed.androidchat.Logger;
import em.zed.androidchat.backend.Auth;
import em.zed.androidchat.backend.Contacts;
import em.zed.androidchat.backend.Files;
import em.zed.androidchat.backend.UserRepository;
import em.zed.androidchat.backend.firebase.FirebaseContacts;
import em.zed.androidchat.backend.firebase.FirebaseEmailAuth;
import em.zed.androidchat.backend.firebase.FirebaseUserRepository;
import em.zed.androidchat.backend.firebase.Schema;


public class AndroidChatApplication extends Application implements Globals {

    public static final Globals GLOBALS = new Globals() {
        @Override
        public ExecutorService compute() {
            return delegate.compute();
        }

        @Override
        public ExecutorService io() {
            return delegate.io();
        }

        @Override
        public Auth.Service auth() {
            return delegate.auth();
        }

        @Override
        public Files.Service dataFiles() {
            return delegate.dataFiles();
        }

        @Override
        public UserRepository users() {
            return delegate.users();
        }

        @Override
        public Contacts.Service contacts() {
            return delegate.contacts();
        }

        @Override
        public Logger logger() {
            return delegate.logger();
        }
    };

    private static Globals delegate = Globals.DEFAULT;

    private final Lazy<FirebaseDatabase> fbRoot = new Lazy<>(() -> {
        FirebaseDatabase db = FirebaseDatabase.getInstance();
        db.setPersistenceEnabled(true);
        return db;
    });

    private final Lazy<DatabaseReference> usersNode = new Lazy<>(() ->
            fbRoot.get().getReference(Schema.USERS));

    private final Lazy<DatabaseReference> chatsNode = new Lazy<>(() ->
            fbRoot.get().getReference(Schema.CHATS));

    private final Lazy<UserRepository> users = new Lazy<>(() ->
            new FirebaseUserRepository(usersNode.get()));

    private final Lazy<Contacts.Service> contacts = new Lazy<>(() ->
            new FirebaseContacts(usersNode.get()));

    private final Lazy<Auth.Service> auth = new Lazy<>(() -> new FirebaseEmailAuth(
            users.get(),
            contacts.get(),
            FirebaseAuth.getInstance(),
            Globals.TIMEOUT,
            logger()));

    private ImageLoader imageLoader;

    @Override
    public void onCreate() {
        super.onCreate();
        delegate = this;
    }

    @Override
    public ExecutorService compute() {
        return (ExecutorService) AsyncTask.THREAD_POOL_EXECUTOR;
    }

    @Override
    public ExecutorService io() {
        return Globals.DEFAULT.io();
    }

    @Override
    public Auth.Service auth() {
        return auth.get();
    }

    @Override
    public Files.Service dataFiles() {
        return new Files.Service(getDir("hands-off", MODE_PRIVATE));
    }

    @Override
    public UserRepository users() {
        return users.get();
    }

    @Override
    public Contacts.Service contacts() {
        return contacts.get();
    }

    @Override
    public Logger logger() {
        return new AndroidLog("mz");
    }

    public ImageLoader getImageLoader() {
        if (imageLoader == null) {
            imageLoader = new GlideImageLoader(this);
        }
        return imageLoader;
    }

}
