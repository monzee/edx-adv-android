/*
 * This file is a part of the Lesson 1 project.
 */

package em.zed.androidchat;

import org.junit.Test;

import static org.junit.Assert.*;

public class TeachMeHowToJava {
    @SuppressWarnings("ConstantConditions")
    @Test
    public void can_i_instanceof_check_a_null_YES_AND_IT_IS_ALWAYS_FALSE() {
        TeachMeHowToJava e = null;
        assertFalse("at least it doesn't NPE", e instanceof TeachMeHowToJava);
    }
}