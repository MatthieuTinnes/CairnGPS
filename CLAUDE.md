# CLAUDE.md

## Overview

Android app displaying real-time GPS/GNSS information, with a **gamification**
layer (achievements, records) to make it fun.

Target audience: outdoor use (hiking, travel). The app must stay readable in
bright sunlight and be battery-efficient.
The app works in both dark and light theme.
Do not launch the app after changes, only verify that the gradle build passes.
## Tech stack

- **Language**: Kotlin
- **UI**: Jetpack Compose + Material 3
- **Architecture**: MVVM
    - `Repository`: access to data sources (GPS, sensors, storage)
    - `ViewModel`: exposes state via `StateFlow`, has no knowledge of Android UI
    - `Composable`: observes `StateFlow`, no business logic
- **minSdk**: 26 — **targetSdk**: latest stable version
- **Async**: Coroutines + Flow. No callback exposed outside of Repositories.

## GPS constraints — IMPORTANT

- **Use `LocationManager` with `GPS_PROVIDER`**, NOT FusedLocationProvider.
  Reason: the app needs raw satellite data (`GnssStatus`), which Fused does
  not expose. Never replace with Fused, even if it seems simpler.
- Always go through `LocationRepository` for positions; no direct access to
  `LocationManager` from a ViewModel or a Composable.

## Permissions

- `ACCESS_FINE_LOCATION`: requested at runtime, with an explanation screen if
  denied.
- Declare each permission in the Manifest at the moment the feature
  introduces it, not before.

## Data model

`LocationData` (central data class):
- `latitude`, `longitude` (Double)
- `altitude` in meters
- `vitesse` (speed) in **m/s** (source) — km/h conversion happens at display time
- `precisionHorizontale` (horizontal accuracy) in meters
- `precisionVerticale` (vertical accuracy) in meters if available
- `timestamp`

## Display conventions

- Coordinates: decimal degrees (6 decimals) AND degrees/minutes/seconds.
- Altitude: integer, in meters.
- Accuracy: color indicator — green `< 5 m`, orange `5–15 m`, red `> 15 m`.
- **No fix available**: display dashes `--`, never `0`.
- Smooth updates, no flickering when values change.

## Storage

Two complementary mechanisms, each for a specific use:

- **Room**: THE app database for all **persisted business data**
    - Single `AppDatabase` (singleton, built via `AppDatabase.getInstance`), exposed
      by `CairnApplication`. File name: `cairn.db`.
    - One entity per table (`@Entity`), one DAO per entity (`suspend` methods for
      writes, `Flow` for observable reads), and one `Repository` per domain that
      exposes the DAO. ViewModels/Composables never touch the DAO or
      `AppDatabase` directly.
    - Adding a new persisted feature = add its entity + DAO to `AppDatabase`,
      bump `version`, and provide a migration. Do not create a separate database.
    - Annotation processor: **KSP** (not kapt).
- **DataStore (Preferences)**: reserved for simple **user preferences**
  (e.g. coordinate format), not business data.


## Quality

- Handle edge cases: no fix, permission denied, sensor unavailable.
- Comment only what isn't obvious (GPS_PROVIDER choice, elevation gain
  calculations, conversions). Do not add a comment when modifying code for a fix.
