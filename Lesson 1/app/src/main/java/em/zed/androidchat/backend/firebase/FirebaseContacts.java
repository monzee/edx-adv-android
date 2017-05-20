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
import em.zed.androidchat.backend.Contacts;
import em.zed.androidchat.util.Either;

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
        if (contacts != null) for (String key : contacts.keySet()) {
            // TODO: consider adding an EmailKey -> email map in the db
            fixed.put(Schema.illegalize(key), contacts.get(key));
        }
        user.setContacts(fixed);
    }

    @Override
    public boolean addContact(String adder, String addee) throws InterruptedException {
        boolean result = getUser(addee).isOnline();
        Map<String, Object> nodes = new HashMap<>();
        nodes.put(Schema.pathTo(adder, Schema.CONTACTS, addee), result);
        nodes.put(Schema.pathTo(addee, Schema.CONTACTS, adder), true);
        usersNode.updateChildren(nodes);
        return result;
    }

    @Override
    public boolean removeContact(String source, String target) throws InterruptedException {
        Map<String, Object> nodes = new HashMap<>();
        nodes.put(Schema.pathTo(source, Schema.CONTACTS, target), null);
        nodes.put(Schema.pathTo(target, Schema.CONTACTS, source), null);
        usersNode.updateChildren(nodes);
        return getUser(target).isOnline();
    }

    @Override
    public void broadcastStatus(User user) throws InterruptedException {
        if (user.getContacts() == null) {
            fillContacts(user);
        }
        String fk = user.getEmail();
        boolean status = user.isOnline();
        Map<String, Object> nodes = new HashMap<>();
        nodes.put(Schema.pathTo(fk, Schema.ONLINE), status);
        for (String email : user.getContacts().keySet()) {
            nodes.put(Schema.pathTo(email, Schema.CONTACTS, fk), status);
        }
        usersNode.updateChildren(nodes);
    }

    @Override
    public boolean exists(String email) throws InterruptedException {
        return getUser(email) != null;
    }

    @Override
    public Contacts.Is checkOnlineStatus(String email, Map<String, Boolean> contacts) {
        Boolean value = contacts.get(Schema.legalize(email));
        if (value == null) {
            return Contacts.Is.ABSENT;
        }
        if (value) {
            return Contacts.Is.ONLINE;
        }
        return Contacts.Is.OFFLINE;
    }

    private User getUser(String email) throws InterruptedException {
        Either.Unchecked<User> result = new Either.Unchecked<>();
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
