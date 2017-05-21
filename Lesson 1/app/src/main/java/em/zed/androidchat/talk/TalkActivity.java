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
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnClick;
import de.hdodenhof.circleimageview.CircleImageView;
import edu.galileo.android.androidchat.R;
import edu.galileo.android.androidchat.chat.entities.ChatMessage;
import em.zed.androidchat.LogLevel;
import em.zed.androidchat.StateRepr;
import em.zed.androidchat.backend.Auth;
import em.zed.androidchat.concerns.SessionFragment;
import em.zed.androidchat.util.Pending;

public class TalkActivity extends AppCompatActivity implements
        Talk.Renderer, Talk.View, SessionFragment.Pipe {

    public static final String EMAIL = "arg-email";
    public static final String ONLINE = "arg-online";

    @Bind(R.id.toolbar) Toolbar toolbar;
    @Bind(R.id.txtUser) TextView txtUser;
    @Bind(R.id.txtStatus) TextView txtStatus;
    @Bind(R.id.editTxtMessage) EditText inputMessage;
    @Bind(R.id.messageRecyclerView) RecyclerView recyclerView;
    @Bind(R.id.imgAvatar) CircleImageView imgAvatar;

    private final ChatAdapter adapter = new ChatAdapter();
    private final Queue<Pending<Talk.Model>> inProgress = new ArrayDeque<>();
    private Scope my;
    private SessionFragment session;
    private Runnable cancelWatch;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        my = (Scope) getLastCustomNonConfigurationInstance();
        if (my == null) {
            Intent i = getIntent();
            my = new Scope(i.getStringExtra(EMAIL), i.getBooleanExtra(ONLINE, false));
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
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
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
        Queue<Talk.Model> backlog = dump();
        my.state = v -> v.booting(backlog);
    }

    @Override
    public Object onRetainCustomNonConfigurationInstance() {
        return my;
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
        if (email == null) {
            apply(v -> v.error(new IllegalArgumentException("Argument required: " + EMAIL)));
            return;
        }
        if (my.actions == null) {
            my.checkpoint = my.state;
            session.start(false);
        } else {
            apply(my.actions.fetchLog(email));
        }
    }

    @Override
    public void fetchingLog(Future<Talk.Model> task) {
        my.checkpoint = my.state;
        apply(task);
    }

    @Override
    public void fetchedLog(List<ChatMessage> chatLog) {
        adapter.replace(chatLog);
        recyclerView.scrollToPosition(adapter.getItemCount() - 1);
        if (cancelWatch == null) {
            my.overlap = chatLog.size();
            cancelWatch = my.actions.listen(message -> {
                if (my.overlap > 0) {
                    my.overlap--;
                } else if (!message.isSentByMe()) {
                    apply(v -> v.heard(message));
                }
            });
        }
    }

    @Override
    public void idle() {
        move(adapter.pull());
    }

    @Override
    public void saying(Future<Talk.Model> task) {
        my.checkpoint = my.state;
        inputMessage.setEnabled(false);
        apply(task);
    }

    @Override
    public void said(ChatMessage message) {
        inputMessage.setEnabled(true);
        message.setSentByMe(true);
        move(adapter.push(message));
        recyclerView.smoothScrollToPosition(adapter.getItemCount() - 1);
    }

    @Override
    public void heard(ChatMessage message) {
        move(adapter.push(message));
        recyclerView.smoothScrollToPosition(adapter.getItemCount() - 1);
    }

    @Override
    public void loggingIn() {
        session.start(true);
    }

    @Override
    public void error(Throwable e) {
        LogLevel.E.to(my.log, e);
        Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
        finish();
    }

    @Override
    public void loggedIn(Auth.Tokens tokens) {
        my.login(tokens);
        my.checkpoint.render(this);
    }

    @Override
    public void loginCancelled() {
        setResult(RESULT_CANCELED);
        finish();
    }

    @OnClick(R.id.btnSendMessage)
    void send() {
        apply(my.actions.say(my.victim.getEmail(), inputMessage.getText()));
        inputMessage.setText(null);
    }

}
