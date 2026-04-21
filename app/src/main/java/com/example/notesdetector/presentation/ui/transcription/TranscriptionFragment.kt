package com.example.notesdetector.presentation.ui.transcription

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.notesdetector.databinding.FragmentTranscriptionBinding
import kotlinx.coroutines.launch

class TranscriptionFragment : Fragment() {

    private val viewModel: TranscriptionViewModel by viewModels()
    private var _binding: FragmentTranscriptionBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTranscriptionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        findNavController().currentBackStackEntry?.savedStateHandle
            ?.getLiveData<String>(AUDIO_URI_KEY)
            ?.observe(viewLifecycleOwner) { uri ->
                viewModel.setAudioUri(uri)
            }

        binding.retryButton.setOnClickListener {
            viewModel.transcribeSelectedAudio()
        }

        binding.transcribeButton.setOnClickListener {
            viewModel.transcribeSelectedAudio()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.progressBar.isVisible = state.isLoading
                    binding.transcribeButton.isEnabled = !state.isLoading && state.selectedAudioUri != null
                    binding.selectedAudioText.text = state.selectedAudioUri ?: "No audio selected"
                    binding.resultText.text = state.transcription.ifBlank { "Transcription will appear here." }

                    val hasError = !state.errorMessage.isNullOrBlank()
                    binding.errorText.isVisible = hasError
                    binding.retryButton.isVisible = hasError
                    binding.errorText.text = state.errorMessage
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val AUDIO_URI_KEY = "audio_uri"
    }
}
