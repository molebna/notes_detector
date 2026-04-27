package com.example.notesdetector.presentation.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.notesdetector.R
import com.example.notesdetector.data.NotesFile
import com.example.notesdetector.data.utils.FileUtils.getFileNameFromUri

class NoteFileAdapter(
    private val onClick: (NotesFile) -> Unit
) : RecyclerView.Adapter<NoteFileAdapter.NoteViewHolder>() {

    private var notes: List<NotesFile> = emptyList()

    class NoteViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val titleText: TextView = view.findViewById(R.id.note_title)
        val dateText: TextView = view.findViewById(R.id.note_date)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NoteViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_note_file, parent, false)

        return NoteViewHolder(view)
    }

    override fun getItemCount(): Int = notes.size

    override fun onBindViewHolder(holder: NoteViewHolder, position: Int) {
        val note = notes[position]

        holder.titleText.text = getFileNameFromUri(holder.itemView.context, note.title)
        holder.dateText.text = "created: ${note.date}"

        holder.itemView.setOnClickListener {
            onClick(note)
        }
    }

    fun submitList(newNotes: List<NotesFile>) {
        notes = newNotes
        notifyDataSetChanged()
    }
}