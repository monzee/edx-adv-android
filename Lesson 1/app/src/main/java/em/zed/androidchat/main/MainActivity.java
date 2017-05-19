/*
 * This file is a part of the Lesson 1 project.
 */

package em.zed.androidchat.main;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnClick;
import edu.galileo.android.androidchat.R;
import edu.galileo.android.androidchat.contactlist.entities.User;
import em.zed.androidchat.LogLevel;
import em.zed.androidchat.Logger;
import em.zed.androidchat.Pending;
import em.zed.androidchat.backend.Auth;
import em.zed.androidchat.backend.Files;
import em.zed.androidchat.backend.Image;
import em.zed.androidchat.backend.UserRepository;
import em.zed.androidchat.concerns.SessionFragment;

import static edu.galileo.android.androidchat.AndroidChatApplication.GLOBALS;

public class MainActivity extends AppCompatActivity implements Main.View, Main.Renderer {

    private static class Scope {
        final ExecutorService io = GLOBALS.io();
        final ExecutorService junction = Executors.newSingleThreadExecutor(runnable -> {
            Thread t = new Thread(runnable);
            t.setName("main-activity-joiner-thread");
            return t;
        });
        final Logger log = GLOBALS.logger();
        final Files.Service files = GLOBALS.dataFiles();
        final Image.Service<ImageView> gravatars = GLOBALS.images();
        final Deque<Main.Model> backlog = new ArrayDeque<>();
        final MainController.Builder ctrlBuilder = new MainController
                .Builder(GLOBALS.users(), GLOBALS.contacts())
                .withExecutorService(io)
                .withLogger(log);

        Main.SourcePort actions = ctrlBuilder.build(Auth.NO_SESSION);
        Main.Model state = Main.View::booting;
        String subtitle;
        Add.Model addState = Add.View::idle;
        Add.Controller addActions;

        void login(Auth.Tokens t) {
            Auth.Session session = GLOBALS.auth().start(t);
            actions = ctrlBuilder.build(session);
            addActions = new AddController(io, GLOBALS.contacts(), session);
        }
    }

    @Bind(R.id.toolbar) Toolbar toolbar;
    @Bind(R.id.recyclerViewContacts) RecyclerView recyclerView;
    @Bind(R.id.fab) View fab;

    private final Deque<Pending<Main.Model>> inProgress = new ArrayDeque<>();
    private Scope my;
    private ContactsAdapter adapter;
    private UserRepository.Canceller watch;
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
        adapter = new ContactsAdapter(my.gravatars, new ContactsAdapter.Pipe() {
            @Override
            public void click(User user) {
                apply(v -> v.willChatWith(user));
            }

            @Override
            public void longClick(User user) {
                apply(my.actions.removeContact(user.getEmail()));
            }
        });
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL));
        recyclerView.setAdapter(adapter);
    }

    @Override
    public void onAttachFragment(Fragment fragment) {
        if (fragment instanceof SessionFragment) {
            session = ((SessionFragment) fragment).inject(
                    my.io, my.files, my.log,
                    new SessionFragment.Pipe() {
                        @Override
                        public void loggedIn(Auth.Tokens tokens) {
                            my.login(tokens);
                            apply(my.actions.loadContacts());
                        }

                        @Override
                        public void cancelled() {
                            LogLevel.D.to(my.log, "bye.");
                            finish();
                        }
                    });
        } else if (fragment instanceof AddDialog) {
            ((AddDialog) fragment).inject(
                    my.junction, my.addState, my.addActions,
                    new AddDialog.Pipe() {
                        @Override
                        public void ok(User contact) {
                            unwatch();
                            apply(adapter.add(contact));
                        }

                        @Override
                        public void cancelled() {
                            fab.setEnabled(true);
                        }

                        @Override
                        public void error(Throwable e) {
                            say("Error: " + e.getMessage());
                            MainActivity.this.error(e);
                        }

                        @Override
                        public void save(Add.Model state) {
                            my.addState = state;
                        }

                        @Override
                        public void say(String message) {
                            Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT)
                                    .show();
                        }

                        @Override
                        public void run(Runnable proc) {
                            runOnUiThread(proc);
                        }
                    });
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
            my.backlog.push(task.cancel());
        }
        move(v -> v.replay(my.backlog));
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
    public void booting() {
        session.start(false);
    }

    @Override
    public void replay(Deque<Main.Model> backlog) {
        Main.Model last = backlog.peekLast();
        while (!backlog.isEmpty()) {
            backlog.pop().render(this);
        }
        move(last);
    }

    @Override
    public void loading(Future<Main.Model> task) {
        adapter.replace(Collections.emptyList());
        join(task);
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
        fab.setEnabled(true);
        watch();
    }

    @Override
    public void removing(Future<Main.Model> task) {
        join(task);
    }

    @Override
    public void removed(User contact) {
        unwatch();
        apply(adapter.remove(contact));
    }

    @Override
    public void loggingOut(Future<Main.Model> task) {
        join(task);
    }

    @Override
    public void loggedOut() {
        unwatch();
        session.destroy();
        session.start(true);
        move(Main.View::booting);
    }

    @Override
    public void willChatWith(User contact) {
        move(adapter.pull());
        // launch chat
    }

    @Override
    public void error(Throwable e) {
        if (e.getCause() instanceof Auth.AuthError) {
            LogLevel.I.to(my.log, e);
            apply(Main.View::loggedOut);
            return;
        }
        throw new RuntimeException(e);
    }

    @Override
    public void move(Main.Model newState) {
        my.state = newState;
        LogLevel.D.to(my.log, "--> %s", StateRepr.stringify(newState));
    }

    @Override
    public void apply(Main.Model newState) {
        runOnUiThread(() -> {
            move(newState);
            newState.render(this);
        });
    }

    @OnClick(R.id.fab)
    void add(View button) {
        AddDialog.show(getSupportFragmentManager());
        button.setEnabled(false);
    }

    void join(Future<Main.Model> result) {
        AtomicReference<Pending<Main.Model>> pending = new AtomicReference<>();
        pending.set(new Pending<>(my.state, my.junction.submit(() -> {
            try {
                apply(result.get());
            } catch (ExecutionException e) {
                apply(v -> v.error(e));
            } catch (InterruptedException ignored) {
            } finally {
                inProgress.remove(pending.get());
            }
        })));
        inProgress.push(pending.get());
        move(adapter.pull());
    }

    void watch() {
        if (watch == null) {
            watch = my.actions
                    .observe(cs -> apply(v -> v.loaded(my.subtitle, cs)));
        }
    }

    void unwatch() {
        if (watch != null) {
            watch.cancel();
            watch = null;
        }
    }

}
