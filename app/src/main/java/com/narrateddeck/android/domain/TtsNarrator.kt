package com.narrateddeck.android.domain

import android.content.Context
import android.media.MediaPlayer
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Thin wrapper around system [TextToSpeech] for preview playback and WAV synthesis to file.
 *
 * Audio format from TTS synthesizeToFile is typically WAV (PCM) on modern Android;
 * [AudioExporter] may re-wrap or copy as-is into the export zip.
 */
class TtsNarrator(context: Context) {

    private val appContext = context.applicationContext
    private val mutex = Mutex()
    private var tts: TextToSpeech? = null
    private var ready = false
    private var mediaPlayer: MediaPlayer? = null

    suspend fun ensureReady(): Unit = mutex.withLock {
        if (ready && tts != null) return
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                val engine = TextToSpeech(appContext) { status ->
                    if (status == TextToSpeech.SUCCESS) {
                        ready = true
                        if (cont.isActive) cont.resume(Unit)
                    } else {
                        ready = false
                        if (cont.isActive) {
                            cont.resumeWithException(
                                IllegalStateException("TextToSpeech init failed (status=$status)")
                            )
                        }
                    }
                }
                tts = engine
                cont.invokeOnCancellation {
                    engine.shutdown()
                    tts = null
                    ready = false
                }
            }
        }
        tts?.language = Locale.getDefault()
    }

    /**
     * Speak [text] aloud for preview. Stops any current utterance first.
     */
    suspend fun speak(text: String) {
        if (text.isBlank()) return
        ensureReady()
        withContext(Dispatchers.Main) {
            stopPlayback()
            val engine = tts ?: return@withContext
            val utteranceId = UUID.randomUUID().toString()
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, Bundle(), utteranceId)
        }
    }

    fun stopSpeaking() {
        tts?.stop()
        stopPlayback()
    }

    /**
     * Synthesize [text] to a WAV file under [outputDir]. Returns the file path.
     */
    suspend fun synthesizeToFile(text: String, outputDir: File, fileName: String): String {
        if (text.isBlank()) {
            throw IllegalArgumentException("Cannot synthesize empty notes")
        }
        ensureReady()
        outputDir.mkdirs()
        val outFile = File(outputDir, fileName)

        return withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                val engine = tts
                if (engine == null) {
                    cont.resumeWithException(IllegalStateException("TTS not ready"))
                    return@suspendCancellableCoroutine
                }
                val utteranceId = UUID.randomUUID().toString()
                engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {
                        if (cont.isActive) cont.resume(outFile.absolutePath)
                    }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        if (cont.isActive) {
                            cont.resumeWithException(IllegalStateException("TTS synthesis error"))
                        }
                    }
                    override fun onError(utteranceId: String?, errorCode: Int) {
                        if (cont.isActive) {
                            cont.resumeWithException(
                                IllegalStateException("TTS synthesis error code=$errorCode")
                            )
                        }
                    }
                })
                val result = engine.synthesizeToFile(text, Bundle(), outFile, utteranceId)
                if (result != TextToSpeech.SUCCESS && cont.isActive) {
                    cont.resumeWithException(
                        IllegalStateException("synthesizeToFile returned $result")
                    )
                }
                cont.invokeOnCancellation { engine.stop() }
            }
        }
    }

    /**
     * Play a previously synthesized audio file for preview.
     */
    suspend fun playFile(path: String) = withContext(Dispatchers.IO) {
        withContext(Dispatchers.Main) {
            stopSpeaking()
            stopPlayback()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(path)
                prepare()
                start()
            }
        }
    }

    private fun stopPlayback() {
        try {
            mediaPlayer?.stop()
        } catch (_: Exception) {
        }
        mediaPlayer?.release()
        mediaPlayer = null
    }

    fun shutdown() {
        stopSpeaking()
        tts?.shutdown()
        tts = null
        ready = false
    }
}
