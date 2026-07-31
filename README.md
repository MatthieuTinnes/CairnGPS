# CairnGPS

![Logo CairnGPS](app/src/main/res/mipmap-hdpi/ic_launcher.webp)

Android app displaying real-time GPS/GNSS information, with a gamification
layer (achievements, records, levels) to make it fun.

Designed for outdoor use (hiking, travel): readable in bright sunlight,
battery-efficient, and fully functional offline.

## Features

- **Position**: GPS coordinates (decimal and DMS), altitude, speed,
  horizontal/vertical accuracy, real-time updates.
- **Compass**: magnetic and true heading (locally computed declination),
  navigation to a waypoint.
- **Satellites**: raw GNSS status (`GnssStatus`) — satellites in view/used,
  2D sky plot and 3D globe, constellation info (GPS, GLONASS, Galileo,
  BeiDou, QZSS, IRNSS).
- **Logbook**: waypoints (with icons) and recorded tracks (sessions),
  altitude and speed profiles, GPX export.
- **Track recording**: background tracking via a foreground service
  (distance, duration, average/max speed, elevation gain/loss).
- **Gamification**: unlockable achievements, personal records, levels based
  on accumulated XP.
- **Backup/restore**: export/import all app data into a single file.
- Light and dark themes, French and English.

## Tech stack

- **Kotlin** + **Jetpack Compose** (Material 3)
- **MVVM architecture**: `Repository` (data access) → `ViewModel`
  (state exposed via `StateFlow`) → `Composable` (display, no business logic)
- **Coroutines + Flow** for async operations
- **Room** (via KSP) for persisted business data (waypoints, tracks,
  achievements, records), **DataStore Preferences** for user settings
- **LocationManager / `GPS_PROVIDER`** — deliberately not FusedLocationProvider,
  since the app needs raw satellite data (`GnssStatus`)
- minSdk 26 · targetSdk 37 · compileSdk 37

## Requirements

- Recent Android Studio (AGP 9.3, Kotlin 2.4)
- JDK 17+ (the JBR bundled with Android Studio works)
- A device or emulator with a GPS chip to test location features
  (an emulator can simulate a mock position/route)

## Build

```bash
./gradlew assembleDebug
```

On Windows (PowerShell), make sure `JAVA_HOME` points to the Android Studio
JBR before calling `gradlew`:

```powershell
$env:JAVA_HOME = "<path to Android Studio>\jbr"
.\gradlew.bat assembleDebug
```

## Tests

```bash
./gradlew test
```

## Project structure

```
app/src/main/java/app/matthieu/cairngps/
├── data/         Repositories, Room entities, DAOs, data models
├── domain/       Pure business logic (geodesy, formatting, gamification)
├── service/      Foreground service for track recording
└── ui/           Compose screens and ViewModels, by feature
    ├── location/     Position screen (home)
    ├── compass/      Compass
    ├── satellites/   GNSS status, 3D globe, constellation info
    ├── history/      Logbook (waypoints, tracks)
    ├── waypoints/
    ├── gamification/ Achievements, records, levels
    ├── profile/      Profile hub
    ├── settings/
    └── permission/   Location permission request screen
```

The Room database (`AppDatabase`, file `cairn.db`) and the repositories are
instantiated once and exposed by `CairnApplication`.
