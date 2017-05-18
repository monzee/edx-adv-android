/*
 * This file is a part of the Lesson 1 project.
 */

package em.zed.androidchat.backend;

public interface Image {

    interface LoadInto<T> {
        void into(T t);
    }

    interface Service<T> {
        LoadInto<T> load(String identifier);
    }

}
