# Architecture: Sahra Split (a rebuild of GWM Split's core features)

Read from: `replica/recon.md`, `replica/features.csv` (67 rows), and the existing code in
`Abusamiha3393/gwm-themes/split/` at commit `1a8acff` ("new clean app, step 0").
Date: 2026-11-03

The original needs a native layer (FDBus, curl, OpenSSL) and a bundled speech engine. Sahra Split
already reads the car through the `gwm_adapter` binder service in plain Kotlin. Keep that: it is
smaller, has no native build, and fits the GitHub-Actions-only build.

## Stack

This is an on-device Android app, not a web app, so the pack's default web stack does not apply.
Every row keeps what `split/` already uses unless there is a reason in the "why".

| layer | choice | why |
| --- | --- | --- |
| language / build | Kotlin 1.9, AGP 8.5, JDK 17, minSdk 26, targetSdk 34; built only on GitHub Actions | already in place; nothing compiles locally |
| UI on the head unit | framework Views drawn in code, no AndroidX | already the rule in PRODUCT.md; keeps the APK small and avoids AndroidX on old head-unit builds |
| UI on the cluster | HTML themes in a WebView, `common.js`, `window.onSahraData(d)` | already working; themes are easy to add and preview with Playwright |
| car data | `gwm_adapter` binder (`IGwmAdapterService`, transaction 1, string keys), polled 1 Hz | already working for 16 keys; no FDBus or native code needed |
| storage: settings | SharedPreferences `split` + presets as JSON | small, already used |
| storage: logbook | one JSON file `logbook.json`, atomic write via `.tmp` + rename, capped lists | 30 trips / 30 refuels / 20 reminders is tiny; SQLite would add code for no gain. Revisit only if history grows past ~1,000 rows |
| windows on the head unit | `ActivityOptions.setLaunchBounds` + windowing mode by reflection | already in `AppLauncher.kt` |
| app on the cluster | `ActivityOptions.setLaunchDisplayId(clusterDisplay)` | the only public way to move an app (Navi) to display 2; see "parts that bite" |
| overlays | `TYPE_APPLICATION_OVERLAY` from foreground services | already used for ball, panel, speed badge, cluster theme |
| Back / PiP close | AccessibilityService `performGlobalAction` | already `BackService` |
| media | NotificationListenerService + MediaSession controls | already `MediaListenerService` |
| weather | a keyless public forecast API with the user's location, cached in prefs | already `Weather.kt`; check which API before shipping (OpenWeather needs a key) |
| voice (phase 2) | Android `SpeechRecognizer` first, offline Vosk Arabic model only if the head unit has no recogniser | no bundled 10 MB engine unless needed |
| updates | in-app download of the GitHub release APK + PackageInstaller | already `Updater.kt`; same signing key forever |
| payments / auth / email | none | PRODUCT.md: built for its users, not for sale. No accounts, no server |

One process, no server, no network except weather and update checks.

## Data model

No database tables; three stores. Times are epoch milliseconds UTC; display in the head unit's
zone. Unknown numbers are `NaN` in Kotlin and `null` in JSON, never 0.

```
VehicleState (memory, 1 Hz)   exists: speed, rpm, fuelPercent, rangeKm, odometerKm, coolantTemp,
                              outsideTemp, insideTemp, batteryVoltage, steeringAngle, pitch, roll,
                              compass, altitude, live, demo
                              add: gear (P|R|N|D|S|M + number), tires[4] (kPa), tireWarn[4],
                                   cruiseOn, cruiseSpeed, driveMode (normal|sport|eco|offroad),
                                   fuelLiters (derived: fuelPercent × tankLiters)
                              rule: store tyre pressure in kPa, convert for display once

Prefs (SharedPreferences "split")
                              exists: demo, autoSplit, autostart, ball(+x,y), floatingPanel,
                              windowMode, clusterOn, clusterTheme, clusterAccent, clusterTop/
                              Bottom/Side, clusterDebug, speedMax, rpmMax, showPanel, clockStyle,
                              presetIndex, autoDayNight, dayPreset, nightPreset, lat, lon,
                              weatherJson, weatherAt, tankLiters, refuel*, lastFuel,
                              lastUpdateCheck, autoUpdateCheck
                              add: speedFactor (default 1.0, e.g. 1.045), warnSpeed (220),
                                   warnRpm (8000), warnTireKpa (210), pressureUnit (bar|psi|kPa),
                                   rhd, carModel (h9|h3|f7|t500|auto), navClusterMargins{l,r,t,b},
                                   keyMap (wheel key code -> action), schemaVersion

Preset (JSON in Prefs, 7 slots)
                              exists: name, navApp, musicApp, ratio (0.3–0.7), side, panelWidth, page
                              add: clusterMode (theme | nav-window | nav-full), mediaOnCluster

Logbook (filesDir/logbook.json)
                              exists: trips[≤30], refuels[≤30], reminders[≤20], stats, current
                              add: version; refuel.liters and avg consumption since last refuel

SettingsBackup (export file)  { schemaVersion, prefs{...}, presets[7], logbook? }
                              written to Download/ as JSON; import validates schemaVersion first
```

