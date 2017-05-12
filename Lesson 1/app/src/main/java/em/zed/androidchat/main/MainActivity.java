/*
 * This file is a part of the Lesson 1 project.
 */

package em.zed.androidchat.main;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;

import butterknife.Bind;
import butterknife.ButterKnife;
import edu.galileo.android.androidchat.R;
import em.zed.androidchat.login.LoginActivity;

public class MainActivity extends AppCompatActivity {

    private static class Retained {
        Main.Session session = Main.Session.Case::guest;
    }

    @Bind(R.id.toolbar) Toolbar toolbar;
    @Bind(R.id.recyclerViewContacts) RecyclerView recyclerView;

    private Retained scope;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        scope = (Retained) getLastCustomNonConfigurationInstance();
        if (scope == null) {
            scope = new Retained();
        }
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_contact_list);
        ButterKnife.bind(this);
    }

    @Override
    public Object onRetainCustomNonConfigurationInstance() {
        return scope;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != LoginActivity.RESULT) {
            return;
        }
        String auth = data.getStringExtra(LoginActivity.TOKEN_AUTH);
        String refresh = data.getStringExtra(LoginActivity.TOKEN_REFRESH);
        applyContacts(Main.Contacts.Case::cold);
    }

    void apply(Main.Session newState) {
        runOnUiThread(() -> {
            scope.session = newState;
            update();
        });
    }

    void applyContacts(Main.Contacts newState) {
        apply(of -> of.loggedIn(newState));
    }

    void update() {

    }

}
