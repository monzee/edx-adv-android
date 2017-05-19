/*
 * This file is a part of the Lesson 1 project.
 */

package em.zed.androidchat.main;

import java.util.Deque;
import java.util.List;
import java.util.concurrent.Future;

import edu.galileo.android.androidchat.contactlist.entities.User;
import em.zed.androidchat.backend.UserRepository;

public interface Main {

    interface Model {
        void render(View v);
    }

    interface View {
        void booting();
        void replay(Deque<Model> backlog);

        void loading(Future<Model> task);
        void loaded(String userEmail, List<User> contacts);
        void idle(List<User> contacts);

        void removing(Future<Model> task);
        void removed(User contact);

        void adding(Future<Model> task);
        void added(User contact);

        void loggingOut(Future<Model> task);
        void loggedOut();

        void willChatWith(User contact);
        void error(Throwable e);
    }

    interface Renderer {
        void move(Model newState);
        void apply(Model newState);
    }

    interface SourcePort {
        UserRepository.Canceller observe(List<User> contacts, UserRepository.OnUserUpdate listener);
        Model loadContacts();
        Model addContact(String email);
        Model removeContact(String email);
        Model logout();
    }

    interface TargetPort {
        void replace(List<User> contacts);
        Model add(User contact);
        Model remove(User contact);
        Model update(User contact);
        Model pull();
    }

}
