/*
 * This file is a part of the Lesson 1 project.
 */

package em.zed.androidchat.main;

import android.annotation.SuppressLint;

import java.util.Deque;
import java.util.List;
import java.util.concurrent.Future;

import edu.galileo.android.androidchat.contactlist.entities.User;

@SuppressLint("DefaultLocale")
public class StateRepr implements Main.Model.Case {

    public static String stringify(Main.Model state) {
        state.match(INSTANCE);
        return INSTANCE.repr;
    }

    private static StateRepr INSTANCE = new StateRepr();

    private String repr;

    private StateRepr() {}

    @Override
    public void booting() {
        repr = "booting";
    }

    @Override
    public void replay(Deque<Main.Model> backlog) {
        repr = "replay | backlog size: " + backlog.size();
    }

    @Override
    public void loading(Future<Main.Model> task) {
        repr = "loading";
    }

    @Override
    public void loaded(String userEmail, List<User> contacts) {
        repr = String.format(
                "loaded | userEmail: %s; contacts size: %d",
                userEmail,
                contacts.size());
    }

    @Override
    public void idle(List<User> contacts) {
        repr = String.format("idle | contacts size: %d", contacts.size());
    }

    @Override
    public void removing(Future<Main.Model> task) {
        repr = "removing";
    }

    @Override
    public void removed(User contact) {
        repr = "removed | contact email: " + contact.getEmail();
    }

    @Override
    public void adding(Future<Main.Model> task) {
        repr = "adding";
    }

    @Override
    public void added(User contact) {
        repr = "added | contact email: " + contact.getEmail();
    }

    @Override
    public void loggingOut(Future<Main.Model> task) {
        repr = "logging-out";
    }

    @Override
    public void loggedOut() {
        repr = "logged-out";
    }

    @Override
    public void willChatWith(User contact) {
        repr = "will-chat-with | contact email: " + contact.getEmail();
    }

    @Override
    public void error(Throwable e) {
        repr = String.format("error | cause: %s; message: %s", e.getCause(), e.getMessage());
    }

}