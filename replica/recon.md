# Recon map: GWM Split (Android, GWM head units)

Scope: the core loop (split head-unit screen + navigation on the instrument cluster + cluster themes and live vehicle data), then the "Split info" extras (trips, refuels, stats, weather, voice) as phase 2.
For: Sahra Dash, the user's own GWM head-unit app, Gulf / Arabic market.
Date: 2026-11-03
Version studied: 6261015 (current per the public site)

Clean-room note: built only from the developer's public site, public wiki index and public changelog. The APK was identified (name, version) and not decompiled; no code, assets, theme art or copy are to be reused. Drive2 posts and YouTube videos were not readable from here and are not used.

## Sources

| # | source | URL | notes |
| --- | --- | --- | --- |
| 1 | marketing site + FAQ + pricing | https://gwm-split.ru/ | features, free vs full, install permissions, FAQ |
| 2 | changelog (≈90 versions) | https://gwm-split.ru/history/ | the richest source: every feature area and when it landed |
| 3 | wiki index | https://gwm-split.ru/wiki | sections only: install, versions, price, FAQ, SBox, TBox, hutCfgEdit, partners |
| 4 | installer page | https://teyes-sibir.ru/work/ustanovka-gwm-split-tank-500-novosibirsk | third-party summary of the pitch |
| 5 | developer posts (not read) | https://www.drive2.ru/l/689148600596305885/ , https://www.drive2.ru/l/685977609061799125/ , https://www.drive2.ru/l/692011453997129419/ | blocked from here; open on a computer to add screenshots |
| 6 | Telegram channel (not read) | https://t.me/t500_split | release notes, user questions |
| 7 | the user's own install (Haval H9, v6261030 full, Arabic build) | `replica/screens/` | 5 settings tabs + open-source credits screen, 2026-11-03. Still missing: S01 home, cluster themes, Split info, trips/refuels |
| 8 | in-app open-source credits | `replica/screens/S31-*.jpg` | public credits list; tells the stack without opening the binary |

Version on the car (6261030) is newer than the site's (6261015). The About screen shows the Car ID; keep it out of anything shared.

## Core loop

Run navigation (or any app) on the instrument cluster while the head unit shows two apps side by side, switched in one press via presets, with live car data drawn on the cluster in a chosen theme.

## Screens

Inferred from the site and changelog. States marked "?" need a screenshot from the car to confirm.

