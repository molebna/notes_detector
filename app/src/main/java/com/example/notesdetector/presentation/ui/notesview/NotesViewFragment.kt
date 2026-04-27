package com.example.notesdetector.presentation.ui.notesview

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.notesdetector.R
import com.example.notesdetector.presentation.ui.views.TablatureView
import kotlinx.coroutines.launch

class NotesViewFragment : Fragment(R.layout.fragment_notes_view) {

    private val viewModel: NotesViewModel by viewModels()

    private lateinit var tabView: TablatureView
    private lateinit var fileNameText: android.widget.TextView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        fileNameText = view.findViewById(R.id.fileNameText)
        tabView = view.findViewById(R.id.tabView)

        observeViewModel()
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    fileNameText.text = state.errorMessage ?: state.fileName
                    tabView.setNotes(state.tabNotes)
                }
            }
        }
    }
}
