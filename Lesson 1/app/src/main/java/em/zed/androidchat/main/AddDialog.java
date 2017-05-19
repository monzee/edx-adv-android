/*
 * This file is a part of the Lesson 1 project.
 */

package em.zed.androidchat.main;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ProgressBar;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import butterknife.Bind;
import butterknife.ButterKnife;
import edu.galileo.android.androidchat.R;
import edu.galileo.android.androidchat.contactlist.entities.User;
import em.zed.androidchat.LogLevel;
import em.zed.androidchat.Logger;

public class AddDialog extends DialogFragment implements Add.View {

    public static final String TAG = AddDialog.class.getSimpleName();

    public interface Pipe {
        void added(User contact);
        void addCancelled();
        void error(Throwable e);
        void save(Add.Model state);
        void say(String message);
        void runOnUiThread(Runnable proc);
    }

    public static void show(FragmentManager fm) {
        if (fm.findFragmentByTag(TAG) == null) {
            new AddDialog().show(fm, TAG);
        }
    }

    private ExecutorService junction;
    private Logger log;
    private Add.Model state = Add.View::idle;
    private Add.Controller actions;
    private Pipe pipe;

    private volatile Future<?> join;

    @Bind(R.id.editTxtEmail) EditText inputEmail;
    @Bind(R.id.progressBar) ProgressBar progressBar;

    public AddDialog inject(
            ExecutorService junction,
            Logger log,
            Add.Model state,
            Add.Controller actions,
            Pipe pipe) {
        this.junction = junction;
        this.log = log;
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
                .setNegativeButton(R.string.addcontact_message_cancel, (d, _i) -> d.cancel())
                .setView(root)
                .create();
        dialog.setOnShowListener(dialogInterface -> dialog
                .getButton(DialogInterface.BUTTON_POSITIVE)
                .setOnClickListener(_v -> apply(actions.addContact(inputEmail.getText()))));
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
        pipe.save(state);
        if (join != null) {
            join.cancel(true);
        }
    }

    @Override
    public void onCancel(DialogInterface dialog) {
        super.onCancel(dialog);
        pipe.addCancelled();
    }

    @Override
    public void onDetach() {
        super.onDetach();
        ButterKnife.unbind(this);
        pipe = null;
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
        pipe.added(new User(email, online, null));
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
        pipe.runOnUiThread(() -> {
            state = newState;
            newState.render(this);
            LogLevel.D.to(log, "  -> %s", StateRepr.stringify(newState));
        });
    }

    void spin(boolean busy) {
        if (busy) {
            inputEmail.setEnabled(false);
            inputEmail.setVisibility(View.INVISIBLE);
            progressBar.setVisibility(View.VISIBLE);
        } else {
            inputEmail.setEnabled(true);
            inputEmail.setVisibility(View.VISIBLE);
            progressBar.setVisibility(View.GONE);
        }
    }

}
