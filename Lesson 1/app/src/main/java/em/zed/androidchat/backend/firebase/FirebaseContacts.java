/*
 * This file is a part of the Lesson 1 project.
 */

package em.zed.androidchat.backend.firebase;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;

import edu.galileo.android.androidchat.contactlist.entities.User;
import em.zed.androidchat.Either;
import em.zed.androidchat.backend.Contacts;

public class FirebaseContacts implements Contacts.Service {

    private final DatabaseReference usersNode;

    public FirebaseContacts(DatabaseReference usersNode) {
        this.usersNode = usersNode;
    }

    @Override
    public void fillContacts(User user) throws InterruptedException {
        User fetched = getUser(user.getEmail());
        Map<String, Boolean> fixed = new HashMap<>();
        Map<String, Boolean> contacts = fetched.getContacts();
        for (String key : contacts.keySet()) {
            fixed.put(Schema.illegalize(key), contacts.get(key));
        }
        user.setContacts(fixed);
    }

    @Override
    public boolean addContact(String adder, String addee) throws InterruptedException {
        boolean result = getUser(addee).isOnline();
        Map<String, Object> nodes = new HashMap<>();
        String left = Schema.legalize(adder);
        String right = Schema.legalize(addee);
        nodes.put(Schema.pathTo(left, Schema.CONTACTS, right), result);
        nodes.put(Schema.pathTo(right, Schema.CONTACTS, left), true);
        usersNode.updateChildren(nodes);
        return result;
    }

    @Override
    public boolean removeContact(String source, String target) throws InterruptedException {
        String left = Schema.legalize(source);
        String right = Schema.legalize(target);
        usersNode.child(left).child(Schema.CONTACTS).child(right).removeValue();
        usersNode.child(right).child(Schema.CONTACTS).child(left).removeValue();
        return getUser(target).isOnline();
    }

    @Override
    public void broadcastStatus(User user) throws InterruptedException {
        if (user.getContacts() == null) {
            fillContacts(user);
        }
        String fk = Schema.legalize(user.getEmail());
        boolean status = user.isOnline();
        Map<String, Object> nodes = new HashMap<>();
        nodes.put(Schema.pathTo(fk, Schema.ONLINE), status);
        for (String email : user.getContacts().keySet()) {
            // email is already encoded
            nodes.put(Schema.pathTo(email, Schema.CONTACTS, fk), status);
        }
        usersNode.updateChildren(nodes);
    }

    private User getUser(String email) throws InterruptedException {
        Either.Wait<RuntimeException, User> result = new Either.Wait<>();
        usersNode.child(Schema.legalize(email))
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        result.ok(dataSnapshot.getValue(User.class));
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {
                        result.err(databaseError.toException());
                    }
                });
        return result.await();
    }
}
