package com.example.notesdetector.domain.transcription

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import org.jtransforms.fft.DoubleFFT_1D
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.flex.FlexDelegate
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

class TfliteAudioTranscriber(
    private val context: Context,
    private val modelAssetPath: String = "guitar_model.tflite"
) {

    companion object {
        private const val SR = 22_050
        private const val HOP_LENGTH = 512
        private const val SEGMENT_W = 128
        private const val NUM_NOTES = 49
        private const val MIN_MIDI = 40
        private const val ONSET_THRESHOLD = 0.6f
        private const val FRAME_THRESHOLD = 0.1f
        private const val N_BINS = 84
        private const val BINS_PER_OCTAVE = 12
        private const val FMIN = 32.70319566
        private const val N_FFT = 4096
    }

    data class NoteEvent(val startSec: Float, val midi: Int, val name: String)

    suspend fun transcribe(audioUri: Uri): String {
        val modelBuffer = loadModelFile(modelAssetPath)
        val rawAudio = decodeToMonoFloatPcm(audioUri, targetSampleRate = SR)
        val cqtLike = buildCqtLikeFeatures(rawAudio)

        if (cqtLike.isEmpty()) return "No notes detected"

        return runWithInterpreter(modelBuffer) { interpreter ->
            val (fullFrames, fullOnsets) = predictSegments(interpreter, cqtLike)
            val finalRoll = applyPostprocess(fullFrames, fullOnsets)
            noteEventsToText(finalRoll)
        }
    }

    private fun <T> runWithInterpreter(modelBuffer: ByteBuffer, block: (Interpreter) -> T): T {
        return try {
            FlexDelegate().use { flexDelegate ->
                val options = Interpreter.Options().apply {
                    setNumThreads(4)
                    addDelegate(flexDelegate)
                }
                Interpreter(modelBuffer, options).use(block)
            }
        } catch (_: Throwable) {
            val fallback = Interpreter.Options().apply { setNumThreads(4) }
            Interpreter(modelBuffer, fallback).use(block)
        }
    }

    private fun predictSegments(
        interpreter: Interpreter,
        features: Array<FloatArray>
    ): Pair<Array<FloatArray>, Array<FloatArray>> {
        val totalFrames = features.size
        val fullFrames = Array(totalFrames) { FloatArray(NUM_NOTES) }
        val fullOnsets = Array(totalFrames) { FloatArray(NUM_NOTES) }

        for (i in 0 until (totalFrames - SEGMENT_W).coerceAtLeast(0) step SEGMENT_W) {
            val x = Array(1) { Array(SEGMENT_W) { Array(N_BINS) { FloatArray(1) } } }
            for (t in 0 until SEGMENT_W) {
                for (f in 0 until N_BINS) {
                    x[0][t][f][0] = features[i + t][f]
                }
            }

            val predFrames = Array(1) { Array(SEGMENT_W) { FloatArray(NUM_NOTES) } }
            val predOnsets = Array(1) { Array(SEGMENT_W) { FloatArray(NUM_NOTES) } }

            interpreter.runForMultipleInputsOutputs(
                arrayOf(x),
                mapOf(0 to predFrames, 1 to predOnsets)
            )

            for (t in 0 until SEGMENT_W) {
                fullFrames[i + t] = predFrames[0][t]
                fullOnsets[i + t] = predOnsets[0][t]
            }
        }

        return fullFrames to fullOnsets
    }

    private fun applyPostprocess(
        fullFrames: Array<FloatArray>,
        fullOnsets: Array<FloatArray>
    ): Array<IntArray> {
        val totalFrames = fullFrames.size
        val finalRoll = Array(totalFrames) { IntArray(NUM_NOTES) }

        for (n in 0 until NUM_NOTES) {
            var active = false
            for (t in 0 until totalFrames) {
                when {
                    fullOnsets[t][n] > ONSET_THRESHOLD -> {
                        active = true
                        finalRoll[t][n] = 1
                    }
                    active && fullFrames[t][n] > FRAME_THRESHOLD -> {
                        finalRoll[t][n] = 1
                    }
                    else -> {
                        active = false
                    }
                }
            }
        }

        return finalRoll
    }

    private fun noteEventsToText(finalRoll: Array<IntArray>): String {
        val totalFrames = finalRoll.size
        val events = mutableListOf<NoteEvent>()

        for (n in 0 until NUM_NOTES) {
            val midi = MIN_MIDI + n
            var start: Int? = null

            for (t in 0 until totalFrames) {
                if (finalRoll[t][n] == 1 && start == null) {
                    start = t
                } else if ((finalRoll[t][n] == 0 || t == totalFrames - 1) && start != null) {
                    val end = t
                    val t0 = start * HOP_LENGTH / SR.toFloat()
                    val t1 = end * HOP_LENGTH / SR.toFloat()
                    if (t1 - t0 > 0.05f) {
                        events += NoteEvent(t0, midi, midiName(midi))
                    }
                    start = null
                }
            }
        }

        if (events.isEmpty()) return "No notes detected"

        return events.sortedBy { it.startSec }
            .joinToString("\n") { "${"%.2f".format(it.startSec)}s | ${it.midi} | ${it.name}" }
    }

    private fun midiName(midi: Int): String {
        val names = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
        return "${names[midi % 12]}${(midi / 12) - 1}"
    }

    private fun buildCqtLikeFeatures(audio: FloatArray): Array<FloatArray> {
        val window = FloatArray(N_FFT) { i -> (0.5 - 0.5 * cos(2.0 * PI * i / N_FFT)).toFloat() }
        val frameCount = max(1, (audio.size - N_FFT).coerceAtLeast(0) / HOP_LENGTH + 1)
        val fftBins = N_FFT / 2 + 1

        val stftMag = Array(fftBins) { FloatArray(frameCount) }
        val fft = DoubleFFT_1D(N_FFT.toLong())

        for (frame in 0 until frameCount) {
            val start = frame * HOP_LENGTH
            val complex = DoubleArray(2 * N_FFT)
            for (i in 0 until N_FFT) {
                val sample = if (start + i < audio.size) audio[start + i] else 0f
                complex[2 * i] = (sample * window[i]).toDouble()
                complex[2 * i + 1] = 0.0
            }
            fft.complexForward(complex)

            for (k in 0 until fftBins) {
                val re = complex[2 * k]
                val im = complex[2 * k + 1]
                stftMag[k][frame] = sqrt(re * re + im * im).toFloat()
            }
        }

        val fftFreqs = FloatArray(fftBins) { k -> k * SR / N_FFT.toFloat() }
        val cqtFreqs = FloatArray(N_BINS) { i -> (FMIN * 2.0.pow(i / BINS_PER_OCTAVE.toDouble())).toFloat() }
        val q = 1.0 / (2.0.pow(1.0 / BINS_PER_OCTAVE) - 1.0)
        val eps = 1e-10f

        val features = Array(frameCount) { FloatArray(N_BINS) }

        for (i in 0 until N_BINS) {
            val f = cqtFreqs[i]
            val bandwidth = (f / q).toFloat()

            val weights = FloatArray(fftBins)
            var weightSum = 0f
            for (k in 0 until fftBins) {
                val z = (fftFreqs[k] - f) / (bandwidth + eps)
                val w = exp((-0.5f * z * z).toDouble()).toFloat()
                weights[k] = w
                weightSum += w
            }
            val norm = max(weightSum, eps)
            for (k in 0 until fftBins) {
                weights[k] /= norm
            }

            for (t in 0 until frameCount) {
                var value = 0f
                for (k in 0 until fftBins) {
                    value += weights[k] * stftMag[k][t]
                }
                features[t][i] = value
            }
        }

        for (t in 0 until frameCount) {
            for (i in 0 until N_BINS) {
                val v = max(features[t][i], eps)
                val db = 20f * log10(v)
                features[t][i] = ((db + 80f) / 80f).coerceIn(0f, 1f)
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
