# KOTranslate — Offline Translation Companion App

**KOTranslate** is an Android companion app that provides offline machine translation for KOReader using Google ML Kit. It runs a local HTTP server on your Android device, allowing KOReader (on the same device or external e-readers like Kobo/Kindle) to translate text without an internet connection.

---

## Features

- **Offline Translation:** Uses Google ML Kit's on-device translation models
- **56 Languages Supported:** Including English, Spanish, French, German, Chinese, Japanese, Arabic, and more
- **Local HTTP Server:** Exposes REST API via NanoHTTPD on port 8787
- **Zero-Config for Android KOReader:** Automatically works with `http://127.0.0.1:8787`
- **WiFi Support:** External e-readers (Kobo, Kindle) can connect via LAN IP
- **Language Model Management:** Download and delete models directly from the app
- **Foreground Service:** Keeps server running in the background
- **Material Design 3 UI:** Modern Jetpack Compose interface

---

## Requirements

- **Android 7.0 (API 24) or higher**
- **~30 MB per language model** (storage space)
- **WiFi connection** (for external device support)

---

## Building the APK with Docker

No need to install Android SDK, JDK, or Gradle on your machine. Everything runs inside Docker.

### Prerequisites

- **Docker Desktop for Windows** must be installed and running
- No other dependencies required

### Build Commands (PowerShell)

```powershell
# Navigate to the project directory
cd "PATH_TO_KOTRANSLATE"

# Build the Docker image (first time only, ~5-10 minutes)
docker build -t kotranslate-builder .

# Build the debug APK
docker run --rm -v "${PWD}:/project" kotranslate-builder

# The APK will be at:
# .\app\build\outputs\apk\debug\app-debug.apk
```

### Build Release APK (Optional)

```powershell
docker run --rm -v "${PWD}:/project" kotranslate-builder gradle assembleRelease --no-daemon

# Output: .\app\build\outputs\apk\release\app-release-unsigned.apk
```

---

## Installation

1. **Transfer the APK** to your Android device
2. **Enable "Install from Unknown Sources"** in Android settings
3. **Install the APK** by tapping on it
4. **Grant notification permission** when prompted (Android 13+)

---

## Usage

### Starting the Server

1. **Open KOTranslate** on your Android device
2. **Tap "Start Server"**
3. **Note the IP address** displayed (e.g., `http://192.168.1.42:8787`)
4. The app will show:
   - Server status (Running/Stopped)
   - IP address for external devices
   - `localhost:8787` for same-device usage

### Downloading Language Models

1. **Scroll down** to the "Available to Download" section
2. **Tap the download icon** next to any language
3. **Wait for download** to complete (~30 MB per language)
4. Downloaded languages appear in the "Downloaded Languages" section

**Note:** English is required as a pivot language for some translation pairs. Download it first.

### Deleting Language Models

1. Find the language in **"Downloaded Languages"**
2. **Tap the delete icon** (trash can)
3. Confirm deletion

### Stopping the Server

1. **Tap "Stop Server"** in the app, or
2. **Swipe away the notification** to stop the service

---

## REST API Endpoints

The server exposes the following endpoints on port **8787**:

### `GET /status`
Returns server status, IP address, and request count.

**Response:**
```json
{
  "status": "ok",
  "app_version": "1.0.0",
  "device_name": "Pixel 6",
  "ip_address": "192.168.1.42",
  "port": 8787,
  "request_count": 42
}
```

### `GET /languages`
Lists all available languages and their download status.

**Response:**
```json
{
  "languages": [
    {
      "code": "en",
      "name": "English",
      "downloaded": true,
      "size_mb": 30
    },
    {
      "code": "es",
      "name": "Spanish",
      "downloaded": false,
      "size_mb": 30
    }
  ]
}
```

### `POST /translate`
Translates a single text.

**Request:**
```json
{
  "text": "Hello, world!",
  "source": "en",
  "target": "es"
}
```

**Response:**
```json
{
  "translated_text": "¡Hola, mundo!",
  "source": "en",
  "target": "es",
  "model_type": "offline"
}
```

### `POST /translate/batch`
Translates multiple texts in one request.

**Request:**
```json
{
  "texts": ["Hello", "Goodbye"],
  "source": "en",
  "target": "fr"
}
```

**Response:**
```json
{
  "translations": [
    {
      "source_text": "Hello",
      "translated_text": "Bonjour"
    },
    {
      "source_text": "Goodbye",
      "translated_text": "Au revoir"
    }
  ],
  "source": "en",
  "target": "fr"
}
```

