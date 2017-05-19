/*
 * This file is a part of the Lesson 1 project.
 */

package em.zed.androidchat.backend.firebase;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;

import edu.galileo.android.androidchat.contactlist.entities.User;
import em.zed.androidchat.Either;
import em.zed.androidchat.Globals;
import em.zed.androidchat.backend.UserRepository;

public class FirebaseUserRepository implements UserRepository {

    private final DatabaseReference usersNode;

    public FirebaseUserRepository(DatabaseReference usersNode) {
        this.usersNode = usersNode;
    }

    @Override
    public User getByEmail(String email) throws InterruptedException {
        Either.Unchecked<User> result = new Either.Unchecked<>();
        usersNode.child(Schema.legalize(email))
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot node) {
                        result.ok(node.exists() ? node.getValue(User.class) : null);
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {
                        result.err(databaseError.toException());
                    }
                });
        return result.await();
    }

    @Override
    public void put(User user) throws InterruptedException {
        put(user, false);
    }

    @Override
    public void put(User user, boolean fireAndForget) throws InterruptedException {
        CountDownLatch done = new CountDownLatch(1);
        String email = user.getEmail();
        DatabaseReference node = usersNode.child(Schema.legalize(email));
        if (user.getContacts() != null) {
            node.setValue(user, (_err, _ref) -> done.countDown());
        } else {
            Map<String, Object> values = new HashMap<>();
            values.put(Schema.EMAIL, email);
            values.put(Schema.ONLINE, user.isOnline());
            node.updateChildren(values, (_err, _ref) -> done.countDown());
        }
        if (!fireAndForget) {
            done.await();
        }
    }

    @Override
    public Canceller onUpdate(String email, OnContactsUpdate listener) {
        return onUpdate(Globals.IMMEDIATE, email, listener);
    }

    @Override
    public Canceller onUpdate(Executor ex, String email, OnContactsUpdate listener) {
        ValueEventListener profileChange = new ValueEventListener() {
            boolean first = true;

            @Override
            public void onDataChange(DataSnapshot node) {
                if (first) {
                    first = false;
                    return;
                }
                User u = node.getValue(User.class);
                List<User> fixed = new ArrayList<>();
                Map<String, Boolean> contacts = u.getContacts();
                if (contacts != null) {
                    for (Map.Entry<String, Boolean> e : contacts.entrySet()) {
                        fixed.add(new User(
                                Schema.illegalize(e.getKey()),
                                e.getValue(),
                                null));
                    }
                }
                ex.execute(() -> listener.updated(fixed));
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
            }
        };
        DatabaseReference ref = usersNode.child(Schema.legalize(email));
        ref.addValueEventListener(profileChange);
        return () -> ref.removeEventListener(profileChange);
    }

}
