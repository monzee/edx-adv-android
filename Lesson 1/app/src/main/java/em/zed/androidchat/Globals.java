/*
 * This file is a part of the Lesson 1 project.
 */

package em.zed.androidchat;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import em.zed.androidchat.backend.Auth;
import em.zed.androidchat.backend.fake.FakeAuth;

public interface Globals {

    ExecutorService compute();
    ExecutorService io();
    Auth.Service auth();
    Logger logger();

    Executor IMMEDIATE = Runnable::run;

    Globals DEFAULT = new Globals() {
        private ExecutorService compute;
        private ExecutorService io;
        private Auth.Service auth;

        @Override
        public ExecutorService compute() {
            if (compute == null) {
                compute = Executors.newFixedThreadPool(4);
            }
            return compute;
        }

        @Override
        public ExecutorService io() {
            if (io == null) {
                io = Executors.newCachedThreadPool();
            }
            return io;
        }

        @Override
        public Auth.Service auth() {
            if (auth == null) {
                auth = new FakeAuth();
            }
            return auth;
        }

        @Override
        public Logger logger() {
            return null;
        }
    };

}
