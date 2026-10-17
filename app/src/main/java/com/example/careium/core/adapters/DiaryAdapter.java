package com.example.careium.core.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.careium.R;
import com.example.careium.frontend.home.fragments.NoteInformation;

import java.text.DateFormat;
import java.util.ArrayList;

public class DiaryAdapter extends  RecyclerView.Adapter<DiaryAdapter.MyViewHolder>{

    Context context;
    ArrayList<NoteInformation> noteList;

    public DiaryAdapter(Context context, ArrayList<NoteInformation> noteList) {
        this.context = context;
        this.noteList = noteList;
    }

    @NonNull
    @Override
    public MyViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new MyViewHolder(LayoutInflater.from(context).inflate(R.layout.layout_note_items, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull MyViewHolder holder, int position) {
        NoteInformation note =noteList.get(position);
        holder.TitleOut.setText(note.getTitle());
        holder.DescriptionOut.setText(note.getDescription());

        String Time = DateFormat.getTimeInstance().format(note.getCreatedTime());
        holder.CreatedTimeOut.setText(Time);
        holder.itemView.setOnLongClickListener(v -> {
            new AlertDialog.Builder(context)
                    .setTitle(context.getString(R.string.note_delete_title))
                    .setMessage(context.getString(R.string.note_delete_message))
                    .setNegativeButton(context.getString(R.string.cancel), null)
                    .setPositiveButton(context.getString(R.string.ok), (dialog, which) -> {
                        int adapterPosition = holder.getAdapterPosition();
                        if (adapterPosition == RecyclerView.NO_POSITION) return;
                        noteList.remove(adapterPosition);
                        persistNotes();
                        notifyDataSetChanged();
                        TextView empty = holder.itemView.getRootView().findViewById(R.id.diary_empty);
                        if (empty != null) empty.setVisibility(noteList.isEmpty() ? View.VISIBLE : View.GONE);
                        Toast.makeText(context, context.getString(R.string.note_deleted), Toast.LENGTH_SHORT).show();
                    }).show();
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return noteList.size();
    }

    private void persistNotes() {
        JSONArray saved = new JSONArray();
        for (NoteInformation note : noteList) {
            JSONObject item = new JSONObject();
            try {
                item.put("title", note.getTitle());
                item.put("description", note.getDescription());
                item.put("created", note.getCreatedTime());
                saved.put(item);
            } catch (Exception ignored) { }
        }
        context.getSharedPreferences("diary_notes", Context.MODE_PRIVATE).edit()
                .putString("items", saved.toString()).apply();
    }

    public static class MyViewHolder extends RecyclerView.ViewHolder{

        TextView TitleOut;
        TextView DescriptionOut;
        TextView CreatedTimeOut;
        public MyViewHolder(@NonNull View itemView) {
            super(itemView);
            TitleOut = itemView.findViewById(R.id.text_note_title_output);
            DescriptionOut = itemView.findViewById(R.id.text_note_description_output);
            CreatedTimeOut = itemView.findViewById(R.id.text_note_time_output);
        }
    }
}