| ID | screen | route / how to reach | purpose | key components | states seen |
| --- | --- | --- | --- | --- | --- |
| S01 | Split home (head unit) | app start / as default launcher | two app windows + bottom menu | window frames, bottom menu bar, top status bar | filled; launcher mode; invisible mode |
| S02 | Bottom menu | always visible on S01 (can be disabled) | switch presets, swap windows, open app list, settings | preset chips, swap, menu, back | normal, reduced height, hidden |
| S03 | Split ball | floating button (when enabled) | opens menu when bottom bar is off; notifications; vertical menu option | floating ball, radial/vertical menu | idle, expanded, notification badge |
| S04 | App list | Menu button | launch any installed app into a window or fullscreen | app grid | filled, empty? |
| S05 | Floating Back / Navi / PiP buttons | overlays on top of any app | system back, send Navi to cluster, close PiP | draggable floating buttons | position saved |
| S06 | Settings shell | menu, or hold wheel "*" + any key | 5 bottom tabs (General, Display, Advanced 1, Advanced 2, About), "Preset: n" top-left, Help top-right, Close bottom-right | tab bar, titled bordered panels | seen |
| S06a | Settings > General | tab 1 | per preset: music app + autostart, navigation app, "Alice always", border offset (1000); launch manual / auto; theme picker (‹ › arrows, user theme "U2#"); background picker ("Classic") | app pickers with icon + package, ‹ › steppers, toggles, number field | seen |
| S06b | Settings > Display | tab 2 | 4 columns of toggles. Head unit: window frames, show Split info, Split info on right, two Split info windows, cruise info, drive-mode cluster tint, Split ball, show menu. Buttons: launch program, Back button, Navi-to-cluster button, PiP close button, adjust Yandex map borders. Cluster data: distance, trip, day, tires, reduce menu height. Cluster: use cluster, speedometer on right, avg consumption, show gauges, media info, speed/RPM digits | toggle grid | seen |
| S06c | Settings > Advanced 1 | tab 3 | Wi-Fi hotspot (name, password, create); open Android settings, check updates, reboot TBox; model picker (Haval H9), RHD, speed multiplier (1.045), TBox control; non-standard tank, tank capacity (80) | text fields, buttons, ‹ › stepper, toggles | seen |
| S06d | Settings > Advanced 2 | tab 4 | sub-pages: Yandex Music, day/night, auto brightness, Split info, messages, Split ball, voice control, gear modes; toggles: firmware 1791, don't check updates, disable hotspot; Navi window margins (L/R/T/B for cluster and head unit, Default); warning limits: speed 220, RPM 8.0, tire 2.10 bar | button grid, number grid | seen |
| S06e | Settings > About | tab 5 | logo, version + edition, regional reseller note, Telegram QR; save settings, load settings, Help; climate, third-party software; head-unit firmware, model, Car ID | buttons, QR | seen |
| S07 | Preset editor | S06 | per preset: left/right apps, window sizes, cluster screen, extra screen, media info | app pickers, sliders | 2 slots free / 5–7 full |
| S08 | Cluster output: Navi window | cluster | navigation on cluster, windowed or fullscreen, inside the theme | Navi frame, wells | windowed, fullscreen, hidden messages moved to top |
| S09 | Cluster themes | cluster | gauge "wells" with speed, RPM, fuel, temp, cruise, tires, drive mode tint | wells, needles/digits, icons | Normal/Sport/Eco tint, warnings yellow/red, wells hidden (animation); driving state seen (`screens/S09-cluster-theme-driving.jpg`) |
| S10 | Cluster info (right well) | single/double Back or right joystick scroll | extra info page in the right well, auto-close 15 s | info card | open, scrolled |
| S11 | Theme picker | S06 | choose cluster theme, preview immediately | thumbnails | selected |
| S12 | Styles | S06 | backgrounds incl. custom images | image picker | built-in, custom |
| S13 | User theme slots / import / help | S06 | import and store custom cluster themes | slot list, import flow | empty slot, filled, import error? |
| S14 | Split info | extra screen | dashboard pages: two clocks (skins), fuel, music, off-road, weather; volume by vertical swipe; can run two instances | pages, swipe | loading weather, refreshed |
| S15 | Weather fullscreen | from S14 | forecast | cards, refresh | loading, error? |
| S16 | Charts fullscreen | from S14 | (not in changelog; name assumed) | charts | ? |
| S17 | Trips | Split info / stats | trip log with avg consumption | list rows | empty, filled |
| S18 | Refuels | Split info / stats | refuel log with avg consumption per refuel | list rows, add? | empty, filled |
| S19 | Statistics | S06 / Split info | totals since reset / since refuel | stat tiles | zero until data changes |
| S20 | Auto brightness settings | S06 | head unit + cluster brightness, day/night levels | sliders | day, night |
| S21 | Auto preset settings | S06 | switch preset by day/night | toggles, time | on/off |
| S22 | Drive mode settings | S06 | well colouring per Sport/Eco | toggles | on/off |
| S23 | Cluster messages settings | S06 | where car warnings appear, turn off well/bottom messages | toggles | — |
| S24 | Voice settings | S06 | voice control | toggles, language? | mic denied? |
| S25 | Reminders | S06 (assumed) | (not in changelog) | list | ? |
| S26 | About + wheel button help | S06 | version, head-unit PCode, RAM, free disk, button help | text | — |
| S27 | Activation / Pay | S06 | buy full version, enter code from email | email field, code field | trial (30 days), free, full, wrong code |
| S28 | Update | auto / S26 | in-app update over internet, "don't check" | dialog, progress | none found, downloading, needs install permission |
| S30 | Left rail | always on the head unit | apps grid, car, navigation shortcut, climate widget (cabin temp + fan bars), music, home | icon rail, climate mini-widget | seen |
| S31 | Open-source credits | About > third-party software | licence list | list with links | seen |
| S32 | Speed bubble | floating, bottom centre | live speed (km/h) over any screen | round badge | seen (0 km/h parked) |
| S33 | Status bar | top | outside temp, time, BT, signal | — | seen |
| S29 | Permission onboarding | first run | phone, files, overlay, install, accessibility, ADB grant | step list, open-settings buttons | each missing / granted |

