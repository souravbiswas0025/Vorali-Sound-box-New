# PayBox – UPI SoundBox for Android

Announces UPI payments in voice — works with GPay, Paytm, PhonePe, BHIM, and all Indian banks.

---

## How it works

When someone pays you via any UPI app, your bank sends an SMS like:
> "Rs.500 credited to your a/c via UPI Ref: 123456"

PayBox reads this SMS automatically and:
1. Plays a chime sound 🔔
2. Announces the amount in your language (Hindi, English, Marathi, Gujarati, Tamil, Telugu, Bengali)
3. Shows the transaction in the app

No internet needed. No payment gateway. Works 100% offline.

---

## Build & Install (Step by Step)

### Method A — Android Studio (Recommended, Free)

1. Download and install **Android Studio**
   https://developer.android.com/studio

2. Open Android Studio → **Open an Existing Project** → select the `PayBoxSoundBox` folder

3. Wait for Gradle sync to finish (first time takes 2–3 minutes)

4. Connect your Android phone via USB, OR go to **Build → Build Bundle(s)/APK(s) → Build APK(s)**

5. The APK will be at:
   `app/build/outputs/apk/debug/app-debug.apk`

6. Transfer the APK to your phone and install it
   (You may need to enable "Install from unknown sources" in Settings → Security)

### Method B — GitHub Actions (No installation needed)

1. Create a free GitHub account at github.com
2. Create a new repository and upload all these files
3. Create `.github/workflows/build.yml` with:

```yaml
name: Build APK
on: [push]
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-java@v3
        with:
          java-version: '17'
          distribution: 'temurin'
      - name: Build APK
        run: ./gradlew assembleDebug
      - uses: actions/upload-artifact@v3
        with:
          name: PayBox-APK
          path: app/build/outputs/apk/debug/app-debug.apk
```

4. Push your code — GitHub will build the APK automatically
5. Download the APK from the Actions tab

---

## Permissions Required

| Permission | Why |
|---|---|
| READ_SMS / RECEIVE_SMS | To detect payment messages from your bank |
| FOREGROUND_SERVICE | To keep running in background |
| RECEIVE_BOOT_COMPLETED | To auto-start when phone reboots |
| POST_NOTIFICATIONS | To show "PayBox Active" notification |
| VIBRATE | Optional vibration on payment |

---

## SMS Patterns Supported

PayBox detects payments from all major Indian banks and UPI apps:

- **Banks**: SBI, HDFC, ICICI, Axis, Kotak, PNB, Bank of India, Canara, Union, IndusInd, Yes Bank, Federal, IDFC, RBL, and more
- **UPI Apps**: Google Pay, Paytm, PhonePe, BHIM, and any app that triggers bank SMS

### Example SMS messages it will detect:
- `Rs.500 credited to your a/c XXXXXX via UPI`
- `INR 1000 credited to your account by GPAY`
- `Your a/c XXXX1234 is credited with Rs.250 on 15-03-26`
- `Received Rs.2000 from Ravi Kumar via UPI Ref: 412345678`
- `Amount Rs.750 deposited in your account`

---

## Languages Supported

| Code | Language | Announcement Example |
|---|---|---|
| hi | Hindi | 500 rupaye prapt hue |
| en | English | Rupees 500 received. Thank you! |
| mr | Marathi | 500 rupaye milale |
| gu | Gujarati | 500 rupiya mali gaya |
| ta | Tamil | 500 rubai kidaichathu |
| te | Telugu | 500 rupaayalu vacchaayi |
| bn | Bengali | 500 taka paoa gaeche |

---

## Project Structure

```
PayBoxSoundBox/
├── app/
│   ├── build.gradle
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/paybox/soundbox/
│       │   ├── MainActivity.java      ← Main UI
│       │   ├── SmsReceiver.java       ← Reads bank SMS, parses amount
│       │   ├── PayBoxService.java     ← Background service, plays sound & TTS
│       │   └── BootReceiver.java      ← Auto-start on reboot
│       └── res/
│           ├── layout/
│           │   ├── activity_main.xml
│           │   └── item_transaction.xml
│           ├── drawable/              ← UI shapes & backgrounds
│           └── values/
│               ├── strings.xml
│               ├── colors.xml
│               └── styles.xml
├── build.gradle
├── settings.gradle
└── gradle.properties
```

---

## Troubleshooting

**App not detecting payments?**
- Make sure you granted SMS permissions when the app asked
- Go to Settings → Apps → PayBox → Permissions → SMS → Allow
- Some phones (Xiaomi, Realme, Oppo, Vivo) block SMS for non-default apps — set PayBox as "trusted app" or whitelist it in Security settings

**No sound / voice?**
- Tap "Test Sound" in the app to check volume
- Make sure media volume is turned up
- Hindi/regional voices require the Google TTS engine — install it from Play Store if missing

**App stops in background?**
- Go to Settings → Battery → PayBox → Disable battery optimization
- On Xiaomi: Settings → Apps → Manage Apps → PayBox → Autostart → Enable

---

Made with ❤️ — Open source, no ads, no tracking.
