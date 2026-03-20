package com.vorali.soundbox

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.speech.tts.TextToSpeech
import java.util.Locale

class NotificationService : NotificationListenerService(), TextToSpeech.OnInitListener {
    private lateinit var tts: TextToSpeech
    private var mediaPlayer: MediaPlayer? = null
    private var isTtsInitialized = false

    override fun onCreate() {
        super.onCreate()
        tts = TextToSpeech(this, this)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == "indwin.c3.shareapp") {
            val extras = sbn.notification.extras
            val text = extras.getCharSequence("android.text")?.toString() ?: ""
            
            val amountRegex = "₹\\s?([\\d,.]+)".toRegex()
            val match = amountRegex.find(text)
            val amount = match?.groupValues?.get(1) ?: ""

            if (amount.isNotEmpty()) {
                playAlertAndSpeak(amount)
            }
        }
    }

    private fun playAlertAndSpeak(amount: String) {
        val prefs = getSharedPreferences("VoraliPrefs", Context.MODE_PRIVATE)
        val langChoice = prefs.getString("language", "bn") ?: "bn"
        
        // Switch the voice engine language dynamically
        if (isTtsInitialized) {
            when (langChoice) {
                "bn" -> tts.language = Locale("bn", "IN")
                "hi" -> tts.language = Locale("hi", "IN")
                "en" -> tts.language = Locale("en", "IN")
            }
        }

        // Pick the correct personalized greeting
        val speechText = when (langChoice) {
            "hi" -> "बढ़िया! सौरव, वोराली में $amount रुपये प्राप्त हुए।"
            "en" -> "Great! Sourav, $amount rupees received on Vorali."
            else -> "দারুণ! সৌরভ, ভোরালিতে $amount টাকা এসেছে।"
        }

        // Force volume to 100%
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxVolume, 0)

        try {
            mediaPlayer = MediaPlayer.create(this, R.raw.bell)?.apply {
                val audioAttributes = AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .build()
                setAudioAttributes(audioAttributes)
                
                start()
                setOnCompletionListener {
                    it.release()
                    tts.speak(speechText, TextToSpeech.QUEUE_FLUSH, null, null)
                }
            }
        } catch (e: Exception) {
            tts.speak(speechText, TextToSpeech.QUEUE_FLUSH, null, null)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isTtsInitialized = true
            tts.setPitch(1.15f) // Lively pitch
            tts.setSpeechRate(0.95f) // Clear pacing
        }
    }

    override fun onDestroy() {
        if (this::tts.isInitialized) {
            tts.stop()
            tts.shutdown()
        }
        super.onDestroy()
    }
}
