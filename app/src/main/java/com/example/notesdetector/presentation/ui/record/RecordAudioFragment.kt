package com.example.notesdetector.presentation.ui.record

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.notesdetector.R
import com.example.notesdetector.presentation.ui.transcription.TranscriptionFragment
import java.io.File

class RecordAudioFragment : Fragment(R.layout.fragment_record_audio) {

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null

    private lateinit var statusText: TextView
    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private lateinit var useRecordingButton: Button

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startRecording()
        } else {
            Toast.makeText(requireContext(), R.string.record_permission_denied, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        statusText = view.findViewById(R.id.recordStatusText)
        startButton = view.findViewById(R.id.startRecordingButton)
        stopButton = view.findViewById(R.id.stopRecordingButton)
        useRecordingButton = view.findViewById(R.id.useRecordingButton)

        startButton.setOnClickListener { ensurePermissionAndRecord() }
        stopButton.setOnClickListener { stopRecording() }
        useRecordingButton.setOnClickListener { openTranscription() }

        updateUi(isRecording = false, hasRecordedAudio = false)
    }

    private fun ensurePermissionAndRecord() {
        val hasPermission = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            startRecording()
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startRecording() {
        stopRecordingInternal()

        val file = File(requireContext().cacheDir, "recording_${System.currentTimeMillis()}.m4a")
        outputFile = file

        val mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(requireContext())
        } else {
            MediaRecorder()
        }

        runCatching {
            mediaRecorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44_100)
                setAudioEncodingBitRate(128_000)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
        }.onSuccess {
            recorder = mediaRecorder
            updateUi(isRecording = true, hasRecordedAudio = false)
        }.onFailure {
            mediaRecorder.release()
            recorder = null
            outputFile = null
            Toast.makeText(requireContext(), it.message ?: getString(R.string.record_start_error), Toast.LENGTH_SHORT).show()
            updateUi(isRecording = false, hasRecordedAudio = false)
        }
    }

    private fun stopRecording() {
        val hadRecorder = recorder != null
        stopRecordingInternal()
        val hasAudio = outputFile?.exists() == true
        updateUi(isRecording = false, hasRecordedAudio = hasAudio)
        if (hadRecorder && hasAudio) {
            Toast.makeText(requireContext(), R.string.recording_saved, Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopRecordingInternal() {
        recorder?.let { mediaRecorder ->
            runCatching { mediaRecorder.stop() }
            mediaRecorder.release()
        }
        recorder = null
    }

    private fun openTranscription() {
        val file = outputFile
        if (file == null || !file.exists()) {
            Toast.makeText(requireContext(), R.string.no_recording_yet, Toast.LENGTH_SHORT).show()
            return
        }

        val uri = Uri.fromFile(file).toString()
        findNavController().navigate(R.id.action_nav_record_audio_to_nav_transcription)
        findNavController().getBackStackEntry(R.id.nav_transcription)
            .savedStateHandle[TranscriptionFragment.AUDIO_URI_KEY] = uri
    }

    private fun updateUi(isRecording: Boolean, hasRecordedAudio: Boolean) {
        statusText.text = when {
            isRecording -> getString(R.string.recording_in_progress)
            hasRecordedAudio -> getString(R.string.recording_ready)
            else -> getString(R.string.recording_idle)
        }

        startButton.isEnabled = !isRecording
        stopButton.isEnabled = isRecording
        useRecordingButton.isEnabled = hasRecordedAudio
    }

    override fun onStop() {
        super.onStop()
        if (recorder != null) {
            stopRecording()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopRecordingInternal()
    }
}
