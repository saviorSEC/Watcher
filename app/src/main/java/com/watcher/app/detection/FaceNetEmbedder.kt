package com.watcher.app.detection

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * Runs FaceNet or ArcFace TFLite model for face embedding extraction.
 *
 * Uses a TFLite model placed in assets/models/facenet.tflite.
 * If no model is present, embeddings are not computed.
 *
 * Model source: https://github.com/serengil/deepface (convert to TFLite)
 * or use pre-converted models from tflite-face-recognition.
 */
class FaceNetEmbedder(private val context: Context) {

    companion object {
        private const val TAG = "Watcher.FaceNet"
        private const val MODEL_FILE = "facenet.tflite"
        private const val INPUT_SIZE = 160
        private const val EMBEDDING_SIZE = 128
    }

    private var interpreter: Interpreter? = null
    private var isLoaded = false

    init {
        loadModel()
    }

    private fun loadModel() {
        try {
            val modelBuffer = loadModelFile()
            interpreter = Interpreter(modelBuffer)
            isLoaded = true
            Log.i(TAG, "FaceNet TFLite model loaded successfully")
        } catch (e: Exception) {
            Log.w(TAG, "FaceNet model not found ($MODEL_FILE) — " +
                    "embeddings disabled. Detection still works with ML Kit.")
            isLoaded = false
        }
    }

    /**
     * Extract face embedding from a face crop bitmap.
     * The bitmap should be an aligned face crop (already detected by ML Kit).
     *
     * @param faceBitmap Cropped face image (will be resized to 160x160)
     * @return FloatArray embedding vector, or null if model not loaded
     */
    fun getEmbedding(faceBitmap: Bitmap): FloatArray? {
        if (!isLoaded || interpreter == null) return null

        try {
            // Resize to model input size
            val resized = Bitmap.createScaledBitmap(faceBitmap, INPUT_SIZE, INPUT_SIZE, true)

            // Convert to ByteBuffer (float32 input)
            val input = ByteBuffer.allocateDirect(1 * INPUT_SIZE * INPUT_SIZE * 3 * 4)
            input.order(ByteOrder.nativeOrder())
            input.rewind()

            val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
            resized.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)

            for (pixel in pixels) {
                input.putFloat(((pixel shr 16) and 0xFF) / 255.0f) // R
                input.putFloat(((pixel shr 8) and 0xFF) / 255.0f)  // G
                input.putFloat((pixel and 0xFF) / 255.0f)           // B
            }

            // Run inference
            val output = Array(1) { FloatArray(EMBEDDING_SIZE) }
            interpreter?.run(input, output)

            // L2 normalize the embedding
            val embedding = output[0]
            val norm = kotlin.math.sqrt(embedding.sumOf { it * it })
            if (norm > 0) {
                for (i in embedding.indices) {
                    embedding[i] /= norm
                }
            }

            return embedding

        } catch (e: Exception) {
            Log.w(TAG, "Embedding extraction failed: ${e.message}")
            return null
        }
    }

    /**
     * Compute cosine similarity between two normalized embeddings.
     * Range: -1 (opposite) to 1 (identical).
     */
    fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size) return 0f
        var dot = 0f
        for (i in a.indices) {
            dot += a[i] * b[i]
        }
        return dot.coerceIn(-1f, 1f)
    }

    /**
     * Compute L2 distance between two embeddings.
     * Lower = more similar. Typically 0.5-1.0 = same person, >1.5 = different.
     */
    fun l2Distance(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size) return Float.MAX_VALUE
        var sum = 0f
        for (i in a.indices) {
            val diff = a[i] - b[i]
            sum += diff * diff
        }
        return kotlin.math.sqrt(sum)
    }

    private fun loadModelFile(): MappedByteBuffer {
        val fd = context.assets.openFd(MODEL_FILE)
        val inputStream = FileInputStream(fd.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fd.startOffset
        val declaredLength = fd.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    fun close() {
        interpreter?.close()
        interpreter = null
        Log.i(TAG, "FaceNet closed")
    }
}
