package com.kotranslate

import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class TranslatorPool {

    private data class LangPair(val source: String, val target: String)

    private val translators = ConcurrentHashMap<LangPair, Translator>()

    private fun getOrCreateTranslator(sourceMlKit: String, targetMlKit: String): Translator {
        val key = LangPair(sourceMlKit, targetMlKit)
        return translators.getOrPut(key) {
            val options = TranslatorOptions.Builder()
                .setSourceLanguage(sourceMlKit)
                .setTargetLanguage(targetMlKit)
                .build()
            Translation.getClient(options)
        }
    }

    suspend fun ensureModelDownloaded(sourceMlKit: String, targetMlKit: String): Boolean =
        suspendCancellableCoroutine { cont ->
            val translator = getOrCreateTranslator(sourceMlKit, targetMlKit)
            translator.downloadModelIfNeeded()
                .addOnSuccessListener { cont.resume(true) }
                .addOnFailureListener { e -> cont.resumeWithException(e) }
        }

    suspend fun translate(
        text: String,
        sourceMlKit: String,
        targetMlKit: String
    ): String = suspendCancellableCoroutine { cont ->
        val translator = getOrCreateTranslator(sourceMlKit, targetMlKit)
        translator.translate(text)
            .addOnSuccessListener { result -> cont.resume(result) }
            .addOnFailureListener { e -> cont.resumeWithException(e) }
    }

    fun closeAll() {
        translators.values.forEach { it.close() }
        translators.clear()
    }
}
