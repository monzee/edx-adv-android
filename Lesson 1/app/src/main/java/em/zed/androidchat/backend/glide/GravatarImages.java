/*
 * This file is a part of the Lesson 1 project.
 */

package em.zed.androidchat.backend.glide;

import android.widget.ImageView;

import com.bumptech.glide.RequestManager;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import em.zed.androidchat.backend.Image;

public class GravatarImages implements Image.Service<ImageView> {

    private static final String GRAVATAR_URL = "http://www.gravatar.com/avatar/";
    private static final String[] BYTE_MAP = new String[256];

    static {
        for (int i = 0; i < 256; i++) {
            BYTE_MAP[i] = String.format("%02x", i);
        }
    }

    private final RequestManager glide;

    public GravatarImages(RequestManager glide) {
        this.glide = glide;
    }

    @Override
    public Image.LoadInto<ImageView> load(String identifier) {
        String url = GRAVATAR_URL + md5Digest(identifier) + "?s=72";
        return view -> glide.load(url).into(view);
    }

    static String md5Digest(String s) {
        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            StringBuilder sb = new StringBuilder();
            for (byte b : md5.digest(s.getBytes())) {
                sb.append(BYTE_MAP[0xFF & b]);
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

}
