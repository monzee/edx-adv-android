/*
 * This file is a part of the Lesson 1 project.
 */

package em.zed.androidchat.login;

import java.util.EnumSet;
import java.util.concurrent.Future;

import em.zed.androidchat.backend.Auth;

public interface Login {

    interface Model {
        void match(Case of);

        interface Case {
            void idle();
            void invalid(EnumSet<Invalid> errors);

            void loggingIn(Future<Model> result);
            void loggedIn(Auth.Tokens tokens);
            void loginFailed(Reason reason);

            void signingUp(Future<Model> result);
            void signedUp(Model loggingIn);

            /**
             * pretty bad API and default impl. consider refactoring.
             *
             * @param rejected if empty, it means the email is already taken.
             *                 otherwise contains the field(s) deemed
             *                 unacceptable by the server. e.g. malformed
             *                 email, weak password.
             */
            void signUpFailed(EnumSet<Invalid> rejected);

            void error(Throwable e);
        }
    }

    interface View {
        void apply(Model newState);
    }

    interface Controller {
        Model login(String email, String password);
        Model signUp(String email, String password);
    }

    enum Invalid {
        EMAIL, PASSWORD, REJECTED,
    }

    enum Reason {
        UNKNOWN_EMAIL, BAD_PASSWORD, UNAVAILABLE,
    }

}