/*
 * This file is a part of the Lesson 1 project.
 */

package em.zed.androidchat.main;

import java.util.List;
import java.util.concurrent.Future;

import edu.galileo.android.androidchat.contactlist.entities.User;
import em.zed.androidchat.backend.UserRepository;

public interface Main {

    interface View {
        void apply(Session newState);
        void applyContacts(Contacts innerState);
    }

    interface Session {
        void match(Case of);

        interface Case {
            void guest();
            void loggedIn(Contacts state);
            void error(Throwable e);
        }
    }

    interface Contacts {
        void match(Case of);

        interface Case {
            void cold();

            void loading(Future<Contacts> result);
            void loaded(List<User> contacts, UserRepository.Canceller watch);
            void updated(User contact);
            void refreshed(List<User> contacts);

            void removing(Future<Contacts> result);
            void removed(User contact);

            void adding(Future<Contacts> result);
            void added(User contact);
        }
    }

    interface Controller {
        Contacts loadContacts(UserRepository.OnUserUpdate watch);
    }

}
