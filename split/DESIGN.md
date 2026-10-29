---
name: Sahra Split
description: An Arabic-first head-unit launcher where every value the driver needs is set as a Saudi road sign on asphalt.
colors:
  asphalt: "#18191B"
  gantry: "#111214"
  shoulder: "#1F2023"
  kerb: "#26282B"
  lane-dash: "#3B3D41"
  highway-green: "#00673F"
  city-blue: "#1B4C8C"
  tourist-brown: "#6E3B1E"
  warning-amber: "#F3B300"
  legend-white: "#F4F5F0"
  legend-dim: "#F4F5F0CC"
  verge-grey: "#9FA3A8"
  sign-ink: "#16130A"
typography:
  display-numeral:
    fontFamily: "Overpass, sans-serif"
    fontWeight: 900
  value-numeral:
    fontFamily: "Overpass, sans-serif"
    fontWeight: 900
    fontSize: "34sp"
  headline:
    fontFamily: "Noto Kufi Arabic, sans-serif"
    fontWeight: 800
    fontSize: "30sp"
  title:
    fontFamily: "Noto Kufi Arabic, sans-serif"
    fontWeight: 800
    fontSize: "24sp"
  button:
    fontFamily: "Noto Kufi Arabic, sans-serif"
    fontWeight: 800
    fontSize: "17sp"
  body:
    fontFamily: "Noto Kufi Arabic, sans-serif"
    fontWeight: 500
    fontSize: "16sp"
  meta:
    fontFamily: "Noto Kufi Arabic, sans-serif"
    fontWeight: 500
    fontSize: "14sp"
rounded:
  plaque: "9dp"
  sign-small: "10dp"
  field: "12dp"
  sign: "14dp"
  overlay: "16dp"
  app-plate: "20dp"
  full: "50%"
spacing:
  hair: "4dp"
  tight: "8dp"
  gap: "12dp"
  panel-gap: "14dp"
  edge: "16dp"
  sign-pad: "22dp"
components:
  sign-panel:
    backgroundColor: "{colors.highway-green}"
    textColor: "{colors.legend-white}"
    rounded: "{rounded.sign}"
    padding: "22dp"
  sign-panel-services:
    backgroundColor: "{colors.city-blue}"
    textColor: "{colors.legend-white}"
    rounded: "{rounded.sign}"
  sign-panel-tourist:
    backgroundColor: "{colors.tourist-brown}"
    textColor: "{colors.legend-white}"
    rounded: "{rounded.sign}"
  sign-button:
    backgroundColor: "{colors.city-blue}"
    textColor: "{colors.legend-white}"
    typography: "{typography.button}"
    rounded: "{rounded.sign-small}"
    padding: "12dp 18dp"
    height: "64dp"
  sign-button-primary:
    backgroundColor: "{colors.highway-green}"
    textColor: "{colors.legend-white}"
    typography: "{typography.button}"
    rounded: "{rounded.sign-small}"
    height: "84dp"
  dock-sign:
    backgroundColor: "{colors.city-blue}"
    textColor: "{colors.legend-white}"
    rounded: "{rounded.sign-small}"
    size: "84dp"
  lane-strip:
    backgroundColor: "{colors.gantry}"
    textColor: "{colors.verge-grey}"
    rounded: "{rounded.sign-small}"
    height: "64dp"
  lane-active:
    backgroundColor: "{colors.highway-green}"
    textColor: "{colors.legend-white}"
    rounded: "8dp"
  exit-tab:
    backgroundColor: "{colors.highway-green}"
    textColor: "{colors.legend-white}"
    typography: "{typography.body}"
    padding: "8dp 16dp"
  warning-strip:
    backgroundColor: "{colors.warning-amber}"
    textColor: "{colors.sign-ink}"
    typography: "{typography.button}"
    rounded: "{rounded.sign-small}"
    padding: "10dp 16dp"
  shoulder-row:
    backgroundColor: "{colors.shoulder}"
    textColor: "{colors.legend-white}"
    rounded: "{rounded.field}"
    padding: "14dp 20dp"
    height: "68dp"
  choice-off:
    backgroundColor: "{colors.asphalt}"
    textColor: "{colors.verge-grey}"
    rounded: "{rounded.sign-small}"
    padding: "12dp 18dp"
  choice-on:
    backgroundColor: "{colors.highway-green}"
    textColor: "{colors.legend-white}"
    rounded: "{rounded.sign-small}"
    padding: "12dp 18dp"
  round-control-primary:
    backgroundColor: "{colors.legend-white}"
    textColor: "{colors.city-blue}"
    rounded: "{rounded.full}"
    size: "80dp"
  round-control:
    backgroundColor: "transparent"
    textColor: "{colors.legend-white}"
    rounded: "{rounded.full}"
    size: "72dp"
  app-plate:
    backgroundColor: "{colors.legend-white}"
    rounded: "{rounded.app-plate}"
    size: "92dp"
