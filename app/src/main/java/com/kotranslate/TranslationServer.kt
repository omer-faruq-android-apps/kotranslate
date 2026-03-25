package com.kotranslate

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.mlkit.nl.languageid.LanguageIdentification
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class TranslationServer(
    private val context: Context,
    port: Int = DEFAULT_PORT
) : NanoHTTPD(port) {

    companion object {
        const val DEFAULT_PORT = 8787
        private const val TAG = "TranslationServer"
    }

    private val gson = Gson()
    private val translatorPool = TranslatorPool()
    private val modelManager = ModelManager()
    private val languageIdentifier = LanguageIdentification.getClient()
    var requestCount: Int = 0
        private set

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        val method = session.method

        Log.d(TAG, "$method $uri")

        return try {
            when {
                method == Method.GET && uri == "/status" -> handleStatus()
                method == Method.GET && uri == "/languages" -> handleLanguages()
                method == Method.POST && uri == "/translate" -> handleTranslate(session)
                method == Method.POST && uri == "/translate/batch" -> handleBatchTranslate(session)
                method == Method.POST && uri == "/detect" -> handleDetect(session)
                method == Method.POST && uri == "/models/download" -> handleModelDownload(session)
                method == Method.POST && uri == "/models/delete" -> handleModelDelete(session)
                else -> jsonError(Response.Status.NOT_FOUND, "Unknown endpoint: $uri")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling $uri", e)
            jsonError(Response.Status.INTERNAL_ERROR, e.message ?: "Internal server error")
        }
    }

    private fun handleStatus(): Response {
        val ip = NetworkUtils.getLocalIpAddress(context)
        val data = mapOf(
            "status" to "ok",
            "app_version" to "1.0.0",
            "device_name" to android.os.Build.MODEL,
            "ip_address" to ip,
            "port" to DEFAULT_PORT,
            "request_count" to requestCount
        )
        return jsonResponse(data)
    }

    private fun handleLanguages(): Response = runBlocking {
        val models = modelManager.getAllModelsWithStatus()
        val langList = models.map { m ->
            mapOf(
                "code" to m.code,
                "name" to m.name,
                "downloaded" to m.downloaded,
                "size_mb" to m.sizeMb
            )
        }
        jsonResponse(mapOf("languages" to langList))
    }

    private fun handleTranslate(session: IHTTPSession): Response = runBlocking {
        requestCount++
        val body = readBody(session)
        val json = JsonParser.parseString(body).asJsonObject
        val text = json.get("text")?.asString ?: return@runBlocking jsonError(
            Response.Status.BAD_REQUEST, "Missing 'text' field"
        )
        val sourceCode = json.get("source")?.asString ?: return@runBlocking jsonError(
            Response.Status.BAD_REQUEST, "Missing 'source' field"
        )
        val targetCode = json.get("target")?.asString ?: return@runBlocking jsonError(
            Response.Status.BAD_REQUEST, "Missing 'target' field"
        )

        val sourceMlKit = LanguageUtils.getMlKitCode(sourceCode)
            ?: return@runBlocking jsonError(
                Response.Status.BAD_REQUEST, "Unknown source language: $sourceCode"
            )
        val targetMlKit = LanguageUtils.getMlKitCode(targetCode)
            ?: return@runBlocking jsonError(
                Response.Status.BAD_REQUEST, "Unknown target language: $targetCode"
            )

        try {
            translatorPool.ensureModelDownloaded(sourceMlKit, targetMlKit)
        } catch (e: Exception) {
            return@runBlocking jsonResponse(
                mapOf(
                    "error" to "model_not_downloaded",
                    "message" to "Required model is not downloaded. Please download it from the companion app.",
                    "missing_model" to targetCode
                ),
                Response.Status.BAD_REQUEST
            )
        }

        val translated = translatorPool.translate(text, sourceMlKit, targetMlKit)
        jsonResponse(
            mapOf(
                "translated_text" to translated,
                "source" to sourceCode,
                "target" to targetCode,
                "model_type" to "offline"
            )
        )
    }

    private fun handleBatchTranslate(session: IHTTPSession): Response = runBlocking {
        requestCount++
        val body = readBody(session)
        val json = JsonParser.parseString(body).asJsonObject
        val textsArray = json.getAsJsonArray("texts")
            ?: return@runBlocking jsonError(Response.Status.BAD_REQUEST, "Missing 'texts' array")
        val sourceCode = json.get("source")?.asString
            ?: return@runBlocking jsonError(Response.Status.BAD_REQUEST, "Missing 'source' field")
        val targetCode = json.get("target")?.asString
            ?: return@runBlocking jsonError(Response.Status.BAD_REQUEST, "Missing 'target' field")

        val sourceMlKit = LanguageUtils.getMlKitCode(sourceCode)
            ?: return@runBlocking jsonError(
                Response.Status.BAD_REQUEST, "Unknown source language: $sourceCode"
            )
        val targetMlKit = LanguageUtils.getMlKitCode(targetCode)
            ?: return@runBlocking jsonError(
                Response.Status.BAD_REQUEST, "Unknown target language: $targetCode"
            )

        try {
            translatorPool.ensureModelDownloaded(sourceMlKit, targetMlKit)
        } catch (e: Exception) {
            return@runBlocking jsonResponse(
                mapOf(
                    "error" to "model_not_downloaded",
                    "message" to "Required model is not downloaded.",
                    "missing_model" to targetCode
                ),
                Response.Status.BAD_REQUEST
            )
        }

        val translations = textsArray.map { element ->
            val srcText = element.asString
            val translated = translatorPool.translate(srcText, sourceMlKit, targetMlKit)
            mapOf("source_text" to srcText, "translated_text" to translated)
        }

        jsonResponse(
            mapOf(
                "translations" to translations,
                "source" to sourceCode,
                "target" to targetCode
            )
        )
    }

    private fun handleDetect(session: IHTTPSession): Response = runBlocking {
        val body = readBody(session)
        val json = JsonParser.parseString(body).asJsonObject
        val text = json.get("text")?.asString ?: return@runBlocking jsonError(
            Response.Status.BAD_REQUEST, "Missing 'text' field"
        )

        val result = detectLanguage(text)
        jsonResponse(
            mapOf(
                "detected_language" to (result.first ?: "und"),
                "confidence" to (result.second ?: 0f)
            )
        )
    }

    private suspend fun detectLanguage(text: String): Pair<String?, Float?> =
        suspendCancellableCoroutine { cont ->
            languageIdentifier.identifyLanguage(text)
                .addOnSuccessListener { langCode ->
                    val code = if (langCode == "und") null
                    else LanguageUtils.getByMlKitCode(langCode)?.code ?: langCode
                    cont.resume(Pair(code, null))
                }
                .addOnFailureListener { e ->
                    cont.resumeWithException(e)
                }
        }

    private fun handleModelDownload(session: IHTTPSession): Response = runBlocking {
        val body = readBody(session)
        val json = JsonParser.parseString(body).asJsonObject
        val langCode = json.get("language")?.asString ?: return@runBlocking jsonError(
            Response.Status.BAD_REQUEST, "Missing 'language' field"
        )

        try {
            modelManager.downloadModel(langCode)
            jsonResponse(
                mapOf(
                    "status" to "downloaded",
                    "language" to langCode,
                    "message" to "${LanguageUtils.getNameByCode(langCode)} model downloaded successfully"
                )
            )
        } catch (e: Exception) {
            jsonError(
                Response.Status.INTERNAL_ERROR,
                "Failed to download $langCode model: ${e.message}"
            )
        }
    }

    private fun handleModelDelete(session: IHTTPSession): Response = runBlocking {
        val body = readBody(session)
        val json = JsonParser.parseString(body).asJsonObject
        val langCode = json.get("language")?.asString ?: return@runBlocking jsonError(
            Response.Status.BAD_REQUEST, "Missing 'language' field"
        )

        try {
            modelManager.deleteModel(langCode)
            jsonResponse(
                mapOf(
                    "status" to "deleted",
                    "language" to langCode
                )
            )
        } catch (e: Exception) {
            jsonError(
                Response.Status.INTERNAL_ERROR,
                "Failed to delete $langCode model: ${e.message}"
            )
        }
    }

    private fun readBody(session: IHTTPSession): String {
        val contentLength = session.headers["content-length"]?.toIntOrNull() ?: 0
        if (contentLength == 0) return "{}"
        val buffer = ByteArray(contentLength)
        session.inputStream.read(buffer, 0, contentLength)
        return String(buffer)
    }

    private fun jsonResponse(
        data: Any,
        status: Response.Status = Response.Status.OK
    ): Response {
        val json = gson.toJson(data)
        return newFixedLengthResponse(status, "application/json", json)
    }

    private fun jsonError(status: Response.Status, message: String): Response {
        return jsonResponse(mapOf("error" to message), status)
    }

    fun shutdown() {
        stop()
        translatorPool.closeAll()
        languageIdentifier.close()
    }
}
