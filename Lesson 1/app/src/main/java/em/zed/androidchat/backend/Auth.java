/*
 * This file is a part of the Lesson 1 project.
 */

package em.zed.androidchat.backend;

import edu.galileo.android.androidchat.contactlist.entities.User;

public interface Auth {

    interface Service {
        /**
         * TODO: reconsider this API
         * @return false means the email passed is already registered.
         * @throws SignUpError e.g. malformed email, weak password
         */
        boolean signUp(String email, String password) throws SignUpError, InterruptedException;

        /**
         * @return an auth + refresh token pair
         * @throws AuthError when email/password combo is invalid
         */
        Tokens login(String email, String password) throws AuthError, InterruptedException;

        /**
         * @param token the refresh token obtained during initial login
         * @return fresh tokens
         * @throws AuthError when email/password combo is invalid
         */
        Tokens refresh(String token) throws AuthError, InterruptedException;

        /**
         * @param token auth token to check the validity of
         */
        Status check(String token) throws InterruptedException;

        /**
         * TODO: shouldn't this take an auth token?
         * @return a user object
         */
        User minimalProfile() throws InterruptedException;

        void logout(String token) throws InterruptedException;
    }

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
