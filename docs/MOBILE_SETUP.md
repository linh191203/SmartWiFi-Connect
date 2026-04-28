# SmartWiFi Connect - Mobile Setup & Build Guide

## Overview
Your SmartWiFi Connect app is now configured as a **Progressive Web App (PWA)** with **Capacitor** support for native mobile builds on Android and iOS.

### Current Project Structure
```
web/
├── dist/                      # Built web app (ready for mobile)
├── android/                   # Android native project (Capacitor)
├── ios/                        # iOS native project (Capacitor)
├── src/                        # React source code
├── vite.config.js            # PWA + Vite config
├── capacitor.config.json     # Capacitor configuration
├── package.json              # Build scripts included
└── index.html                # PWA meta tags configured
```

## Build Scripts Available

```bash
# Build web app only
npm run build

# Full mobile build (web + sync Android & iOS)
npm run build:mobile

# Android only
npm run build:android

# iOS only  
npm run build:ios

# Build APK debug (requires Android setup)
npm run android:apk

# Open iOS project in Xcode
npm run ios:build
```

---

## 🤖 Android Setup & Build

### Prerequisites
1. **Java 17** (recommended baseline for this project)
2. **Android SDK** (Android Studio or command-line tools)
3. **ANDROID_HOME** environment variable configured

### Step 1: Install Java 17

#### Option A: Using Chocolatey (Windows)
```powershell
choco install openjdk17
```

