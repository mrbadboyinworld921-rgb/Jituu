# MAIM — Holographic AI Friend (Android)

**Owner:** Jitendra maurya (`jitendermourya921@gmail.com`)

A native Kotlin + Jetpack Compose Android application with a transparent cyber-hacker HUD, animated Matrix-rain + perspective grid background, optional on-device CameraX AR HUD, interactive 3D-projected holographic female avatar, hands-free Hinglish/English voice loop, Coding Mode with sandboxed HTML/JS preview, encrypted utilities, GitHub Uplink, and a zero-knowledge AES-256-GCM local vault.

## Features

- **Cyber-Hacker Holographic HUD**: Translucent glassmorphic panels, neon cyan/emerald corner brackets, scanline overlay, glitch `MAIM` title, simulated telemetry (`SIMULATED` badge), custom adaptive launcher icon, and bundled **Orbitron** + **JetBrains Mono** fonts.
- **3D Holographic Avatar**: Custom 60fps Compose `Canvas` rig with volumetric pedestal, idle breathing, blinking, pointer/touch eye contact, speaking arm gestures, and speech-energy lip-sync. Pauses automatically when backgrounded and respects reduced-motion settings.
- **On-Device AR Camera HUD**: Optional CameraX rear camera feed (`CAMERA` runtime permission) that stays strictly on-device.
- **Hands-Free Voice Mode**: Android `SpeechRecognizer` (`hi-IN` with automatic `en-IN` fallback) + `TextToSpeech` with voice picker, **Test Voice** button, and markdown/emoji stripping.
- **Coding Mode**: Syntax-highlighted code drawer with Copy, Save (`CreateDocument` SAF), and a strictly sandboxed HTML/JS `WebView` preview (file/storage disabled, external network requests blocked, strict CSP meta header).
- **Encrypted Utilities & GitHub Uplink**: Voice- and UI-controlled timers, reminders, to-dos, quick notes, live clock, plus a GitHub repository & root file browser (`GITHUB` panel) supporting public accounts (e.g. `Crosas27`) or an optional PAT sealed inside your encrypted vault.

## Security Model

1. **Firebase AI Logic + App Check**: Gemini inference (`gemini-3.8-flash`) runs through `Firebase.ai` with App Check attestation (`DebugAppCheckProviderFactory` in debug, `RecaptchaAppCheckProviderFactory` in release). No Gemini API key is stored in client code.
2. **Zero-Knowledge Local Vault**: Your 6-digit PIN is stretched with **PBKDF2WithHmacSHA256 (310,000 iterations)** and split via **HKDF-SHA256** into a constant-time PIN verifier and an **AES-256-GCM** encryption key. Owner profile, chat history, notes, to-dos, reminders, and optional GitHub PAT are encrypted before saving to internal storage.
3. **Biometrics, Lockout & Panic Wipe**: Optional `BiometricPrompt` backed by an Android Keystore AES-GCM key (`setUserAuthenticationRequired(true)`), 5-minute lockout after 5 wrong PIN attempts, 2-minute idle auto-lock, `android:allowBackup="false"`, and a **Panic Wipe** button that erases the vault, metadata, and Keystore key.
