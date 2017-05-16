/*
 * This file is a part of the Lesson 1 project.
 */

package em.zed.androidchat.backend.firebase;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseAuthWeakPasswordException;
import com.google.firebase.auth.FirebaseUser;

import edu.galileo.android.androidchat.contactlist.entities.User;
import em.zed.androidchat.Either;
import em.zed.androidchat.Globals;
import em.zed.androidchat.LogLevel;
import em.zed.androidchat.Logger;
import em.zed.androidchat.backend.Auth;
import em.zed.androidchat.backend.Contacts;
import em.zed.androidchat.backend.UserRepository;

public class FirebaseEmailAuth implements Auth.Service {

    private final UserRepository users;
    private final Contacts.Service contacts;
    private final FirebaseAuth fbAuth;
    private final Logger log;

    public FirebaseEmailAuth(
            UserRepository users,
            Contacts.Service contacts,
            FirebaseAuth fbAuth) {
        this(users, contacts, fbAuth, null);
    }

    public FirebaseEmailAuth(
            UserRepository users,
            Contacts.Service contacts,
            FirebaseAuth fbAuth,
            Logger log) {
        this.users = users;
        this.contacts = contacts;
        this.fbAuth = fbAuth;
        this.log = log;
    }

    @Override
    public boolean signUp(String email, String password)
            throws Auth.SignUpError, InterruptedException {
        Either.Wait<Auth.SignUpError, Boolean> result = new Either.Wait<>();
        fbAuth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(Globals.IMMEDIATE, authResult -> result.ok(true))
                .addOnFailureListener(Globals.IMMEDIATE, e -> {
                    LogLevel.E.to(log, e, "#signUp");
                    if (e instanceof FirebaseAuthUserCollisionException) {
                        result.ok(false);
                    } else if (e instanceof FirebaseAuthWeakPasswordException ||
                            e.getMessage().contains("WEAK_PASSWORD")) {
                        result.err(new Auth.PasswordRejected());
                    } else if (e instanceof FirebaseAuthInvalidCredentialsException) {
                        result.err(new Auth.EmailRejected(email));
                    } else {
                        result.err(new Auth.SignUpError(e.getMessage()));
                    }
                });
        boolean ok = result.await();
        if (ok) {
            users.put(new User(email, true, null), true);
        }
        return ok;
    }

    @Override
    public Auth.Tokens login(String email, String password)
            throws Auth.AuthError, InterruptedException {
        String refresh = tokenize(email, password);
        Either.Wait<Auth.AuthError, Auth.Tokens> result = new Either.Wait<>();
        fbAuth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(Globals.IMMEDIATE, authResult -> {
                    FirebaseUser u = authResult.getUser();
                    Auth.Tokens t = new Auth.Tokens(u.getUid(), refresh);
                    result.ok(t);
                })
                .addOnFailureListener(Globals.IMMEDIATE, e -> {
                    if (e instanceof FirebaseAuthInvalidUserException) {
                        result.err(new Auth.UnknownEmail(email));
                    } else if (e instanceof FirebaseAuthInvalidCredentialsException) {
                        result.err(new Auth.BadPassword(email));
                    }
                });
        Auth.Tokens t = result.await();
        // this has to be here because the success listener is called in the
        // main thread
        contacts.broadcastStatus(new User(email, true, null));
        return t;
    }

    @Override
    public Auth.Session start(Auth.Tokens tokens) {
        return new Auth.Session() {
            Auth.Tokens t = tokens;

            @Override
            public Auth.Status check() throws InterruptedException {
                FirebaseUser user = fbAuth.getCurrentUser();
                if (user != null && t.auth != null && t.auth.equals(user.getUid())) {
                    return Auth.Status.LOGGED_IN;
                }
                return t.auth == null ? Auth.Status.GUEST : Auth.Status.EXPIRED;
            }

            @Override
            public boolean refresh() throws Auth.AuthError, InterruptedException {
                String[] parts = split(t.refresh);
                if (parts == null) {
                    return false;
                }
                t = login(parts[0], parts[1]);
                return true;
            }

            @Override
            public Auth.Tokens current() {
                return t;
            }

            @Override
            public User minimalProfile() {
                FirebaseUser user = fbAuth.getCurrentUser();
                if (user == null) {
                    return null;
                }
                return new User(user.getEmail(), true, null);
            }

            @Override
            public void logout() throws InterruptedException {
                FirebaseUser currentUser = fbAuth.getCurrentUser();
                if (currentUser == null) {
                    return;
                }
                User u = users.getByEmail(currentUser.getEmail());
                u.setOnline(User.OFFLINE);
                contacts.broadcastStatus(u);
                fbAuth.signOut();
            }
        };
    }

    private static String tokenize(String left, String right) {
        return left + '\0' + right;
    }

    private static String[] split(String token) {
        if (token != null) {
            int i = token.indexOf(0);
            if (i > -1) {
                return new String[]{token.substring(0, i), token.substring(i)};
            }
        }
        return null;
    }

}
