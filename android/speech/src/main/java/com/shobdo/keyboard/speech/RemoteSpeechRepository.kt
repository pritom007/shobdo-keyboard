package com.shobdo.keyboard.speech

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

/**
 * Talks to the Shobdo backend's `POST /v1/transcriptions` endpoint over OkHttp.
 *
 * The constructor takes an [OkHttpClient] so callers (and tests) can configure
 * timeouts, interceptors, or a [okhttp3.mockwebserver.MockWebServer] behind
 * the same code path. A default client is built from [SpeechConfig] when none
 * is supplied.
 */
class RemoteSpeechRepository(
    private val config: SpeechConfig,
    private val deviceId: String,
    private val client: OkHttpClient = buildClient(config),
) : SpeechRepository {

    override fun transcribe(wavBytes: ByteArray, language: String): SpeechResult {
        val url = "${config.baseUrl.trimEnd('/')}/v1/transcriptions"
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "audio",
                "audio.wav",
                wavBytes.toRequestBody("audio/wav".toMediaType()),
            )
            .addFormDataPart("language", language)
            .build()

        val request = Request.Builder()
            .url(url)
            .post(body)
            .header(HEADER_DEVICE_ID, deviceId)
            .build()

        return try {
            client.newCall(request).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (resp.isSuccessful) parseSuccess(raw) else parseError(raw, resp.code)
            }
        } catch (e: SocketTimeoutException) {
            SpeechResult.Error(SpeechError.Timeout)
        } catch (_: IOException) {
            // Includes ConnectException, UnknownHostException, connection reset.
            SpeechResult.Error(SpeechError.NoNetwork)
        }
    }

    private fun parseSuccess(raw: String): SpeechResult {
        return try {
            val json = JSONObject(raw)
            val text = json.optString("text", "")
            if (text.isBlank()) {
                SpeechResult.Error(SpeechError.Unknown(null))
            } else {
                SpeechResult.Success(
                    TranscriptionResult(
                        text = text,
                        language = json.optString("language", "bn"),
                        durationMs = json.optLong("duration_ms", 0L),
                    ),
                )
            }
        } catch (_: Exception) {
            SpeechResult.Error(SpeechError.Unknown(null))
        }
    }

    private fun parseError(raw: String, httpCode: Int): SpeechResult {
        val code = try {
            JSONObject(raw).getJSONObject("error").optString("code")
        } catch (_: Exception) {
            ""
        }
        val error = when {
            code.isNotEmpty() -> SpeechError.fromCode(code)
            httpCode == 401 -> SpeechError.Unauthorized
            httpCode == 429 -> SpeechError.RateLimit
            httpCode in 400..499 -> SpeechError.Unknown(null)
            httpCode >= 500 -> SpeechError.Provider("HTTP_$httpCode")
            else -> SpeechError.Unknown(null)
        }
        return SpeechResult.Error(error)
    }

    companion object {
        const val HEADER_DEVICE_ID = "X-Device-Id"

        fun buildClient(config: SpeechConfig): OkHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(config.connectTimeoutMs, TimeUnit.MILLISECONDS)
                .readTimeout(config.readTimeoutMs, TimeUnit.MILLISECONDS)
                .writeTimeout(config.writeTimeoutMs, TimeUnit.MILLISECONDS)
                .build()
    }
}
