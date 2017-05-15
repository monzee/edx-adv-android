/*
 * This file is a part of the Lesson 1 project.
 */

package em.zed.androidchat.main;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import java.util.ArrayDeque;
import java.util.Deque;
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
import edu.galileo.android.androidchat.R;
import edu.galileo.android.androidchat.contactlist.entities.User;
import em.zed.androidchat.backend.Auth;
import em.zed.androidchat.backend.UserRepository;
import em.zed.androidchat.login.LoginActivity;

import static edu.galileo.android.androidchat.AndroidChatApplication.GLOBALS;

public class MainActivity extends AppCompatActivity
        implements UserRepository.OnUserUpdate, Main.Model.Case {

    private static class Retained {
        final ExecutorService joiner = Executors.newSingleThreadExecutor(runnable -> {
            Thread t = new Thread(runnable);
            t.setName("main-activity-joiner-thread");
            return t;
        });

        final Main.SourcePort will = new MainController(
                GLOBALS.io(),
                GLOBALS.auth(),
                GLOBALS.users(),
                GLOBALS.contacts(),
                GLOBALS.logger());

        Main.Model state = Main.Model.Case::booting;
        String subtitle;
    }

    @Bind(R.id.toolbar) Toolbar toolbar;
    @Bind(R.id.recyclerViewContacts) RecyclerView recyclerView;

    private final Queue<Future<?>> inProgress = new ArrayDeque<>();
    private final Deque<Main.Model> backlog = new ArrayDeque<>();
    private Retained scope;
    private ContactsAdapter adapter;
    private UserRepository.Canceller watch;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        scope = (Retained) getLastCustomNonConfigurationInstance();
        if (scope == null) {
            scope = new Retained();
        }
        super.onCreate(savedInstanceState);
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
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.d("mz", "#onActivityResult");
        if (requestCode != LoginActivity.RESULT || resultCode != RESULT_OK) {
            finish();
            return;
        }
        String auth = data.getStringExtra(LoginActivity.TOKEN_AUTH);
        String refresh = data.getStringExtra(LoginActivity.TOKEN_REFRESH);
        scope.state = scope.will.loadContacts(new Auth.Tokens(auth, refresh));
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
        scope.state = of -> of.working(backlog);
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
        Log.d("mz", "#booting");
        startActivityForResult(new Intent(this, LoginActivity.class), LoginActivity.RESULT);
    }

    @Override
    public void working(Deque<Main.Model> backlog) {
        Main.Model last = backlog.removeLast();
        for (Main.Model model : backlog) {
            model.match(this);
        }
        render(last);
    }

    @Override
    public void loading(Future<Main.Model> result) {
        join(result);
    }

    @Override
    public void loaded(String userEmail, List<User> contacts) {
        toolbar.setSubtitle(userEmail);
        scope.subtitle = userEmail;
        render(of -> of.idle(contacts));
    }

    @Override
    public void idle(List<User> contacts) {
        Log.d("mz", "#idle");
        adapter.replace(contacts);
        if (watch == null) {
            watch = scope.will.observe(contacts, this);
        }
    }

    @Override
    public void removing(Future<Main.Model> result) {
        join(result);
    }

    @Override
    public void removed(User contact) {
        unwatch();
        render(adapter.remove(contact));
    }

    @Override
    public void adding(Future<Main.Model> result) {
        join(result);
    }

    @Override
    public void added(User contact) {
        unwatch();
        render(adapter.add(contact));
    }

    @Override
    public void loggingOut(Future<Main.Model> result) {
        join(result);
    }

    @Override
    public void loggedOut() {
        unwatch();
        booting();
    }

    @Override
    public void willChatWith(User contact) {
        render(adapter.sync());
        // launch chat
    }

    @Override
    public void error(Throwable e) {
        throw new RuntimeException(e);
    }

    @OnClick(R.id.fab)
    void add() {
        // show dialog, receive email
        // call controller.addContact
    }

    void render(Main.Model newState) {
        runOnUiThread(() -> {
            scope.state = newState;
            newState.match(this);
        });
    }

    void join(Future<Main.Model> result) {
        Main.Model snapshot = scope.state;
        AtomicReference<Future<?>> join = new AtomicReference<>();
        join.set(scope.joiner.submit(() -> {
            try {
                render(result.get());
            } catch (ExecutionException e) {
                render(of -> of.error(e));
            } catch (InterruptedException e) {
                backlog.add(snapshot);
            } finally {
                inProgress.remove(join.get());
            }
        }));
        inProgress.add(join.get());
        render(adapter.sync());
    }

    void unwatch() {
        if (watch != null) {
            watch.cancel();
            watch = null;
        }
    }

}