---

# Design System: Sahra Split

## Overview

**Creative North Star: "The Highway Gantry"**

Every value the driver needs is set as a Saudi road sign: a coloured reflective panel, a white legend inside a thin white inset border, a pictogram, a number. The screen is a stretch of road at night. The ground is warm charcoal asphalt with faint vertical lane-dash texture; on it stand sign panels whose colour is their meaning, exactly as on the highway: green for roads and the primary action, blue for services and apps, brown for weather, tourism and off-road, amber for something that needs attention now.

Density is low and deliberate. A screen holds few things, each large enough to read in a one- or two-second glance at arm's length. Words are wide Noto Kufi Arabic; every number is set in Overpass, a highway-gothic face, at heavy weight. Composition is right-to-left: the label sits on the right, the number on the left, joined by a dotted leader like a distance sign. The panels are flat; depth comes from the colour of the board against the road, never from glass, blur or glow.

The system rejects the head-unit category default of glassy dark cards, neon glow and generic line icons. Pictograms are solid white shapes drawn for this product on a 48 grid, never outlines from a stock set and never emoji or Unicode symbols.

**Key Characteristics:**
- Asphalt ground, sign-colour panels, white legends inside a white inset border.
- Colour is semantic by sign family: green roads, blue services, brown tourism, amber warning.
- Kufi for words, Overpass Black for every number, Latin digits throughout.
- Distance-sign rows: name right, dotted leader, value and unit left.
- Flat panels; no shadows except on the one window that floats over other apps.
- Solid white 48-grid pictograms; no animation.

## Colors

A night road with reflective signs: three dark asphalt neutrals, three sign-family colours at full saturation, one warning amber, and white for every legend.

### Primary
- **Highway Green** (highway-green): the road family. Clock and charts pages, the navigation exit panel, the active lane, the split-screen command, header boards, selected tabs and choices, switch and slider tracks. It is the colour of "where you are going" and of the one primary action on a screen.

### Secondary
- **City Blue** (city-blue): the services family. Apps, music, fuel, refuels and reminders, back buttons, the square dock signs, the settings and apps headers. Also the ink of the play glyph on the white primary round control.

### Tertiary
- **Tourist Brown** (tourist-brown): weather, off-road, the outside-temperature plaque, statistics. Used for leisure and environment data, never for commands.
- **Warning Amber** (warning-amber): due reminders, an unfinished trip, low fuel, tilt beyond the safe angle, the north letter on the compass, the "grant" permission badge. Text on amber is always Sign Ink, and an amber state always carries a warning triangle or words as well.

