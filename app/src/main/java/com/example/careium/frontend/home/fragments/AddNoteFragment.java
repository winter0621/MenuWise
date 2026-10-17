package com.example.careium.frontend.home.fragments;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.example.careium.R;
import com.example.careium.databinding.FragmentAddNoteBinding;

import java.util.ArrayList;
import org.json.JSONArray;
import org.json.JSONObject;

public class AddNoteFragment extends Fragment {

    static ArrayList<NoteInformation> noteList = new ArrayList<>();
    FragmentAddNoteBinding binding;

    public AddNoteFragment() {
        // Required empty public constructor
    }

    public static AddNoteFragment newInstance() {
        return new AddNoteFragment();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_add_note_, container, false);

    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        binding = FragmentAddNoteBinding.bind(view);
        loadNotes();
        EditText TitleInput = view.findViewById(R.id.TitleText);
        EditText DescriptionInput = view.findViewById(R.id.DescriptionText);
        Button SaveNoteBtn = view.findViewById(R.id.SaveNote_Btn);

        SaveNoteBtn.setOnClickListener(v -> {
            String Title = TitleInput.getText().toString();
            String Description = DescriptionInput.getText().toString();
            if (Title.trim().isEmpty() && Description.trim().isEmpty()) {
                Toast.makeText(getContext(), getString(R.string.note_empty_error), Toast.LENGTH_SHORT).show();
                return;
            }
            if (Title.trim().isEmpty()) Title = getString(R.string.note_default_title);
            long CreatedTime = System.currentTimeMillis();

            NoteInformation note = new NoteInformation(Title, Description, CreatedTime);
            noteList.add(note);
            saveNotes();

            note.setTitle(Title);
            note.setDescription(Description);
            note.setCreatedTime(CreatedTime);
            Toast.makeText(getContext(), "日记已保存", Toast.LENGTH_SHORT).show();

            requireActivity().getSupportFragmentManager().beginTransaction()
                    .replace(R.id.layout_main_frame, DiaryFragment.newInstance())
                    .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
                    .commit();
        });
    }

    private void loadNotes() {
        noteList.clear();
        try {
            JSONArray saved = new JSONArray(requireContext().getSharedPreferences("diary_notes", 0).getString("items", "[]"));
            for (int i = 0; i < saved.length(); i++) {
                JSONObject item = saved.getJSONObject(i);
                noteList.add(new NoteInformation(item.optString("title"), item.optString("description"), item.optLong("created")));
            }
        } catch (Exception ignored) { }
    }

    private void saveNotes() {
        JSONArray saved = new JSONArray();
        for (NoteInformation item : noteList) {
            try {
                JSONObject entry = new JSONObject();
                entry.put("title", item.getTitle());
                entry.put("description", item.getDescription());
                entry.put("created", item.getCreatedTime());
                saved.put(entry);
            } catch (Exception ignored) { }
        }
        requireContext().getSharedPreferences("diary_notes", 0).edit().putString("items", saved.toString()).apply();
    }
}
