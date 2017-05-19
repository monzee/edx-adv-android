package em.zed.androidchat.main;
/*
 * This file is a part of the Lesson 1 project.
 */

import java.util.concurrent.Future;

public interface Add {

    interface Model {
        void render(View v);
    }

    interface View {
        void idle();
        void invalid(String message);
        void adding(Future<Model> task);
        void added(String email, boolean online);
        void addFailed(String reason);
        void error(Throwable e);
    }

    interface Controller {
        Model addContact(CharSequence email);
    }

}
