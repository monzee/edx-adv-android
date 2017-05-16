/*
 * This file is a part of the Lesson 1 project.
 */

package em.zed.androidchat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public interface Either<E extends Throwable, T> {

    T get() throws E;

    class Wait<E extends Throwable, T> implements Either<E, T> {
        private final CountDownLatch done = new CountDownLatch(1);
        private Either<E, T> either;

        @Override
        public T get() throws E {
            try {
                return await();
            } catch (InterruptedException e) {
                return null;
            }
        }

        public void ok(T t) {
            set(() -> t);
        }

        public void err(E e) {
            set(() -> { throw e; });
        }

        public void set(Either<E, T> either) {
            if (this.either == null) {
                this.either = either;
                done.countDown();
            }
        }

        public T await() throws E, InterruptedException {
            done.await();
            return either.get();
        }

        public T await(int millis) throws E, InterruptedException, TimeoutException {
            done.await(millis, TimeUnit.MILLISECONDS);
            if (either == null) {
                throw new TimeoutException();
            }
            return either.get();
        }
    }

    class Unchecked<T> extends Wait<RuntimeException, T> {}

}
