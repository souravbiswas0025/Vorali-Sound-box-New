package com.vorali.soundbox

import android.content.Context
import android.media.MediaPlayer
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.speech.tts.TextToSpeech
import java.util.Locale

class NotificationService : NotificationListenerService(), TextToSpeech.OnInitListener {
    private lateinit var tts: TextToSpeech
    private var mediaPlayer: MediaPlayer? = null

    override fun onCreate() {
        super.onCreate()
        tts = TextToSpeech(this, this)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        // Look specifically for the slice app notification
        if (sbn.packageName == "indwin.c3.shareapp") {
            val extras = sbn.notification.extras
            val text = extras.getCharSequence("android.text")?.toString() ?: ""
            
            // Look for the Rupee symbol and numbers
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
        val volume = prefs.getInt("volume", 100) / 100f // Convert 0-100 to 0.0-1.0
        val soundChoice = prefs.getString("sound", "bell")

        // Determine which sound file to play
        val soundResId = if (soundChoice == "whistle") R.raw.whistle else R.raw.bell

        try {
            mediaPlayer = MediaPlayer.create(this, soundResId)?.apply {
                setVolume(volume, volume)
                start()
                setOnCompletionListener {
                    // Release media player and trigger voice prompt
                    it.release()
                    val speechText = "You have received $amount rupees on your Vorali account. Thank you."
                    tts.speak(speechText, TextToSpeech.QUEUE_FLUSH, null, null)
                }
            }
        } catch (e: Exception) {
            // If the sound file is missing, just speak the text
            val speechText = "You have received $amount rupees on your Vorali account. Thank you."
            tts.speak(speechText, TextToSpeech.QUEUE_FLUSH, null, null)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale("en", "IN") // Indian English accent
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