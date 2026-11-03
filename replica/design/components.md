# Sahra Split components

Tokens: `tokens.json` (Kotlin `Tokens`, CSS `tokens.css`). Sizes in design dp on the 1280×720
canvas, always through `Context.dp()` so `uiScale()` applies. Labels are written fresh in Arabic;
none come from the original.

What the original's screens taught (recon S06a–e, S09), and the rule that answers it:

| seen in the original | rule here |
| --- | --- |
| tiny toggles (~40 dp) in a dense 4-column grid | one setting per row, the whole row is the target, min 64 dp |
| numbers drawn on top of labels in the margin grid | numbers live in their own column; grids get a header row |
| Russian and English words mixed into Arabic | Arabic only; brand names (Google Maps) stay as the app shows them |
| psi on screen, bar in the limit | one pressure unit, chosen once, used everywhere |
| teal outline on every panel | road-sign panels: solid fill + inset white border, colour means category |

Colour by category, as on real road signs: **green** = driving and the road (presets, cluster,
navigation), **blue** = services and apps (app picker, media, settings actions), **brown** =
leisure and outdoors (weather, off-road, trips), **amber** = warning, **red** = limit or danger.
Never colour alone: every warning also has an icon or a word.

---

```
SignButton                                   exists: Ui.kt signButton()
  variants  road (green), service (blue), leisure (brown), warning (amber, ink text), danger (red)
  sizes     md 64 high, lg 72 high (rail and ball menus)
  parts     optional 28 dp icon + label/17 bold; icon-only allowed with a content description
  states    default, pressed (white ripple 20%), focused (3 dp amber ring outside the sign
            border), disabled (fill 40%, label text-muted, not clickable), busy (label kept,
            small spinner replaces icon)
  tokens    fill sign-*, inset border border-sign 2 dp at 3.5 dp, radius sign-sm
  a11y      contentDescription = label; focusable for wheel-key navigation
  used on   S02, S04, S06, S07, S13, S27, S28, S29
```

```
SignPanel (settings group, info card)        exists: Ui.kt sign()
  variants  road, service, leisure, plain (surface fill, border-input outline, no inset)
  parts     title row (icon 28 + title/17 bold), body with 16 dp padding
  states    default; collapsed (title only) for long settings pages
  tokens    radius sign 14, inset 5, stroke 2.5
  used on   S06a–e, S10, S14, S26
```

```
SettingRow (toggle)                          new; replaces the original's dense toggle grid
  parts     label/16 (+ optional caption/13 text-muted under it), switch at the end
  size      full row ≥ 64 high, row is the touch target
  states    off, on (switch track success, thumb text), disabled (all text-muted + reason caption),
            needs-permission (switch replaced by a small amber "يلزم إذن" SignButton)
  a11y      role switch, checked state announced; label is the switch's description
  used on   S06b, S20–S24
```

```
Stepper (‹ value ›)                          new; the original uses arrow pairs for theme/model
  parts     prev button, value/label centred, next button; RTL: "next" points left
  size      buttons 64×64, value min width 160
  states    default, at-first / at-last (that arrow disabled), pressed
  used on   S06a theme + background, S06c model, S07 cluster mode
```

```
NumberField                                  new
  parts     label, value in digits face, unit after the value inside an LTR isolate (ltr())
  size      ≥ 64 high; value column right-aligned in RTL, never overlapping the label
  states    default, editing (border-input → focus amber 3 dp), invalid (danger-text caption
            with the allowed range), disabled
  input     numeric keypad; clamp on save, show the clamped value
  used on   S06c speed factor, tank; S06d limits; nav margins grid (with a header row)
```

```
AppPicker                                    new
  parts     app icon 40, app name/16, package/13 text-muted (only in "details" mode), chevron
  states    empty ("اختر تطبيقًا"), filled, app missing (warning icon + "غير مثبت")
  opens     S04 app list in pick mode
  used on   S06a, S07
```

```
TabBar (settings sections)                   new
  parts     5 tabs, label/17; selected tab: green sign behind label; Close as a blue SignButton
  size      80 high, bottom of the settings screen like the original's, but tabs ≥ 120 wide
  states    selected, default, pressed
  used on   S06
```

