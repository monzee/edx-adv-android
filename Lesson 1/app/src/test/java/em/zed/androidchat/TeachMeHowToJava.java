/*
 * This file is a part of the Lesson 1 project.
 */

package em.zed.androidchat;

import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class TeachMeHowToJava {
    @SuppressWarnings("ConstantConditions")
    @Test
    public void can_i_instanceof_check_a_null_YES_AND_IT_IS_ALWAYS_FALSE() {
        TeachMeHowToJava e = null;
        assertFalse("at least it doesn't NPE", e instanceof TeachMeHowToJava);
    }

    @Test
    public void what_happens_when_a_cdl_await_times_out() {
        CountDownLatch cdl = new CountDownLatch(1);
        try {
            cdl.await(1, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            fail("you'd think it'd throw something, but apparently not.");
        }
    }

    @Test(timeout = 1000)
    public void can_i_interrupt_a_thread_waiting_on_a_regular_object_NO_USE_A_CDL_INSTEAD()
            throws InterruptedException {
        ExecutorService ex = Executors.newSingleThreadExecutor();
        CountDownLatch setup = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(1);
        Future<?> f = ex.submit(() -> {
            try {
                setup.countDown();
                new CountDownLatch(1).await();
            } catch (InterruptedException e) {
                done.countDown();
            }
        });
        setup.await();
        f.cancel(true);
        done.await();
    }
}