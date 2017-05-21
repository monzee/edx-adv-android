/*
 * This file is a part of the Lesson 1 project.
 */

package em.zed.androidchat.main;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v4.app.Fragment;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnClick;
import edu.galileo.android.androidchat.R;
import edu.galileo.android.androidchat.contactlist.entities.User;
import em.zed.androidchat.LogLevel;
import em.zed.androidchat.StateRepr;
import em.zed.androidchat.backend.Auth;
import em.zed.androidchat.concerns.SessionFragment;
import em.zed.androidchat.main.add.Add;
import em.zed.androidchat.main.add.AddDialog;
import em.zed.androidchat.talk.TalkActivity;
import em.zed.androidchat.util.Pending;

public class MainActivity extends AppCompatActivity implements
        Main.View, Main.Renderer, ContactsAdapter.Pipe, SessionFragment.Pipe, AddDialog.Pipe {

    @Bind(R.id.toolbar) Toolbar toolbar;
    @Bind(R.id.recyclerViewContacts) RecyclerView recyclerView;
    @Bind(R.id.fab) View fab;

    private final Queue<Pending<Main.Model>> inProgress = new ArrayDeque<>();
    private Scope my;
    private ContactsAdapter adapter;
    private Runnable cancelWatch;
    private SessionFragment session;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        my = (Scope) getLastCustomNonConfigurationInstance();
        if (my == null) {
            my = new Scope();
        }
        super.onCreate(savedInstanceState);
        SessionFragment.attach(getSupportFragmentManager());
        setContentView(R.layout.activity_contact_list);
        ButterKnife.bind(this);
        toolbar.setTitle(R.string.contactlist_title);
        toolbar.setSubtitle(my.subtitle);
        setSupportActionBar(toolbar);
        adapter = new ContactsAdapter(my.gravatars, this);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL));
        recyclerView.setAdapter(adapter);
    }

    @Override
    public void onAttachFragment(Fragment fragment) {
        if (fragment instanceof SessionFragment) {
            session = ((SessionFragment) fragment).inject(my.io, my.files, my.log, this);
        } else if (fragment instanceof AddDialog) {
            ((AddDialog) fragment).inject(my.junction, my.log, my.addState, my.addActions, this);
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
        unwatch();
        if (inProgress.isEmpty()) {
            return;
        }
        for (Pending<Main.Model> task : inProgress) {
            my.backlog.add(task.cancel());
        }
        my.backlog.add(my.state);
        move(v -> v.fold(my.backlog));
    }

    @Override
    public Object onRetainCustomNonConfigurationInstance() {
        return my;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_contactlist, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_logout) {
            apply(my.actions.logout());
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void move(Main.Model newState) {
        my.state = newState;
        LogLevel.D.to(my.log, "-> %s", StateRepr.stringify(newState));
    }

    @Override
    public void apply(Main.Model newState) {
        runOnUiThread(() -> {
            move(newState);
            newState.render(this);
        });
    }

    @Override
    public void apply(Future<Main.Model> task) {
        AtomicReference<Pending<Main.Model>> pending = new AtomicReference<>();
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
        move(adapter.pull());
    }

    @Override
    public void booting() {
        session.start(false);
    }

    @Override
    public void fold(Queue<Main.Model> backlog) {
        Main.Model last = Main.View::booting;
        while (!backlog.isEmpty()) {
            last = backlog.poll();
            last.render(this);
        }
        move(last);
    }

    @Override
    public void loading(Future<Main.Model> task) {
        adapter.replace(Collections.emptyList());
        apply(task);
    }

    @Override
    public void loaded(String userEmail, List<User> contacts) {
        toolbar.setSubtitle(userEmail);
        my.subtitle = userEmail;
        unwatch();
        apply(v -> v.idle(contacts));
    }

    @Override
    public void idle(List<User> contacts) {
        adapter.replace(contacts);
        watch();
    }

    @Override
    public void removing(Future<Main.Model> task) {
        apply(task);
    }

    @Override
    public void removed(User contact) {
        unwatch();
        apply(adapter.remove(contact));
    }

    @Override
    public void loggingOut(Future<Main.Model> task) {
        apply(task);
    }

    @Override
    public void loggedOut() {
        unwatch();
        session.start(true);
        move(Main.View::booting);
    }

    @Override
    public void willChatWith(User contact) {
        move(adapter.pull());
        startActivity(new Intent(this, TalkActivity.class)
                .putExtra(TalkActivity.EMAIL, contact.getEmail())
                .putExtra(TalkActivity.ONLINE, contact.isOnline()));
    }

    @Override
    public void error(Throwable e) {
        if (e.getCause() instanceof Auth.AuthError) {
            LogLevel.I.to(my.log, e);
            apply(Main.View::loggedOut);
            return;
        }
        LogLevel.E.to(my.log, e);
        Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
        finish();
    }

    @Override
    public void click(User user) {
        apply(v -> v.willChatWith(user));
    }

    @Override
    public void longClick(User user) {
        apply(my.actions.removeContact(user.getEmail()));
    }

    @Override
    public void loggedIn(Auth.Tokens tokens) {
        my.login(tokens);
        apply(my.actions.loadContacts());
    }

    @Override
    public void loginCancelled() {
        LogLevel.I.to(my.log, "bye.");
        finish();
    }

    @Override
    public void added(User contact) {
        unwatch();
        apply(adapter.add(contact));
    }

    @Override
    public void addCancelled() {
        LogLevel.I.to(my.log, "#addCancelled");
        apply(adapter.pull());
    }

    @Override
    public void save(Add.Model state) {
        my.addState = state;
    }

    @Override
    public void say(String message) {
        Snackbar.make(recyclerView, message, Snackbar.LENGTH_LONG).show();
    }

    @OnClick(R.id.fab)
    void add() {
        AddDialog.show(getSupportFragmentManager());
    }

    void watch() {
        if (cancelWatch == null) {
            cancelWatch = my.actions.observe(
                    cs -> apply(v -> v.loaded(my.subtitle, cs)));
        }
    }

    void unwatch() {
        if (cancelWatch != null) {
            cancelWatch.run();
            cancelWatch = null;
        }
    }

}
