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
import em.zed.androidchat.Either;
import em.zed.androidchat.backend.ChatRepository;

public class FirebaseChatRepository implements ChatRepository {

    private final DatabaseReference chatRoot;

    public FirebaseChatRepository(DatabaseReference chatRoot) {
        this.chatRoot = chatRoot;
    }

    @Override
    public Log getLog(String sender, String receiver) {
        return new Log() {
            final String pairKey = Schema.legalize(pairKey(sender, receiver));
            final List<ChatMessage> history = new ArrayList<>();

            @Override
            public List<ChatMessage> history() throws InterruptedException {
                if (history.isEmpty()) {
                    Either.Unchecked<Void> result = new Either.Unchecked<>();
                    chatRoot.child(pairKey).addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(DataSnapshot dataSnapshot) {
                            for (DataSnapshot row : dataSnapshot.getChildren()) {
                                history.add(row.getValue(ChatMessage.class));
                            }
                            result.ok(null);
                        }

                        @Override
                        public void onCancelled(DatabaseError databaseError) {
                            result.err(databaseError.toException());
                        }
                    });
                    result.await();
                }
                return history;
            }

            @Override
            public Canceller snoop(OnReceive listener) {
                ChildEventListener onUpdate = new ChildEventListener() {
                    @Override
                    public void onChildAdded(DataSnapshot dataSnapshot, String s) {
                        history.add(dataSnapshot.getValue(ChatMessage.class));
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
                DatabaseReference ref = chatRoot.child(pairKey);
                ref.addChildEventListener(onUpdate);
                return () -> ref.removeEventListener(onUpdate);
            }
        };
    }

    @Override
    public ChatMessage put(String sender, String receiver, String message) {
        try {
            return put(sender, receiver, message, true);
        } catch (InterruptedException ignored) {
            return null;
        }
    }

    @Override
    public ChatMessage put(
            String sender,
            String receiver,
            String message,
            boolean fireAndForget) throws InterruptedException {
        String pairKey = Schema.legalize(pairKey(sender, receiver));
        boolean sentByMe = sender.compareTo(receiver) > 0;
        Map<String, Object> values = new HashMap<>();
        values.put(Schema.MESSAGE, message);
        values.put(Schema.SENDER, sender);
        values.put(Schema.SENT_BY_ME, sentByMe);
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
        m.setSentByMe(sentByMe);
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
