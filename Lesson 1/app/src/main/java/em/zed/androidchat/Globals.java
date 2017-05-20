/*
 * This file is a part of the Lesson 1 project.
 */

package em.zed.androidchat;

import java.io.File;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import em.zed.androidchat.backend.Auth;
import em.zed.androidchat.backend.ChatRepository;
import em.zed.androidchat.backend.Contacts;
import em.zed.androidchat.backend.Files;
import em.zed.androidchat.backend.Image;
import em.zed.androidchat.backend.UserRepository;
import em.zed.androidchat.backend.fake.FakeAuth;
import em.zed.androidchat.backend.fake.FakeUserRepository;
import em.zed.androidchat.util.Lazy;

public interface Globals {

    ExecutorService compute();
    ExecutorService io();
    Auth.Service auth();
    Files.Service dataFiles();
    UserRepository users();
    ChatRepository chats();
    Contacts.Service contacts();
    /**
     * This is generic because I don't want platform types (especially
     * views) in this interface.
     */
    <T> Image.Service<T> images();
    Logger logger();

    Executor IMMEDIATE = Runnable::run;
    int TIMEOUT = 60_000;  // msec

    Globals DEFAULT = new Globals() {
        private Lazy<ExecutorService> compute = new Lazy<>(() -> Executors.newFixedThreadPool(4));
        private Lazy<ExecutorService> io = new Lazy<>(Executors::newCachedThreadPool);
        private Lazy<FakeUserRepository> fakeUsers = new Lazy<>(FakeUserRepository::new);
        private Lazy<Files.Service> dataFiles = new Lazy<>(() -> new Files.Service(new File("/tmp")));

        @Override
        public ExecutorService compute() {
            return compute.get();
        }

        @Override
        public ExecutorService io() {
            return io.get();
        }

        @Override
        public Auth.Service auth() {
            return new FakeAuth(fakeUsers.get());
        }

        @Override
        public Files.Service dataFiles() {
            return dataFiles.get();
        }

        @Override
        public UserRepository users() {
            return fakeUsers.get();
        }

        @Override
        public ChatRepository chats() {
            return null;
        }

        @Override
        public Contacts.Service contacts() {
            return null;
        }

        @Override
        public <T> Image.Service<T> images() {
            return null;
        }

        @Override
        public Logger logger() {
            return null;
        }
    };

}
