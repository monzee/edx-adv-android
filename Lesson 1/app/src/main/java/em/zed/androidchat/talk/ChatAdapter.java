/*
 * This file is a part of the Lesson 1 project.
 */

package em.zed.androidchat.talk;

import android.support.v7.widget.RecyclerView;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Collections;
import java.util.List;

import butterknife.Bind;
import butterknife.BindColor;
import butterknife.ButterKnife;
import edu.galileo.android.androidchat.R;
import edu.galileo.android.androidchat.chat.entities.ChatMessage;

public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.Message>
        implements Talk.TargetPort {

    static class Message extends RecyclerView.ViewHolder {
        @Bind(R.id.txtMessage) TextView txtMessage;
        @BindColor(R.color.colorPrimary) int primaryColor;
        @BindColor(R.color.colorAccent) int accentColor;

        LinearLayout.LayoutParams layout;

        Message(View itemView) {
            super(itemView);
            ButterKnife.bind(this, itemView);
            layout = (LinearLayout.LayoutParams) txtMessage.getLayoutParams();
        }

        void bind(ChatMessage message) {
            txtMessage.setText(message.getMsg());
            if (message.isSentByMe()) {
                txtMessage.setBackgroundColor(primaryColor);
                layout.gravity = Gravity.START;
            } else {
                txtMessage.setBackgroundColor(accentColor);
                layout.gravity = Gravity.END;
            }
        }

    }

    private List<ChatMessage> items = Collections.emptyList();

    @Override
    public Message onCreateViewHolder(ViewGroup parent, int viewType) {
        View root = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.content_chat, parent, false);
        return new Message(root);
    }

    @Override
    public void onBindViewHolder(Message holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @Override
    public void replace(List<ChatMessage> messages) {
        items = messages;
        notifyDataSetChanged();
    }

    @Override
    public Talk.Model pull() {
        return v -> v.fetchedLog(items);
    }

    @Override
    public Talk.Model push(ChatMessage message) {
        int pos = items.size();
        items.add(message);
        notifyItemInserted(pos);
        return v -> v.fetchedLog(items);
    }

}
