# hc4tw - Health Connect bridge for TrendWeight

A lightweight, standalone Android mobile app that automatically synchronizes weight and body fat measurements from **Google Health Connect** directly to your **TrendWeight** account.

This mobile app is **100% independent** of the Python bridge daemon and Cloudflare Worker solutions. It runs directly on your Android device with zero server infrastructure required.

---

## Features

- ⚖️ **Automatic Sync**: Reads weight and body fat records from Health Connect and synchronizes them with TrendWeight.
- 🕒 **Background Periodic Sync**: Built with Android WorkManager to reliably sync new weigh-ins every hour in the background with minimal battery usage.
- 📱 **Simple Configuration**: Requires only your TrendWeight API key and one-click Health Connect read permission.
- 📋 **Integrated Diagnostic Logs**: In-app log viewer with a one-click **"Copy Logs to Clipboard"** and **"Share Logs"** button so you can easily copy and paste logs if you run into issues.
- 🔒 **Privacy First**: Direct device-to-TrendWeight HTTPS communication. Your health data is never routed through intermediary servers, and API keys are automatically masked in exported logs.

---

## Requirements

- **Android Device or Emulator**: Android 8.0+ (API 26+)
  - Android 14+ (API 34): Health Connect is built directly into Android system settings.
  - Android 8 to 13: Requires the [Google Health Connect](https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata) app from Google Play Store.
- **TrendWeight Account**: Obtain your API key from [trendweight.com/settings](https://trendweight.com/settings).

---

## Quick Start Guide

### 1. Grant Health Connect Permissions
1. Open the **TrendWeight Bridge** app.
2. Under **Health Connect Access**, tap **Grant Health Connect Permissions**.
3. Toggle ON access for **Weight** and **Body Fat**.

### 2. Configure TrendWeight API Key
1. Retrieve your API key from [trendweight.com/settings](https://trendweight.com/settings).
2. Enter the key into the **TrendWeight API Key** field in the app.
3. Tap **Save & Test**. The app will test the connection to TrendWeight and save your key.

### 3. Sync Your Data
- **Manual Sync**: Tap **Sync Now** to sync recent weigh-ins immediately.
- **30-Day Historical Sync**: Tap **Force Full Sync (Last 30 Days)** to backfill previous data.
- **Background Sync**: Toggle **Background Sync** ON to automatically sync new weigh-ins every hour.

---

## Troubleshooting & Copying Diagnostic Logs

If you encounter any issues syncing data, the app includes a dedicated diagnostics system:

1. Scroll to the **Diagnostic Logs** card at the bottom of the screen.
2. Tap **Copy Logs to Clipboard** (or the Share icon).
3. The app generates a formatted diagnostic report that includes:
   - Device model, manufacturer, and Android version
   - Health Connect SDK status and permission states
   - TrendWeight connection status (with your API key securely masked)
   - Detailed chronological logs with timestamps, HTTP status codes, and error messages
4. Paste the copied report into an email, GitHub issue, or support ticket.

---

## Building from Source

### Prerequisites
- JDK 17
- Android Studio Iguana+ (or Android SDK Command-line Tools)

### Build Debug APK
```bash
git clone https://github.com/your-username/trendweight-google-health-android.git
cd trendweight-google-health-android
./gradlew assembleDebug
```

The compiled APK will be located at:
```
app/build/outputs/apk/debug/app-debug.apk
```

### Install onto Connected Device / Emulator via ADB
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.trendweight.healthconnect.bridge/.MainActivity
```

### Run Unit Tests
```bash
./gradlew testDebugUnitTest
```

---

## Technical Architecture

| Component | Technology | Description |
| :--- | :--- | :--- |
| **UI** | Jetpack Compose + Material 3 | Modern declarative reactive UI with dark/light theme support. |
| **Health API** | `androidx.health.connect:connect-client` | Official Android Health Connect Jetpack library. |
| **Background Jobs**| Android WorkManager | Battery-friendly periodic background worker (`SyncWorker`). |
| **HTTP Client** | Square OkHttp 4 | High-performance HTTP client for TrendWeight's REST API. |
| **Diagnostics** | Custom `AppLogger` | In-memory circular buffer + persistent file logging + formatted clipboard export. |

---

## License

MIT License. See [LICENSE](LICENSE) for details.
