package com.shobdo.keyboard.speech

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertIs

/**
 * Exercises [RemoteSpeechRepository] against a [MockWebServer] so no real
 * network is involved. Covers the happy path, every backend error code, the
 * network/timeout failure paths, and the shape of the outgoing request.
 */
class RemoteSpeechRepositoryTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun repo(client: OkHttpClient = defaultClient()): RemoteSpeechRepository {
        val baseUrl = server.url("/").toString().trimEnd('/')
        return RemoteSpeechRepository(
            config = SpeechConfig(baseUrl = baseUrl),
            deviceId = "dev1",
            client = client,
        )
    }

    private fun defaultClient(): OkHttpClient = OkHttpClient.Builder().build()

    private fun enqueue(code: Int, body: String) {
        server.enqueue(
            MockResponse()
                .setResponseCode(code)
                .setBody(body)
                .setHeader("Content-Type", "application/json"),
        )
    }

    @Test
    fun `success returns the transcribed text`() {
        enqueue(200, """{"text":"আমি ভালো আছি","language":"bn","duration_ms":1500}""")
        val result = repo().transcribe(ByteArray(100), "bn")
        assertTrue(result is SpeechResult.Success)
        val data = (result as SpeechResult.Success).data
        assertEquals("আমি ভালো আছি", data.text)
        assertEquals("bn", data.language)
        assertEquals(1500L, data.durationMs)
    }

    @Test
    fun `success with blank text maps to unknown error`() {
        enqueue(200, """{"text":"","language":"bn","duration_ms":0}""")
        val result = repo().transcribe(ByteArray(100), "bn")
        assertIs<SpeechResult.Error>(result)
        assertIs<SpeechError.Unknown>((result as SpeechResult.Error).error)
    }

    @Test
    fun `malformed success body maps to unknown error`() {
        enqueue(200, "not json at all")
        val result = repo().transcribe(ByteArray(100), "bn")
        assertIs<SpeechResult.Error>(result)
        assertIs<SpeechError.Unknown>((result as SpeechResult.Error).error)
    }

    @Test
    fun `request posts multipart with device id and language`() {
        enqueue(200, """{"text":"hi","language":"bn","duration_ms":100}""")
        repo().transcribe(ByteArray(50), "bn")
        val recorded = server.takeRequest()
        assertTrue(recorded.path!!.endsWith("/v1/transcriptions"))
        assertEquals("dev1", recorded.getHeader("X-Device-Id"))
        val contentType = recorded.getHeader("Content-Type")
        assertTrue(contentType != null && contentType.startsWith("multipart/form-data"))
        val body = recorded.body.readUtf8()
        assertTrue(body.contains("name=\"audio\""))
        assertTrue(body.contains("name=\"language\""))
        assertTrue(body.contains("audio.wav"))
        assertTrue(body.contains("bn"))
    }

    @Test
    fun `NO_AUDIO error code maps to NoAudio`() {
        enqueue(400, """{"error":{"code":"NO_AUDIO"}}""")
        val result = repo().transcribe(ByteArray(0), "bn")
        assertIs<SpeechResult.Error>(result)
        assertIs<SpeechError.NoAudio>((result as SpeechResult.Error).error)
    }

    @Test
    fun `BAD_AUDIO error code maps to BadAudio`() {
        enqueue(400, """{"error":{"code":"BAD_AUDIO"}}""")
        assertIs<SpeechError.BadAudio>(errorOf(repo().transcribe(ByteArray(10), "bn")))
    }

    @Test
    fun `TOO_LONG error code maps to TooLong`() {
        enqueue(413, """{"error":{"code":"TOO_LONG"}}""")
        assertIs<SpeechError.TooLong>(errorOf(repo().transcribe(ByteArray(10), "bn")))
    }

    @Test
    fun `TOO_LARGE error code maps to TooLarge`() {
        enqueue(413, """{"error":{"code":"TOO_LARGE"}}""")
        assertIs<SpeechError.TooLarge>(errorOf(repo().transcribe(ByteArray(10), "bn")))
    }

    @Test
    fun `RATE_LIMIT error code maps to RateLimit`() {
        enqueue(429, """{"error":{"code":"RATE_LIMIT"}}""")
        assertIs<SpeechError.RateLimit>(errorOf(repo().transcribe(ByteArray(10), "bn")))
    }

    @Test
    fun `UNAUTHORIZED error code maps to Unauthorized`() {
        enqueue(401, """{"error":{"code":"UNAUTHORIZED"}}""")
        assertIs<SpeechError.Unauthorized>(errorOf(repo().transcribe(ByteArray(10), "bn")))
    }

    @Test
    fun `PROVIDER_ERROR code maps to Provider`() {
        enqueue(502, """{"error":{"code":"PROVIDER_ERROR"}}""")
        val err = errorOf(repo().transcribe(ByteArray(10), "bn"))
        assertIs<SpeechError.Provider>(err)
        assertEquals("PROVIDER_ERROR", (err as SpeechError.Provider).providerCode)
    }

    @Test
    fun `PROVIDER_TIMEOUT code maps to Provider`() {
        enqueue(504, """{"error":{"code":"PROVIDER_TIMEOUT"}}""")
        assertIs<SpeechError.Provider>(errorOf(repo().transcribe(ByteArray(10), "bn")))
    }

    @Test
    fun `unknown error code maps to Unknown carrying the raw code`() {
        enqueue(418, """{"error":{"code":"I_AM_A_TEAPOT"}}""")
        val err = errorOf(repo().transcribe(ByteArray(10), "bn"))
        assertIs<SpeechError.Unknown>(err)
        assertEquals("I_AM_A_TEAPOT", (err as SpeechError.Unknown).rawCode)
    }

    @Test
    fun `error response with no code falls back to HTTP-based guess for 5xx`() {
        enqueue(500, "Internal Server Error")
        val err = errorOf(repo().transcribe(ByteArray(10), "bn"))
        assertIs<SpeechError.Provider>(err)
    }

    @Test
    fun `read timeout maps to Timeout`() {
        // A raw listening socket that accepts the connection but never writes
        // back — deterministic SocketTimeoutException regardless of how
        // MockWebServer would throttle a body delay.
        val serverSocket = java.net.ServerSocket(0)
        val port = serverSocket.localPort
        Thread {
            try {
                // Accept and hold the socket open without writing anything.
                serverSocket.accept()
            } catch (_: Exception) {
            }
        }.apply { isDaemon = true; start() }

        val slowClient = OkHttpClient.Builder()
            .readTimeout(100, TimeUnit.MILLISECONDS)
            .build()
        val timeoutRepo = RemoteSpeechRepository(
            config = SpeechConfig(baseUrl = "http://127.0.0.1:$port"),
            deviceId = "dev1",
            client = slowClient,
        )
        val result = timeoutRepo.transcribe(ByteArray(10), "bn")
        try {
            serverSocket.close()
        } catch (_: Exception) {
        }
        assertIs<SpeechResult.Error>(result)
        assertIs<SpeechError.Timeout>((result as SpeechResult.Error).error)
    }

    @Test
    fun `connection refused maps to NoNetwork`() {
        val port = server.url("/").toUri().port
        server.shutdown()
        val closedRepo = RemoteSpeechRepository(
            config = SpeechConfig(baseUrl = "http://127.0.0.1:$port"),
            deviceId = "dev1",
        )
        val result = closedRepo.transcribe(ByteArray(10), "bn")
        assertIs<SpeechResult.Error>(result)
        assertIs<SpeechError.NoNetwork>((result as SpeechResult.Error).error)
    }

    private fun errorOf(result: SpeechResult): SpeechError {
        assertIs<SpeechResult.Error>(result)
        return (result as SpeechResult.Error).error
    }
}