```
Header                                       exists: Ui.kt header()
  parts     back SignButton at the start (right in RTL), wide green sign with icon + title/24
  used on   S04, S06, S13, S17–S19
```

```
PresetBar / PresetChip                       exists in HomeActivity
  chip      green SignButton with preset number in digits face + short name
  states    active (white fill, ink text, still sign-shaped), default, relaunching (busy)
  bar       80 high, bottom; can hide (then the ball opens it)
  used on   S01, S02
```

```
Rail                                         new (S30)
  parts     72 wide column: apps, car, nav shortcut, climate mini-widget, music, home
  climate   cabin temp in digits/20 + fan level as 7 short bars (filled = success, empty = border)
  states    item active (green sign behind icon), default
  used on   S01
```

```
SplitBall + menu                             exists: OverlayService addBall/toggleMenu
  ball      76 round, surface-raised fill, 2 dp border-sign ring, icon ic_split
  menu      vertical stack of lg SignButtons by category colour
  states    idle (alpha 85%), dragging (alpha 100%, position saved on release), open,
            badge (amber dot + count when a reminder or warning is waiting)
  used on   S03
```

```
SpeedRoundel                                 exists: OverlayService SpeedRoundel
  parts     round 76, speed in digits/34 black, unit/13 under it
  states    normal, over limit (fill warning, text on-warning), no data ("—")
  used on   S32
```

```
GaugeWell (HTML, cluster)                    new themes; contract in architecture.md
  parts     ring with ticks, needle, swept arc up to the needle, hub with metric digits,
            sub-lines (ODO/trip or coolant), bottom segment bar (fuel or drive mode)
  states    normal; mode tint (sport warning-ish, eco success-ish, off by setting);
            warning (arc + digits amber, with icon); limit (danger, with icon); no data (—);
            hidden (well slides out, 200 ms, not at night)
  rules     centre ~40% stays empty for the stock ADAS view; needles ease with rAF; data 1 Hz;
            three widths: 1920 / 960 / 640 at 720 high
  used on   S08, S09
```

```
TyreBlock (HTML + Kotlin)                    new
  parts     car outline (own drawing), four values at the corners in one unit
  states    ok (text), low (warning + icon), very low (danger + icon), no data (—)
  used on   S09, S14
```

```
AlertBanner                                  exists in common.js (themes) and Alerts
  variants  warning (amber, ink text), danger (red, white text)
  parts     icon + one short line; never covers the speed digits
  used on   S08, S09, S01
```

```
InfoPage (Split info)                        exists: Pages.kt, SplitInfoView.kt
  pages     clock, fuel, music, off-road, weather, trip
  parts     one SignPanel per page, metric digits large, max three numbers per page
  gesture   swipe between pages; vertical swipe = volume (could-have)
  used on   S10, S14
```

```
LogRow                                       exists: LogActivity
  parts     date/13 muted, distance + consumption in digits, delete icon button 64
  states    default, current trip (green edge), empty list (one line + an illustration of
            our own)
  used on   S17, S18
```

```
PermissionStep                               new (S29)
  parts     icon, what it is for (one sentence), status (granted ✓ text / missing),
            "افتح الإعدادات" SignButton
  states    granted (success word + icon), missing (warning), not needed on this model (muted)
  used on   S29
```

```
Dialog                                       new
  parts     SignPanel plain, title/24, body/16, 1–2 SignButtons (primary blue, cancel plain)
  rules     blocks only for destructive actions (clear logbook, import settings)
  used on   S06e, S27, S28
```

```
ThemeThumb                                   exists in settings theme picker
  parts     live mini preview (ThemeView, non-interactive), name/16 under it
  states    selected (green sign frame), default, user slot empty ("مكان فارغ")
  used on   S11, S13
```

## Icons, fonts, art

- Icons: Sahra's own vector drawables already in `res/drawable/ic_*.xml`. Missing ones (tyre,
  cruise, gear, battery, climate fan) are drawn fresh in the same 2 dp stroke style.
- Fonts: Noto Kufi Arabic and Overpass, both OFL and bundled with their licences.
- Cluster theme art, car outline and wells: drawn for Sahra. Nothing traced from GWM Split.
