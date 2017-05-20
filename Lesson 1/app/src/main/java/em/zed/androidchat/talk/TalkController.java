/*
 * This file is a part of the Lesson 1 project.
 */

package em.zed.androidchat.talk;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import edu.galileo.android.androidchat.chat.entities.ChatMessage;
import em.zed.androidchat.LogLevel;
import em.zed.androidchat.Logger;
import em.zed.androidchat.backend.Auth;
import em.zed.androidchat.backend.ChatRepository;

public class TalkController implements Talk.SourcePort {

    private final ExecutorService io;
    private final Logger log;
    private final ChatRepository chats;
    private final Auth.Session session;

    private ChatRepository.Log chatLog;

    public TalkController(
            ExecutorService io,
            Logger log,
            ChatRepository chats,
            Auth.Session session) {
        this.io = io;
        this.log = log;
        this.chats = chats;
        this.session = session;
    }

    @Override
    public Runnable listen(ChatRepository.OnReceive listener) {
        if (chatLog != null) {
            return chatLog.snoop(listener);
        }
        return () -> {};
    }

    @Override
    public Talk.Model fetchLog(String otherEmail) {
        LogLevel.I.to(log, "#fetchLog(%s)", otherEmail);
        Future<Talk.Model> f = io.submit(() -> {
            switch (session.check()) {
                case LOGGED_IN:
                    chatLog = chats.getLog(session.minimalProfile().getEmail(), otherEmail);
                    List<ChatMessage> history = chatLog.history();
                    return v -> v.fetchedLog(history);
                case EXPIRED:
                    if (session.refresh()) {
                        return fetchLog(otherEmail);
                    }
            }
            return Talk.View::loggingIn;
        });
        return v -> v.fetchingLog(f);
    }

    @Override
    public Talk.Model say(String to, CharSequence message) {
        LogLevel.I.to(log, "#say(%s, %s)", to, message);
        if (to.isEmpty() || message.length() == 0) {
            return Talk.View::idle;
        }
        Future<Talk.Model> f = io.submit(() -> {
            switch (session.check()) {
                case LOGGED_IN:
                    String me = session.minimalProfile().getEmail();
                    String msg = message.toString();
                    chats.put(me, to, msg);
                    ChatMessage m = new ChatMessage(me, msg);
                    return v -> v.said(m);
                case EXPIRED:
                    if (session.refresh()) {
                        return say(to, message);
                    }
            }
            return Talk.View::loggingIn;
        });
        return v -> v.saying(f);
    }

}
