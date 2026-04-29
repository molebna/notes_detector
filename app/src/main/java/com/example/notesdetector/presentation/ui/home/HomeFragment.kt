package com.example.notesdetector.presentation.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.core.os.bundleOf
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.notesdetector.R
import com.example.notesdetector.data.NotesFile
import com.example.notesdetector.presentation.adapters.NoteFileAdapter
import kotlinx.coroutines.launch

class HomeFragment : Fragment(R.layout.fragment_home) {

    private val viewModel: HomeViewModel by viewModels()

    private lateinit var adapter: NoteFileAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val recycler = view.findViewById<RecyclerView>(R.id.notesList)

        adapter = NoteFileAdapter(
            onClick = { note ->
                findNavController().navigate(
                    R.id.nav_notesview,
                    bundleOf("tabNoteId" to note.id)
                )
            },
            onRenameClick = { note -> showRenameDialog(note) },
            onDeleteClick = { note -> showDeleteDialog(note) }
        )

        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = adapter

        observeNotes()
    }

    private fun observeNotes() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.notes.collect { notes ->
                    adapter.submitList(notes)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshNotes()
    }

    private fun showRenameDialog(note: NotesFile) {
        val input = EditText(requireContext()).apply {
            setText(note.title)
            setSelection(text.length)
            hint = getString(R.string.rename_note_hint)
        }

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.rename_note_title)
            .setView(input)
            .setPositiveButton(R.string.rename_note_confirm) { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty()) {
                    viewModel.renameNoteFile(note.id, newName)
                } else {
                    Toast.makeText(requireContext(), R.string.rename_note_empty_error, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showDeleteDialog(note: NotesFile) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.delete_note_title)
            .setMessage(getString(R.string.delete_note_message, note.title))
            .setPositiveButton(R.string.delete_note_confirm) { _, _ ->
                viewModel.deleteNoteFile(note.id)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
