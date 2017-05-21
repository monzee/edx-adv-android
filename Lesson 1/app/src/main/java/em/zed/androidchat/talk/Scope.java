/*
 * This file is a part of the Lesson 1 project.
 */

package em.zed.androidchat.talk;

import android.widget.ImageView;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import edu.galileo.android.androidchat.contactlist.entities.User;
import em.zed.androidchat.Logger;
import em.zed.androidchat.backend.Auth;
import em.zed.androidchat.backend.Files;
import em.zed.androidchat.backend.Image;

import static edu.galileo.android.androidchat.AndroidChatApplication.GLOBALS;

class Scope {

    final ExecutorService io = GLOBALS.io();
    final ExecutorService junction = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r);
        t.setName("talk-joiner-thread");
        return t;
    });
    final Files.Service files = GLOBALS.dataFiles();
    final Logger log = GLOBALS.logger();
    final Image.Service<ImageView> images = GLOBALS.images();
    final User victim;
    private final Auth.Service auth = GLOBALS.auth();

    Talk.Model state;
    Talk.Model checkpoint;
    Talk.SourcePort actions;
    int overlap;

    Scope(String email, boolean online) {
        victim = new User(email, online, null);
        state = v -> v.talking(email, online);
    }

    void login(Auth.Tokens tokens) {
        actions = new TalkController(io, log, GLOBALS.chats(), auth.start(tokens));
    }

}
