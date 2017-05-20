/*
 * This file is a part of the Lesson 1 project.
 */

package em.zed.androidchat.talk;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnClick;
import de.hdodenhof.circleimageview.CircleImageView;
import edu.galileo.android.androidchat.R;
import edu.galileo.android.androidchat.chat.entities.ChatMessage;
import edu.galileo.android.androidchat.contactlist.entities.User;
import em.zed.androidchat.LogLevel;
import em.zed.androidchat.Logger;
import em.zed.androidchat.Pending;
import em.zed.androidchat.StateRepr;
import em.zed.androidchat.backend.Auth;
import em.zed.androidchat.backend.Files;
import em.zed.androidchat.backend.Image;
import em.zed.androidchat.concerns.SessionFragment;

import static edu.galileo.android.androidchat.AndroidChatApplication.GLOBALS;

public class TalkActivity extends AppCompatActivity implements
        Talk.Renderer, Talk.View, SessionFragment.Pipe {

    public static final String EMAIL = "arg-email";
    public static final String ONLINE = "arg-online";

    private static class Scope {
        final ExecutorService junction = Executors.newSingleThreadExecutor();
        final ExecutorService io = GLOBALS.io();
        final Files.Service files = GLOBALS.dataFiles();
        final Logger log = GLOBALS.logger();
        final Image.Service<ImageView> images = GLOBALS.images();
        final Auth.Service auth = GLOBALS.auth();
        final User victim;
        Talk.Model state;
        Talk.SourcePort actions = new TalkController(io, log, null, Auth.NO_SESSION);

        Scope(String email, boolean online) {
            victim = new User(email, online, null);
            state = v -> v.talking(victim.getEmail(), victim.isOnline());
        }

        void login(Auth.Tokens tokens) {
            actions = new TalkController(io, log, GLOBALS.chats(), auth.start(tokens));
        }
    }

    @Bind(R.id.toolbar) Toolbar toolbar;
    @Bind(R.id.txtUser) TextView txtUser;
    @Bind(R.id.txtStatus) TextView txtStatus;
    @Bind(R.id.editTxtMessage) EditText inputMessage;
    @Bind(R.id.messageRecyclerView) RecyclerView recyclerView;
    @Bind(R.id.imgAvatar) CircleImageView imgAvatar;

    private final Queue<Pending<Talk.Model>> inProgress = new ArrayDeque<>();
    private Scope my;
    private SessionFragment session;
    private Talk.Model checkpoint;
    private Runnable cancelWatch;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        my = (Scope) getLastCustomNonConfigurationInstance();
        if (my == null) {
            Intent i = getIntent();
            my = new Scope(i.getStringExtra(EMAIL), i.getBooleanExtra(ONLINE, false));
        }
        if (checkpoint == null) {
            checkpoint = my.state;
        }
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);
        ButterKnife.bind(this);
        setSupportActionBar(toolbar);
        SessionFragment.attach(getSupportFragmentManager());
        String email = my.victim.getEmail();
        txtUser.setText(email);
        if (my.victim.isOnline()) {
            txtStatus.setText("online");
            txtStatus.setTextColor(Color.GREEN);
        } else {
            txtStatus.setText("offline");
            txtStatus.setTextColor(Color.RED);
        }
        my.images.load(email).into(imgAvatar);
    }

    @Override
    public Object onRetainCustomNonConfigurationInstance() {
        return my;
    }

    @Override
    public void onAttachFragment(Fragment fragment) {
        if (fragment instanceof SessionFragment) {
            session = ((SessionFragment) fragment).inject(my.io, my.files, my.log, this);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        my.state.render(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (cancelWatch != null) {
            cancelWatch.run();
            cancelWatch = null;
        }
        my.state = v -> v.booting(dump());
    }

    @Override
    public void move(Talk.Model newState) {
        my.state = newState;
        LogLevel.D.to(my.log, "-> %s", StateRepr.stringify(newState));
    }

    @Override
    public void apply(Talk.Model newState) {
        runOnUiThread(() -> {
            move(newState);
            newState.render(this);
        });
    }

    @Override
    public void apply(Future<Talk.Model> task) {
        AtomicReference<Pending<Talk.Model>> pending = new AtomicReference<>();
        pending.set(new Pending<>(my.state, my.junction.submit(() -> {
            try {
                apply(task.get());
            } catch (ExecutionException e) {
                apply(v -> v.error(e));
            } catch (InterruptedException ignored) {
            } finally {
                inProgress.remove(pending.get());
            }
        })));
        inProgress.add(pending.get());
        move(Talk.View::noop);
    }

    @Override
    public Queue<Talk.Model> dump() {
        Queue<Talk.Model> backlog = new ArrayDeque<>();
        for (Pending<Talk.Model> p : inProgress) {
            backlog.add(p.cancel());
        }
        backlog.add(my.state);
        return backlog;
    }

    @Override
    public void booting(Queue<Talk.Model> backlog) {
        for (Talk.Model model : backlog) {
            apply(model);
        }
    }

    @Override
    public void talking(String email, boolean online) {
        checkpoint = my.state;
        if (email == null) {
            apply(v -> v.error(new IllegalArgumentException("Argument required: " + EMAIL)));
            return;
        }
        apply(my.actions.fetchLog(email));
    }

    @Override
    public void fetchingLog(Future<Talk.Model> task) {
        apply(task);
    }

    @Override
    public void fetchedLog(List<ChatMessage> chatLog) {
        apply(v -> v.idleChat(chatLog));
    }

    @Override
    public void noop() {
        // apply(adapter.pull());
    }

    @Override
    public void idleChat(List<ChatMessage> log) {
        checkpoint = my.state;
        // adapter.push(log)
        if (cancelWatch == null) {
            cancelWatch = my.actions.listen(message -> {
                if (message.isSentByMe()) {
                    apply(v -> v.said(message));
                } else {
                    apply(v -> v.heard(message));
                }
            });
        }
    }

    @Override
    public void saying(Future<Talk.Model> task) {
        apply(task);
    }

    @Override
    public void said(ChatMessage message) {
        // apply(adapter.push(message))
    }

    @Override
    public void heard(ChatMessage message) {
        // apply(adapter.push(message))
    }

    @Override
    public void loggingIn() {
        session.start(false);
    }

    @Override
    public void error(Throwable e) {
        throw new RuntimeException(e);
    }

    @Override
    public void loggedIn(Auth.Tokens tokens) {
        my.login(tokens);
        apply(checkpoint);
    }

    @Override
    public void loginCancelled() {
        setResult(RESULT_CANCELED);
        finish();
    }

    @OnClick(R.id.btnSendMessage)
    void send() {
        apply(my.actions.say(my.victim.getEmail(), inputMessage.getText()));
    }

}
