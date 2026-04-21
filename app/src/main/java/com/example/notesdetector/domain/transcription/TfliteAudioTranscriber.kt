package com.example.notesdetector.domain.transcription

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import be.tarsos.dsp.ConstantQ
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.flex.FlexDelegate
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

class TfliteAudioTranscriber(
    private val context: Context,
    private val modelAssetPath: String = "guitar_crnn_onsets_frames_2.tflite"
) {

    companion object {
        private const val SR = 22_050
        private const val HOP_LENGTH = 512
        private const val SEGMENT_W = 128
        private const val CQT_BINS = 84
        private const val NUM_NOTES = 49
        private const val MIN_MIDI = 40
        private const val ONSET_THRESHOLD = 0.3f
        private const val FRAME_THRESHOLD = 0.15f
    }

    suspend fun transcribe(audioUri: Uri): String {
        val modelBuffer = loadModelFile(modelAssetPath)
        val rawAudio = decodeToMonoFloatPcm(audioUri, targetSampleRate = SR)
        val cqtNorm = buildCqtLikeFeatures(rawAudio)

        if (cqtNorm.isEmpty()) {
            return "No notes detected"
        }

        return runWithInterpreter(modelBuffer) { interpreter ->
            val (fullFrames, fullOnsets) = predictSegments(interpreter, cqtNorm)
            val pianoRoll = applyOnsetFrameLogic(fullFrames, fullOnsets)
            noteEventsFromPianoRoll(pianoRoll)
        }
    }


    private fun <T> runWithInterpreter(modelBuffer: ByteBuffer, block: (Interpreter) -> T): T {
        val baseOptions = Interpreter.Options().apply {
            setNumThreads(4)
        }

        return try {
            FlexDelegate().use { flexDelegate ->
                val optionsWithFlex = Interpreter.Options().apply {
                    setNumThreads(4)
                    addDelegate(flexDelegate)
                }
                Interpreter(modelBuffer, optionsWithFlex).use(block)
            }
        } catch (flexFailure: Throwable) {
            Interpreter(modelBuffer, baseOptions).use { interpreter ->
                try {
                    block(interpreter)
                } catch (interpreterFailure: Throwable) {
                    throw IllegalStateException(
                        "Model requires Select TF Ops/Flex delegate. Ensure `tensorflow-lite-select-tf-ops` is included and app reinstalled.",
                        interpreterFailure
                    )
                }
            }
        }
    }

    private fun predictSegments(
        interpreter: Interpreter,
        cqtNorm: Array<FloatArray>
    ): Pair<Array<FloatArray>, Array<FloatArray>> {
        val totalFrames = cqtNorm.size
        val fullFrames = Array(totalFrames) { FloatArray(NUM_NOTES) }
        val fullOnsets = Array(totalFrames) { FloatArray(NUM_NOTES) }

        val segmentStarts = (0 until (totalFrames - SEGMENT_W).coerceAtLeast(0) step SEGMENT_W).toList()

        for (start in segmentStarts) {
            val input = Array(1) { Array(SEGMENT_W) { Array(CQT_BINS) { FloatArray(1) } } }
            for (t in 0 until SEGMENT_W) {
                val sourceFrame = start + t
                for (f in 0 until CQT_BINS) {
                    input[0][t][f][0] = cqtNorm[sourceFrame][f]
                }
            }

            val predFrames = Array(1) { Array(SEGMENT_W) { FloatArray(NUM_NOTES) } }
            val predOnsets = Array(1) { Array(SEGMENT_W) { FloatArray(NUM_NOTES) } }

            interpreter.runForMultipleInputsOutputs(
                arrayOf(input),
                mapOf(
                    0 to predFrames,
                    1 to predOnsets
                )
            )

            for (t in 0 until SEGMENT_W) {
                val target = start + t
                if (target >= totalFrames) break
                System.arraycopy(predFrames[0][t], 0, fullFrames[target], 0, NUM_NOTES)
                System.arraycopy(predOnsets[0][t], 0, fullOnsets[target], 0, NUM_NOTES)
            }
        }

        return fullFrames to fullOnsets
    }

    private fun applyOnsetFrameLogic(
        fullFrames: Array<FloatArray>,
        fullOnsets: Array<FloatArray>
    ): Array<IntArray> {
        val totalFrames = fullFrames.size
        val finalPianoRoll = Array(totalFrames) { IntArray(NUM_NOTES) }

        for (noteIdx in 0 until NUM_NOTES) {
            var isNoteActive = false
            for (t in 0 until totalFrames) {
                when {
                    fullOnsets[t][noteIdx] > ONSET_THRESHOLD -> {
                        if (isNoteActive && t > 0) {
                            finalPianoRoll[t - 1][noteIdx] = 0
                        }
                        isNoteActive = true
                        finalPianoRoll[t][noteIdx] = 1
                    }

                    isNoteActive && fullFrames[t][noteIdx] > FRAME_THRESHOLD -> {
                        finalPianoRoll[t][noteIdx] = 1
                    }

                    else -> {
                        isNoteActive = false
                    }
                }
            }
        }

        return finalPianoRoll
    }

    private fun noteEventsFromPianoRoll(pianoRoll: Array<IntArray>): String {
        val events = mutableListOf<String>()

        for (noteIdx in 0 until NUM_NOTES) {
            var startFrame = -1
            for (t in pianoRoll.indices) {
                val active = pianoRoll[t][noteIdx] == 1
                if (active && startFrame == -1) {
                    startFrame = t
                }

                val isLastFrame = t == pianoRoll.lastIndex
                if ((!active || isLastFrame) && startFrame != -1) {
                    val endFrame = if (active && isLastFrame) t else t - 1
                    val startSec = (startFrame * HOP_LENGTH) / SR.toFloat()
                    val endSec = ((endFrame + 1) * HOP_LENGTH) / SR.toFloat()
                    val midi = MIN_MIDI + noteIdx
                    events += "${midiToName(midi)} ${"%.2f".format(startSec)}s-${"%.2f".format(endSec)}s"
                    startFrame = -1
                }
            }
        }

        return if (events.isEmpty()) {
            "No notes detected"
        } else {
            events.sortedBy { it.substringAfter(' ').substringBefore('s').toFloatOrNull() ?: 0f }
                .joinToString(separator = "\n")
        }
    }

    private fun midiToName(midi: Int): String {
        val names = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
        val pitchClass = names[midi % 12]
        val octave = (midi / 12) - 1
        return "$pitchClass$octave"
    }

    private fun buildCqtLikeFeatures(audio: FloatArray): Array<FloatArray> {
        val minFreq = 32.70319566f // C1
        val maxFreq = (minFreq * 2.0.pow((CQT_BINS - 1) / 12.0)).toFloat()
        val cqt = ConstantQ(SR.toFloat(), minFreq, maxFreq, 12f)
        val fftLength = cqt.getFFTlength()

        val frameCount = max(1, (audio.size - fftLength).coerceAtLeast(0) / HOP_LENGTH + 1)
        val features = Array(frameCount) { FloatArray(CQT_BINS) }

        var globalMax = Float.MIN_VALUE
        for (frame in 0 until frameCount) {
            val start = frame * HOP_LENGTH
            val frameSamples = FloatArray(fftLength)
            for (i in 0 until fftLength) {
                frameSamples[i] = if (start + i < audio.size) audio[start + i] else 0f
            }

            cqt.calculateMagintudes(frameSamples)
            val magnitudes = cqt.getMagnitudes()
            val validBins = min(CQT_BINS, magnitudes.size)
            for (bin in 0 until validBins) {
                val value = magnitudes[bin]
                features[frame][bin] = value
                if (value > globalMax) {
                    globalMax = value
                }
            }
            for (bin in validBins until CQT_BINS) {
                features[frame][bin] = 0f
            }
        }

        if (globalMax <= 0f) return features

        for (frame in features.indices) {
            for (bin in 0 until CQT_BINS) {
                val relative = max(features[frame][bin], 1e-8f) / max(globalMax, 1e-8f)
                val db = 20f * ln(relative) / ln(10f)
                val normalized = ((db + 80f) / 80f).coerceIn(0f, 1f)
                features[frame][bin] = normalized
            }
        }

        return features
    }

    private fun decodeToMonoFloatPcm(uri: Uri, targetSampleRate: Int): FloatArray {
        val extractor = MediaExtractor()
        extractor.setDataSource(context, uri, null)

        val trackIndex = (0 until extractor.trackCount).firstOrNull { index ->
            extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
        } ?: throw IllegalStateException("No audio track found")

        extractor.selectTrack(trackIndex)
        val format = extractor.getTrackFormat(trackIndex)
        val mimeType = format.getString(MediaFormat.KEY_MIME)
            ?: throw IllegalStateException("Audio mime type missing")

        val decoder = MediaCodec.createDecoderByType(mimeType)
        decoder.configure(format, null, null, 0)
        decoder.start()

        val sourceSampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

        val outputBytes = ArrayList<Byte>()
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false

        while (!outputDone) {
            if (!inputDone) {
                val inputBufferIndex = decoder.dequeueInputBuffer(10_000)
                if (inputBufferIndex >= 0) {
                    val inputBuffer = decoder.getInputBuffer(inputBufferIndex)
                        ?: throw IllegalStateException("Input buffer is null")
                    val sampleSize = extractor.readSampleData(inputBuffer, 0)
                    if (sampleSize < 0) {
                        decoder.queueInputBuffer(
                            inputBufferIndex,
                            0,
                            0,
                            0,
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM
                        )
                        inputDone = true
                    } else {
                        decoder.queueInputBuffer(inputBufferIndex, 0, sampleSize, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }

            val outputBufferIndex = decoder.dequeueOutputBuffer(info, 10_000)
            when {
                outputBufferIndex >= 0 -> {
                    val outputBuffer = decoder.getOutputBuffer(outputBufferIndex)
                        ?: throw IllegalStateException("Output buffer is null")
                    val chunk = ByteArray(info.size)
                    outputBuffer.get(chunk)
                    outputBuffer.clear()
                    outputBytes.addAll(chunk.toList())
                    decoder.releaseOutputBuffer(outputBufferIndex, false)

                    if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        outputDone = true
                    }
                }

                outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED || outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
            }
        }

        decoder.stop()
        decoder.release()
        extractor.release()

        val pcm16 = outputBytes.toByteArray()
        val shortBuffer = ByteBuffer.wrap(pcm16)
            .order(ByteOrder.LITTLE_ENDIAN)
            .asShortBuffer()

        val totalSamples = shortBuffer.limit()
        val frames = totalSamples / channels
        val mono = FloatArray(frames)

        for (frame in 0 until frames) {
            var mixed = 0f
            for (channel in 0 until channels) {
                val index = frame * channels + channel
                mixed += shortBuffer.get(index) / Short.MAX_VALUE.toFloat()
            }
            mono[frame] = mixed / channels
        }

        return resampleLinear(mono, sourceSampleRate, targetSampleRate)
    }

    private fun resampleLinear(input: FloatArray, sourceRate: Int, targetRate: Int): FloatArray {
        if (sourceRate == targetRate) return input
        val ratio = targetRate.toFloat() / sourceRate.toFloat()
        val targetLength = max(1, (input.size * ratio).toInt())
        val output = FloatArray(targetLength)

        for (i in 0 until targetLength) {
            val sourceIndex = i / ratio
            val left = sourceIndex.toInt()
            val right = min(left + 1, input.lastIndex)
            val t = sourceIndex - left
            output[i] = (1 - t) * input[left] + t * input[right]
        }

        return output
    }

    private fun loadModelFile(assetPath: String): ByteBuffer {
        val bytes = context.assets.open(assetPath).use { it.readBytes() }
        return ByteBuffer.allocateDirect(bytes.size)
            .order(ByteOrder.nativeOrder())
            .put(bytes)
            .apply { rewind() }
    }
}
