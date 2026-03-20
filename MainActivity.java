package com.paybox.soundbox;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.*;

public class MainActivity extends AppCompatActivity {

    private static final int PERM_REQUEST = 101;
    private SharedPreferences prefs;
    private LinearLayout txnListView;
    private TextView tvLastAmount, tvLastTime, tvTotal, tvCount, tvAvg, tvStatus;
    private TextView tvMerchantName, tvUpiId;
    private SeekBar volumeBar;
    private TextView tvVolume;
    private Switch swService;
    private EditText etMerchantName, etUpiId;
    private Spinner spLanguage;
    private View setupLayout, mainLayout;
    private boolean isSetupDone = false;

    // Broadcast receiver to update UI when payment arrives
    private final BroadcastReceiver paymentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if ("com.paybox.PAYMENT_RECEIVED".equals(intent.getAction())) {
                double amount = intent.getDoubleExtra("amount", 0);
                String sender = intent.getStringExtra("sender");
                String ref = intent.getStringExtra("ref");
                runOnUiThread(() -> onPaymentReceived(amount, sender, ref));
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("paybox", MODE_PRIVATE);
        initViews();

        isSetupDone = prefs.getBoolean("setup_done", false);
        if (isSetupDone) {
            showMainScreen();
        } else {
            showSetupScreen();
        }

        requestPermissions();
    }