### Neutral
- **Asphalt** (asphalt): the screen ground, with 1/24-width vertical dashes every 48dp at 3.5% white (`#09FFFFFF`). Also the navigation bar and unselected choice chips.
- **Gantry** (gantry): the darkest structure: the side panel column, the lane strip, the status bar, the floating menu.
- **Shoulder** (shoulder): parked-use surfaces: settings rows, app tiles, unselected log tabs.
- **Kerb** (kerb): 1-2dp borders on shoulder and gantry surfaces, and the 2dp divider between panel and main area.
- **Lane Dash** (lane-dash): dashed dividers between lanes, empty-state pictograms, inactive switch and slider tracks.
- **Legend White** (legend-white): every legend, pictogram and inset border on a sign. Slightly warm, not pure white.
- **Legend Dim** (legend-dim): white at 80% for secondary lines on a sign (units, Hijri date, "feels like", subtitles). Stored in code as ARGB `#CCF4F5F0`.
- **Verge Grey** (verge-grey): text and pictograms that sit on the asphalt rather than on a sign: inactive lanes and tabs, dock captions, settings values and notes.
- **Sign Ink** (sign-ink): legends on amber.

### Named Rules
**The Sign Family Rule.** A panel's colour states its family, not its importance: green roads, blue services, brown tourism and environment, amber warning. Never pick a sign colour for contrast or variety.

**The Legend Rule.** Text on a sign is Legend White or Legend Dim; text on the road is Legend White or Verge Grey; text on amber is Sign Ink. No other text colours exist.

**The Red Ring Reserve.** Regulatory red (`#C8102E`, declared in Palette) means a limit and nothing else. It is not used in the shipped build; the speed roundel is deliberately green because a red ring would read as the limit, not the current speed.

## Typography

**Word Font:** Noto Kufi Arabic, Medium 500 and ExtraBold 800 (bundled as `kufi_medium`, `kufi_bold`)
**Numeral Font:** Overpass, Bold 700 and Black 900 (bundled as `digits_bold`, `digits_black`)

**Character:** A wide, upright Kufi carries Arabic words with the authority of a sign legend; Overpass, drawn from the American highway-gothic tradition, carries every number with the same open, heavy forms as a distance board. Font padding is off everywhere so legends sit tight inside their borders.

### Hierarchy
- **Display Numeral** (Overpass 900, proportional to the page): the digital clock, the fuel percentage, the weather temperature, the live chart value, the speed roundel. Canvas pages size type from the page box (for example clock at 0.36 of the page unit), so the display scales with the panel width rather than stepping.
- **Value Numeral** (Overpass 900, 34sp): the value column of a log entry; on canvas distance rows the value is 1.5x the row's word size.
- **Headline** (Kufi 800, 30sp): the app name on an exit panel, the largest word on the home screen.
- **Title** (Kufi 800, 24sp): sub-screen header boards; music page track title at 22sp.
- **Button** (Kufi 800, 17sp): sign-button legends, warning strip, floating menu rows, row titles in settings (17sp at 500 when not a button).
- **Body** (Kufi 500, 15-16sp): exit tab names, subtitles on signs, settings values, log tab names.
- **Meta** (Kufi 500, 14sp): dock captions, log entry third line, settings notes. The smallest text on any screen.

### Named Rules
**The Every-Number Rule.** Every number, in any context, is set in Overpass with Latin digits (`ar-u-nu-latn`), thousands grouped with a comma as on distance signs. Arabic words never set numbers in Kufi.

**The Distance Row Rule.** A labelled value is a distance-sign row: Kufi 800 name on the right, a dotted leader (2dp dots every 6dp at 44% white), the Overpass 900 value, and the unit in Kufi 500 at 70% size in Legend Dim on the far left.

**The Isolate Rule.** A number joined to a symbol (36°) inside an Arabic sentence is wrapped in a left-to-right isolate so the symbol never flips side.

## Layout

The home screen is a road seen from the driver's seat. A side gantry (default 28% of the screen width, 20-45% by preset, either side or hidden) sits on Gantry with 12dp padding: a row of six 64dp pictogram tabs, then the current page as one large sign filling the rest. A 2dp Kerb line separates it from the main area.

