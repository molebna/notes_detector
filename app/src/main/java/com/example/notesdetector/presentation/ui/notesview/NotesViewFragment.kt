package com.example.notesdetector.presentation.ui.notesview

import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import android.view.View
import android.media.MediaPlayer
import android.net.Uri
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
    private lateinit var playAudioButton: Button
    private var audioUri: String? = null
    private var mediaPlayer: MediaPlayer? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        fileNameText = view.findViewById(R.id.fileNameText)
        tabView = view.findViewById(R.id.tabView)
        playAudioButton = view.findViewById(R.id.playAudioButton)
        playAudioButton.setOnClickListener { playSourceAudio() }

        observeViewModel()
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    fileNameText.text = state.errorMessage ?: state.fileName
                    tabView.setNotes(state.tabNotes)
                    audioUri = state.audioUri
                    playAudioButton.isEnabled = !state.audioUri.isNullOrBlank()
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        releasePlayer()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        releasePlayer()
    }

    private fun playSourceAudio() {
        val uriValue = audioUri ?: return
        runCatching {
            releasePlayer()
            mediaPlayer = MediaPlayer.create(requireContext(), Uri.parse(uriValue)).apply {
                setOnCompletionListener {
                    releasePlayer()
                }
                start()
            }
        }.onFailure {
            Toast.makeText(requireContext(), getString(R.string.audio_play_error), Toast.LENGTH_SHORT).show()
        }
    }

    private fun releasePlayer() {
        mediaPlayer?.release()
        mediaPlayer = null
    }
}
