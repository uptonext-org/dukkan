package app.dukkan.capture

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.module.annotations.ReactModule
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Shopping list by voice and video: SpeechRecognizer (on-device or the system's recognition
 * service; only the text is passed on) and frames of a video via MediaMetadataRetriever.
 *
 * The recognizer stops after a pause; while the customer has not tapped "done", it is restarted and
 * the phrases are joined, so a longer list can be dictated in one go (at most two minutes).
 */
@ReactModule(name = DukkanCaptureModule.NAME)
class DukkanCaptureModule(context: ReactApplicationContext) : NativeDukkanCaptureSpec(context) {

  companion object {
    const val NAME = "DukkanCapture"
    private const val MAX_LISTEN_MS = 120_000L
    private const val STOP_GRACE_MS = 2_000L
  }

  private val main = Handler(Looper.getMainLooper())
  private val io = Executors.newSingleThreadExecutor()
  private var recognizer: SpeechRecognizer? = null
  private var language = "de-DE"
  private var startedAt = 0L
  /** Callers waiting for the final transcript (a second stop while stopping waits as well). */
  private val stopPromises = mutableListOf<Promise>()

  @Volatile private var listening = false
  @Volatile private var committed = ""
  @Volatile private var partial = ""
  @Volatile private var error = ""

  override fun isSpeechAvailable(promise: Promise) {
    promise.resolve(SpeechRecognizer.isRecognitionAvailable(reactApplicationContext))
  }

  override fun startSpeech(languageTag: String, promise: Promise) {
    if (reactApplicationContext.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
      promise.reject("permission", "Microphone permission not granted")
      return
    }
    if (!SpeechRecognizer.isRecognitionAvailable(reactApplicationContext)) {
      promise.reject("unavailable", "No speech recognition service")
      return
    }
    main.post {
      if (listening) {
        promise.reject("busy", "Already listening")
        return@post
      }
      committed = ""
      partial = ""
      error = ""
      language = languageTag
      startedAt = SystemClock.elapsedRealtime()
      try {
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(reactApplicationContext).also { it.setRecognitionListener(listener) }
        listening = true
        listen()
        promise.resolve(null)
      } catch (e: RuntimeException) {
        listening = false
        promise.reject("failed", e.message, e)
      }
    }
  }

  override fun isListening(): Boolean = listening

  override fun speechTranscript(): String = listOf(committed, partial).filter { it.isNotBlank() }.joinToString(" ")

  override fun speechError(): String = error

  override fun stopSpeech(promise: Promise) {
    main.post {
      val active = recognizer
      if (!listening || active == null) {
        promise.resolve(speechTranscript())
        return@post
      }
      stopPromises.add(promise)
      if (stopPromises.size > 1) {
        return@post
      }
      active.stopListening()
      // onResults normally follows within a moment; do not wait for it forever.
      main.postDelayed({ if (stopPromises.contains(promise)) finish() }, STOP_GRACE_MS)
    }
  }

  override fun cancelSpeech() {
    main.post {
      listening = false
      val transcript = speechTranscript()
      stopPromises.forEach { it.resolve(transcript) }
      stopPromises.clear()
      recognizer?.cancel()
      recognizer?.destroy()
      recognizer = null
    }
  }

  override fun extractVideoFrames(uri: String, count: Double, maxEdge: Double, promise: Promise) {
    io.execute {
      val retriever = MediaMetadataRetriever()
      try {
        retriever.setDataSource(reactApplicationContext, Uri.parse(uri))
        val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        val frames = count.toInt().coerceIn(1, 8)
        val edge = maxEdge.toInt().coerceIn(64, 4096)
        val out = Arguments.createArray()
        for (i in 0 until frames) {
          val atUs = durationMs * 1000L * (2 * i + 1) / (2 * frames)
          val frame = retriever.getFrameAtTime(atUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) ?: continue
          val scaled = scale(frame, edge)
          val file = File(reactApplicationContext.cacheDir, "dukkan-frame-${System.currentTimeMillis()}-$i.jpg")
          FileOutputStream(file).use { scaled.compress(Bitmap.CompressFormat.JPEG, 80, it) }
          if (scaled !== frame) {
            scaled.recycle()
          }
          frame.recycle()
          out.pushString(Uri.fromFile(file).toString())
        }
        promise.resolve(out)
      } catch (e: Exception) {
        promise.reject("failed", e.message, e)
      } finally {
        try {
          retriever.release()
        } catch (_: Exception) {
          // Nothing left to clean up.
        }
      }
    }
  }

  override fun invalidate() {
    cancelSpeech()
    io.shutdown()
    super.invalidate()
  }

  private fun listen() {
    val intent =
      Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2_500L)
      }
    recognizer?.startListening(intent)
  }

  private val listener =
    object : RecognitionListener {
      override fun onReadyForSpeech(params: Bundle?) {}

      override fun onBeginningOfSpeech() {}

      override fun onRmsChanged(rmsdB: Float) {}

      override fun onBufferReceived(buffer: ByteArray?) {}

      override fun onEndOfSpeech() {}

      override fun onEvent(eventType: Int, params: Bundle?) {}

      override fun onPartialResults(partialResults: Bundle?) {
        partial = best(partialResults)
      }

      override fun onResults(results: Bundle?) {
        commit(best(results))
        continueOrFinish()
      }

      override fun onError(code: Int) {
        when (code) {
          SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT, SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {}
          SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> error = "permission"
          SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER -> error = "network"
          else -> error = "failed"
        }
        commit(partial)
        if (error.isNotEmpty()) finish() else continueOrFinish()
      }
    }

  private fun best(bundle: Bundle?): String =
    bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim().orEmpty()

  private fun commit(text: String) {
    if (text.isNotBlank()) {
      committed = listOf(committed, text.trim()).filter { it.isNotBlank() }.joinToString(" ")
    }
    partial = ""
  }

  private fun continueOrFinish() {
    if (listening && stopPromises.isEmpty() && SystemClock.elapsedRealtime() - startedAt < MAX_LISTEN_MS) {
      main.postDelayed({ if (listening && stopPromises.isEmpty()) listen() }, 150)
    } else {
      finish()
    }
  }

  private fun finish() {
    commit(partial)
    listening = false
    recognizer?.destroy()
    recognizer = null
    stopPromises.forEach { it.resolve(committed) }
    stopPromises.clear()
  }

  private fun scale(bitmap: Bitmap, maxEdge: Int): Bitmap {
    val longEdge = max(bitmap.width, bitmap.height)
    if (longEdge <= maxEdge) {
      return bitmap
    }
    val factor = maxEdge.toDouble() / longEdge
    return Bitmap.createScaledBitmap(bitmap, (bitmap.width * factor).roundToInt().coerceAtLeast(1), (bitmap.height * factor).roundToInt().coerceAtLeast(1), true)
  }
}