#### Option B: Manual Download
- Download from [Adoptium OpenJDK](https://adoptium.net/) or [Oracle JDK](https://www.oracle.com/java/technologies/downloads/)
- Install JDK 17
- Add to PATH or set `JAVA_HOME`

**Verify installation:**
```powershell
java -version
javac -version
```

### Step 2: Install Android SDK

#### Option A: Android Studio (Recommended)
1. Download [Android Studio](https://developer.android.com/studio)
2. Install and open Android Studio
3. Go to **Settings → Appearance & Behavior → System Settings → Android SDK**
4. Install:
   - **Android SDK Platform 34** (latest)
   - **Android SDK Build-Tools 34.0.0+**
   - **Android Emulator** (optional, for testing)
5. Accept licenses:
   ```bash
   cd $ANDROID_HOME/tools/bin
   ./sdkmanager --licenses
   ```

#### Option B: Command-Line Tools Only
```powershell
# Download cmdline-tools and extract
cd C:\Android
# Use sdkmanager to install:
sdkmanager "platforms;android-34" "build-tools;34.0.0" "platform-tools"
```

### Step 3: Set Environment Variables

Add to your system environment variables:
```powershell
$env:ANDROID_HOME = "C:\Users\<YourUsername>\AppData\Local\Android\Sdk"
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17" # or your JDK path
$env:PATH += ";$env:ANDROID_HOME\platform-tools;$env:ANDROID_HOME\tools\bin"
```

**Verify:**
```powershell
$env:ANDROID_HOME
adb --version
```

### Step 4: Build the APK

**Navigate to web directory:**
```bash
cd web
npm run build:android
cd android
.\gradlew.bat assembleDebug --stacktrace
```

**APK Output Location:**
```
web/android/app/build/outputs/apk/debug/app-debug.apk
```

### Step 5: Install on Device or Emulator

**Using ADB (Android Debug Bridge):**
```bash
adb devices  # List connected devices
adb install app-debug.apk
```

**Or open in Android Studio:**
```bash
open android/  # (or use Android Studio directly)
```

---

## 🍎 iOS Setup & Build (macOS only)

### Prerequisites
- **macOS** (10.15 or newer)
- **Xcode** (14 or newer)
- **CocoaPods**

### Step 1: Prepare Build

```bash
cd web
npm run build:ios
```

### Step 2: Open in Xcode

```bash
open ios/App/App.xcworkspace
```

### Step 3: Sign & Build

1. Select your team in Xcode (Signing & Capabilities)
2. Select target device
3. Build: `Cmd + B` or Product → Build
4. Run on device: `Cmd + R` or Product → Run

**IPA Output:**
```
ios/App/build/Release-iphoneos/App.ipa
```

---

## 🌐 PWA (Works on All Devices - No Installation Needed)

Your app is already a PWA and works on **Android, iOS, Windows, Mac, Linux**.

### How to Use:

**On Android Phone:**
1. Open in Chrome: `https://yourdomain.com`
2. Tap **⋮** (menu) → "Install app"
3. App appears on home screen

**On iPhone:**
1. Open in Safari: `https://yourdomain.com`
2. Tap **Share** → "Add to Home Screen"
3. App appears on home screen

### Features:
- ✅ Offline support (service worker)
- ✅ Standalone full-screen mode
- ✅ Status bar styling
- ✅ Network-first caching strategy
- ✅ Works without internet (cached content)

---

## 📦 Deployment Options

### 1. **Google Play Store** (Android)
```bash
# Build release APK
cd android
./gradlew bundleRelease

# Upload app-release.aab to Google Play Console
```

### 2. **Apple App Store** (iOS)
```bash
# Build release IPA in Xcode
# Upload via App Store Connect
```

### 3. **Web/PWA Hosting**
- Deploy `dist/` folder to any static hosting (Vercel, Netlify, Firebase, AWS S3, etc.)
- Users install from web, works as native app

---

## 🔍 Testing Checklist

- [ ] APK installs and launches on Android device
- [ ] App responds to back button correctly
- [ ] Bottom navigation works on mobile
- [ ] Responsive layout on different screen sizes
- [ ] OCR parsing works (backend reachable)
- [ ] Browser storage (localStorage) persists data
- [ ] PWA offline mode works
- [ ] Icons display correctly
- [ ] Status bar color matches theme

---

## 📋 Capacitor Mobile Feature Support

| Feature | Status | Notes |
|---------|--------|-------|
| Web UI | ✅ Full | Responsive React UI |
| PWA | ✅ Full | Works on all devices |
| Android APK | ✅ Ready | Use build script |
| iOS IPA | ✅ Ready | macOS + Xcode only |
| API Calls | ✅ Full | Network requests work |
| LocalStorage | ✅ Full | Data persistence |
| Camera | ⚠️ Optional | Not yet implemented in this version |
| Wi-Fi Connect | ⚠️ Limited | Platform APIs restrict direct access on web |

---

## 🐛 Troubleshooting

### "Could not resolve com.android.tools.build:gradle"
- Install Java 11+ and set `JAVA_HOME`
- Run `./gradlew clean` then rebuild

### "ANDROID_HOME not set"
- Set environment variable pointing to Android SDK directory
- Restart terminal and verify: `echo $env:ANDROID_HOME`

### APK won't install
- Clear app cache: `adb shell pm clear com.smartwificonnect.app`
- Uninstall previous: `adb uninstall com.smartwificonnect.app`
- Reinstall: `adb install app-debug.apk`

### Cannot find device with ADB
- Verify USB debugging enabled on device
- Install USB drivers (if on Windows)
- Run: `adb kill-server && adb start-server`

---

## 📞 Build Commands Summary

```bash
# Development
npm run dev                    # Start dev server (5173)

# Production Web Build
npm run build                  # Build web app → dist/

# Mobile Builds
npm run build:mobile          # Web build + sync Android & iOS
npm run build:android         # Web build + sync Android only
npm run build:ios             # Web build + sync iOS only

# Android APK Build (requires Android SDK setup)
npm run android:apk

# iOS Xcode Project (macOS only)
npm run ios:build
```

---

## ✅ Your App is Ready!

1. **Web/PWA**: Ready to deploy now (works everywhere)
2. **Android**: Ready after Java 11 + Android SDK setup → build APK
3. **iOS**: Ready after macOS + Xcode setup → build IPA

Next steps:
- Set up Android SDK (Java 11, Android SDK)
- Build APK: `npm run android:apk`
- Test on device
- Deploy to Google Play Store or use PWA

For more info: https://capacitorjs.com/docs/getting-started
