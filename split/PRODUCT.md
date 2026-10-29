# Product

<!-- impeccable:product-schema 1 -->

## Platform

android

## Users
The owner and a close circle of GWM drivers. The main car is the **Haval H9** with its large central landscape screen (resolution not confirmed; assumed 1920×1080 at 14.6", must also hold up at 1920×720). Used mostly while driving: glances of one or two seconds, touches with one finger at arm's length, in bright sun and at night. Settings and the logbook are used while parked.

## Product Purpose
A home screen (launcher) for the head unit that puts navigation and music side by side with a live car panel: clock, fuel, charts, weather, music, off-road. Success: the driver never has to dig through the stock system menus to see what matters or to switch between apps.

## Positioning
Built by and for its users, not for sale. It is free to choose taste over mass appeal, Arabic-first in language and composition, and to read live car data directly from the `gwm_adapter` service.

## Operating Context
- Head unit running a GWM build of Android (minSdk 26), right-to-left Arabic interface.
- Live data: speed, revs, fuel, range, odometer, temperatures, battery, steering, pitch/roll, compass, altitude. Demo data when the service is missing.
- Navigation and music apps open in windows next to the panel; a floating ball stays on top of other apps.
- The logbook (trips, refuels, reminders, stats) is read while parked.

## Capabilities and Constraints
- Plain Android framework views drawn in code; no AndroidX. Built only on GitHub Actions.
- Icons, artwork and fonts must ship inside the APK (no runtime downloads apart from weather).
- No emoji or Unicode symbols as icons.
- Undecided: final product name (currently the working name "صحراء سبليت" / Sahra Split; the owner is open to a new name).

## Brand Commitments
None binding yet. The working name and desert theme can be reconsidered.

## Evidence on Hand
No screenshots of the real head unit. No user testimonials. Do not invent either.

## Product Principles
1. Readable in two seconds at arm's length: big numbers, high contrast, few things at once.
2. Arabic first: right-to-left composition, real Arabic typography, never translated from English.
3. Made, not generated: every icon and illustration drawn for this product.
4. The road comes first: no animation or brightness that pulls attention at night.

## Accessibility & Inclusion
Night-driving glare limits; touch targets at least 64dp; information must never rely on colour alone (warnings carry an icon or text as well).
