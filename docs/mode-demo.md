# Demo mode — anonymized screenshots and videos

Demo mode replaces **all** displayed data with fake data, so that screenshots and screencasts
(F-Droid, README, store) can be made without ever exposing your real position, tracks or
waypoints.

The switch itself and the database swap (`cairn-demo.db`) exist **only in debug builds**:
`DemoMode.isAvailable` is `BuildConfig.DEBUG`, hence `false` in release, and R8 strips those
branches. However, `demo/DemoGpsSource.kt`, `demo/DemoRoute.kt` and `demo/DemoDataSeeder.kt` are
also used by the first-launch guided tour (see `docs/onboarding.md`), which must work in release:
these three files therefore remain in the release APK (a few KB). Only `DemoMode` and its process
restart go away. The reproducibility of the F-Droid build is not affected.

## Usage

1. Install the debug build (`./gradlew installDebug`).
2. **Settings → Debug → Demo mode** (section visible in debug only).
3. The app restarts: the database and data sources are chosen when the process starts, not on
   the fly.
4. Take the screenshots. Store screenshots, on the other hand, are generated automatically from
   the same data: see `docs/store-screenshots.md`.
5. Flip the switch back to return to real data — it was never touched.

## What is simulated

| Data | In demo mode |
|---|---|
| Position, altitude, speed, accuracy | Fake looping walk, ~4.5 km/h, around 2400 m, ~3 m accuracy (green indicator) |
| Satellites (`GnssStatus`) | 33 GPS / Galileo / GLONASS / BeiDou / SBAS satellites, slow drift, ~2/3 used in the fix |
| Compass | Heading follows the walking direction, high accuracy (never a calibration warning) |
| GPS state | Always enabled (no "GPS disabled" banner) |
| Database | Separate `cairn-demo.db` file — `cairn.db` is neither read nor written |
| History | 10 fake sessions over 10 months, in unrelated mountain ranges |
| Waypoints | 16 waypoints, one per icon in the picker |
| Records, achievements, level, XP | **Not pre-filled**: `GamificationManager` recomputes them from the sessions/waypoints above, so they cannot contradict the displayed tracks |

All live values are pure functions of the clock: they move continuously (good for video), with no
accumulated drift, and are identical from one launch to the next.

The dataset is tuned so that the gamification screens are interesting to capture: 3 consecutive
days for the streak, 6 distinct months, a session spanning midnight, a session fast enough for the
"cruising speed" condition, a point at −19 m and a summit at 3113 m — while **leaving many
achievements locked**, so that the progress bars stay visible.

## Known limitation

Recording a track still requires the real location permission, even in demo mode: the foreground
service is of type `location`, and Android 14+ refuses to start it without
`ACCESS_FINE_LOCATION`. On a device where the permission is granted, recording works normally and
records the simulated track. The rest of the app (permission screen included) is accessible
without any permission.

## Where it is implemented

- `demo/DemoMode.kt` — the switch, its `SharedPreferences` and the process restart
- `demo/DemoRoute.kt` — the loop geometry shared between live data and history
- `demo/DemoGpsSource.kt` — simulated position, satellites and heading
- `demo/DemoDataSeeder.kt` — the fake dataset

The hook points are deliberately few: `LocationRepository`, `CompassRepository`,
`AppDatabase.databaseName()`, `CairnApplication.onCreate`,
`GamificationManager.startLiveTracking` and `LocationPermissionGate`. No ViewModel or composable
knows that demo mode exists.
