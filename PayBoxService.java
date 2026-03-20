package com.paybox.soundbox;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.IBinder;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import java.util.HashMap;
import java.util.Locale;

public class PayBoxService extends Service {

    private static final String TAG = "PayBox-Service";
    private static final String CHANNEL_ID = "paybox_channel";
    private static final int NOTIF_ID = 1001;

    private TextToSpeech tts;
    private boolean ttsReady = false;
    private static float volume = 0.85f;

    private SharedPreferences prefs;

    // Language configs
    private static final HashMap<String, Locale> LANG_LOCALE = new HashMap<String, Locale>() {{
        put("hi", new Locale("hi", "IN"));
        put("en", new Locale("en", "IN"));
        put("mr", new Locale("mr", "IN"));
        put("gu", new Locale("gu", "IN"));
        put("ta", new Locale("ta", "IN"));
        put("te", new Locale("te", "IN"));
        put("bn", new Locale("bn", "IN"));
    }};

    public static void setVolume(float v) { volume = v; }

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences("paybox", MODE_PRIVATE);
        volume = prefs.getInt("volume", 85) / 100f;
        createNotificationChannel();
        startForeground(NOTIF_ID, buildNotification("Listening for payments..."));
        initTTS();
        Log.d(TAG, "PayBoxService started");
    }

    private void initTTS() {
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                String lang = prefs.getString("language", "hi");
                Locale locale = LANG_LOCALE.containsKey(lang) ? LANG_LOCALE.get(lang) : new Locale("hi","IN");
                int result = tts.setLanguage(locale);
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    // Fallback to English
                    tts.setLanguage(Locale.ENGLISH);
                    Log.w(TAG, "Language not supported, falling back to English");
                }
                tts.setSpeechRate(0.88f);
                tts.setPitch(1.05f);
                ttsReady = true;
                Log.d(TAG, "TTS initialized for lang: " + lang);
            } else {
                Log.e(TAG, "TTS init failed");
            }
        });
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.hasExtra("amount")) {
            double amount = intent.getDoubleExtra("amount", 0);
            String sender = intent.getStringExtra("sender");
            if (amount > 0) {
                announcePayment(amount, sender);
            }
        }
        return START_STICKY;
    }

    private void announcePayment(double amount, String sender) {
        long amtLong = (long) amount;
        String lang = prefs.getString("language", "hi");

        // Play beep first
        playBeep();

        // Build announcement text
        String text = buildAnnouncementText(amtLong, lang);

        // Update notification
        String notifText = "₹" + amtLong + " received" + (sender != null ? " from " + sender : "");
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(NOTIF_ID, buildNotification(notifText));

        // Speak after 700ms (after beep)
        new android.os.Handler().postDelayed(() -> speakText(text, lang), 700);

        Log.d(TAG, "Announcing: " + text);
    }

    private String buildAnnouncementText(long amount, String lang) {
        switch (lang) {
            case "hi":
                return buildHindi(amount);
            case "mr":
                return buildMarathi(amount);
            case "gu":
                return buildGujarati(amount);
            case "ta":
                return amount + " rubai kidaichathu. Nandri!";
            case "te":
                return amount + " rupaayalu vacchaayi. Dhanyavaadaalu!";
            case "bn":
                return amount + " taka paoa gaeche. Dhanyabaad!";
            default: // en
                return buildEnglish(amount);
        }
    }

    private String buildHindi(long amount) {
        if (amount >= 100000) {
            long lakh = amount / 100000;
            long rem = amount % 100000;
            if (rem == 0) return lakh + " lakh rupaye prapt hue";
            return lakh + " lakh " + rem + " rupaye prapt hue";
        } else if (amount >= 1000) {
            long hazar = amount / 1000;
            long rem = amount % 1000;
            if (rem == 0) return hazar + " hazaar rupaye prapt hue";
            return hazar + " hazaar " + rem + " rupaye prapt hue";
        }
        return amount + " rupaye prapt hue";
    }

    private String buildEnglish(long amount) {
        if (amount >= 100000) {
            double lakh = amount / 100000.0;
            String fmt = lakh == Math.floor(lakh) ? String.valueOf((long)lakh) : String.format("%.1f", lakh);
            return "Rupees " + fmt + " lakh received. Thank you!";
        } else if (amount >= 1000) {
            double k = amount / 1000.0;
            String fmt = k == Math.floor(k) ? String.valueOf((long)k) : String.format("%.1f", k);
            return "Rupees " + fmt + " thousand received. Thank you!";
        }
        return "Rupees " + amount + " received. Thank you!";
    }

    private String buildMarathi(long amount) {
        if (amount >= 1000) {
            long hazar = amount / 1000;
            long rem = amount % 1000;
            if (rem == 0) return hazar + " hazaar rupaye milale";
            return hazar + " hazaar " + rem + " rupaye milale";
        }
        return amount + " rupaye milale";
    }

    private String buildGujarati(long amount) {
        if (amount >= 1000) {
            long hazar = amount / 1000;
            long rem = amount % 1000;
            if (rem == 0) return hazar + " hazaar rupiya mali gaya";
            return hazar + " hazaar " + rem + " rupiya mali gaya";
        }
        return amount + " rupiya mali gaya";
    }

    private void speakText(String text, String lang) {
        if (!ttsReady || tts == null) {
            Log.w(TAG, "TTS not ready, retrying...");
            new android.os.Handler().postDelayed(() -> speakText(text, lang), 500);
            return;
        }

        // Update language in case it changed
        Locale locale = LANG_LOCALE.containsKey(lang) ? LANG_LOCALE.get(lang) : new Locale("hi","IN");
        tts.setLanguage(locale);

        // Set volume
        AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
        if (am != null) {
            int maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            int targetVol = (int)(maxVol * volume);
            am.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, 0);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "paybox_" + System.currentTimeMillis());
        } else {
            HashMap<String, String> params = new HashMap<>();
            params.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "paybox");
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, params);
        }
    }

    private void playBeep() {
        try {
            // Use ToneGenerator for the chime
            int vol = (int)(ToneGenerator.MAX_VOLUME * volume);
            ToneGenerator tg = new ToneGenerator(AudioManager.STREAM_MUSIC, vol);
            // Play ascending tones: DO-MI-SOL
            tg.startTone(ToneGenerator.TONE_PROP_BEEP, 120);
            new android.os.Handler().postDelayed(() -> {
                tg.startTone(ToneGenerator.TONE_PROP_BEEP2, 120);
            }, 150);
            new android.os.Handler().postDelayed(() -> {
                tg.startTone(ToneGenerator.TONE_CDMA_HIGH_PBX_SLS, 280);
                new android.os.Handler().postDelayed(tg::release, 500);
            }, 300);
        } catch (Exception e) {
            Log.e(TAG, "Beep error: " + e.getMessage());
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "PayBox Service",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("PayBox is listening for UPI payment SMS");
            channel.setShowBadge(false);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification(String text) {
        Intent openApp = new Intent(this, MainActivity.class);
        openApp.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, 0, openApp,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("PayBox Active")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pi)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .build();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (tts != null) { tts.stop(); tts.shutdown(); }
        Log.d(TAG, "PayBoxService stopped");
    }
}
