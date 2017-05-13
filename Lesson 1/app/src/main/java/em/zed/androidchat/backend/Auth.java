/*
 * This file is a part of the Lesson 1 project.
 */

package em.zed.androidchat.backend;

import edu.galileo.android.androidchat.contactlist.entities.User;

public interface Auth {

    interface Service {
        /**
         * @return false means the email passed is already registered.
         * TODO: reconsider this API
         */
        boolean signUp(String email, String password) throws SignUpError, InterruptedException;
        Tokens login(String email, String password) throws AuthError, InterruptedException;
        Tokens refresh(String token) throws AuthError, InterruptedException;
        Status check(String token) throws InterruptedException;
        User profile() throws InterruptedException;
        void logout(String token) throws InterruptedException;
    }

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

    class CannotRefresh extends AuthError {
        public CannotRefresh() {
            super("Refresh token expired.");
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
