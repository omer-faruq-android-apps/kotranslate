package com.kotranslate

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateRemoteModel
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class ModelInfo(
    val code: String,
    val name: String,
    val downloaded: Boolean,
    val sizeMb: Int = 30
)

class ModelManager {

    private val remoteModelManager = RemoteModelManager.getInstance()

    suspend fun getDownloadedModels(): Set<String> = suspendCancellableCoroutine { cont ->
        remoteModelManager.getDownloadedModels(TranslateRemoteModel::class.java)
            .addOnSuccessListener { models ->
                val codes = models.mapNotNull { model ->
                    LanguageUtils.getByMlKitCode(model.language)?.code
                }.toSet()
                cont.resume(codes)
            }
            .addOnFailureListener { e ->
                cont.resumeWithException(e)
            }
    }

    suspend fun getAllModelsWithStatus(): List<ModelInfo> {
        val downloaded = try {
            getDownloadedModels()
        } catch (_: Exception) {
            emptySet()
        }
        return LanguageUtils.allLanguages.map { lang ->
            ModelInfo(
                code = lang.code,
                name = lang.name,
                downloaded = lang.code in downloaded,
                sizeMb = 30
            )
        }
    }

    suspend fun downloadModel(languageCode: String, requireWifi: Boolean = false): Boolean =
        suspendCancellableCoroutine { cont ->
            val mlKitCode = LanguageUtils.getMlKitCode(languageCode)
            if (mlKitCode == null) {
                cont.resumeWithException(
                    IllegalArgumentException("Unknown language code: $languageCode")
                )
                return@suspendCancellableCoroutine
            }

            val model = TranslateRemoteModel.Builder(mlKitCode).build()
            val conditions = DownloadConditions.Builder().apply {
                if (requireWifi) requireWifi()
            }.build()

            remoteModelManager.download(model, conditions)
                .addOnSuccessListener { cont.resume(true) }
                .addOnFailureListener { e -> cont.resumeWithException(e) }
        }

    suspend fun deleteModel(languageCode: String): Boolean =
        suspendCancellableCoroutine { cont ->
            val mlKitCode = LanguageUtils.getMlKitCode(languageCode)
            if (mlKitCode == null) {
                cont.resumeWithException(
                    IllegalArgumentException("Unknown language code: $languageCode")
                )
                return@suspendCancellableCoroutine
            }

            val model = TranslateRemoteModel.Builder(mlKitCode).build()
            remoteModelManager.deleteDownloadedModel(model)
                .addOnSuccessListener { cont.resume(true) }
                .addOnFailureListener { e -> cont.resumeWithException(e) }
        }
}
