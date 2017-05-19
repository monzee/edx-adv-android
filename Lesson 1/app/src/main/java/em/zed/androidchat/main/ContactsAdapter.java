/*
 * This file is a part of the Lesson 1 project.
 */

package em.zed.androidchat.main;

import android.graphics.Color;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import butterknife.Bind;
import butterknife.ButterKnife;
import de.hdodenhof.circleimageview.CircleImageView;
import edu.galileo.android.androidchat.R;
import edu.galileo.android.androidchat.contactlist.entities.User;
import em.zed.androidchat.backend.Image;

public class ContactsAdapter extends RecyclerView.Adapter<ContactsAdapter.Contact>
        implements Main.TargetPort {

    public interface Pipe {
        void click(User user);
        void longClick(User user);
    }

    public class Contact extends RecyclerView.ViewHolder {

        @Bind(R.id.imgAvatar) CircleImageView avatar;
        @Bind(R.id.txtUser) TextView contact;
        @Bind(R.id.txtStatus) TextView status;

        private User user;

        public Contact(View itemView) {
            super(itemView);
            ButterKnife.bind(this, itemView);
            itemView.setOnClickListener(view -> pipe.click(user));
            itemView.setOnLongClickListener(view -> {
                pipe.longClick(user);
                return true;
            });
        }

        void bind(User row) {
            user = row;
            contact.setText(row.getEmail());
            avatars.load(user.getEmail()).into(avatar);
            if (row.isOnline()) {
                status.setText("online");
                status.setTextColor(Color.GREEN);
            } else {
                status.setText("offline");
                status.setTextColor(Color.GRAY);
            }
        }
    }

    private final Image.Service<ImageView> avatars;
    private final Pipe pipe;
    private List<User> items = Collections.emptyList();
    private Map<String, Integer> byEmail = Collections.emptyMap();

    public ContactsAdapter(Image.Service<ImageView> avatars, Pipe pipe) {
        this.avatars = avatars;
        this.pipe = pipe;
    }

    @Override
    public Contact onCreateViewHolder(ViewGroup parent, int viewType) {
        return new Contact(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.content_contact, parent, false));
    }

    @Override
    public void onBindViewHolder(Contact holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @Override
    public void replace(List<User> items) {
        this.items = items;
        reindex();
        notifyDataSetChanged();
    }

    @Override
    public Main.Model add(@NonNull User contact) {
        int pos = items.size();
        items.add(contact);
        byEmail.put(contact.getEmail(), pos);
        notifyItemInserted(pos);
        return pull();
    }

    @Override
    public Main.Model update(@NonNull User contact) {
        Integer i = byEmail.get(contact.getEmail());
        if (i != null) {
            items.set(i, contact);
            notifyItemChanged(i);
        } else {
            add(contact);
        }
        return pull();
    }

    @Override
    public Main.Model remove(@NonNull User contact) {
        Integer i = byEmail.remove(contact.getEmail());
        if (i != null) {
            items.remove(i.intValue());
            notifyItemRemoved(i);
        }
        return pull();
    }

    @Override
    public Main.Model pull() {
        return v -> v.idle(items);
    }

    private void reindex() {
        byEmail = new HashMap<>();
        for (int i = 0; i < items.size(); i++) {
            byEmail.put(items.get(i).getEmail(), i);
        }
    }

}
