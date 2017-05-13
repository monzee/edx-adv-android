/*
 * This file is a part of the Lesson 1 project.
 */

package em.zed.androidchat;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import em.zed.androidchat.backend.Auth;
import em.zed.androidchat.backend.Contacts;
import em.zed.androidchat.backend.UserRepository;
import em.zed.androidchat.backend.fake.FakeAuth;

public interface Globals {

    ExecutorService compute();
    ExecutorService io();
    Auth.Service auth();
    UserRepository users();
    Contacts.Service contacts();
    Logger logger();

    Executor IMMEDIATE = Runnable::run;

    Globals DEFAULT = new Globals() {
        private Lazy<ExecutorService> compute = new Lazy<>(() -> Executors.newFixedThreadPool(4));
        private Lazy<ExecutorService> io = new Lazy<>(Executors::newCachedThreadPool);
        private Lazy<Auth.Service> auth = new Lazy<>(FakeAuth::new);

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
            return auth.get();
        }

        @Override
        public UserRepository users() {
            return null;
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
