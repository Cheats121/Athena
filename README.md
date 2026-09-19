<p align="center">
  <img src="docs/images/athena_logo.png" alt="Athena logo" width="140" />
</p>

<h1 align="center">Athena</h1>

<p align="center">
  <b>Offline, privacy-first password management with strong encryption and on-device security.</b>
</p>

<p align="center">
  Athena is a local-first Android password vault designed around strong on-device security, clean UX, and zero cloud dependency.
</p>

<p align="center">
  <img alt="Platform" src="https://img.shields.io/badge/platform-Android-3DDC84?logo=android&logoColor=white">
  <img alt="Language" src="https://img.shields.io/badge/language-Kotlin-7F52FF?logo=kotlin&logoColor=white">
  <img alt="Min SDK" src="https://img.shields.io/badge/minSdk-24-blue">
  <img alt="Target SDK" src="https://img.shields.io/badge/targetSdk-36-blue">
  <img alt="Security" src="https://img.shields.io/badge/encryption-AES--256--GCM-informational">
  <img alt="KDF" src="https://img.shields.io/badge/KDF-Argon2id-orange">
  <img alt="Tests" src="https://img.shields.io/badge/tests-passing-success">
  <img alt="Release" src="https://img.shields.io/github/v/release/Cheats121/Athena">
</p>

---

## Overview

Athena is an **offline, privacy-first password manager for Android**.

It stores credentials in an encrypted local vault and keeps sensitive operations on-device.

The project was built around a simple principle:

> **Keep password management local, secure, and understandable.**

Athena does not require:

- a cloud backend
- online account creation
- analytics
- remote synchronization
- continuous internet access

The vault is protected with modern cryptography and optional biometric quick unlock using Android Keystore.

---

## Download

The latest signed Android release is available through GitHub Releases.

<p align="center">
  <a href="[https://github.com/Cheats121/Athena/releases/latest/download/athena-release.apk](https://github.com/Cheats121/Athena/releases/download/v1.1/athena_release.apk)">
    <img src="https://img.shields.io/badge/Download-Latest%20APK-2ea44f?style=for-the-badge&logo=android&logoColor=white" alt="Download Athena APK">
  </a>
</p>

<p align="center">
  <a href="https://github.com/Cheats121/Athena/releases/latest">
    View latest release notes and verification information
  </a>
</p>

> The APK is signed using Athena's release signing key.  
> SHA-256 checksums are published with each GitHub release.

Because Athena is distributed outside Google Play, Android may ask you to allow installation from your browser or file manager.

---

## Demo

### App Preview

<p align="center">
  <img src="docs/gifs/athena_demo.gif" alt="Athena demo" width="200" />
</p>

---

## Screenshots

<p align="center">
  <img src="docs/images/screenshot_main.jpg" alt="Athena main screen" width="220" />
  <img src="docs/images/screenshot_vault.jpg" alt="Athena vault screen" width="220" />
  <img src="docs/images/screenshot_entry.jpg" alt="Athena entry detail screen" width="220" />
</p>

<p align="center">
  <img src="docs/images/screenshot_add.jpg" alt="Athena add credential screen" width="220" />
  <img src="docs/images/screenshot_about.jpg" alt="Athena about and security screen" width="220" />
</p>

---

## Key Features

- **Offline-first architecture**
  - no account creation
  - no remote backend requirement
  - no cloud synchronization dependency

- **Encrypted local vault**
  - vault contents protected using **AES-256-GCM**
  - authenticated encryption provides confidentiality and integrity
  - tampered vault data is rejected

- **Strong password-based key derivation**
  - master password processed using **Argon2id**
  - memory-hard key derivation
  - defensive bounds applied to stored Argon2 parameters

- **Separate KEK and DEK design**
  - master password derives a Key Encryption Key
  - vault data uses an independent random Data Encryption Key
  - the DEK is wrapped using the password-derived KEK

- **Biometric quick unlock**
  - optional biometric authentication
  - DEK wrapping using Android Keystore
  - biometric enrollment changes invalidate the Keystore key
  - StrongBox requested when supported by the device

