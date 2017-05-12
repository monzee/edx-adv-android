/*
 * This file is a part of the Lesson 1 project.
 */

package em.zed.androidchat.backend.firebase;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseException;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;

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
        Either.Wait<DatabaseException, User> result = new Either.Wait<>();
        usersNode.child(legalize(email))
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
        CountDownLatch done = new CountDownLatch(1);
        usersNode.child(legalize(user.getEmail()))
                .setValue(user, (databaseError, databaseReference) -> done.countDown());
        done.await();
    }

    @Override
    public Canceller onUpdate(String email, OnUserUpdate listener) {
        return onUpdate(Globals.IMMEDIATE, email, listener);
    }

    @Override
    public Canceller onUpdate(Executor ex, String email, OnUserUpdate listener) {
        ValueEventListener onChange = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot node) {
                User user = node.getValue(User.class);
                ex.execute(() -> listener.updated(user));
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
            }
        };
        DatabaseReference ref = usersNode.child(legalize(email));
        ref.addValueEventListener(onChange);
        return () -> ref.removeEventListener(onChange);
    }

    private static String legalize(String key) {
        return key.replaceAll("[.$#\\[\\]]", "-");
    }

}
