/*
 * This file is a part of the Lesson 1 project.
 */

package em.zed.androidchat;

import org.junit.Test;

import java.util.concurrent.CountDownLatch;
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
}