package com.example.notesdetector.domain.transcription

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min

class TfliteAudioTranscriber(
    private val context: Context,
    private val modelAssetPath: String = "guitar_model.tflite",
    private val labelsAssetPath: String = "tokens.txt"
) {

    suspend fun transcribe(audioUri: Uri): String {
        val modelBuffer = loadModelFile(modelAssetPath)
        val audio = decodeToMonoFloatPcm(audioUri, targetSampleRate = 16_000)

        Interpreter(modelBuffer, Interpreter.Options().apply { setNumThreads(4) }).use { interpreter ->
            val inputTensor = interpreter.getInputTensor(0)
            val inputShape = inputTensor.shape()

            when {
                inputShape.size == 2 -> interpreter.resizeInput(0, intArrayOf(1, audio.size))
                inputShape.size == 3 -> interpreter.resizeInput(0, intArrayOf(1, audio.size, 1))
                else -> throw IllegalStateException("Unsupported input shape: ${inputShape.contentToString()}")
            }
            interpreter.allocateTensors()

            val input: Any = if (inputShape.size == 2) {
                arrayOf(audio)
            } else {
                Array(1) { Array(audio.size) { index -> floatArrayOf(audio[index]) } }
            }

            val outputTensor = interpreter.getOutputTensor(0)
            return when (outputTensor.dataType()) {
                DataType.STRING -> {
                    val result = Array(1) { "" }
                    interpreter.run(input, result)
                    result.first().trim()
                }

                DataType.FLOAT32 -> {
                    val shape = outputTensor.shape()
                    if (shape.size != 3) {
                        throw IllegalStateException("Unsupported FLOAT32 output shape: ${shape.contentToString()}")
                    }

                    val logits = Array(shape[0]) { Array(shape[1]) { FloatArray(shape[2]) } }
                    interpreter.run(input, logits)
                    val labels = loadLabels(labelsAssetPath)
                    decodeGreedyCtc(logits[0], labels)
                }

                else -> throw IllegalStateException("Unsupported output tensor type: ${outputTensor.dataType()}")
            }
        }
    }

    private fun decodeGreedyCtc(logits: Array<FloatArray>, labels: List<String>): String {
        val blankToken = 0
        val output = StringBuilder()
        var previousIndex = blankToken

        for (step in logits) {
            var bestIndex = 0
            var bestValue = Float.NEGATIVE_INFINITY
            for (i in step.indices) {
                if (step[i] > bestValue) {
                    bestValue = step[i]
                    bestIndex = i
                }
            }

            if (bestIndex != blankToken && bestIndex != previousIndex && bestIndex < labels.size) {
                output.append(labels[bestIndex])
            }
            previousIndex = bestIndex
        }

        return output.toString().replace("▁", " ").trim()
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

        val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
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

        return resample(mono, sampleRate, targetSampleRate)
    }

    private fun resample(input: FloatArray, sourceRate: Int, targetRate: Int): FloatArray {
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

    private fun loadLabels(assetPath: String): List<String> {
        return context.assets.open(assetPath).bufferedReader().readLines()
    }
}
