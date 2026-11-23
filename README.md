<div align="center">

# MyCalculator

**Calculate. Organize. Manage.**

An Android calculator that remembers your work: every result is saved with an optional remark, and you can group calculations into projects with a budget to see what you've spent and what's left.

![Android 7.0+](https://img.shields.io/badge/Android-7.0%2B-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-7F52FF?logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-4285F4?logo=jetpackcompose&logoColor=white)
![Material 3](https://img.shields.io/badge/Material%203-FF9800)

</div>

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
