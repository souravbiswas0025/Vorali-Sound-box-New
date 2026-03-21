package com.vorali.soundbox

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.Switch
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private lateinit var switchNotif: Switch
    private lateinit var btnBattery: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        switchNotif = findViewById(R.id.switchNotification)
        btnBattery = findViewById(R.id.btnBattery)

        // Notification Slider Click
        switchNotif.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        // Battery Button Click
        btnBattery.setOnClickListener {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            intent.data = Uri.parse("package:$packageName")
            startActivity(intent)
        }

        val prefs = getSharedPreferences("VoraliPrefs", Context.MODE_PRIVATE)

        findViewById<CheckBox>(R.id.cbSlice).isChecked = prefs.getBoolean("app_slice", true)
        findViewById<CheckBox>(R.id.cbGPay).isChecked = prefs.getBoolean("app_gpay", true)
        findViewById<CheckBox>(R.id.cbPhonePe).isChecked = prefs.getBoolean("app_phonepe", true)
        findViewById<CheckBox>(R.id.cbPaytm).isChecked = prefs.getBoolean("app_paytm", true)
        findViewById<CheckBox>(R.id.cbBhim).isChecked = prefs.getBoolean("app_bhim", true)
        findViewById<CheckBox>(R.id.cbAirtel).isChecked = prefs.getBoolean("app_airtel", true)

        findViewById<EditText>(R.id.etGreeting).setText(prefs.getString("greeting", "দারুণ!"))
        findViewById<EditText>(R.id.etClosing).setText(prefs.getString("closing", "ধন্যবাদ।"))

        val rgLanguage = findViewById<RadioGroup>(R.id.rgLanguage)
        when (prefs.getString("language", "bn")) {
            "bn" -> rgLanguage.check(R.id.rbBengali)
            "hi" -> rgLanguage.check(R.id.rbHindi)
            "en" -> rgLanguage.check(R.id.rbEnglish)
        }

        findViewById<Button>(R.id.btnTest).setOnClickListener {
            saveAllPreferences()
            val intent = Intent(this, NotificationService::class.java)
            intent.action = "com.vorali.soundbox.TEST_AUDIO"
            startService(intent)
        }
    }

    // This runs every time you open the app or return from the settings screen
    override fun onResume() {
        super.onResume()
        checkSystemPermissions()
    }

    private fun checkSystemPermissions() {
        // 1. Check Notification Access
        val cn = ComponentName(this, NotificationService::class.java)
        val enabledListeners = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        val isNotifEnabled = enabledListeners?.contains(cn.flattenToString()) == true
        
        // Update slider visually
        switchNotif.isChecked = isNotifEnabled
        switchNotif.text = if (isNotifEnabled) "Notification Access: ENABLED" else "Notification Access: DISABLED"

        // 2. Check Battery Optimization
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        val isIgnoringBattery = pm.isIgnoringBatteryOptimizations(packageName)
        
        // Update battery button visually
        if (isIgnoringBattery) {
            btnBattery.text = "Battery Optimization: BYPASSED"
            btnBattery.setBackgroundColor(Color.parseColor("#4CAF50")) // Turn Green
            btnBattery.isEnabled = false // Lock the button since it's already done
        } else {
            btnBattery.text = "Bypass Battery Optimization"
        }
    }

    override fun onPause() {
        super.onPause()
        saveAllPreferences()
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