Hard rules the recon found:

- A key the car does not send stays `null` on screen ("—"), never a fake 0. The original shows 0
  for fuel and mileage until the value changes; that is the bug to avoid.
- Only one writer per store: the vehicle thread writes the logbook; UI calls go through the
  `@Synchronized` Logbook methods.

## Internal interfaces (the "API")

### Car data: `gwm_adapter` keys

| key | field | status |
| --- | --- | --- |
| `car.basic.vehicle_speed`, `ivivehiclespeed` | speed | read |
| `car.basic.engine_speed` | rpm | read |
| `car.basic.remain_fuel_percentage`, `remain_odometer`, `total_odometer`, `odometer` | fuel, range, odo | read |
| `car.basic.coolant_temp`, `outside_temp`, `inside_temp`, `battery_voltage`, `steering_wheel_angle` | temps, volts, steering | read |
| `car.off_road_info.pitch_angle`, `roll_angle`, `compass_direction`, `altitude` | off-road | read |
| `car.basic.gear_status` | gear letter (raw; H9: 2 = D, 3 seen when stopped) | **found 2026-11-04**, mapped by the driver's one-time learning (Gears.kt) |
| `car.basic.current_gear` | gear number (0 when stopped) | **found** |
| `car.basic.tpms_status` | 8 numbers: psi, °C for FL, FR, RL, RR | **found**; order to confirm against the stock display |
| `cur_journey_odometer`, `accumulated_odometer`, `avg_fuel_consumption`, `battery_power_level`, `baropressure` | journey, trip, L/100 km, battery %, hPa | **found** |
| cruise on / set speed | cruise | **not found** on the H9 (cruise_*, acc_* silent) |
| drive mode | driveMode | **not found** (`tod.systemoper_mode = 4` unexplained) |
| pitch, roll, compass, altitude | off-road | silent on the H9 |

Displays on the H9: 0 = 1920×1080 head unit, 1 and 2 = 1920×720 (2 is the cluster: Yandex opened there), 3 = 480×240.

"Find" means: build the key probe (M1) and read candidate keys on the user's own H9. Never take
key names from the original's binary.

### Cluster theme contract (`window.onSahraData(d)`)

Extend the payload that `ClusterPayload.build` already sends with `gear`, `gearNo`, `tires[]`,
`tireWarn[]`, `cruise{on,speed}`, `driveMode`, `fuelLiters`, `warn{speed,rpm}`, and
`units.pressure`. Old themes ignore unknown fields, so this is backward compatible.

### Host calls from themes

Exists: `media(cmd)`, `themes()`. Stubs to fill: `openApp(pkg)`, `floatApp()`, `openSettings()`,
`nextTheme()`.

### Services and entry points

| component | does | started by | flow |
| --- | --- | --- | --- |
| `HomeActivity` (HOME) | split home, presets bar | launcher / Home key | F01, F03 |
| `OverlayService` | ball, menu, panel, speed badge | boot, settings | F01, F02 |
| `ClusterService` | theme on display 2 | boot, settings | F04 |
| `NavCluster` (new, in `AppLauncher`) | launch Navi on display 2 with bounds; bring it back | ball button, preset clusterMode | F02 |
| `BackService` (accessibility) | Back, PiP close, wheel keys (new) | user enables once | F09 |
| `MediaListenerService` | now-playing | user enables once | — |
| `KeyProbeActivity` (new) | list adapter keys and live values, copy to clipboard | Settings > About, long-press | M1 |
| `BootReceiver` | start services | BOOT_COMPLETED | — |

### Background loops

