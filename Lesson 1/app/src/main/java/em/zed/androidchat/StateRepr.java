/*
 * This file is a part of the Lesson 1 project.
 */

package em.zed.androidchat;

import android.annotation.SuppressLint;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.Future;

import edu.galileo.android.androidchat.chat.entities.ChatMessage;
import edu.galileo.android.androidchat.contactlist.entities.User;
import em.zed.androidchat.main.Main;
import em.zed.androidchat.main.add.Add;
import em.zed.androidchat.talk.Talk;

@SuppressLint("DefaultLocale")
public class StateRepr implements Talk.View, Main.View, Add.View {

    public static String stringify(Talk.Model state) {
        state.render(INSTANCE);
        return INSTANCE.repr;
    }

    public static String stringify(Main.Model state) {
        state.render(INSTANCE);
        return INSTANCE.repr;
    }

    public static String stringify(Add.Model state) {
        state.render(INSTANCE);
        return INSTANCE.repr;
    }

    private static StateRepr INSTANCE = new StateRepr();
    private String repr;

    private StateRepr() {}

    @Override
    public void booting(Queue<Talk.Model> backlog) {
        repr = "booting | backlog size: " + backlog.size();
    }

    @Override
    public void talking(String email, boolean online) {
        repr = "talking | email: " + email + "; online: " + online;
    }

    @Override
    public void fetchingLog(Future<Talk.Model> task) {
        repr = "fetching-log";
    }

    @Override
    public void fetchedLog(List<ChatMessage> chatLog) {
        repr = "fetched-log | log size: " + chatLog.size();
    }

    @Override
    public void saying(Future<Talk.Model> task) {
        repr = "saying";
    }

    @Override
    public void said(ChatMessage message) {
        repr = "said | sender: " + message.getSender() + "; msg: " + message.getMsg();
    }

    @Override
    public void heard(ChatMessage message) {
        repr = "heard | sender: " + message.getSender() + "; msg: " + message.getMsg();
    }

    @Override
    public void loggingIn() {
        repr = "logging-in";
    }

    @Override
    public void booting() {
        repr = "booting";
    }

    @Override
    public void fold(Queue<Main.Model> backlog) {
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
    public void idle() {
        repr = "idle";
    }

    @Override
    public void invalid(String message) {
        repr = "invalid | message: " + message;
    }

    @Override
    public void adding(Future<Add.Model> task) {
        repr = "adding";
    }

    @Override
    public void added(String email, boolean online) {
        repr = "added | email: " + email + "; online: " + online;
    }

    @Override
    public void addFailed(String reason) {
        repr = "add-failed | reason: " + reason;
    }

    @Override
    public void error(Throwable e) {
        repr = String.format("error | cause: %s; message: %s", e.getCause(), e.getMessage());
    }

}
