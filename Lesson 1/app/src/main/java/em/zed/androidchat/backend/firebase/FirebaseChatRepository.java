/*
 * This file is a part of the Lesson 1 project.
 */

package em.zed.androidchat.backend.firebase;

import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.galileo.android.androidchat.chat.entities.ChatMessage;
import em.zed.androidchat.backend.ChatRepository;
import em.zed.androidchat.util.Either;

public class FirebaseChatRepository implements ChatRepository {

    private final DatabaseReference chatRoot;

    public FirebaseChatRepository(DatabaseReference chatRoot) {
        this.chatRoot = chatRoot;
    }

    @Override
    public Log getLog(String sender, String receiver) {
        return new Log() {
            final DatabaseReference ref =
                    chatRoot.child(Schema.legalize(pairKey(sender, receiver)));

            @Override
            public List<ChatMessage> history() throws InterruptedException {
                Either.Unchecked<List<ChatMessage>> result = new Either.Unchecked<>();
                ref.addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        List<ChatMessage> history = new ArrayList<>();
                        for (DataSnapshot row : dataSnapshot.getChildren()) {
                            ChatMessage m = row.getValue(ChatMessage.class);
                            m.setSentByMe(sender.equals(m.getSender()));
                            history.add(m);
                        }
                        result.ok(history);
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {
                        result.err(databaseError.toException());
                    }
                });
                return result.await();
            }

            @Override
            public Runnable snoop(OnReceive listener) {
                ChildEventListener onUpdate = new ChildEventListener() {
                    @Override
                    public void onChildAdded(DataSnapshot dataSnapshot, String s) {
                        ChatMessage message = dataSnapshot.getValue(ChatMessage.class);
                        message.setSentByMe(sender.equals(message.getSender()));
                        listener.got(message);
                    }

                    @Override
                    public void onChildChanged(DataSnapshot dataSnapshot, String s) {

                    }

                    @Override
                    public void onChildRemoved(DataSnapshot dataSnapshot) {

                    }

                    @Override
                    public void onChildMoved(DataSnapshot dataSnapshot, String s) {

                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {

                    }
                };
                ref.addChildEventListener(onUpdate);
                return () -> ref.removeEventListener(onUpdate);
            }
        };
    }

    @Override
    public ChatMessage put(String sender, String receiver, String message) {
        try {
            return put(sender, receiver, message, true);
        } catch (InterruptedException impossible) {
            throw new RuntimeException("nothing is", impossible);
        }
    }

    @Override
    public ChatMessage put(
            String sender,
            String receiver,
            String message,
            boolean fireAndForget) throws InterruptedException {
        String pairKey = Schema.legalize(pairKey(sender, receiver));
        Map<String, Object> values = new HashMap<>();
        values.put(Schema.MESSAGE, message);
        values.put(Schema.SENDER, sender);
        values.put(Schema.SENT_BY_ME, sender.compareTo(receiver) > 0);
        DatabaseReference newRow = chatRoot.child(pairKey).push();
        if (fireAndForget) {
            newRow.updateChildren(values);
        } else {
            Either.Unchecked<Void> result = new Either.Unchecked<>();
            newRow.updateChildren(values, (databaseError, databaseReference) -> {
                if (databaseError == null) {
                    result.ok(null);
                } else {
                    result.err(databaseError.toException());
                }
            });
            result.await();
        }
        ChatMessage m = new ChatMessage(sender, message);
        m.setSentByMe(true);
        return m;
    }

    static String pairKey(String sender, String receiver) {
        if (sender.compareTo(receiver) < 1) {
            return sender + "___" + receiver;
        } else {
            return receiver + "___" + sender;
        }
    }

}