    private void initViews() {
        setupLayout = findViewById(R.id.setupLayout);
        mainLayout  = findViewById(R.id.mainLayout);

        // Setup views
        etMerchantName = findViewById(R.id.etMerchantName);
        etUpiId        = findViewById(R.id.etUpiId);
        spLanguage     = findViewById(R.id.spLanguage);

        // Main views
        tvMerchantName = findViewById(R.id.tvMerchantName);
        tvUpiId        = findViewById(R.id.tvUpiId);
        tvLastAmount   = findViewById(R.id.tvLastAmount);
        tvLastTime     = findViewById(R.id.tvLastTime);
        tvTotal        = findViewById(R.id.tvTotal);
        tvCount        = findViewById(R.id.tvCount);
        tvAvg          = findViewById(R.id.tvAvg);
        tvStatus       = findViewById(R.id.tvStatus);
        txnListView    = findViewById(R.id.txnList);
        volumeBar      = findViewById(R.id.volumeBar);
        tvVolume       = findViewById(R.id.tvVolume);
        swService      = findViewById(R.id.swService);

        // Language spinner
        String[] langs = {"Hindi (हिंदी)", "English", "Marathi (मराठी)",
                          "Gujarati (ગુજરાતી)", "Tamil (தமிழ்)", "Telugu (తెలుగు)", "Bengali (বাংলা)"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, langs);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spLanguage.setAdapter(adapter);

        // Volume bar
        int vol = prefs.getInt("volume", 85);
        volumeBar.setProgress(vol);
        tvVolume.setText(vol + "%");
        volumeBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean u) {
                tvVolume.setText(p + "%");
                prefs.edit().putInt("volume", p).apply();
                PayBoxService.setVolume(p / 100f);
            }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        });

        // Service toggle
        swService.setChecked(prefs.getBoolean("service_on", true));
        swService.setOnCheckedChangeListener((b, checked) -> {
            prefs.edit().putBoolean("service_on", checked).apply();
            if (checked) startPayBoxService();
            else stopService(new Intent(this, PayBoxService.class));
            tvStatus.setText(checked ? "Listening for payments..." : "Service stopped");
        });

        // Start button
        findViewById(R.id.btnStart).setOnClickListener(v -> saveSetupAndStart());

        // Settings button
        View settingsBtn = findViewById(R.id.btnSettings);
        if (settingsBtn != null) settingsBtn.setOnClickListener(v -> showSetupScreen());

        // Test button
        View testBtn = findViewById(R.id.btnTest);
        if (testBtn != null) testBtn.setOnClickListener(v -> {
            String[] testAmts = {"100", "500", "1000", "250", "2000"};
            String amt = testAmts[new Random().nextInt(testAmts.length)];
            // Simulate a test SMS
            Intent i = new Intent("com.paybox.PAYMENT_RECEIVED");
            i.putExtra("amount", Double.parseDouble(amt));
            i.putExtra("sender", "Test Payment");
            i.putExtra("ref", "TEST" + System.currentTimeMillis());
            sendBroadcast(i);
        });
    }

    private void saveSetupAndStart() {
        String name = etMerchantName.getText().toString().trim();
        String upi  = etUpiId.getText().toString().trim();

        if (name.isEmpty()) { etMerchantName.setError("Required"); return; }
        if (upi.isEmpty())  { etUpiId.setError("Required"); return; }

        String[] langCodes = {"hi", "en", "mr", "gu", "ta", "te", "bn"};
        String lang = langCodes[spLanguage.getSelectedItemPosition()];

        prefs.edit()
            .putString("merchant_name", name)
            .putString("upi_id", upi)
            .putString("language", lang)
            .putBoolean("setup_done", true)
            .apply();

        showMainScreen();
        startPayBoxService();
    }

    private void showSetupScreen() {
        setupLayout.setVisibility(View.VISIBLE);
        mainLayout.setVisibility(View.GONE);
        // Pre-fill saved values
        etMerchantName.setText(prefs.getString("merchant_name", ""));
        etUpiId.setText(prefs.getString("upi_id", ""));
        String[] langCodes = {"hi","en","mr","gu","ta","te","bn"};
        String saved = prefs.getString("language", "hi");
        for (int i = 0; i < langCodes.length; i++) {
            if (langCodes[i].equals(saved)) { spLanguage.setSelection(i); break; }
        }
    }

    private void showMainScreen() {
        setupLayout.setVisibility(View.GONE);
        mainLayout.setVisibility(View.VISIBLE);
        tvMerchantName.setText(prefs.getString("merchant_name", "My Shop"));
        tvUpiId.setText(prefs.getString("upi_id", "merchant@upi"));
        updateStats();
        renderTransactions();
        if (prefs.getBoolean("service_on", true)) {
            startPayBoxService();
            tvStatus.setText("Listening for payments...");
        }
    }

    private void startPayBoxService() {
        Intent intent = new Intent(this, PayBoxService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    private void onPaymentReceived(double amount, String sender, String ref) {
        // Format amount
        NumberFormat nf = NumberFormat.getInstance(new Locale("en", "IN"));
        String fmtAmt = "₹" + nf.format((long) amount);

        // Update hero display
        tvLastAmount.setText(fmtAmt);
        SimpleDateFormat sdf = new SimpleDateFormat("hh:mm a", Locale.getDefault());
        tvLastTime.setText("Received at " + sdf.format(new Date()));

        // Flash animation
        tvLastAmount.animate().scaleX(1.15f).scaleY(1.15f).setDuration(150)
            .withEndAction(() -> tvLastAmount.animate().scaleX(1f).scaleY(1f).setDuration(150).start())
            .start();

        // Save transaction
        saveTransaction(amount, sender, ref);
        updateStats();
        renderTransactions();
    }

    private void saveTransaction(double amount, String sender, String ref) {
        try {
            String json = prefs.getString("transactions", "[]");
            JSONArray arr = new JSONArray(json);
            JSONObject txn = new JSONObject();
            txn.put("amount", amount);
            txn.put("sender", sender != null ? sender : "Unknown");
            txn.put("ref", ref != null ? ref : "");
            txn.put("time", System.currentTimeMillis());
            // Insert at front
            JSONArray newArr = new JSONArray();
            newArr.put(txn);
            for (int i = 0; i < Math.min(arr.length(), 49); i++) newArr.put(arr.get(i));
            prefs.edit().putString("transactions", newArr.toString()).apply();
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void updateStats() {
        try {
            String json = prefs.getString("transactions", "[]");
            JSONArray arr = new JSONArray(json);
            double total = 0;
            // Only today's transactions
            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            long todayStart = cal.getTimeInMillis();
            int count = 0;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject t = arr.getJSONObject(i);
                if (t.getLong("time") >= todayStart) {
                    total += t.getDouble("amount");
                    count++;
                }
            }
            NumberFormat nf = NumberFormat.getInstance(new Locale("en","IN"));
            tvTotal.setText("₹" + nf.format((long) total));
            tvCount.setText(String.valueOf(count));
            tvAvg.setText(count > 0 ? "₹" + nf.format((long)(total/count)) : "₹0");
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void renderTransactions() {
        if (txnListView == null) return;
        txnListView.removeAllViews();
        try {
            String json = prefs.getString("transactions", "[]");
            JSONArray arr = new JSONArray(json);
            if (arr.length() == 0) {
                TextView empty = new TextView(this);
                empty.setText("No transactions yet. Waiting for payments...");
                empty.setTextColor(0xFF888888);
                empty.setPadding(0, 32, 0, 32);
                empty.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
                txnListView.addView(empty);
                return;
            }
            NumberFormat nf = NumberFormat.getInstance(new Locale("en","IN"));
            SimpleDateFormat sdf = new SimpleDateFormat("hh:mm a", Locale.getDefault());
            for (int i = 0; i < Math.min(arr.length(), 20); i++) {
                JSONObject t = arr.getJSONObject(i);
                double amt = t.getDouble("amount");
                String sender = t.getString("sender");
                long time = t.getLong("time");
                View row = getLayoutInflater().inflate(R.layout.item_transaction, txnListView, false);
                ((TextView) row.findViewById(R.id.tvTxnName)).setText(sender);
                ((TextView) row.findViewById(R.id.tvTxnTime)).setText(sdf.format(new Date(time)));
                ((TextView) row.findViewById(R.id.tvTxnAmt)).setText("+₹" + nf.format((long) amt));
                // Avatar initials
                TextView avatar = row.findViewById(R.id.tvAvatar);
                String initials = sender.length() >= 2
                    ? String.valueOf(sender.charAt(0)).toUpperCase()
                    : "?";
                avatar.setText(initials);
                int[] colors = {0xFF7C4DFF, 0xFFE74694, 0xFF00BCD4, 0xFFFF6B35, 0xFF4CAF50, 0xFF2196F3};
                avatar.getBackground().mutate();
                // Tint with a color based on index
                txnListView.addView(row);
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void requestPermissions() {
        List<String> perms = new ArrayList<>();
        String[] needed = {
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS
        };
        for (String p : needed) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED)
                perms.add(p);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED)
                perms.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        if (!perms.isEmpty()) {
            ActivityCompat.requestPermissions(this, perms.toArray(new String[0]), PERM_REQUEST);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter("com.paybox.PAYMENT_RECEIVED");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(paymentReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(paymentReceiver, filter);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        try { unregisterReceiver(paymentReceiver); } catch (Exception ignored) {}
    }
}