- **Sensitive action protection**
  - re-authentication required for:
    - revealing passwords
    - copying passwords
    - editing credentials
    - deleting credentials

- **Secure clipboard handling**
  - copied passwords are automatically cleared
  - newer copy operations cancel older pending clear timers

- **Secure password generation**
  - cryptographically secure randomness
  - lowercase, uppercase, digit, and symbol requirements
  - SecureRandom-based shuffling

- **Session protection**
  - runtime-only DEK storage
  - automatic timeout locking
  - sensitive UI cleanup
  - in-memory key wiping

- **Secure input handling**
  - immediate password masking
  - restricted selection and context actions
  - autofill disabled for protected password inputs

- **Security transparency**
  - signing certificate verification
  - APK digest display
  - release/debuggable state detection
  - debugger detection
  - basic hooking framework indicators

---

## Cryptographic Workflow

<p align="center">
  <img src="docs/images/cryptographic_workflow.png" alt="Athena cryptographic workflow" width="100%" />
</p>

Athena separates password-derived key material from the key that directly encrypts vault contents.

### 1. Master Password → KEK

The user enters a master password.

Athena processes the password using **Argon2id** to derive a 256-bit **Key Encryption Key (KEK)**.

The KEK protects the vault encryption key rather than directly encrypting all vault data.

### 2. Random Vault DEK

Athena generates an independent random 256-bit **Data Encryption Key (DEK)**.

The DEK is the key that actually encrypts and decrypts vault contents.

### 3. DEK Wrapping

The password-derived KEK wraps the DEK using authenticated encryption.

This design means:

- the master password is not directly used as the vault encryption key
- the DEK remains independently random
- password-derived key material and vault encryption responsibilities remain separated

### 4. Vault Encryption

Vault contents are encrypted using:

**AES-256-GCM**

AES-GCM provides:

- confidentiality
- authentication
- integrity
- tamper detection

The encrypted vault is stored as a local JSON-based vault envelope.

### 5. Biometric Quick Unlock

When biometric quick unlock is enabled:

- Athena creates a biometric-protected AES key inside **Android Keystore**
- the Keystore key wraps the vault DEK
- biometric authentication is required to unwrap it
- the Keystore key itself does not leave Android Keystore

StrongBox-backed storage is requested when the device supports it.

### 6. Runtime Key Handling

After successful authentication, the DEK is held only in the active runtime session.

Athena uses defensive copies and wipes sensitive byte arrays when the session ends or the vault is locked.

---

## Security Design

Athena uses multiple layers of protection rather than relying on a single security control.

### Password Protection

The master password is processed using **Argon2id** with:

- memory-hard derivation
- multiple iterations
- parallel processing lanes
- random salt
- defensive bounds on stored Argon2 parameters

This increases the computational cost of offline password guessing.

### Authenticated Encryption

Vault encryption uses **AES-256-GCM** with unique nonces and authentication tags.

Modified or corrupted vault ciphertext is rejected rather than silently decrypted.

### Biometric Protection

Biometric quick unlock is implemented using:

- AndroidX Biometric
- Android Keystore
- AES-GCM key wrapping
- per-operation biometric authentication
- biometric enrollment invalidation

### Sensitive Action Re-authentication

Entering the vault does not automatically authorize every sensitive operation.

Actions including password reveal, copy, edit, and deletion require additional authentication using biometrics or the master password fallback.

### Session Isolation

The vault DEK is kept in memory only during an active session.

Persistent preferences store configuration and metadata rather than the plaintext DEK.

---

## Security Notes

Athena is designed to reduce exposure of sensitive data through:

- secure local encryption
- automatic clipboard clearing
- session timeout locking
- runtime key wiping
- screen capture protection in release builds
- restricted secure text input
- authenticated vault writes
- rollback behavior during failed saves
- no analytics
- no cloud transmission in normal operation

No password manager can guarantee absolute security.

Security still depends on factors including:

- device integrity
- Android OS security
- malware exposure
- user password strength
- update hygiene
- physical device access

Athena's tamper and hooking checks should be treated as **defense-in-depth indicators**, not remote attestation or proof that a device is uncompromised.

