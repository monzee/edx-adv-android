/*
 * This file is a part of the Lesson 1 project.
 */

package em.zed.androidchat.backend;

import java.util.List;

import edu.galileo.android.androidchat.chat.entities.ChatMessage;

public interface ChatRepository {

    interface OnReceive {
        void got(ChatMessage message);
    }

    interface Log {
        List<ChatMessage> history() throws InterruptedException;

        /**
         * @return cancels the listener when run
         */
        Runnable snoop(OnReceive listener);
    }

    Log getLog(String sender, String receiver);
    ChatMessage put(String sender, String receiver, String message);
    ChatMessage put(String sender, String receiver, String message, boolean fireAndForget) throws InterruptedException;

}
