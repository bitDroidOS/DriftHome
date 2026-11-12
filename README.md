# DriftHome

The DriftVaneOS launcher. Pages of apps, a floating glass dock, folders, widgets and a smart App Library, styled to match AlexLMP.

## v0.9: setup and home styles

- First-boot setup: right after the phone's setup, DriftHome asks two things:
  1. Pick your Settings: Live tiles, Device card or Category chips
  2. Pick your home screen: Frame, Split, Stack or Columns
  Run it again any time: Home settings -> Home screen -> Run setup again.
- Home styles:
  - Frame: a solid page with a window that shows your wallpaper and the time
  - Split: swipeable cards (clock, now playing, battery) above your apps
  - Stack: a deck of those cards; flick the top one to the back
  - Columns: info cards on the left, your apps as a labeled list on the right

## v0.8

- DriftVane blue by default: clock, outline icons, dots and dock line are all blue
  (pick another accent in Home settings -> Look).
- The Search pill is off by default (just small page dots), so the grid goes lower and apps
  can sit right above the dock. Turn the pill back on in Home settings -> Home screen.

## v0.7

- Bold clock is the default look again (Tonal dashboard stays as an option in Home settings).
- The grid fills the screen down to the dock: as many rows as fit, so you can drop apps right
  above the dock.
- Category view: tapping an app opens it.

## v0.6: Tonal dashboard

- New default look: a solid tonal card with the time and date at the top of the first page,
  then a pastel "Now playing" tile (tap to open the player) and a battery tile.
- Round app icons on a solid tonal panel, no labels; a chunky tonal dock.
- Everything is solid (no see-through) and colored from your accent.
- The Bold clock look is still there: Home settings -> Home screen -> Top of first page.
- Category view: simpler tap targets so tapping an app opens it.

## v0.5: Bold clock

- New look: a huge two-tone clock on the first page (hours in your accent, minutes softer),
  date and battery above it, and what's playing below it (tap it to open the player).
  First time: tap the line under the clock and allow notification access for DriftHome.
- Drift outline icons: accent symbols on dark tiles with an accent outline, no labels.
- Apps sit low on the first page, under the clock, where your thumb reaches.
- Line dock: no box, just a thin accent line above the dock icons.
- App Library: solid category cards (no see-through). Tap an app to open it, tap the card
  or the +N tile to see the whole category.

## v0.4

- **Widgets live on the grid:** in edit mode drag a widget to any spot (icons flow around it),
  past the screen edge to move it to another page, and drag any of its 4 edges to resize it.
- App Library category sheets: tap an app to open it, or hold and drag it to home.

## v0.3.5

- **Put apps anywhere:** drag an app to any cell, gaps are kept. Dropping on a taken cell nudges apps forward into the next gap.
- **Clean start:** a fresh home screen only has the essentials (photos, calendar, clock, mail, maps, music, YouTube, Play Store, files, calculator, settings). Everything else is in the App Library. Newly installed apps still land in the first free spot.
- Large icons by default.

## v0.3.4: premium pass

- Calmer, cleaner look: thin rims on glass, soft icon shadows, lighter type, gentler press animation.
- Matching glass top tiles: big light time on the left, date + battery ring on the right.
- App Library gets the whole screen: the dock and search pill slide away as you swipe to it.
- Default 5 columns with XL icons.

## v0.3.3: new home screen

- **New top widgets:** a colorful clock tile (time, greeting, day) next to a glass date + battery ring tile.
- **New layout:** 4 roomy columns with bigger icons, rows spread evenly down to the dock, side margins.
- **Search pill:** a glass "Search" pill above the dock; it turns into page dots while you swipe.
- Plain page swipe (no page animations).

## v0.3

- **Liquid glass everywhere:** dock, folders, menus and settings use a frosted glass look with a bright rim and sheen.
- **Fluid motion:** springy press squish on icons, menus that grow out of the icon you held and icons cascading in when you come home. Pages swipe the classic way.
- **New long-press menus:** the icon lifts over a blurred home screen, with quick buttons (Edit, Info, Delete), the app's own shortcuts, Add to dock, Remove and Hide app.
- **Widgets:** in edit mode, drag a widget up or down to move it, drag it sideways to send it to another page, drag the bottom handle to resize it, and tap ⇆ to make it half width (two side by side).
- **Icon shapes:** Squircle, Rounded, Pebble, Teardrop, Square and Circle.
- **Icon themes:** Original, Themed (accent tint), Light, Liquid glass and Mono.
- **New Home settings:** a live preview and pages for Icons, Home screen, Dock (glass, clear or solid), App Library, Gestures & feel (haptics, lock layout), Look and Hidden apps.
- **Crash guard:** if DriftHome crashes twice within a minute, it resets the home layout instead of crash-looping.
- **Preview build:** `DriftHome-Preview` installs next to the built-in DriftHome so you can test before putting it in the ROM.

## v0.2

- **Home pages:** squircle icons with 5 columns by default (4/5/6). The clock & battery card can be removed in edit mode or settings.
- **Drag anywhere:** hold an icon and drag it to any spot, any page (hold at the screen edge to flip pages) or the dock.
  The other icons slide out of the way.
- **Folders:** drop an app onto another app. Tap a folder to open it, tap its name to rename it, and drag apps out.
- **Widgets:** long-press an empty spot → **Add widget**, or tap **+ Widget** in edit mode.
- **Edit mode:** icons wiggle; **–** removes an app from home, a folder (its apps stay on home), a widget or the clock card.
- **App Library**, in four styles chosen in settings:
  - **Smart categories** (default): Suggestions, Recently added and auto-sorted groups.
  - **Category cards:** 2×2 cards for each category.
  - **A–Z list.**
  - **Swipe up drawer:** no library page; swipe up anywhere for all apps.
- **Swipe down:** search (default), the notification shade, or nothing.
- **Home settings** (long-press empty space, or ⚙ in the library): columns, icon size, labels, clock card, page dots,
  dock, library style, gestures, accent color, glass strength and reset.

## Build

Push to GitHub, and the **Actions** tab builds `DriftHome.apk`.
