package com.vorali.soundbox

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.RadioGroup
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val btnEnable = findViewById<Button>(R.id.btnEnableNotifications)
        btnEnable.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        val rgLanguage = findViewById<RadioGroup>(R.id.rgLanguage)
        val prefs = getSharedPreferences("VoraliPrefs", Context.MODE_PRIVATE)
        
        // Set the radio button to whatever you saved last (defaults to Bengali)
        when (prefs.getString("language", "bn")) {
            "bn" -> rgLanguage.check(R.id.rbBengali)
            "hi" -> rgLanguage.check(R.id.rbHindi)
            "en" -> rgLanguage.check(R.id.rbEnglish)
        }

        // Save your choice the moment you tap a new language
        rgLanguage.setOnCheckedChangeListener { _, checkedId ->
            val editor = prefs.edit()
            when (checkedId) {
                R.id.rbBengali -> editor.putString("language", "bn")
                R.id.rbHindi -> editor.putString("language", "hi")
                R.id.rbEnglish -> editor.putString("language", "en")
            }
            editor.apply()
        }
    }
}
