package com.vorali.soundbox

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.RadioGroup
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences("VoraliPrefs", Context.MODE_PRIVATE)

        val btnPermissions = findViewById<Button>(R.id.btnPermissions)
        val volumeSlider = findViewById<SeekBar>(R.id.volumeSlider)
        val soundRadioGroup = findViewById<RadioGroup>(R.id.soundRadioGroup)

        // Open Notification Listener Settings when clicked
        btnPermissions.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        // Load saved settings
        volumeSlider.progress = prefs.getInt("volume", 100)
        if (prefs.getString("sound", "bell") == "whistle") {
            soundRadioGroup.check(R.id.radioWhistle)
        } else {
            soundRadioGroup.check(R.id.radioBell)
        }

        // Save volume when changed
        volumeSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                prefs.edit().putInt("volume", progress).apply()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Save sound choice when changed
        soundRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            val soundChoice = if (checkedId == R.id.radioWhistle) "whistle" else "bell"
            prefs.edit().putString("sound", soundChoice).apply()
        }
    }
}