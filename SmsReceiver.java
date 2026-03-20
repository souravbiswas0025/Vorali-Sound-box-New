package com.paybox.soundbox;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.telephony.SmsMessage;
import android.util.Log;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SmsReceiver extends BroadcastReceiver {

    private static final String TAG = "PayBox-SMS";

    // Patterns to detect credit/payment messages from Indian banks and UPI apps
    // Covers: GPay, Paytm, PhonePe, BHIM, banks (SBI, HDFC, ICICI, Axis, Kotak, PNB, etc.)
    private static final Pattern[] AMOUNT_PATTERNS = {
        // "Rs.500 credited" / "INR 500 credited" / "Rs 500 cr"
        Pattern.compile("(?:Rs\\.?|INR|₹)\\s*([\\d,]+(?:\\.\\d{1,2})?)\\s*(?:credited|cr\\b)", Pattern.CASE_INSENSITIVE),
        // "credited Rs.500" / "credited with Rs 500"
        Pattern.compile("credited\\s+(?:with\\s+)?(?:Rs\\.?|INR|₹)\\s*([\\d,]+(?:\\.\\d{1,2})?)", Pattern.CASE_INSENSITIVE),
        // "received Rs.500" 
        Pattern.compile("received\\s+(?:Rs\\.?|INR|₹)\\s*([\\d,]+(?:\\.\\d{1,2})?)", Pattern.CASE_INSENSITIVE),
        // "payment of Rs.500 received"
        Pattern.compile("payment\\s+of\\s+(?:Rs\\.?|INR|₹)\\s*([\\d,]+(?:\\.\\d{1,2})?)", Pattern.CASE_INSENSITIVE),
        // "deposited Rs.500"
        Pattern.compile("deposited\\s+(?:Rs\\.?|INR|₹)\\s*([\\d,]+(?:\\.\\d{1,2})?)", Pattern.CASE_INSENSITIVE),
        // "UPI:500.00" style
        Pattern.compile("UPI[:\\s]+(?:Rs\\.?|INR|₹)?\\s*([\\d,]+(?:\\.\\d{1,2})?)", Pattern.CASE_INSENSITIVE),
        // "Amt:Rs.500" style (some banks)
        Pattern.compile("Amt[:\\s]+(?:Rs\\.?|INR|₹)?\\s*([\\d,]+(?:\\.\\d{1,2})?)", Pattern.CASE_INSENSITIVE),
        // Generic: "500.00 has been credited"
        Pattern.compile("([\\d,]+(?:\\.\\d{1,2})?)\\s+(?:has been|is)\\s+credited", Pattern.CASE_INSENSITIVE),
    };

    // Patterns to detect DEBIT messages (we want to IGNORE these)
    private static final Pattern[] DEBIT_PATTERNS = {
        Pattern.compile("debited|debit|withdrawn|paid|payment\\s+of|sent|transferred\\s+to|spent", Pattern.CASE_INSENSITIVE),
    };

    // Patterns to extract sender name from UPI SMS
    private static final Pattern SENDER_PATTERN = Pattern.compile(
        "(?:from|by|sender[:\\s]+)([A-Za-z\\s]{3,30}?)(?:\\s+(?:via|to|at|on|ref)|\\.|$)",
        Pattern.CASE_INSENSITIVE
    );

    // Reference number pattern
    private static final Pattern REF_PATTERN = Pattern.compile(
        "(?:Ref\\.?\\s*(?:No\\.?)?|UPI Ref|Txn ID|Transaction ID)[:\\s]*([A-Z0-9]{6,20})",
        Pattern.CASE_INSENSITIVE
    );

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!"android.provider.Telephony.SMS_RECEIVED".equals(intent.getAction())) return;

        Bundle bundle = intent.getExtras();
        if (bundle == null) return;

        Object[] pdus = (Object[]) bundle.get("pdus");
        if (pdus == null) return;

        String format = bundle.getString("format");

        StringBuilder fullMessage = new StringBuilder();
        String senderNum = "";

        for (Object pdu : pdus) {
            SmsMessage sms = SmsMessage.createFromPdu((byte[]) pdu, format);
            if (sms != null) {
                fullMessage.append(sms.getMessageBody());
                if (senderNum.isEmpty()) senderNum = sms.getDisplayOriginatingAddress();
            }
        }

        String message = fullMessage.toString();
        Log.d(TAG, "SMS from " + senderNum + ": " + message);

        // Only process messages from bank/UPI senders (usually alphanumeric like HDFCBK, SBIINB, PYTM, GPAY)
        if (!isPaymentSender(senderNum, message)) {
            Log.d(TAG, "Ignoring non-payment SMS from: " + senderNum);
            return;
        }

        // Skip debit messages
        if (isDebitMessage(message)) {
            Log.d(TAG, "Skipping debit message");
            return;
        }

        // Try to extract amount
        double amount = extractAmount(message);
        if (amount <= 0) {
            Log.d(TAG, "No payment amount found in SMS");
            return;
        }

        // Extract sender name and ref
        String sender = extractSender(message, senderNum);
        String ref = extractRef(message);

        Log.d(TAG, "Payment detected: ₹" + amount + " from " + sender);

        // Broadcast to service and activity
        Intent payIntent = new Intent("com.paybox.PAYMENT_RECEIVED");
        payIntent.putExtra("amount", amount);
        payIntent.putExtra("sender", sender);
        payIntent.putExtra("ref", ref);
        payIntent.setPackage(context.getPackageName());
        context.sendBroadcast(payIntent);

        // Also trigger sound via service
        Intent serviceIntent = new Intent(context, PayBoxService.class);
        serviceIntent.putExtra("amount", amount);
        serviceIntent.putExtra("sender", sender);
        context.startService(serviceIntent);
    }

    private boolean isPaymentSender(String sender, String message) {
        if (sender == null) return false;
        sender = sender.toUpperCase();

        // Bank SMS IDs (alphanumeric senders)
        String[] bankSenders = {
            "HDFCBK", "HDFC", "SBIINB", "SBI", "ICICIB", "ICICI",
            "AXISBK", "AXIS", "KOTAKB", "KOTAK", "PNBSMS", "PNB",
            "BOIIND", "CANBNK", "CBSSBI", "UNIONB", "INDBNK",
            "PYTM", "PAYTM", "GPAY", "PHONEPE", "BHIMUPI",
            "IDFCFB", "YESBNK", "RBLBNK", "INDUSB", "FEDERL",
            "IOBILE", "CENTBK", "SYNDBK", "ANDBKN", "CORPBK",
            "UCOBNK", "VJBBNK", "DNSBNK", "SARASB", "TJSB"
        };

        for (String b : bankSenders) {
            if (sender.contains(b)) return true;
        }

        // Also check message content for UPI/bank keywords
        String msgUpper = message.toUpperCase();
        return msgUpper.contains("UPI") || msgUpper.contains("CREDITED") ||
               msgUpper.contains("NEFT") || msgUpper.contains("IMPS") ||
               msgUpper.contains("ACCOUNT") || msgUpper.contains("A/C");
    }

    private boolean isDebitMessage(String message) {
        for (Pattern p : DEBIT_PATTERNS) {
            if (p.matcher(message).find()) {
                // But allow "payment received" even if "payment" matches debit pattern
                if (message.toLowerCase().contains("received") ||
                    message.toLowerCase().contains("credited")) {
                    return false;
                }
                return true;
            }
        }
        return false;
    }

    private double extractAmount(String message) {
        for (Pattern p : AMOUNT_PATTERNS) {
            Matcher m = p.matcher(message);
            if (m.find()) {
                try {
                    String amtStr = m.group(1).replace(",", "");
                    double amt = Double.parseDouble(amtStr);
                    if (amt > 0 && amt < 10000000) return amt; // sanity check
                } catch (Exception ignored) {}
            }
        }
        return 0;
    }

    private String extractSender(String message, String smsOrigin) {
        Matcher m = SENDER_PATTERN.matcher(message);
        if (m.find()) {
            String name = m.group(1).trim();
            if (name.length() >= 3) return capitalize(name);
        }
        // Fallback: use UPI app name or bank name
        String msgUpper = message.toUpperCase();
        if (msgUpper.contains("GPAY") || msgUpper.contains("GOOGLE PAY")) return "Google Pay";
        if (msgUpper.contains("PAYTM") || msgUpper.contains("PYTM")) return "Paytm";
        if (msgUpper.contains("PHONEPE")) return "PhonePe";
        if (msgUpper.contains("BHIM")) return "BHIM UPI";
        if (msgUpper.contains("NEFT")) return "NEFT Transfer";
        if (msgUpper.contains("IMPS")) return "IMPS Transfer";
        return "UPI Payment";
    }

    private String extractRef(String message) {
        Matcher m = REF_PATTERN.matcher(message);
        if (m.find()) return m.group(1);
        return "UPI" + System.currentTimeMillis() % 100000;
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        String[] words = s.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (!w.isEmpty()) {
                sb.append(Character.toUpperCase(w.charAt(0)));
                if (w.length() > 1) sb.append(w.substring(1).toLowerCase());
                sb.append(" ");
            }
        }
        return sb.toString().trim();
    }
}
