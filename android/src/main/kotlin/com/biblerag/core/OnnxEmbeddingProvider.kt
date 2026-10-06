package com.biblerag.core

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.Closeable
import java.nio.LongBuffer

/**
 * Провайдер эмбеддингов на базе ONNX Runtime Mobile.
 * Вычисляет нормализованные 312-мерные векторы текста на CPU мобильного устройства.
 */
class OnnxEmbeddingProvider(
    modelBytes: ByteArray,
    private val tokenizer: BertWordPieceTokenizer
) : Closeable {

    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val session: OrtSession

    init {
        val options = OrtSession.SessionOptions()
        // Оптимизация под мобильный CPU (2 потока)
        options.setIntraOpNumThreads(2)
        session = env.createSession(modelBytes, options)
    }

    /**
     * Вычисляет 312-мерный вектор эмбеддинга для входного текста.
     */
    fun computeEmbedding(text: String): FloatArray {
        val tokenIds = tokenizer.encode(text, maxLen = 64)
        val seqLen = tokenIds.size.toLong()
        val shape = longArrayOf(1, seqLen)

        val inputIdsBuf = LongBuffer.wrap(tokenIds)
        val maskBuf = LongBuffer.wrap(LongArray(tokenIds.size) { 1L })
        val typeBuf = LongBuffer.wrap(LongArray(tokenIds.size) { 0L })

        val inputIdsTensor = OnnxTensor.createTensor(env, inputIdsBuf, shape)
        val maskTensor = OnnxTensor.createTensor(env, maskBuf, shape)
        val typeTensor = OnnxTensor.createTensor(env, typeBuf, shape)

        try {
            val inputs = mapOf(
                "input_ids" to inputIdsTensor,
                "attention_mask" to maskTensor,
                "token_type_ids" to typeTensor
            )

            session.run(inputs).use { results ->
                val outputTensor = results.get(0) as OnnxTensor
                val floatBuffer = outputTensor.floatBuffer
                val embedding = FloatArray(floatBuffer.remaining())
                floatBuffer.get(embedding)
                return embedding
            }
        } finally {
            inputIdsTensor.close()
            maskTensor.close()
            typeTensor.close()
        }
    }

    override fun close() {
        session.close()
        env.close()
    }
}