| name | schedule | does |
| --- | --- | --- |
| vehicle loop | 1 Hz (5 Hz in demo) | read keys, update `VehicleState`, feed History, Logbook, listeners |
| weather | every 30 min while online | refresh cache |
| day/night | on sunrise/sunset from `Sun.kt` | switch preset, brightness (phase 2) |
| reminders | every 10 min | due by date or odometer → notification |
| update check | daily if enabled | compare release number |

## The parts that bite

- **Navi on the cluster.** `setLaunchDisplayId` to display 2 may be refused, may lose the route,
  and the map app may draw at the wrong density. Test Google Maps and Waze on the H9 first. Keep
  the route by moving the existing task instead of starting a new one, and disable the map app's
  background routing if it restarts. Navi margins per display (`navClusterMargins`) cover the
  stock frame.
- **Unknown keys per model and firmware.** The H9 runs firmware 1791 per the recon screenshots.
  Keys can differ on H3, F7 and T500. Read every key in a try block; `carModel = auto` picks a key
  set from what answers.
- **Binder transaction id.** `TX_GET_VALUES = 1` works today. A firmware update could change it;
  the probe screen should show "adapter not answering" plainly, and the app falls back to demo
  with a visible badge.
- **Relaunch cost.** Windows cannot be resized live, so switching presets restarts apps. Only
  relaunch the side that changed; keep the panel in-process.
- **Accessibility gets switched off** after updates on some head units. Check on every Home
  resume and show one clear prompt.
- **Wheel keys.** Not all keys reach apps. Map only what arrives at the accessibility service;
  keep `*` + key free for the stock system.
- **Units.** Pressure in kPa internally, one display unit everywhere (the original shows psi with a
  bar limit). Speed factor applies to displayed speed and trip distance, not to the odometer.
- **Night.** Nothing animates at night beyond needles; brightness changes ease over 2 s.
- **Ignition off / boot.** Services restart on boot; the logbook closes a trip after 2 min engine
  off or 10 min idle (already coded); a crash mid-trip is recovered from `current`.
- **Updates.** Same signing key forever, or every user has to uninstall and lose settings. Export
  settings before risky updates.
- **Not built at all:** TBox / SBox control, hotspot tricks, "invisible mode" for dealers.

## Build order

What exists is marked ✓. Each milestone lists screens (recon S-IDs), stores and components.

1. **M1 Vertical slice: the core loop on the real car**
   ✓ S01 home + S02 presets bar + split windows (`AppLauncher`)
   ✓ S09 cluster theme on display 2 (`ClusterService`)
   - new `KeyProbeActivity`: confirm gear, tyre, cruise and drive-mode keys on the H9
   - new `NavCluster`: Navi to display 2 and back (S05 Navi button, S08), windowed and full
   Done when: on the H9, one press puts Google Maps on the cluster with the route intact, while the
   head unit shows two apps.
2. **M2 Must-haves**
   - S09 payload + one theme with gear, tyres, cruise, drive-mode tint, coolant, ODO/trip, fuel
     L/% bar, range, volts; stock ADAS view kept clear in the centre
   - S07 preset editor gains `clusterMode`
   - S29 permission onboarding (overlay, accessibility, notifications, location, install)
   - F09 wheel keys via `BackService`
   - Arabic-only labels everywhere (no Russian or English leftovers)
3. **M3 Should-haves**
   - S06c/S06d settings: speed factor, tank, warning thresholds, pressure unit, RHD, model
   - S06e settings backup export / import
   - S10 right-well info page, S20 auto brightness, S21 day/night preset (✓ exists), S30 left
     rail with climate mini-widget, media on cluster
   - S14 Split info pages and S17 trips with average consumption (✓ partly in `Pages.kt`,
     `LogActivity`)
4. **M4 Could-haves**
   - S18 refuels with litres, S19 stats, S15 weather fullscreen, S13 user theme import, S24 voice
5. **M5 Fixes from `/replica-entrepreneur`** once it has read what GWM Split users complain about.

## Summary

- Stack: Kotlin framework-View app + HTML cluster themes, car data from `gwm_adapter`, JSON
  storage, no server.
- Stores: 3 (Prefs, Presets, Logbook) + 1 export format. Interfaces: 16 adapter keys read, 4
  groups to find; 8 components; 5 background loops.
- Riskiest: Navi on the cluster without losing the route; finding gear/tyre/cruise keys on each
  model; steering-wheel keys reaching the app.
- Next: `/replica-design`.
