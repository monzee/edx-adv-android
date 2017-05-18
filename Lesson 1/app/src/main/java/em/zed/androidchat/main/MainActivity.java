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
import android.widget.ImageView;

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
import em.zed.androidchat.backend.Auth;
import em.zed.androidchat.backend.Image;
import em.zed.androidchat.backend.Files;
import em.zed.androidchat.backend.UserRepository;
import em.zed.androidchat.concerns.SessionFragment;

import static edu.galileo.android.androidchat.AndroidChatApplication.GLOBALS;

public class MainActivity extends AppCompatActivity implements Main.Model.Case {

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
        Main.Model state = Main.Model.Case::booting;
        String subtitle;

        void login(Auth.Tokens t) {
            actions = ctrlBuilder.build(GLOBALS.auth().start(t));
        }
    }

    @Bind(R.id.toolbar) Toolbar toolbar;
    @Bind(R.id.recyclerViewContacts) RecyclerView recyclerView;

    private final Deque<Future<?>> inProgress = new ArrayDeque<>();
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
                apply(of -> of.willChatWith(user));
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
                            finish();
                        }
                    });
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        my.state.match(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        unwatch();
        if (inProgress.isEmpty()) {
            return;
        }
        for (Future<?> future : inProgress) {
           future.cancel(true);
        }
        move(of -> of.replay(my.backlog));
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
        Main.Model last = backlog.removeLast();
        while (!backlog.isEmpty()) {
            backlog.pop().match(this);
        }
        apply(last);
    }

    @Override
    public void loading(Future<Main.Model> task) {
        join(task);
    }

    @Override
    public void loaded(String userEmail, List<User> contacts) {
        toolbar.setSubtitle(userEmail);
        my.subtitle = userEmail;
        apply(of -> of.idle(contacts));
    }

    @Override
    public void idle(List<User> contacts) {
        adapter.replace(contacts);
        if (watch == null) {
            watch = my.actions.observe(contacts, c -> apply(adapter.update(c)));
        }
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
    public void adding(Future<Main.Model> task) {
        join(task);
    }

    @Override
    public void added(User contact) {
        unwatch();
        apply(adapter.add(contact));
    }

    @Override
    public void loggingOut(Future<Main.Model> task) {
        join(task);
    }

    @Override
    public void loggedOut() {
        unwatch();
        adapter.replace(Collections.emptyList());
        session.destroy();
        session.start(true);
        move(Main.Model.Case::booting);
    }

    @Override
    public void willChatWith(User contact) {
        move(adapter.sync());
        // launch chat
    }

    @Override
    public void error(Throwable e) {
        if (e.getCause() instanceof Auth.AuthError) {
            LogLevel.I.to(my.log, e);
            apply(Main.Model.Case::loggedOut);
            return;
        }
        throw new RuntimeException(e);
    }

    @OnClick(R.id.fab)
    void add() {
        // show dialog, receive email
        // call controller.addContact
    }

    void move(Main.Model newState) {
        my.state = newState;
        LogLevel.D.to(my.log, "--> %s", StateRepr.stringify(newState));
    }

    void apply(Main.Model newState) {
        runOnUiThread(() -> {
            move(newState);
            newState.match(this);
        });
    }

    void join(Future<Main.Model> result) {
        Main.Model snapshot = my.state;
        AtomicReference<Future<?>> join = new AtomicReference<>();
        join.set(my.junction.submit(() -> {
            try {
                apply(result.get());
            } catch (ExecutionException e) {
                apply(of -> of.error(e));
            } catch (InterruptedException e) {
                my.backlog.push(snapshot);
            } finally {
                inProgress.remove(join.get());
            }
        }));
        inProgress.push(join.get());
        move(adapter.sync());
    }

    void unwatch() {
        if (watch != null) {
            watch.cancel();
            watch = null;
        }
    }

}
