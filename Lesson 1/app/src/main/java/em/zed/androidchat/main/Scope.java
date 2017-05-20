/*
 * This file is a part of the Lesson 1 project.
 */

package em.zed.androidchat.main;

import android.widget.ImageView;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import em.zed.androidchat.Logger;
import em.zed.androidchat.backend.Auth;
import em.zed.androidchat.backend.Files;
import em.zed.androidchat.backend.Image;
import em.zed.androidchat.main.add.Add;
import em.zed.androidchat.main.add.AddController;

import static edu.galileo.android.androidchat.AndroidChatApplication.GLOBALS;

class Scope {

    final ExecutorService io = GLOBALS.io();
    final ExecutorService junction = Executors.newSingleThreadExecutor(runnable -> {
        Thread t = new Thread(runnable);
        t.setName("main-activity-joiner-thread");
        return t;
    });
    final Logger log = GLOBALS.logger();
    final Files.Service files = GLOBALS.dataFiles();
    final Image.Service<ImageView> gravatars = GLOBALS.images();
    final Queue<Main.Model> backlog = new ArrayDeque<>();

    private final MainController.Builder ctrlBuilder = new MainController
            .Builder(GLOBALS.users(), GLOBALS.contacts())
            .withExecutorService(io)
            .withLogger(log);

    Main.SourcePort actions = ctrlBuilder.build(Auth.NO_SESSION);
    Main.Model state = Main.View::booting;
    String subtitle;
    Add.Model addState = Add.View::idle;
    Add.Controller addActions;

    void login(Auth.Tokens t) {
        Auth.Session session = GLOBALS.auth().start(t);
        actions = ctrlBuilder.build(session);
        addActions = new AddController(io, GLOBALS.contacts(), session);
    }

}
