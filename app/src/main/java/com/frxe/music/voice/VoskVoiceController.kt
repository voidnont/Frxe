package com.frxe.music.voice

import android.content.Context
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import java.io.File

/**
 * Offline VOSK controller. A speech model is intentionally not bundled because models are large.
 * Put an unpacked model at filesDir/vosk-model and call start().
 */
class VoskVoiceController(
    private val context: Context,
    private val onCommand: (VoiceCommand) -> Unit,
    private val onState: (String) -> Unit = {}
) : RecognitionListener {
    private var speechService: SpeechService? = null
    private var model: Model? = null

    fun isModelInstalled(): Boolean = File(context.filesDir, "vosk-model").exists()

    fun start() {
        if (speechService != null) return
        val modelDir = File(context.filesDir, "vosk-model")
        if (!modelDir.exists()) {
            onState("Install a VOSK model in filesDir/vosk-model")
            return
        }
        runCatching {
            model = Model(modelDir.absolutePath)
            val recognizer = Recognizer(model, 16_000.0f)
            speechService = SpeechService(recognizer, 16_000.0f).also { it.startListening(this) }
            onState("Listening offline")
        }.onFailure {
            onState(it.message ?: "Voice engine failed")
            stop()
        }
    }

    fun stop() {
        speechService?.stop()
        speechService?.shutdown()
        speechService = null
        model?.close()
        model = null
        onState("Voice idle")
    }

    private fun consume(json: String?) {
        if (json.isNullOrBlank()) return
        val text = runCatching { JSONObject(json).optString("text") }.getOrDefault("")
        if (text.isNotBlank()) onCommand(VoiceCommandParser.parse(text))
    }

    override fun onPartialResult(hypothesis: String?) = Unit
    override fun onResult(hypothesis: String?) = consume(hypothesis)
    override fun onFinalResult(hypothesis: String?) = consume(hypothesis)
    override fun onError(exception: Exception?) = onState(exception?.message ?: "Voice error")
    override fun onTimeout() = onState("Voice timeout")
}
