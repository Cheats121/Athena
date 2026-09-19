<p align="center">
  <img src="docs/images/athena-logo.png" alt="Athena logo" width="140" />
</p>

<h1 align="center">Athena</h1>

<p align="center">
  <b>An offline, privacy-first Android password manager.</b>
</p>

<p align="center">
  Athena is a local-first password vault built for strong on-device security, clean UX, and zero cloud dependency.
</p>

<p align="center">
  <img alt="Platform" src="https://img.shields.io/badge/platform-Android-3DDC84?logo=android&logoColor=white">
  <img alt="Language" src="https://img.shields.io/badge/language-Kotlin-7F52FF?logo=kotlin&logoColor=white">
  <img alt="Min SDK" src="https://img.shields.io/badge/minSdk-24-blue">
  <img alt="Target SDK" src="https://img.shields.io/badge/targetSdk-36-blue">
  <img alt="Security" src="https://img.shields.io/badge/encryption-AES--256--GCM-informational">
  <img alt="KDF" src="https://img.shields.io/badge/KDF-Argon2id-orange">
  <img alt="Tests" src="https://img.shields.io/badge/tests-passing-success">
</p>

---

## Overview

Athena is an **offline, privacy-first password manager** for Android.  
It stores credentials in an encrypted local vault and keeps sensitive operations on-device.

The app was designed around a simple goal:

> **Keep password management local, secure, and understandable.**

Athena does **not** depend on a cloud backend, analytics pipeline, or online account system.  
The vault is protected with modern cryptography and optional biometric convenience features built on Android Keystore.

---

## Demo

### App preview
<p align="center">
  <img src="docs/gifs/athena-demo.gif" alt="Athena demo" width="300" />
</p>

> Replace the GIF above with your screen recording export.

### Full walkthrough
If you want, add a full demo link here:

- [Watch the full demo video](YOUR_VIDEO_LINK_HERE)

---

## Screenshots

<p align="center">
  <img src="docs/images/screenshot-main.png" alt="Main screen" width="220" />
  <img src="docs/images/screenshot-vault.png" alt="Vault screen" width="220" />
  <img src="docs/images/screenshot-entry.png" alt="Entry detail screen" width="220" />
</p>

<p align="center">
  <img src="docs/images/screenshot-about.png" alt="About and security screen" width="220" />
</p>

---

## Key Features

- **Offline-first architecture**
  - no account creation
  - no remote server requirement
  - no cloud sync dependency

- **Encrypted local vault**
  - vault content protected with **AES-256-GCM**
  - authentication tag verification for tamper detection

- **Strong password-based key derivation**
  - master password processed with **Argon2id**

- **Biometric quick unlock**
  - optional biometric convenience flow
  - wrapped DEK storage using **Android Keystore**
  - biometric key invalidation on enrollment changes

- **Sensitive action protection**
  - re-authentication required for:
    - reveal password
    - copy password
    - edit credential
    - delete credential

- **Secure clipboard handling**
  - sensitive copies are automatically cleared after a timeout

- **Password generation**
  - cryptographically secure random password generation
  - mixed uppercase, lowercase, digits, and symbols

- **Session protection**
  - runtime session management
  - timeout-based lock behavior
  - sensitive UI cleanup

- **Security transparency**
  - in-app About / Security screen
  - signing certificate check
  - APK checksum display
  - debugger / tamper-related indicators

---

## Security Design

Athena uses a layered model:

### 1. Master password → vault key derivation
The user’s master password is passed through **Argon2id** to derive the vault key material.

### 2. Vault encryption
Vault contents are encrypted with **AES-256-GCM**, providing:

- confidentiality
- integrity
- tamper detection

### 3. Biometric convenience
When enabled, Athena can wrap the vault DEK using a **biometric-protected Android Keystore AES key**.

This means:

- the actual Keystore key never leaves Android Keystore
- biometric authentication is required to use it
- biometric enrollment changes can invalidate access
- StrongBox is requested when supported

### 4. Sensitive action gating
Even after entering the vault, Athena protects high-risk actions like:

- revealing a password
- copying a password
- editing entries
- deleting entries

These actions require re-authentication through biometrics or master password fallback, depending on availability and configuration.

---

## Security Notes

Athena is designed to reduce exposure of sensitive data, including:

- secure local encryption
- automatic clipboard clearing
- secure session handling
- sensitive UI cleanup
- screen capture protection
- no analytics or cloud transmission in normal operation

That said, no password manager is “perfectly secure.”  
Security always depends on device trust, OS integrity, update hygiene, and user behavior.

---

## Testing

Athena includes an instrumented Android test suite covering core security and behavior.

### Covered areas include:
- vault creation and unlocking
- save/load integrity
- wrong-password rejection
- tamper rejection
- session behavior
- clipboard security behavior
- password generation rules
- secure input behavior
- timeout/session logic
- entry detail authentication flow
- edit/add credential flow
- main activity behavior
- vault activity and adapter behavior
- biometric storage behavior

### Result
All implemented tests passed successfully before publication.

> If you want, you can also add a separate `TESTING.md` with a fuller breakdown.

---

## Tech Stack

- **Language:** Kotlin
- **Platform:** Android
- **UI:** Android Views + Material Components
- **Crypto:** AES-GCM, Argon2id
- **Biometrics:** AndroidX Biometric + Android Keystore
- **Storage model:** encrypted local vault file
- **Testing:** Android instrumented tests (Espresso / JUnit)

---

## Project Structure

```text
app/src/main/java/com/athena/j/athena/
├── AboutActivity.kt
├── AddEntryActivity.kt
├── BaseSecureActivity.kt
├── BiometricAuth.kt
├── BiometricStore.kt
├── ClipboardUtils.kt
├── EditEntryActivity.kt
├── EntryDetailActivity.kt
├── InstantPasswordTransformationMethod.kt
├── MainActivity.kt
├── PasswordGenerator.kt
├── SecureEditText.kt
├── SplashActivity.kt
├── TamperChecker.kt
├── TimeoutManager.kt
├── VaultActivity.kt
├── VaultAdapter.kt
├── VaultCrypto.kt
├── VaultLocker.kt
├── VaultManager.kt
├── VaultRuntimeSession.kt
└── VaultSessionManager.kt