The main area (16dp side padding, 12dp top and bottom) stacks: the lane strip (64dp high), optional permission and warning strips (10dp above each), the two exit panels side by side sized by the split ratio (default 60/40, 14dp gap), and the dock row of a wide green split sign (weight 1.6, 84dp) plus four square 84dp blue signs with captions. Exit panels are ordered by their real position on screen (left, then right) regardless of text direction, because each points at its window.

Sub-screens (Log, Settings, Apps) have a header row: a blue back sign on the start side, then a wide header board with pictogram and title. Content scrolls below with 20-24dp side padding and 8-14dp between items.

Spacing rhythm is in even dp steps around a 4dp base: 4 (hair), 8 (tight), 12 (gap), 14 (between panels), 16 (screen edge), 22 (inside a large sign). Canvas pages position everything as fractions of their own box, with page padding at 7% of the shorter side.

Direction: content is RTL; the root and the exit row are forced LTR so the panel side and the window order match the physical screen.

## Elevation & Depth

Flat. Depth is tonal and semantic: Gantry (darkest) for structure, Asphalt for ground, Shoulder for parked-use surfaces, and saturated sign colours standing on top. No panel in the launcher, pages, log, settings or apps screens casts a shadow.

### Shadow Vocabulary
- **Roundel lift** (`0 4dp 0 0 rgba(0,0,0,0.40)` drawn as a 12dp-radius rect offset 4dp below the sign): only on the floating speed roundel, which sits over arbitrary third-party apps and needs separation from unknown backgrounds.

### Named Rules
**The Flat Road Rule.** Signs do not float. Separation comes from the colour of the board, its white inset border, and the Kerb line, never from a shadow, blur or glow. The single exception is a window drawn over other apps.

## Shapes

The signature shape is the road sign: a softly rounded rectangle with a white stroke inset from the edge at a tighter radius, as on a real reflective panel.

- **Large sign** (radius 14dp, border 2.5dp, inset 5dp; inner radius = outer minus inset): pages, exit panels, header boards, log entries.
- **Small sign** (radius 10dp, border 2dp, inset 3.5dp): buttons, dock signs, selected tabs and chips, menu squares, the speed roundel.
- **Plaque** (radius 9dp outer, 6dp inner, 2dp border at 4dp inset): small signs drawn inside a page, such as the outside-temperature badge.
- **Exit tab** (10dp top corners, square bottom): the name tab that sits on top of an exit panel, offset 22dp from the corner.
- **Unbordered surfaces** use plain rounded rectangles: warning strip 10dp, shoulder rows 12dp with a 1dp Kerb stroke, inactive tabs 10dp with a 2dp Kerb stroke, app tiles 14dp, the floating menu 16dp.
- **White plates** (20dp on exit panels, 18dp in the apps grid, 22dp for album art) hold third-party app icons so foreign artwork never sits directly on a sign colour.
- **Circles** only for media controls and the instrument dials (tilt, compass, analogue clock).

Pictograms are solid white fills on a 48 grid with round caps and joins where strokes are used (strokes 3.2-7 units); arrows are thick shafts with filled triangular heads, as on direction signs.

## Components

### Buttons
Signs you can press: confident, square-shouldered, legible from the passenger seat.
- **Shape:** small sign (10dp radius, 2dp white inset border).
- **Sign button:** City Blue fill, 28dp pictogram then Kufi 800 17sp legend, 12dp x 18dp padding, minimum 64dp high. Back is always a blue sign with the U-turn pictogram.
- **Primary (split screen):** the same sign in Highway Green at 84dp, one per screen.
- **Dock sign:** an 84dp square small sign in City Blue with a 38dp pictogram, captioned below in Meta Verge Grey.
- **Pressed:** a 20% white ripple clipped to the sign shape. No hover; no focus ring.

### Chips
- **Style:** a row of choices. Off is Asphalt with a 2dp Kerb border and Verge Grey Body text; on is a green small sign with white bold text. Minimum 52dp high.
- **State:** single-select; the selected chip is the only one with a white border.

