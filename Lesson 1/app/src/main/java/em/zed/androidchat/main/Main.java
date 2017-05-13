/*
 * This file is a part of the Lesson 1 project.
 */

package em.zed.androidchat.main;

import java.util.Deque;
import java.util.List;
import java.util.concurrent.Future;

import edu.galileo.android.androidchat.contactlist.entities.User;
import em.zed.androidchat.backend.Auth;
import em.zed.androidchat.backend.UserRepository;

public interface Main {

    interface Model {
        void match(Case of);

        interface Case {
            void booting();
            void waiting(Deque<Model> backlog);

            void loading(Future<Model> result);
            void idle(List<User> contacts);

            void removing(Future<Model> result);
            void removed(User contact);

            void adding(Future<Model> result);
            void added(User contact);

            void loggingOut(Future<Model> result);
            void loggedOut();

            void conversingWith(User contact);
            void error(Throwable e);
        }
    }

    interface Controller {
        UserRepository.Canceller observe(List<User> contacts, UserRepository.OnUserUpdate listener);
        Model loadContacts(Auth.Tokens tokens);
        Model addContact(String email);
        Model removeContact(String email);
        Model logout();
    }

}
