/*
 * This file is a part of the Lesson 1 project.
 */

package em.zed.androidchat.backend;

import java.util.concurrent.TimeoutException;

import edu.galileo.android.androidchat.contactlist.entities.User;

public interface Auth {

    interface Service {
        /**
         * TODO: reconsider this API
         * @return false means the email passed is already registered.
         * @throws SignUpError e.g. malformed email, weak password
         */
        boolean signUp(String email, String password)
                throws SignUpError, InterruptedException, TimeoutException;

        /**
         * @return an auth + refresh token pair
         * @throws AuthError when email/password combo is invalid
         */
        Tokens login(String email, String password)
                throws AuthError, InterruptedException, TimeoutException;

        Session start(Tokens tokens);
    }

    interface Session {
        /**
         * This should return immediately and never call out into the remote
         * service! Need to implement some sort of local cache. Luckily
         * Firebase already does so.
         *
         * @return the current logged in status
         */
        Status check();
        boolean refresh() throws AuthError, InterruptedException, TimeoutException;
        Tokens current();
        User minimalProfile() throws InterruptedException;
        void logout() throws InterruptedException;
    }

    interface Sensitive<T> {
        T build(Session session);
    }

    Session NO_SESSION = new Session() {
        @Override
        public Status check() {
            return Status.GUEST;
        }

        @Override
        public boolean refresh() {
            throw new IllegalStateException("Not authenticated.");
        }

        @Override
        public Tokens current() {
            throw new IllegalStateException("Not authenticated.");
        }

        @Override
        public User minimalProfile() {
            throw new IllegalStateException("Not authenticated.");
        }

        @Override
        public void logout() {
            throw new IllegalStateException("Not authenticated.");
        }
    };

    /**
     * GUEST - not logged in or previous auth was revoked
     * EXPIRED - the last login happened too long ago and needs to be refreshed
     * LOGGED_IN - the user is currently authenticated
     */
    enum Status { GUEST, EXPIRED, LOGGED_IN }

    class Tokens {
        public final String auth;
        public final String refresh;

        public Tokens(String auth, String refresh) {
            this.auth = auth;
            this.refresh = refresh;
        }

        @Override
        public String toString() {
            return auth + ":" + refresh;
        }
    }

    abstract class AuthError extends RuntimeException {
        private AuthError(String message) {
            super(message);
        }
    }

    class UnknownEmail extends AuthError {
        public UnknownEmail(String email) {
            super("Email not registered: " + email);
        }
    }

    class BadPassword extends AuthError {
        public BadPassword(String email) {
            super("Wrong password for email: " + email);
        }
    }

    class SignUpError extends RuntimeException {
        public SignUpError(String message) {
            super(message);
        }
    }

    class EmailRejected extends SignUpError {
        public EmailRejected(String email) {
            super("Email probably malformed: " + email);
        }
    }

    class PasswordRejected extends SignUpError {
        public PasswordRejected() {
            super("Password probably too weak.");
        }
    }

}
