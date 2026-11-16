# MoneyTracker 💳

A modern, private, 100% offline Android expense & financial tracker that automatically tracks your spending by parsing bank and UPI transaction SMS messages (HDFC, SBI, ICICI, Axis, Bank of India, HSBC, Cash, and more).

Zero internet permissions. AES-256 SQLCipher encrypted. No telemetry or ads.

---

## ✨ Features

- **⚡ Automatic SMS Parsing:** Seamlessly parses incoming and past bank SMS messages without storing raw text.
- **🛡️ 100% Offline & Private:** Built with **zero** internet permissions (`android.permission.INTERNET` is completely absent). Your data never touches any server.
- **🔐 AES-256 Hardware-Backed Encryption:** Local database is encrypted with SQLCipher using a key stored safely in the **Android Keystore**.
- **🔒 App Lock (Biometric & PIN):** Optional fingerprint, face unlock, or device PIN protection on startup.
- **📌 Sticky Total Spent Card:** Dynamic spending card that sticks and compresses neatly at the top as you scroll through transactions.
- **🎨 Modern Fluid UI & Animations:** Built with Jetpack Compose & Material 3, tactile bouncy touch physics, rolling money counters, and ambient shimmering accents.
- **🌓 Dark & Light Theme Support:** Instant toggle between Dark, Light, or System default theme in Settings.
- **💵 Manual Cash Entries:** Add cash transactions or manual customer receipts on the fly via the quick-add floating action button.
- **🏦 Bank Filters:** Filter your transactions and spending totals by bank (e.g., Bank of India, SBI, HDFC, Axis, Cash).
- **🚫 Anti-Screen Capture:** Uses `FLAG_SECURE` to block screenshots and hide account balances in Android's recent apps switcher.

---

## 🔒 Security & Privacy Guarantees

1. **Zero Internet Access:** The application does **not** declare `android.permission.INTERNET`. It is physically impossible for the app to send data anywhere.
2. **Encrypted Storage:** All transactions are stored in a SQLCipher Room database (AES-256). Passphrases are generated on-device inside the hardware-backed Android Keystore.
3. **No SMS Body Retention:** SMS text is parsed strictly in memory and immediately discarded. Only structured numbers (amount in paise, debit/credit, party, account last 4) are retained. A one-way SHA-256 deduplication key prevents duplicate entries.
4. **No Cloud Backup:** Backup and data extraction are explicitly disabled (`allowBackup=false` and `data-extraction-rules`).
5. **No Trackers or Ads:** Zero third-party analytics, tracking libraries, or advertising SDKs.

---

## 🏗️ Architecture & Tech Stack

```
┌──────────────────────────── UI (Jetpack Compose) ────────────────────────────┐
│ LockScreen · PermissionScreen · HomeScreen (Today/Month) · SettingsScreen     │
│                 ▲ collects StateFlow            │ user events                 │
│            ViewModels (Home, Settings)  ◄───────┘                             │
└──────────────────────────────┬───────────────────────────────────────────────┘
                               │ calls
┌──────────────────────────────▼─────────────── Data layer ───────────────────┐
│ TransactionRepository                                                         │
│   ├─ SmsParser            (pure Kotlin, zero Android dependencies, 100% JVM)  │
│   ├─ SmsInboxReader       (ContentResolver import for past 90 days)           │
│   ├─ UserPreferences      (SharedPreferences for theme, lock, profile name)   │
│   └─ TransactionDao + AppDatabase (Room + SQLCipher)                          │
│ DbKeyProvider (Keystore-backed passphrase management)                         │
└──────────────────────────────▲───────────────────────────────────────────────┘
                               │
┌──────── System entry points ─┴─────────────┐
│ SmsReceiver (BroadcastReceiver)            │
│   └─► enqueues ProcessSmsWorker            │
│ ImportInboxWorker (initial setup / sync)   │
└────────────────────────────────────────────┘
```

- **UI:** Jetpack Compose + Material 3, single-activity architecture (`MainActivity`).
- **Amounts:** Handled strictly in **paise as `Long`** to prevent floating-point calculation errors. Formatted in Indian numbering notation (`₹1,25,000.50`).
- **Dependency Injection:** Lightweight manual DI via `AppContainer` initialized in `MoneyTrackerApp`.

---

## 🚀 Building & Running

### Prerequisites
- JDK 17
- Android SDK with platform `android-34` and build-tools `34.0.0`
- Gradle 8.7 (or use `./gradlew`)

### Verification Commands
```bash
# Run all unit tests (Parser, DAO, ViewModels, Preferences)
./gradlew test

# Run Android Lint
./gradlew lintDebug

# Build Debug APK
./gradlew assembleDebug

# Build Optimized Release APK (R8 minified)
./gradlew assembleRelease
```

---

## 🧩 Adding a New Bank's SMS Format

The parser in `app/src/main/java/com/example/moneytracker/data/sms/SmsParser.kt` is pure Kotlin and modular:

1. **Sender Header:** Indian banks use TRAI alphanumeric headers (e.g. `AX-HDFCBK`, `JD-BOIIND`, `VM-SBIUPI`). Sender IDs are validated against `^[A-Za-z]{2}-[A-Za-z0-9]{3,8}$`.
2. **Regex Patterns:** Extend `AMOUNT_REGEX`, `DEBIT_WORDS`, `CREDIT_WORDS`, or `ACCOUNT_REGEX` if a new bank uses unconventional grammar.
3. **Unit Tests:** Add synthetic test cases in `app/src/test/java/com/example/moneytracker/SmsParserTest.kt` (never commit real personal SMS) and run `./gradlew test`.

---

## 📱 Sideloading Note

When sideloading the APK directly on Android 13+:
- Android may disable sensitive permissions by default for sideloaded apps.
- If the SMS permission switch is greyed out: Go to **Settings → Apps → MoneyTracker → ⋮ (top right) → Allow restricted settings**, then grant SMS permission.

---

## 📄 License

Distributed under the Apache 2.0 License. See `LICENSE` for more information.
