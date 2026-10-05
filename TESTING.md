# Athena Testing

Athena includes automated and manual testing focused on vault integrity, authentication, recovery-key handling, session security, secure UI behavior, biometric protection, and release reliability.

---

## Automated Test Coverage

### VaultManagerInstrumentedTest

Covers the v4 encrypted vault lifecycle, including:

- v4 vault creation
- correct master password and recovery key unlock
- wrong master password rejection
- wrong recovery key rejection
- encrypted save and load behavior
- AES-256-GCM vault encryption
- DEK wrapping and unwrapping
- HKDF-SHA-256 key combination
- Argon2id parameter validation
- vault identifier handling
- tamper detection
- corrupted vault rejection
- authenticated overwrite protection
- rollback behavior
- malformed vault rejection
- size and input bounds
- defensive-copy behavior

---

### RecoveryKeyStoreInstrumentedTest

Covers trusted-device recovery-key caching, including:

- recovery-key encryption using Android Keystore
- vault-specific recovery-key storage
- recovery-key loading
- incorrect vault identifier handling
- clearing cached recovery keys
- clearing all cached recovery-key state
- invalid or corrupted stored state
- defensive handling of sensitive key material

---

### RecoveryKeyCodecTest

Covers recovery-key encoding and decoding, including:

- 32-byte recovery-key encoding
- URL-safe Base64 representation
- display formatting
- whitespace-tolerant decoding
- invalid input rejection
- incorrect decoded key length rejection

---

### BaseSecureActivityInstrumentedTest

Covers new-vault master-password creation, including:

- minimum password length
- maximum password length
- uppercase requirement
- lowercase requirement
- numeric requirement
- symbol requirement
- whitespace not counting as a symbol
- exact password confirmation
- password callback behavior
- password-strength display
- zxcvbn strength feedback
- advisory strength behavior

---

### VaultSessionManagerInstrumentedTest

Covers persisted vault session metadata and runtime integration, including:

- vault URI storage
- biometric-enabled state
- session restoration
- vault-session cleanup
- recovery-key cache cleanup
- biometric state cleanup
- runtime-session cleanup
- preference handling

---

### ClipboardUtilsInstrumentedTest

Covers sensitive clipboard behavior, including:

- password copy behavior
- automatic clearing
- replacement of existing clear timers
- manual sensitive clipboard clearing
- repeated copy operations

---

### PasswordGeneratorTest

Covers secure password generation, including:

- requested length
- lowercase requirement
- uppercase requirement
- numeric requirement
- symbol requirement
- randomness-related constraints

---

### InstantPasswordTransformationMethodTest

Covers immediate password masking behavior.

---

### VaultAdapterInstrumentedTest

Covers vault entry list behavior, including:

- list updates
- true index preservation
- filtered results
- adapter consistency

---

### VaultLockerInstrumentedTest

Covers secure vault locking, including:

- runtime key clearing
- timeout reset
- clipboard cleanup
- navigation back to the main activity
- vault URI preservation where appropriate

---

### TimeoutManagerInstrumentedTest

Covers inactivity timeout behavior and secure session expiry.

---

### BiometricStoreInstrumentedTest

Covers biometric-protected DEK storage behavior, including:

- wrapped DEK persistence
- Android Keystore integration
- biometric-protected key usage
- clearing biometric state
- invalid state handling
- biometric metadata behavior
- hardware-backed Keystore checks
- enrollment-sensitive key behavior

---

### EntryDetailActivityInstrumentedTest

Covers protected credential actions, including:

- password reveal
- password masking
- password copy
- editing
- deletion
- biometric authentication
- master-password re-authentication
- correct entry index handling
- recovery-key-backed re-authentication behavior
- sensitive UI cleanup

---

### SecureEditTextInstrumentedTest

Covers secure input behavior, including:

- password input mode
- selection restrictions
- context menu restrictions
- autofill restrictions
- long-click behavior
- immediate masking
- secure cursor and input behavior

---

### EditEntryActivityInstrumentedTest

Covers credential editing, including:

- exact entry index handling
- field validation
- hostname normalization
- encrypted save behavior
- discard confirmation
- sensitive UI cleanup
- v4 vault session integration

---

### MainActivityInstrumentedTest

Covers main-screen vault workflows, including:

- initial application state
- remembered vault handling
- correct master-password unlock
- incorrect master-password rejection
- recovery-key-required unlock
- invalid recovery-key rejection
- incorrect recovery-key rejection
- successful recovery-key unlock
- recovery-key caching after successful authentication
- password-only unlock after recovery-key caching
- session establishment
- vault URI persistence
- sensitive password cleanup
- biometric unlock integration
- password masking behavior

---

### VaultActivityInstrumentedTest

Covers vault display and search behavior, including:

- vault loading
- runtime DEK usage
- search filtering
- real entry index preservation
- display name handling
- v4 session behavior

---

## Automated Test Summary

Athena's automated suite covers the core security and application workflows.

**Total automated tests passed: 244 / 244**

---

## ⚠️ Disclaimer

Passing the automated test suite does not prove that Athena is free from security vulnerabilities.

Athena has not undergone an independent professional penetration test or cryptographic audit.