### S09 as seen while driving (Haval H9, one theme)

Layout: two round wells left and right, the car's own ADAS road view left untouched in the centre, a thin bar across the top.

| zone | what it shows |
| --- | --- |
| left well | speed needle 0–220 with a green swept arc up to the needle, big digital speed (59) in the hub, ODO and Trip under the hub, segmented fuel bar (red→green) with litres and percent (71 L / 100%) |
| right well | RPM needle 0–8 ×1000 with green arc, digital RPM (1.6) in the hub, four tyre pressures around a car icon (31–33, so psi on the display while the warning limit is set in bar), coolant temp (93 °C), segmented drive-mode bar labelled NORMAL |
| top bar | clock, Wi-Fi, internet globe, gear + ratio (D 6), outside temp (34 °C), cabin temp (25 °C) |
| beside the wells | cruise icon with set speed (58), lighting icons, auto-hold icon (amber), lane-keep icon |
| bottom corners | fuel range (648 km) left, battery voltage (13.8 V) right, joystick hint icon |

Observations for the build: needles + digits together; the arc fill is the one big colour change per well; units are mixed (psi tyres vs bar limit); the stock ADAS view must stay visible, so wells sit at the sides only.

## Flows

```
F01 Show two apps on the head unit
    S01 -> S02 preset chip
    happy path clicks: 1
    edge: app refuses multi-window, window too tall for H3, slow relaunch on mode change

F02 Send navigation to the cluster
    S01 -> S05 Navi button (or preset with cluster = Navi)
    happy path clicks: 1
    edge: route lost on move (background routing), car messages hidden under Navi, Navi on cluster at startup

F03 Configure a preset
    S02 menu -> S06 -> S07 pick apps, sizes, cluster screen -> save
    happy path clicks: ~6
    edge: preset limit (free 2), day/night auto switch overriding manual

F04 Change cluster theme / style
    S06 -> S11 pick -> preview on cluster
    happy path clicks: 3
    edge: theme without wells + Navi fullscreen, right-hand-drive

F05 Import a user theme
    S06 -> S13 slot -> import file -> apply
    happy path clicks: ~4
    edge: bad file, storage permission missing

F06 Log a refuel / read trips
    S14 -> S18 (or S17)
    edge: tank size non-standard (correction), values 0 until car sends a change

F07 First install and permissions
    install APK -> S29 each permission -> ADB grant (computer, ADBAppControl or LADB) -> optional default launcher
    edge: accessibility off, overlay off, ADB not possible

F08 Buy and activate
    S27 email -> pay -> code by email -> enter code
    edge: trial expired, wrong code, offline

F09 Steering wheel control
    hold "*" + key opens settings; keys switch presets / cluster screens
    edge: missed presses (polling), model-specific buttons
```

## Components

| component | variants | states | used on |
| --- | --- | --- | --- |
| App window frame | left, right, fullscreen, framed | running, relaunching, swapped | S01 |
| Bottom menu bar | full, reduced height, hidden | active preset | S01, S02 |
| Floating button | ball, back, navi, PiP close | idle, dragged, expanded | S03, S05 |
| Gauge well | speed, RPM, fuel, temp, cruise, tires | normal, warning yellow, alarm red, hidden, mode-tinted | S08–S10 |
| Info card / page | clock (skins), fuel, music, off-road, weather | loading, filled, zero | S10, S14 |
| Settings row | toggle, slider, picker, help (?) | on, off, disabled | S06, S20–S24 |
| Theme thumbnail | built-in, user slot, empty slot | selected | S11, S13 |
| Log row | trip, refuel | — | S17, S18 |
| Permission step | granted, missing | open settings action | S29 |
| Dialog | confirm, update, activation | — | S06, S27, S28 |

## Inferred data model

