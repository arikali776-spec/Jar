package com.stark.jarvis.core

import ai.picovoice.porcupine.Porcupine
import ai.picovoice.porcupine.PorcupineException
import ai.picovoice.porcupine.PorcupineManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.app.NotificationCompat
import com.stark.jarvis.JarvisConfig
import com.stark.jarvis.ai.HybridAiBrain
import com.stark.jarvis.skills.SkillRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

class JarvisVoiceService : Service(), TextToSpeech.OnInitListener {
    companion object {
        val statusLog = MutableStateFlow("SYSTEM STANDBY")
        val lastTranscript = MutableStateFlow("Waiting for 'Jarvis'...")
        val isOnlineMode = MutableStateFlow(true)
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val handler = Handler(Looper.getMainLooper())
    private var porcupine: PorcupineManager? = null
    private lateinit var recognizer: SpeechRecognizer
    private lateinit var tts: TextToSpeech
    private lateinit var aiBrain: HybridAiBrain
    private val beep = ToneGenerator(AudioManager.STREAM_MUSIC, 60)

    override fun onCreate() {
        super.onCreate()
        startForeground(101, NotificationCompat.Builder(this, if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel("jarvis_core", "J.A.R.V.I.S.", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
            "jarvis_core"
        } else "").setContentTitle("J.A.R.V.I.S. Standby").setSmallIcon(android.R.drawable.ic_btn_speak_now).build())

        aiBrain = HybridAiBrain(this)
        tts = TextToSpeech(this, this)
        setupRecognizer()
        initPorcupine()
    }

    private fun initPorcupine() {
        try {
            val builder = PorcupineManager.Builder()
                .setAccessKey(JarvisConfig.PICOVOICE_ACCESS_KEY)
                .setSensitivity(JarvisConfig.WAKE_WORD_SENSITIVITY)
            if (JarvisConfig.CUSTOM_HEY_JARVIS_ASSET.isNotBlank()) {
                builder.setKeywordPath(JarvisConfig.CUSTOM_HEY_JARVIS_ASSET)
            } else {
                builder.setKeyword(Porcupine.BuiltInKeyword.JARVIS)
            }
            porcupine = builder.build(applicationContext) { handler.post { onWakeWord() } }
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun onWakeWord() {
        try { porcupine?.stop() } catch (e: Exception) {}
        beep.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
        isOnlineMode.value = aiBrain.isOnline()
        statusLog.value = if (isOnlineMode.value) "LISTENING (ONLINE)" else "LISTENING (OFFLINE)"
        lastTranscript.value = "Listening, sir..."
        
        try {
            recognizer.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, !aiBrain.isOnline())
            })
        } catch (e: Exception) { startStandby() }
    }

    private fun startStandby() {
        handler.post {
            try {
                porcupine?.start()
                isOnlineMode.value = aiBrain.isOnline()
                statusLog.value = "STANDBY: SAY 'JARVIS'"
            } catch (e: Exception) {}
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale.UK
            tts.setPitch(JarvisConfig.VOICE_PITCH)
            tts.setSpeechRate(JarvisConfig.VOICE_SPEED)
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) {}
                override fun onDone(id: String?) { startStandby() }
                override fun onError(id: String?) { startStandby() }
            })
            speak("Wake word protocol initialized. At your service, sir.")
        }
    }

    private fun setupRecognizer() {
        recognizer = SpeechRecognizer.createSpeechRecognizer(this)
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val spoken = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.lowercase(Locale.ROOT) ?: ""
                if (spoken.isNotBlank()) {
                    lastTranscript.value = "\"$spoken\""
                    statusLog.value = "PROCESSING..."
                    scope.launch {
                        val decision = aiBrain.processCommand(spoken)
                        val reply = SkillRegistry.executeSkill(this@JarvisVoiceService, decision)
                        speak(reply)
                    }
                } else { startStandby() }
            }
            override fun onError(error: Int) { startStandby() }
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    private fun speak(text: String) {
        statusLog.value = "SPEAKING: $text"
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "JARVIS_UTTERANCE")
    }

    override fun onDestroy() {
        try { porcupine?.stop(); porcupine?.delete() } catch (e: Exception) {}
        recognizer.destroy()
        tts.shutdown()
        beep.release()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