### Cards / Containers
- **Shoulder row (settings):** Shoulder fill, 12dp radius, 1dp Kerb stroke, 14dp x 20dp padding, minimum 68dp; title Kufi 17sp white, value Verge Grey, chevron on the end.
- **Log entry:** a large sign in its family colour, 18dp x 24dp padding, 40dp pictogram, Kufi 800 18sp title, Legend Dim lines, Value Numeral on the left with unit. An amber entry switches every legend to Sign Ink.
- **App tile:** Shoulder, 14dp radius, 1dp Kerb, white 78dp plate with the app icon, Kufi 800 14sp name.

### Inputs / Fields
- **Switch:** white thumb on a Highway Green track when on, Verge Grey thumb on a Lane Dash track when off.
- **Slider:** Highway Green progress on Lane Dash, white thumb, with the current value in Overpass 900 22sp.

### Navigation
- **Lane strip:** presets as road lanes on a Gantry bar, 64dp high, divided by Lane Dash dashed lines (3dp, 8 on 7 off). The active lane is a green rounded block with a white up-arrow and Kufi 800; others are Verge Grey Kufi 500. The night lane carries a crescent; the last lane is "+" for a new preset.
- **Page tabs (gantry):** six 64dp pictogram tabs. Active is a small sign in the page's family colour with a white pictogram; inactive is a 2dp Kerb outline with a Verge Grey pictogram. Swipe horizontally to change page.
- **Log tabs:** the same pattern with pictogram and word.

### Exit Panel (signature)
A large sign per app window: green for navigation, blue for music. An exit tab on top names it with an arrow turned toward the window's side of the screen. Inside: the app icon on a 92dp white plate, the app or track name as Headline, a Legend Dim instruction line, and a foot row: a 56dp direction arrow for navigation, or round media controls for music (72dp outlined circles with 3dp white stroke, 80dp solid white primary with a blue glyph). Its width follows the split ratio.

### Warning Strip
An amber rounded strip (10dp) across the main area with a warning triangle, the reminder in Sign Ink Kufi 800 17sp, and a short status word on the end. Permission prompts use the same strip in City Blue with white text.

### Speed Roundel and Floating Menu
The floating ball is a 76dp green small sign with the current speed in Overpass 900 and "km/h" in Legend Dim. Tapping opens a Gantry column (16dp radius, 2dp Kerb) of rows: a 52dp small-sign square in its family colour with a 26dp pictogram, then a Kufi 800 17sp label.

## Do's and Don'ts

### Do:
- **Do** set every value as a sign: family colour, white legend, white inset border (14dp/5dp/2.5dp large, 10dp/3.5dp/2dp small).
- **Do** choose the panel colour by sign family: green roads and the primary action, blue services and apps, brown weather and off-road, amber warning.
- **Do** set every number in Overpass 900 with Latin digits and comma thousands, and every word in Noto Kufi Arabic.
- **Do** lay out labelled values as distance rows: name right, dotted leader, value and unit left.
- **Do** pair every amber or warning state with a triangle pictogram or words; never colour alone.
- **Do** keep touch targets at least 64dp; dock and primary signs are 84dp.
- **Do** put third-party app icons and album art on a white plate.
- **Do** draw new pictograms as solid white shapes on the 48 grid, in the style of `/home/claude/design/icons.py`.

### Don't:
- **Don't** use glassy dark cards, blur, neon glow or gradients on any surface.
- **Don't** use stock line icons, emoji or Unicode symbols as icons.
- **Don't** cast shadows inside the app; only the floating roundel over other apps has a lift.
- **Don't** use red for anything but a limit; never for the current speed, errors or decoration.
- **Don't** animate: no transitions, pulses or count-ups. Pages switch instantly.
- **Don't** put coloured text on a sign; legends are white, dim white, or ink on amber.
- **Don't** set numbers in Kufi or in Arabic-Indic digits.
