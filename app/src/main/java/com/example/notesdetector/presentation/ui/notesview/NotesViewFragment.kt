package com.example.notesdetector.presentation.ui.notesview

import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import android.view.View
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.SeekBar
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
    private var audioUri: String? = null
    private var mediaPlayer: MediaPlayer? = null

    private lateinit var seekBar: SeekBar
    private lateinit var playButton: Button
    private lateinit var pauseButton: Button
    private lateinit var stopButton: Button

    private val handler = Handler(Looper.getMainLooper())

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        fileNameText = view.findViewById(R.id.fileNameText)
        tabView = view.findViewById(R.id.tabView)

        seekBar = view.findViewById(R.id.seekBar)
        playButton = view.findViewById(R.id.playButton)
        pauseButton = view.findViewById(R.id.pauseButton)
        stopButton = view.findViewById(R.id.stopButton)

        playButton.setOnClickListener { playAudio() }
        pauseButton.setOnClickListener { pauseAudio() }
        stopButton.setOnClickListener { stopAudio() }

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    mediaPlayer?.seekTo(progress)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        observeViewModel()
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    fileNameText.text = state.errorMessage ?: state.fileName
                    tabView.setNotes(state.tabNotes)
                    audioUri = state.audioUri
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
        handler.removeCallbacksAndMessages(null)
        releasePlayer()
    }

    private fun releasePlayer() {
        mediaPlayer?.release()
        mediaPlayer = null
    }

    private fun playAudio() {
        val uriValue = audioUri ?: return

        if (mediaPlayer == null) {
            val mp = MediaPlayer.create(requireContext(), Uri.parse(uriValue))

            if (mp == null) {
                Toast.makeText(requireContext(), "Cannot play audio", Toast.LENGTH_SHORT).show()
                return
            }

            mediaPlayer = mp.apply {
                setOnCompletionListener {
                    stopAudio()
                }
            }

            seekBar.max = mediaPlayer!!.duration
        }

        mediaPlayer?.start()
        updateSeekBar()
    }

    private fun pauseAudio() {
        mediaPlayer?.pause()
    }

    private fun stopAudio() {
        mediaPlayer?.let {
            it.pause()
            it.seekTo(0)
        }
        seekBar.progress = 0
    }

    private fun updateSeekBar() {
        handler.post(object : Runnable {
            override fun run() {
                mediaPlayer?.let {
                    if (it.isPlaying) {
                        seekBar.progress = it.currentPosition
                        handler.postDelayed(this, 100)
                    }
                }
            }
        })
    }
}
