package com.vorali.soundbox

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.RadioGroup
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.btnEnableNotifications).setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        val prefs = getSharedPreferences("VoraliPrefs", Context.MODE_PRIVATE)

        // Load saved checkbox states
        findViewById<CheckBox>(R.id.cbSlice).isChecked = prefs.getBoolean("app_slice", true)
        findViewById<CheckBox>(R.id.cbGPay).isChecked = prefs.getBoolean("app_gpay", true)
        findViewById<CheckBox>(R.id.cbPhonePe).isChecked = prefs.getBoolean("app_phonepe", true)
        findViewById<CheckBox>(R.id.cbPaytm).isChecked = prefs.getBoolean("app_paytm", true)
        findViewById<CheckBox>(R.id.cbBhim).isChecked = prefs.getBoolean("app_bhim", true)
        findViewById<CheckBox>(R.id.cbAirtel).isChecked = prefs.getBoolean("app_airtel", true)

        // Load saved custom text
        findViewById<EditText>(R.id.etGreeting).setText(prefs.getString("greeting", "দারুণ!"))
        findViewById<EditText>(R.id.etClosing).setText(prefs.getString("closing", "ধন্যবাদ।"))

        // Load saved language
        val rgLanguage = findViewById<RadioGroup>(R.id.rgLanguage)
        when (prefs.getString("language", "bn")) {
            "bn" -> rgLanguage.check(R.id.rbBengali)
            "hi" -> rgLanguage.check(R.id.rbHindi)
            "en" -> rgLanguage.check(R.id.rbEnglish)
        }

        // Handle Test Button Click
        findViewById<Button>(R.id.btnTest).setOnClickListener {
            saveAllPreferences() // Save right before testing so you hear the newest changes
            val intent = Intent(this, NotificationService::class.java)
            intent.action = "com.vorali.soundbox.TEST_AUDIO"
            startService(intent)
        }
    }

    override fun onPause() {
        super.onPause()
        saveAllPreferences() // Auto-save everything when you leave the app screen
    }

    private fun saveAllPreferences() {
        val editor = getSharedPreferences("VoraliPrefs", Context.MODE_PRIVATE).edit()
        
        editor.putBoolean("app_slice", findViewById<CheckBox>(R.id.cbSlice).isChecked)
        editor.putBoolean("app_gpay", findViewById<CheckBox>(R.id.cbGPay).isChecked)
        editor.putBoolean("app_phonepe", findViewById<CheckBox>(R.id.cbPhonePe).isChecked)
        editor.putBoolean("app_paytm", findViewById<CheckBox>(R.id.cbPaytm).isChecked)
        editor.putBoolean("app_bhim", findViewById<CheckBox>(R.id.cbBhim).isChecked)
        editor.putBoolean("app_airtel", findViewById<CheckBox>(R.id.cbAirtel).isChecked)

        editor.putString("greeting", findViewById<EditText>(R.id.etGreeting).text.toString())
        editor.putString("closing", findViewById<EditText>(R.id.etClosing).text.toString())

        val rgLanguage = findViewById<RadioGroup>(R.id.rgLanguage)
        when (rgLanguage.checkedRadioButtonId) {
            R.id.rbBengali -> editor.putString("language", "bn")
            R.id.rbHindi -> editor.putString("language", "hi")
            R.id.rbEnglish -> editor.putString("language", "en")
        }
        editor.apply()
    }
}
