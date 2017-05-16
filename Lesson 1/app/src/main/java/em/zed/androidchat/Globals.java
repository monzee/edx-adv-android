/*
 * This file is a part of the Lesson 1 project.
 */

package em.zed.androidchat;

import java.io.File;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import em.zed.androidchat.backend.Auth;
import em.zed.androidchat.backend.Contacts;
import em.zed.androidchat.backend.Files;
import em.zed.androidchat.backend.UserRepository;
import em.zed.androidchat.backend.fake.FakeAuth;
import em.zed.androidchat.backend.fake.FakeUserRepository;

public interface Globals {

    ExecutorService compute();
    ExecutorService io();
    Auth.Service auth();
    Files.Service dataFiles();
    UserRepository users();
    Contacts.Service contacts();
    Logger logger();

    Executor IMMEDIATE = Runnable::run;

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
        public Contacts.Service contacts() {
            return null;
        }

        @Override
        public Logger logger() {
            return null;
        }
    };

}