---

## Testing

Athena includes an extensive Android test suite covering core vault, session, UI, biometric, and security behavior.

### Covered Areas

- vault creation
- correct password unlock
- wrong password rejection
- encrypted save and load
- tamper detection
- authenticated overwrite protection
- rollback behavior
- session storage
- runtime key management
- vault URI handling
- biometric storage behavior
- clipboard clearing
- password generation
- secure input handling
- password masking
- timeout behavior
- vault locking
- entry detail authentication
- credential editing
- credential creation
- vault list behavior
- search behavior
- adapter index preservation
- main activity behavior

### Result

All implemented automated tests passed before publication.

The project also received manual release-build smoke testing for core application behavior.

See [`TESTING.md`](TESTING.md) for the full testing breakdown.

---

## Tech Stack

- **Language:** Kotlin
- **Platform:** Android
- **UI:** Android Views + Material Components
- **Cryptography:** AES-256-GCM
- **Password KDF:** Argon2id
- **Biometrics:** AndroidX Biometric
- **Secure key storage:** Android Keystore
- **Storage:** encrypted local vault file
- **Testing:** JUnit + Android instrumented testing
- **Build system:** Gradle Kotlin DSL

---

## Roadmap

Athena currently focuses on secure local credential management on Android.

Future development is planned around expanding portability and usability without abandoning Athena's local-first security model.

### Encrypted Vault Backups

Planned backup functionality will focus on preserving the encrypted vault rather than exporting credentials as plaintext.

Goals include:

- encrypted vault backup creation
- safe backup verification
- encrypted restore and import workflows
- integrity verification before accepting restored vaults
- no plaintext credential exports during the backup process

### Seamless Credential Filling on PC

A longer-term goal is to provide secure credential filling for desktop applications and websites.

The intended direction includes:

- authenticated credential retrieval
- reduced reliance on manual copy and paste
- explicit user authorization before filling sensitive credentials
  
### Cross-Device Workflows

Athena may explore secure ways to move encrypted vault data between devices without requiring a traditional cloud account.

The focus would remain on:

- end-to-end encrypted data
- user-controlled vault files
- explicit device authorization
- avoiding unnecessary server-side access to credential data

### Accessibility and UX

Future improvements may also include:

- expanded accessibility support
- smoother navigation
- additional vault organization tools
- improved credential management workflows

---

## Vault Format

Athena currently uses its **v3 encrypted vault format**.

At a high level, the vault contains:

- vault format metadata
- Argon2id parameters
- random salt
- vault identifier
- wrapped DEK
- encrypted vault payload
- AES-GCM nonces and authentication data

Plaintext credentials are not stored directly in the vault file.

---

## Build Instructions

### Requirements

- Android Studio
- Android SDK
- compatible JDK
- Android device or emulator

### Clone

```bash
git clone https://github.com/Cheats121/Athena.git
cd Athena
```

Open the project in Android Studio and allow Gradle to sync.

### Debug Build

On Windows:

```powershell
.\gradlew assembleDebug
```

### Release Build

Release builds should be generated using your own signing key.

The repository intentionally does not include Athena's private release signing key.

---

## Privacy

Athena is designed to operate locally.

The application does not require:

- cloud accounts
- remote credential storage
- analytics services
- cloud synchronization
- a backend server

The user's encrypted vault remains under the user's control.

---

## Repository Contents

This repository includes:

- Kotlin source code
- Android XML resources
- security-related utilities
- instrumented tests
- Gradle configuration
- ProGuard / R8 rules
- backup exclusion rules
- project documentation
- demo media

The repository intentionally excludes:

- private signing keys
- `.jks` / `.keystore` files
- release APKs from source control
- AAB files
- local Android SDK paths
- build directories
- machine-specific configuration
- secrets and environment files

Release APKs are distributed separately through **GitHub Releases**.

---

## ⚠️ Disclaimer

Athena is a personal and educational security-focused software project.

It has **not undergone an independent professional security audit**.

Users should review the source code, threat model, build process, and operational security assumptions before trusting Athena with real-world sensitive credentials.

---

## Author

**Cheats121**
