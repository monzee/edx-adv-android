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

import java.util.ArrayDeque;
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
import em.zed.androidchat.backend.Files;
import em.zed.androidchat.backend.UserRepository;
import em.zed.androidchat.concerns.SessionFragment;

import static edu.galileo.android.androidchat.AndroidChatApplication.GLOBALS;

public class MainActivity extends AppCompatActivity
        implements UserRepository.OnUserUpdate, Main.Model.Case {

    private static class Retained {
        final ExecutorService io = GLOBALS.io();
        final ExecutorService junction = Executors.newSingleThreadExecutor(runnable -> {
            Thread t = new Thread(runnable);
            t.setName("main-activity-joiner-thread");
            return t;
        });
        final Logger log = GLOBALS.logger();
        final Files.Service files = GLOBALS.dataFiles();
        final Deque<Main.Model> backlog = new ArrayDeque<>();
        final MainController.Builder ctrlBuilder = new MainController
                .Builder(GLOBALS.users(), GLOBALS.contacts())
                .withExecutorService(io)
                .withLogger(log);

        Main.SourcePort will = ctrlBuilder.build(Auth.NO_SESSION);
        Main.Model state = Main.Model.Case::booting;
        String subtitle;

        void login(Auth.Tokens t) {
            will = ctrlBuilder.build(GLOBALS.auth().start(t));
        }
    }

    @Bind(R.id.toolbar) Toolbar toolbar;
    @Bind(R.id.recyclerViewContacts) RecyclerView recyclerView;

    private final Deque<Future<?>> inProgress = new ArrayDeque<>();
    private Retained scope;
    private ContactsAdapter adapter;
    private UserRepository.Canceller watch;
    private SessionFragment session;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        scope = (Retained) getLastCustomNonConfigurationInstance();
        if (scope == null) {
            scope = new Retained();
        }
        super.onCreate(savedInstanceState);
        SessionFragment.attach(getSupportFragmentManager());
        setContentView(R.layout.activity_contact_list);
        ButterKnife.bind(this);
        toolbar.setTitle(R.string.contactlist_title);
        toolbar.setSubtitle(scope.subtitle);
        setSupportActionBar(toolbar);
        adapter = new ContactsAdapter(new ContactsAdapter.Hook() {
            @Override
            public void click(User user) {
                render(of -> of.willChatWith(user));
            }

            @Override
            public void longClick(User user) {
                render(scope.will.removeContact(user.getEmail()));
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
                    scope.io,
                    scope.files,
                    scope.log,
                    new SessionFragment.Pipe() {
                        @Override
                        public void send(Auth.Tokens tokens) {
                            scope.login(tokens);
                            render(scope.will.loadContacts());
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
        scope.state.match(this);
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
        jump(of -> of.replay(scope.backlog));
    }

    @Override
    public Object onRetainCustomNonConfigurationInstance() {
        return scope;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_contactlist, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_logout) {
            render(scope.will.logout());
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void updated(User contact) {
        render(adapter.update(contact));
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
        render(last);
    }

    @Override
    public void loading(Future<Main.Model> task) {
        join(task);
    }

    @Override
    public void loaded(String userEmail, List<User> contacts) {
        toolbar.setSubtitle(userEmail);
        scope.subtitle = userEmail;
        render(of -> of.idle(contacts));
    }

    @Override
    public void idle(List<User> contacts) {
        adapter.replace(contacts);
        if (watch == null) {
            watch = scope.will.observe(contacts, this);
        }
    }

    @Override
    public void removing(Future<Main.Model> task) {
        join(task);
    }

    @Override
    public void removed(User contact) {
        unwatch();
        render(adapter.remove(contact));
    }

    @Override
    public void adding(Future<Main.Model> task) {
        join(task);
    }

    @Override
    public void added(User contact) {
        unwatch();
        render(adapter.add(contact));
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
        jump(Main.Model.Case::booting);
    }

    @Override
    public void willChatWith(User contact) {
        jump(adapter.sync());
        // launch chat
    }

    @Override
    public void error(Throwable e) {
        if (e instanceof Auth.AuthError) {
            LogLevel.I.to(scope.log, e);
            render(Main.Model.Case::loggedOut);
            return;
        }
        throw new RuntimeException(e);
    }

    @OnClick(R.id.fab)
    void add() {
        // show dialog, receive email
        // call controller.addContact
    }

    void jump(Main.Model newState) {
        scope.state = newState;
        LogLevel.D.to(scope.log, StateRepr.stringify(newState));
    }

    void render(Main.Model newState) {
        runOnUiThread(() -> {
            jump(newState);
            newState.match(this);
        });
    }

    void join(Future<Main.Model> result) {
        Main.Model snapshot = scope.state;
        AtomicReference<Future<?>> join = new AtomicReference<>();
        join.set(scope.junction.submit(() -> {
            try {
                render(result.get());
            } catch (ExecutionException e) {
                render(of -> of.error(e.getCause()));
            } catch (InterruptedException e) {
                scope.backlog.push(snapshot);
            } finally {
                inProgress.remove(join.get());
            }
        }));
        inProgress.push(join.get());
        jump(adapter.sync());
    }

    void unwatch() {
        if (watch != null) {
            watch.cancel();
            watch = null;
        }
    }

}
