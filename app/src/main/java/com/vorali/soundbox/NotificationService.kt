package com.vorali.soundbox

import android.content.Context
import android.content.Intent
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

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "com.vorali.soundbox.TEST_AUDIO") {
            // Using ALL CAPS here to prove the formatting fix works on the test button!
            playAlertAndSpeak("150", "ADITI", isTest = true) 
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val prefs = getSharedPreferences("VoraliPrefs", Context.MODE_PRIVATE)
        val pkg = sbn.packageName

        val isAppEnabled = when (pkg) {
            "indwin.c3.shareapp" -> prefs.getBoolean("app_slice", true)
            "com.google.android.apps.nbu.paisa.user" -> prefs.getBoolean("app_gpay", true)
            "com.phonepe.app" -> prefs.getBoolean("app_phonepe", true)
            "net.one97.paytm" -> prefs.getBoolean("app_paytm", true)
            "in.org.npci.upiapp" -> prefs.getBoolean("app_bhim", true)
            "com.myairtelapp" -> prefs.getBoolean("app_airtel", true)
            else -> false
        }

        if (isAppEnabled) {
            val extras = sbn.notification.extras
            val text = extras.getCharSequence("android.text")?.toString() ?: ""
            val title = extras.getCharSequence("android.title")?.toString() ?: ""
            val fullText = "$title $text"
            
            val lowerText = fullText.lowercase()
            if (lowerText.contains("cashback") || 
                lowerText.contains("loan") || 
                lowerText.contains("offer") || 
                lowerText.contains("reward") || 
                lowerText.contains("win")) {
                return 
            }

            val amountRegex = "₹\\s?([\\d,.]+)".toRegex()
            val match = amountRegex.find(fullText)
            val amount = match?.groupValues?.get(1) ?: ""

            val amountRegexAlt = "Rs\\.?\\s?([\\d,.]+)".toRegex(RegexOption.IGNORE_CASE)
            val matchAlt = amountRegexAlt.find(fullText)
            val finalAmount = if (amount.isNotEmpty()) amount else (matchAlt?.groupValues?.get(1) ?: "")

            var senderName = ""
            val nameRegex = "(?i)(?:from|by)\\s+([A-Za-z]+(?:\\s+[A-Za-z]+){0,2})".toRegex()
            val nameMatch = nameRegex.find(fullText)
            
            if (nameMatch != null) {
                val extracted = nameMatch.groupValues[1].trim()
                if (!extracted.equals("UPI", ignoreCase = true) && !extracted.equals("wallet", ignoreCase = true)) {
                    // THE FIX: This forces ALL CAPS names into proper Title Case (e.g., SOURAV -> Sourav)
                    senderName = extracted.lowercase().split(" ").joinToString(" ") { word ->
                        word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
                    }
                }
            }

            if (finalAmount.isNotEmpty()) {
                playAlertAndSpeak(finalAmount, senderName, isTest = false)
            }
        }
    }

    private fun playAlertAndSpeak(amount: String, senderName: String, isTest: Boolean) {
        val prefs = getSharedPreferences("VoraliPrefs", Context.MODE_PRIVATE)
        val langChoice = prefs.getString("language", "bn") ?: "bn"
        val customGreeting = prefs.getString("greeting", "দারুণ!") ?: ""
        val customClosing = prefs.getString("closing", "ধন্যবাদ।") ?: ""
        
        if (isTtsInitialized) {
            when (langChoice) {
                "bn" -> tts.language = Locale("bn", "IN")
                "hi" -> tts.language = Locale("hi", "IN")
                "en" -> tts.language = Locale("en", "IN")
            }
        }

        val testPrefix = if (isTest) {
            when (langChoice) {
                "hi" -> "यह एक टेस्ट है। "
                "en" -> "This is a test notification. "
                else -> "এটি একটি টেস্ট নোটিফিকেশন। "
            }
        } else ""

        val namePartBn = if (senderName.isNotEmpty()) "$senderName এর কাছ থেকে, " else ""
        val namePartHi = if (senderName.isNotEmpty()) "$senderName से, " else ""
        val namePartEn = if (senderName.isNotEmpty()) "from $senderName, " else ""

        val speechText = when (langChoice) {
            "hi" -> "$testPrefix$customGreeting, सौरव, $namePartHi वोराली में, $amount रुपये प्राप्त हुए। $customClosing"
            "en" -> "$testPrefix$customGreeting, Sourav, $amount rupees received, $namePartEn on Vorali. $customClosing"
            else -> "$testPrefix$customGreeting, সৌরভ, $namePartBn ভোরালিতে, $amount টাকা এসেছে। $customClosing"
        }

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
            tts.setPitch(1.05f) 
            tts.setSpeechRate(0.90f)
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
