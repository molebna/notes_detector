package com.example.notesdetector.presentation.ui.transcription

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.notesdetector.R
import com.example.notesdetector.data.TabNote
import com.example.notesdetector.presentation.ui.views.TablatureView
import kotlinx.coroutines.launch

class NotesViewFragment : Fragment(R.layout.fragment_notes_view) {

    private val viewModel: TranscriptionViewModel by activityViewModels()

    private lateinit var tabView: TablatureView
    private lateinit var fileNameText: TextView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        fileNameText = view.findViewById<TextView>(R.id.fileNameText)
        tabView = view.findViewById<TablatureView>(R.id.tabView)

        observeViewModel()
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    render(state.tabNotes)
                    fileNameText.text = state.selectedAudioUri?.let { getFileNameFromUri(it) }
                }
            }
        }
    }

    private fun render(notes: List<TabNote>) {
        tabView.setNotes(notes)
    }

    private fun getFileNameFromUri(filePath: String): String {

        var uri = filePath.toUri()
        var name = "Unknown file"

        val cursor = requireContext().contentResolver.query(
            uri,
            null,
            null,
            null,
            null
        )

        cursor?.use {
            val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (it.moveToFirst() && nameIndex != -1) {
                name = it.getString(nameIndex)
            }
        }

        return name.substringBeforeLast(".")
    }
}