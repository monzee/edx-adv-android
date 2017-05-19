/*
 * This file is a part of the Lesson 1 project.
 */

package em.zed.androidchat.main;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import butterknife.Bind;
import butterknife.ButterKnife;
import edu.galileo.android.androidchat.R;
import edu.galileo.android.androidchat.contactlist.entities.User;

public class AddDialog extends DialogFragment implements Add.View {

    public static final String TAG = AddDialog.class.getSimpleName();

    public interface Pipe {
        void ok(User contact);
        void cancelled();
        void error(Throwable e);
        void save(Add.Model state);
        void say(String message);
        void run(Runnable proc);
    }

    public static void show(FragmentManager fm) {
        if (fm.findFragmentByTag(TAG) == null) {
            new AddDialog().show(fm, TAG);
        }
    }

    private ExecutorService junction;
    private Add.Model state = Add.View::idle;
    private Add.Controller actions;
    private Pipe pipe;

    private volatile Future<?> join;

    @Bind(R.id.editTxtEmail) EditText inputEmail;
    @Bind(R.id.progressBar) ProgressBar progressBar;

    public AddDialog inject(
            ExecutorService junction,
            Add.Model state,
            Add.Controller actions,
            Pipe pipe) {
        this.junction = junction;
        this.state = state;
        this.actions = actions;
        this.pipe = pipe;
        return this;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        View root = LayoutInflater.from(getActivity())
                .inflate(R.layout.fragment_add_contact_dialog, null);
        ButterKnife.bind(this, root);
        AlertDialog dialog = new AlertDialog.Builder(getActivity())
                .setTitle(R.string.addcontact_message_title)
                .setPositiveButton(R.string.addcontact_message_add, null)
                .setNegativeButton(R.string.addcontact_message_cancel, null)
                .setView(root)
                .create();
        dialog.setOnShowListener(_dlg -> {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE)
                    .setOnClickListener(view -> {
                        apply(actions.addContact(inputEmail.getText()));
                    });
            dialog.setOnCancelListener(dialogInterface -> pipe.cancelled());
        });
        return dialog;
    }

    @Override
    public void onResume() {
        super.onResume();
        state.render(this);
    }

    @Override
    public void onPause() {
        super.onPause();
        if (join != null) {
            join.cancel(true);
        }
        pipe.save(state);
    }

    @Override
    public void idle() {
        spin(false);
    }

    @Override
    public void invalid(String message) {
        inputEmail.setError(message);
        apply(Add.View::idle);
    }

    @Override
    public void adding(Future<Add.Model> task) {
        spin(true);
        join = junction.submit(() -> {
            try {
                apply(task.get());
            } catch (ExecutionException e) {
                apply(v -> v.error(e));
            } catch (InterruptedException ignored) {
            } finally {
                join = null;
            }
        });
    }

    @Override
    public void added(String email, boolean online) {
        apply(Add.View::idle);
        pipe.ok(new User(email, online, null));
        dismiss();
    }

    @Override
    public void addFailed(String reason) {
        pipe.say(reason);
        apply(Add.View::idle);
    }

    @Override
    public void error(Throwable e) {
        spin(false);
        pipe.error(e);
        dismiss();
    }

    void apply(Add.Model newState) {
        pipe.run(() -> {
            state = newState;
            newState.render(this);
        });
    }

    void spin(boolean busy) {
        if (busy) {
            inputEmail.setEnabled(false);
            inputEmail.setVisibility(View.GONE);
            progressBar.setVisibility(View.VISIBLE);
        } else {
            inputEmail.setEnabled(true);
            inputEmail.setVisibility(View.VISIBLE);
            progressBar.setVisibility(View.GONE);
        }
    }

}
