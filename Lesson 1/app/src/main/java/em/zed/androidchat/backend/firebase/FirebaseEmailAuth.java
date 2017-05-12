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
import em.zed.androidchat.backend.UserRepository;

public class FirebaseEmailAuth implements Auth.Service {

    private final UserRepository users;
    private final FirebaseAuth fbAuth;
    private final Logger log;

    public FirebaseEmailAuth(UserRepository users, FirebaseAuth fbAuth) {
        this(users, fbAuth, null);
    }

    public FirebaseEmailAuth(UserRepository users, FirebaseAuth fbAuth, Logger log) {
        this.users = users;
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
                    LogLevel.E.to(log, e, "#signUp(%s, %s)", email, password);
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
            users.put(new User(email, true, null));
        }
        return ok;
    }

    @Override
    public Auth.Tokens login(String email, String password)
            throws Auth.AuthError, InterruptedException {
        Either.Wait<Auth.AuthError, Auth.Tokens> result = new Either.Wait<>();
        fbAuth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(Globals.IMMEDIATE, authResult -> {
                    FirebaseUser u = authResult.getUser();
                    Auth.Tokens t = new Auth.Tokens(u.getUid(), null);
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
        User u = users.getByEmail(email);
        u.setOnline(User.ONLINE);
        users.put(u);
        return t;
    }

    @Override
    public Auth.Tokens refresh(String token) throws Auth.AuthError {
        throw new Auth.CannotRefresh();
    }

    @Override
    public Auth.Status check(String token) {
        FirebaseUser user = fbAuth.getCurrentUser();
        if (user != null && token != null && token.equals(user.getUid())) {
            return Auth.Status.LOGGED_IN;
        }
        return Auth.Status.GUEST;
    }

}
