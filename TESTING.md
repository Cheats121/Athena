# Athena Testing

Athena includes automated and manual testing focused on vault integrity, authentication, session handling, secure UI behavior, and release reliability.

---

## Automated Test Coverage

### VaultManagerInstrumentedTest

Covers the encrypted vault lifecycle, including:

- vault creation
- correct master password unlock
- wrong password rejection
- vault save/load behavior
- encrypted payload handling
- tamper detection
- authenticated overwrite protection
- rollback behavior
- Argon2 parameter validation
- key derivation behavior
- vault identifier handling
- DEK verification
- malformed vault rejection
- size and input bounds

**Result:** 28 / 28 passed

---

### VaultSessionManagerInstrumentedTest

Covers persisted vault session metadata and runtime integration, including:

- vault URI storage
- biometric-enabled state
- session restoration
- forgetting a vault
- runtime session cleanup
- preference handling

**Result:** 17 / 17 passed

---

### ClipboardUtilsInstrumentedTest

Covers sensitive clipboard behavior, including:

- password copy behavior
- automatic clearing
- replacement of existing clear timers
- manual sensitive clipboard clearing
- repeated copy operations

**Result:** 10 / 10 passed

---

### PasswordGeneratorTest

Covers secure password generation, including:

- requested length
- lowercase requirement
- uppercase requirement
- numeric requirement
- symbol requirement
- randomness-related constraints

**Result:** 6 / 6 passed

---

### InstantPasswordTransformationMethodTest

Covers immediate password masking behavior.

**Result:** 8 / 8 passed

---

### VaultAdapterInstrumentedTest

Covers vault entry list behavior, including:

- list updates
- true index preservation
- filtered results
- adapter consistency

**Result:** 8 / 8 passed

---

### VaultLockerInstrumentedTest

Covers secure vault locking, including:

- runtime key clearing
- timeout reset
- clipboard cleanup
- navigation back to the main activity
- vault URI preservation where appropriate

**Result:** 8 / 8 passed

---

### TimeoutManagerInstrumentedTest

Covers inactivity timeout behavior.

**Result:** 8 / 8 passed

---

### BiometricStoreInstrumentedTest

Covers biometric-protected DEK storage behavior, including:

- wrapped DEK persistence
- Keystore integration
- clearing biometric state
- invalid state handling
- biometric metadata behavior

**Result:** 27 / 27 passed

---

### EntryDetailActivityInstrumentedTest

Covers protected credential actions, including:

- password reveal
- password copy
- editing
- deletion
- biometric authentication
- master password fallback
- correct entry index handling

**Result:** 14 / 14 passed

---

### SecureEditTextInstrumentedTest

Covers secure input behavior, including:

- password input mode
- selection restrictions
- context menu restrictions
- autofill restrictions
- long-click behavior
- immediate masking

**Result:** 13 / 13 passed

---

### EditEntryActivityInstrumentedTest

Covers credential editing, including:

- exact entry index handling
- field validation
- hostname normalization
- encrypted save behavior
- discard confirmation
- sensitive UI cleanup

**Result:** 19 / 19 passed

---

### MainActivityInstrumentedTest

Covers main-screen vault workflows.

**Result:** 15 / 15 passed

---

### VaultActivityInstrumentedTest

Covers vault display and search behavior, including:

- vault loading
- search filtering
- real entry index preservation
- display name handling

**Result:** 17 / 17 passed

---

## Automated Test Summary

| Test Suite | Passed |
|---|---:|
| VaultManagerInstrumentedTest | 28 / 28 |
| VaultSessionManagerInstrumentedTest | 17 / 17 |
| ClipboardUtilsInstrumentedTest | 10 / 10 |
| PasswordGeneratorTest | 6 / 6 |
| InstantPasswordTransformationMethodTest | 8 / 8 |
| VaultAdapterInstrumentedTest | 8 / 8 |
| VaultLockerInstrumentedTest | 8 / 8 |
| TimeoutManagerInstrumentedTest | 8 / 8 |
| BiometricStoreInstrumentedTest | 27 / 27 |
| EntryDetailActivityInstrumentedTest | 14 / 14 |
| SecureEditTextInstrumentedTest | 13 / 13 |
| EditEntryActivityInstrumentedTest | 19 / 19 |
| MainActivityInstrumentedTest | 15 / 15 |
| VaultActivityInstrumentedTest | 17 / 17 |

**Total automated tests passed: 198 / 198**

---

## Manual Release Testing

The signed release APK was also manually smoke-tested after enabling R8 and resource shrinking.

Manual checks included:

- application launch
- vault creation
- correct password unlock
- incorrect password rejection
- credential creation
- credential editing
- credential deletion
- password reveal
- password copy
- clipboard clearing
- vault timeout behavior
- biometric quick unlock
- About / security information
- signing certificate verification
- release build detection
- screenshot protection in release builds

---

## Known Testing Gaps

The current test suite is substantial, but it is not a substitute for an independent security audit.

Areas for future testing include:

- broader biometric testing across real devices
- process death and state restoration
- Android lifecycle edge cases
- Storage Access Framework provider edge cases
- device-specific Keystore behavior
- StrongBox-specific behavior
- additional release-build instrumentation
- fuzzing of malformed vault files
- long-running reliability testing
- accessibility testing

---

## Security Testing Philosophy

Athena's tests focus on validating security-sensitive behavior rather than only checking UI output.

Key goals include:

- reject invalid or tampered vault data
- prevent incorrect entry indexing
- minimize plaintext credential exposure
- clear sensitive clipboard data
- clear runtime keys when locking
- require re-authentication for sensitive actions
- verify authenticated vault writes
- validate release security controls

---

## Disclaimer

Passing the automated test suite does not prove that Athena is free from security vulnerabilities.

Athena has not undergone an independent professional penetration test or cryptographic audit.