### `POST /detect`
Detects the language of a text.

**Request:**
```json
{
  "text": "Bonjour le monde"
}
```

**Response:**
```json
{
  "detected_language": "fr",
  "confidence": null
}
```

### `POST /models/download`
Downloads a language model.

**Request:**
```json
{
  "language": "de"
}
```

**Response:**
```json
{
  "status": "downloaded",
  "language": "de",
  "message": "German model downloaded successfully"
}
```

### `POST /models/delete`
Deletes a language model.

**Request:**
```json
{
  "language": "de"
}
```

**Response:**
```json
{
  "status": "deleted",
  "language": "de"
}
```

---

## Architecture

### Technology Stack

- **Kotlin** — Primary language
- **Jetpack Compose** — Modern declarative UI
- **Google ML Kit Translation** — On-device translation engine
- **NanoHTTPD** — Embedded HTTP server
- **Foreground Service** — Keeps server running in background
- **WakeLock + WiFi Lock** — Prevents device sleep during translation

### Key Components

| Component | Purpose |
|-----------|---------|
| `MainActivity` | Jetpack Compose UI, server controls, language management |
| `MainViewModel` | State management with Kotlin Flow |
| `TranslationServer` | NanoHTTPD server handling REST API requests |
| `TranslationService` | Foreground service with notification |
| `TranslatorPool` | Manages cached ML Kit Translator instances |
| `ModelManager` | Downloads, deletes, and lists language models |
| `LanguageUtils` | Maps language codes to ML Kit language constants |
| `NetworkUtils` | Detects device IP address for WiFi connections |

### Project Structure

```
app/src/main/
├── AndroidManifest.xml
├── java/com/kotranslate/
│   ├── KOTranslateApp.kt          # Application class
│   ├── MainActivity.kt             # Main UI
│   ├── MainViewModel.kt            # ViewModel
│   ├── TranslationServer.kt        # HTTP server
│   ├── TranslationService.kt       # Foreground service
│   ├── TranslatorPool.kt           # Translator cache
│   ├── ModelManager.kt             # Model management
│   ├── LanguageUtils.kt            # Language mappings
│   └── NetworkUtils.kt             # IP detection
└── res/
    ├── values/
    │   ├── strings.xml
    │   ├── themes.xml
    │   └── ic_launcher_colors.xml
    ├── drawable/
    │   └── ic_launcher_foreground.xml
    └── mipmap-anydpi-v26/
        └── ic_launcher.xml
```

---

## Supported Languages (56 Total)

Afrikaans, Albanian, Arabic, Belarusian, Bengali, Bulgarian, Catalan, Chinese, Croatian, Czech, Danish, Dutch, English, Esperanto, Estonian, Finnish, French, Galician, Georgian, German, Greek, Gujarati, Hindi, Hungarian, Icelandic, Indonesian, Irish, Italian, Japanese, Kannada, Korean, Latvian, Lithuanian, Macedonian, Malay, Maltese, Marathi, Norwegian, Persian, Polish, Portuguese, Romanian, Russian, Slovak, Slovenian, Spanish, Swahili, Swedish, Tagalog, Tamil, Telugu, Thai, Turkish, Ukrainian, Urdu, Vietnamese, Welsh

---

## Troubleshooting

### Server won't start
- **Check permissions:** Ensure the app has notification permission (Android 13+)
- **Check port:** Port 8787 must not be in use by another app
- **Restart app:** Force close and reopen KOTranslate

### Cannot connect from KOReader
- **Same WiFi network:** Ensure both devices are on the same WiFi
- **Firewall:** Some routers block device-to-device communication
- **IP address:** Verify the IP shown in KOTranslate matches the one in KOReader settings
- **Server running:** Check that "Server Running" is displayed in KOTranslate

### Translation fails with "model_not_downloaded"
- **Download models:** Both source and target language models must be downloaded
- **English required:** Some language pairs require English as a pivot language

### App crashes or freezes
- **Clear app data:** Settings → Apps → KOTranslate → Storage → Clear Data
- **Reinstall:** Uninstall and reinstall the APK
- **Check logs:** Use `adb logcat` to view error messages

---

## License & Attribution

This app uses **Google ML Kit** for on-device translation. By using this app, you agree to comply with Google's [ML Kit Terms of Service](https://developers.google.com/ml-kit/terms).

**Attribution:** Powered by Google ML Kit Translation API

---

## Contributing

This is a companion app for the [**KOTranslate** plugin](https://github.com/omer-faruq/kotranslate.koplugin). For issues or feature requests, please refer to the main plugin repository.
