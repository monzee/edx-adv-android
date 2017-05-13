/*
 * This file is a part of the Lesson 1 project.
 */

package em.zed.androidchat.main;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
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
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

import butterknife.Bind;
import butterknife.ButterKnife;
import edu.galileo.android.androidchat.R;
import edu.galileo.android.androidchat.contactlist.entities.User;
import em.zed.androidchat.backend.Auth;
import em.zed.androidchat.backend.UserRepository;
import em.zed.androidchat.login.LoginActivity;

import static edu.galileo.android.androidchat.AndroidChatApplication.GLOBALS;

public class MainActivity extends AppCompatActivity
        implements Main.Model.Case, UserRepository.OnUserUpdate {

    private static class Retained {
        final ExecutorService bg = GLOBALS.io();
        final Main.Controller will = new MainController(
                bg,
                GLOBALS.auth(),
                GLOBALS.users(),
                GLOBALS.contacts(),
                GLOBALS.logger());
        Main.Model state = Main.Model.Case::booting;
    }

    @Bind(R.id.toolbar) Toolbar toolbar;
    @Bind(R.id.recyclerViewContacts) RecyclerView recyclerView;

    private final Queue<Future<?>> pending = new ArrayDeque<>();
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
        adapter = new ContactsAdapter(new ContactsAdapter.Hook() {
            @Override
            public void click(User user) {
                render(of -> of.conversingWith(user));
            }

            @Override
            public void longClick(User user) {
                render(scope.will.removeContact(user.getEmail()));
            }
        });
        ButterKnife.bind(this);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // TODO: check if this is called again after config change
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
        if (pending.isEmpty()) {
            return;
        }
        while (!pending.isEmpty()) {
            pending.remove().cancel(true);
        }
        scope.state = of -> of.waiting(backlog);
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
        render(adapter.updateContact(contact));
    }

    @Override
    public void booting() {
        Log.d("mz", "#booting");
        startActivityForResult(new Intent(this, LoginActivity.class), LoginActivity.RESULT);
    }

    @Override
    public void waiting(Deque<Main.Model> backlog) {
        Main.Model last = backlog.removeLast();
        for (Main.Model model : backlog) {
            model.match(this);
        }
        render(last);
    }

    @Override
    public void loading(Future<Main.Model> result) {
        Log.d("mz", "#loading");
        async(result);
    }

    @Override
    public void idle(List<User> contacts) {
        Log.d("mz", "#idle");
        adapter.setContacts(contacts);
        if (watch == null) {
            watch = scope.will.observe(contacts, this);
        }
    }

    @Override
    public void removing(Future<Main.Model> result) {
        async(result);
    }

    @Override
    public void removed(User contact) {
        unwatch();
        render(adapter.removeContact(contact));
    }

    @Override
    public void adding(Future<Main.Model> result) {
        async(result);
    }

    @Override
    public void added(User contact) {
        unwatch();
        render(adapter.addContact(contact));
    }

    @Override
    public void loggingOut(Future<Main.Model> result) {
        async(result);
    }

    @Override
    public void loggedOut() {
        unwatch();
        booting();
    }

    @Override
    public void conversingWith(User contact) {
        unwatch();
        // launch chat
    }

    @Override
    public void error(Throwable e) {
        throw new RuntimeException(e);
    }

    void render(Main.Model newState) {
        runOnUiThread(() -> {
            scope.state = newState;
            newState.match(this);
        });
    }

    void async(Future<Main.Model> result) {
        Main.Model snapshot = scope.state;
        AtomicReference<Future<?>> future = new AtomicReference<>();
        future.set(scope.bg.submit(() -> {
            try {
                render(result.get());
            } catch (ExecutionException e) {
                render(of -> of.error(e));
            } catch (InterruptedException e) {
                backlog.add(snapshot);
            } finally {
                pending.remove(future.get());
            }
        }));
        pending.add(future.get());
        scope.state = adapter.sync();
    }

    void unwatch() {
        if (watch != null) {
            watch.cancel();
            watch = null;
        }
    }

}
