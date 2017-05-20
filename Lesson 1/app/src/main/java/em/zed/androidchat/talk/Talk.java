/*
 * This file is a part of the Lesson 1 project.
 */

package em.zed.androidchat.talk;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.Future;

import edu.galileo.android.androidchat.chat.entities.ChatMessage;
import em.zed.androidchat.backend.ChatRepository;

public interface Talk {

    interface Model {
        void render(View v);
    }

    interface View {
        void booting(Queue<Model> backlog);

        void talking(String email, boolean online);
        void fetchingLog(Future<Model> task);
        void fetchedLog(List<ChatMessage> chatLog);

        void idle();

        void saying(Future<Model> task);
        void said(ChatMessage message);
        void heard(ChatMessage message);

        void loggingIn();
        void error(Throwable e);
    }

    interface Renderer {
        void move(Model newState);
        void apply(Model newState);
        void apply(Future<Model> task);
        Queue<Model> dump();
    }

    interface SourcePort {
        Runnable listen(ChatRepository.OnReceive listener);
        Model fetchLog(String otherEmail);
        Model say(String to, CharSequence message);
    }

    interface TargetPort {
        void replace(List<ChatMessage> messages);
        Model pull();
        Model push(ChatMessage message);
    }

}
