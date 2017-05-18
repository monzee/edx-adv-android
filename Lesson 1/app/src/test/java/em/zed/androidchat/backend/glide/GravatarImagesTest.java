/*
 * This file is a part of the Lesson 1 project.
 */

package em.zed.androidchat.backend.glide;

import org.junit.Test;

import static org.junit.Assert.*;

public class GravatarImagesTest {

    @Test
    public void md5Digest() throws Exception {
        assertEquals("acbd18db4cc2f85cedef654fccc4a4d8", GravatarImages.md5Digest("foo"));
        assertEquals("37b51d194a7513e45b56f6524f2d51f2", GravatarImages.md5Digest("bar"));
        assertEquals("7ba9e6047acf33ff5d40fa5b3046060c", GravatarImages.md5Digest("password!123$"));
    }

}