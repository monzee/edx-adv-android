/*
 * This file is a part of the Lesson 1 project.
 */

package em.zed.androidchat;

public class Lazy<T> {

    public interface Factory<T> {
        T get();
    }

    private final Factory<T> factory;
    private T value;
    private boolean set;

    public Lazy(Factory<T> factory) {
        this.factory = factory;
    }

    public T get() {
        if (!set) {
            value = factory.get();
            set = true;
        }
        return value;
    }

}
