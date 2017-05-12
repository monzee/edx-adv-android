package edu.galileo.android.androidchat;

import android.app.Application;
import android.os.AsyncTask;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.FirebaseDatabase;

import java.util.concurrent.ExecutorService;

import edu.galileo.android.androidchat.lib.GlideImageLoader;
import edu.galileo.android.androidchat.lib.ImageLoader;
import em.zed.androidchat.AndroidLog;
import em.zed.androidchat.Globals;
import em.zed.androidchat.Logger;
import em.zed.androidchat.backend.Auth;
import em.zed.androidchat.backend.UserRepository;
import em.zed.androidchat.backend.firebase.FirebaseEmailAuth;
import em.zed.androidchat.backend.firebase.FirebaseUserRepository;


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
        public Logger logger() {
            return delegate.logger();
        }
    };

    private static Globals delegate = Globals.DEFAULT;

    private ImageLoader imageLoader;
    private Auth.Service auth;
    private UserRepository users;

    @Override
    public void onCreate() {
        super.onCreate();
        setupFirebase();
        setupImageLoader();
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
        if (auth == null) {
            auth = new FirebaseEmailAuth(users(), FirebaseAuth.getInstance(), logger());
        }
        return auth;
    }

    UserRepository users() {
        if (users == null) {
            users = new FirebaseUserRepository(
                    FirebaseDatabase.getInstance().getReference("users"));
        }
        return users;
    }

    @Override
    public Logger logger() {
        return new AndroidLog("mz");
    }

    private void setupImageLoader() {
        imageLoader = new GlideImageLoader(this);
    }

    public ImageLoader getImageLoader() {
        return imageLoader;
    }

    private void setupFirebase(){
        FirebaseDatabase.getInstance().setPersistenceEnabled(true);
    }

}