```
Preset      id, slot (1..7), name?, left_app, right_app, window_sizes,
            cluster_screen (navi | theme | media), cluster_navi_mode (window | fullscreen),
            extra_screen_open, extra_screen_page, media_info_on, day_night (day | night | any)
            evidence: changelog 5260714, 6260917, 5260622, site presets
            confidence: high

Settings    output_head_unit, output_cluster, launcher_mode, bottom_menu_on, menu_height,
            split_ball_on, back_button_on, navi_button_on, theme_id, style_id,
            rhd, car_model, tank_volume_correction, tire_alarm_kpa, units?, language (ru | en | ar),
            brightness_day/night (hu, cluster), auto_update_check, hotspot_off
            evidence: site FAQ, changelog
            confidence: high (fields), guess (names)

VehicleState  speed, rpm, gear?, drive_mode (normal | sport | eco), fuel_level, fuel_range_km,
              coolant_temp, tire_pressures[4], cruise_on, cruise_speed, cruise_distance,
              odometer, hv_battery (hybrids), ignition
              evidence: changelog (tires, cruise, fuel, HV battery, drive mode tint)
              confidence: high (that they exist), guess (source per car)

Trip        id, started_at, ended_at, distance_km, fuel_used_l, avg_consumption
            evidence: changelog 6260825, 6260830   confidence: medium

Refuel      id, at, odometer, litres?, avg_since_last
            evidence: changelog 6260912, 6260917   confidence: medium (fields guessed)

Theme       id, kind (built-in | user), slot, wells_layout, supports_navi_window, modes
            evidence: changelog 6261001, 6261015   confidence: medium

License     state (trial | free | full), trial_started_at, email, code
            evidence: site pricing   confidence: high
```

Relationships: Settings 1-n Preset, Preset n-1 Theme (via cluster), Trip/Refuel read from VehicleState over time.

Settings fields confirmed by S06a–e (add to Settings): wifi_ssid, wifi_password, speed_multiplier, tank_capacity_l, nonstandard_tank, warn_speed, warn_rpm, warn_tire_bar, navi_margins{cluster,hu}{l,r,t,b}, firmware_1791, border_offset, background_id, launch_mode (manual | auto). Settings export/import exists (save / load).

## Stack seen in the credits list (S31)

Public credits, useful for replica-architect. They show what is needed, not code to copy.

| need | what they use | note for the clone |
| --- | --- | --- |
| talking to the head unit's internal bus | FDBus (Apache-2.0, "with modifications") | the key to vehicle data and cluster output; research GWM's FDBus services on your own unit |
| offline voice commands | Vosk, small models | Arabic model availability is the question to check |
| weather | OpenWeather (data), Meteocons (MIT icons), Lottie | needs your own API key |
| place names | Android Geocoder | |
| TBox | JSch (SSH) | out of scope |
| native networking / JSON | curl, OpenSSL, RapidJSON, zlib, libc++ | a native (NDK) layer |
| UI | AndroidX / Material Components, Gson, Material Icons | |

## Arabic quality (a gap for Sahra Dash)

- Mixed languages on the same screen: "Контроль TBox" in Russian, "ЯМ" for Yandex Music, English "Show menu", "Split Info", "Firmware 1791", "Tire pressure".
- Overlapping text: the margin grid draws "30" on top of "لوحة العدادات" and "الوحدة الرئيسية".
- Russia-first defaults: Yandex Navi, Yandex Music, Alice. Gulf users run Google Maps, Waze, Anghami, Spotify, prayer and Qibla apps (the user's own preset runs HAVAL Qibla).
- Arabic is sold through a reseller build, not the developer's own.

## Feature matrix

See `features.csv`. Must: 15, should: 32, could: 14, skip: 6.

## Out of scope (cannot or should not be cloned)

- Their theme art, wells graphics, styles ("Morning Road", "Matrix", "Tank", "NFS", "Mili", "Neon", "RRStyle"...) and names: draw your own.
- Yandex Alice and Yandex Music integrations: Russia-specific; replace with what Gulf users use.
- "Invisible mode" for dealer visits: its purpose is hiding modifications from service; not recommended to build.
- TBox / SBox control and the eSIM traffic-leak features: touch the car's telematics unit; high risk, warranty and legal exposure.
- Their installer/partner network (installer codes with custom logos).
- Any undocumented GWM system interfaces you would only learn from their binary: find vehicle-data access yourself from the head unit's own documented APIs or your own testing.

## Size

Screens 38, flows 9, entities 7. Hard parts: (1) getting live vehicle data per model without root, (2) drawing on the instrument cluster as a second display and moving Navi there without losing the route, (3) reliable multi-window on a locked-down head unit (relaunch cost, accessibility, overlays, ADB-granted permissions). Size: L for the core loop across 4 models; Split info extras add M.
