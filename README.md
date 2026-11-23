<div align="center">

# MyCalculator

**Calculate. Organize. Manage.**

An Android calculator that remembers your work: every result is saved with an optional remark, and you can group calculations into projects with a budget to see what you've spent and what's left.

[![Download APK](https://img.shields.io/badge/Download-APK-FF9800?style=for-the-badge&logo=android&logoColor=white)](https://github.com/rangopango/MyCalculator/raw/main/downloads/MyCalculator.apk)

![Version 2.0.0](https://img.shields.io/badge/version-2.0.0-FF9800)
![Android 7.0+](https://img.shields.io/badge/Android-7.0%2B-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-7F52FF?logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-4285F4?logo=jetpackcompose&logoColor=white)
![Material 3](https://img.shields.io/badge/Material%203-FF9800)

</div>

## Download and install

1. On an Android phone (7.0 or newer), tap **[Download APK](https://github.com/rangopango/MyCalculator/raw/main/downloads/MyCalculator.apk)** (7.9 MB).
2. Open the downloaded file. If Android asks, allow your browser to **install unknown apps**.
3. Tap **Install**, then open **my calculator**.

The APK isn't on Google Play, so Android may show a warning before installing. If you installed an earlier test build from Android Studio, uninstall it first. Its SHA-256 checksum is `877F1D773DF2BF32BDE16D6A02E0C21A38AD8002442C9B0FDF26218BDF4134A5`.

## Features

| Calculator | History | Projects |
| --- | --- | --- |
| Add, subtract, multiply and divide, with correct order of operations | Every result is saved automatically with date and time | Create a project with a budget, such as Travel or Shopping |
| Chain several numbers in one calculation | Add a remark to any calculation | Save calculations to a project to track spending |
| Decimals, percentage and sign change | Tap a record to edit its remark or project | See budget, spent and remaining at a glance, with a progress bar |
| Backspace and clear | Recently Deleted bin keeps records for 30 days, with restore | Share a project summary through any app |
| Shows Error for invalid input, such as division by zero | Clear all history in one tap, with confirmation | Budgets accept Indian (10,00,000) and international (100,000,000) formats |

All data is stored on the phone. Nothing is uploaded anywhere.

## Build and run

Requirements: a recent version of Android Studio, which includes the JDK and the Android SDK manager.

```
git clone https://github.com/rangopango/MyCalculator.git
```

1. Open the folder in Android Studio and let Gradle sync.
2. Connect an Android phone (7.0 or newer) or start an emulator.
3. Click **Run**.

Or from the command line:

```
./gradlew :app:assembleDebug
```

## Project structure

Source is in `app/src/main/java/com/sabil/calculator`:

| File | Contents |
| --- | --- |
| `MainActivity.kt` | Screens, the expression parser, and saving of history, projects and the bin |
| `ui/theme/` | Colours, typography and theme |

The app icon is in `app/src/main/res/mipmap-*`.

## Built with

- Kotlin
- Jetpack Compose and Material 3
- Android Studio

## Developer

**Masum Mahmud** · GitHub [@rangopango](https://github.com/rangopango)
