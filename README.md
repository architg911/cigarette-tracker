# Cigarette Tracker

A minimal Android app to track daily cigarette consumption.

## Features

- Tap **+** to log a cigarette; timestamps are recorded for each entry
- Tap **undo** to remove the last entry (removes its timestamp too)
- Count resets automatically at midnight each day
- **Streak counter** — tracks consecutive days at or under your goal
- **Daily goal** — set a max and see a live progress bar and remaining count
- **7-day history** bar chart on the home screen
- **Trends page** — swipe left/right to travel back in time; 7-day or 30-day window
- **Journal notes** — tap any bar on the trends chart to add a note for that day; noted bars get a subtle dot indicator
- **History editor** — view, edit, or add entries for any date with rotary-style pickers for count and goal
- Landscape orientation supported
- Dark, minimal UI

## Tech Stack

- Kotlin
- Jetpack Compose
- Material 3
- SharedPreferences for local storage

## Requirements

- Android 7.0 (API 24) or higher

## Build & Run

1. Clone the repo
2. Open in Android Studio
3. Run on a device or emulator
