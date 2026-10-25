# Product

<!-- impeccable:product-schema 1 -->

## Platform

android

## Users
One person, the owner, using their own TCL G10 Google TV (55" 4K, Android 12) from the sofa with
the TCL remote. They know the remote well. This fork is for that one TV and does not need to stay
compatible with the other TVs the upstream cleanyourtv project supports.

## Product Purpose
A home screen that replaces Google TV's launcher (with its ads and sponsored rows) and gets out of
the way: you open an app, switch to an HDMI input or Live TV, and are watching within a press or two.
It works when the home screen feels instant and calm, and the TV stays responsive after hours of
use.

## Operating Context
- Daily use: streaming (Netflix, YouTube, Apple TV, Showmax, DStv), live and IPTV (DStv, Sparkle TV,
  OnStream, the TCL Live TV app), and a console or PC on HDMI 1–3.
- D-pad remote only: no touch and no pointer. The Home, Back and Input keys are handled.
- Watched from across a room on a 4K panel that renders the UI at 1080p.

## Capabilities and Constraints
- Native Android TV launcher in Java (package com.gathin.tvhome), minSdk 26, targetSdk 34, built
  with launcher/build-and-install.sh and installed over ADB.
- The hardware is a Realtek RTD2875P with 1.8 GB RAM, of which about 700 MB is free while in use.
  No blur, no video backgrounds and no network polling. Animation must stay on cheap properties
  (transform and alpha).
- Top row (6 across, in a glass band): Netflix, YouTube, Apple TV, DStv, Showmax, then the first app
  alphabetically (Google Play Store). Sparkle TV and OnStream are disabled.
- Top shelf: a full-bleed wallpaper (two built-in Unsplash aerials, one per day; see launcher/WALLPAPERS.md).
  The TV holds no show data for a launcher to read (its TvProvider is empty with Google TV off), so no
  live show art. Frame budget held: 12 ms median while moving focus (measured 19 Oct 2026). The TCL Live TV app (com.tcl.tv) is reached
  through Inputs, not as a tile. Utility apps that must keep running (Quick panel) are hidden but
  never disabled.
- Packages that have been turned off are listed in backup/my-debloat.txt (local, not committed).
- Greeting in two tiers: a small line with a time-of-day icon (sun, setting sun 17-19, moon) and the greeting
  or occasion ("Good evening", "Happy birthday"), then the name in SF Pro Display Bold 42sp.
- Home widgets under the greeting (glass cards, widget_glass): time and date (SF Pro Rounded Semibold numerals,
  git-ignored and auto-copied like SF Pro), weather (temperature, condition and range, then the note or "Accra"),
  and data used this month. The capsule keeps only Inputs, Wi-Fi and Settings.
- Top left: a time-of-day greeting, "Good afternoon, Wevs" (config owner_name) and Accra weather in °C from Open-Meteo (no key; one
  request per 30 min, cached; config weather_*). TCL HOME (com.tcl.dashboard) is disabled.
- Greeting occasions (Greeting.java): birthday 5 May (config owner_birthday), Ghana public holidays
  with a red/gold/green line on national days, Easter by computation, Eid by the Umm al-Qura calendar
  (can differ by a day from Ghana's announcement), "Happy Saturday/Sunday" on weekend days.
- Weather notes: rain likely within 6 hours, raining now, or a high of 33°C or more.
- Inputs: an awake CEC device's announced name replaces "HDMI n" on its row.
- Screensaver (ClockDream): tvOS-style aerials with slow pan and zoom, 3-minute crossfades, greeting,
  weather, time and place. Today's photo is shared with Home; the other is freed when it ends (+10 MB
  while running). SF Pro faces come from Fonts (one per weight per process) and the font is stored
  uncompressed: layout fontVariationSettings leaked ~3 MB of native typefaces per screensaver run, and
  Android 12 keeps finished screensaver activities, so ClockDream empties itself on detach. Measured
  19 Oct 2026: Home 58 MB, 67 MB after the first screensaver run, then flat.
- Data panel: the Wi-Fi button opens Wi-Fi data today / this week / this month and the top 3 apps this
  month (NetworkStatsManager; usage access granted at install by appops GET_USAGE_STATS), plus a row into
  Wi-Fi settings. Built once and refilled, icons cached: reopening it costs no memory (measured).
- Streaming limiter, now Netflix, Apple TV, Showmax and DStv (LimiterVpn + Socks5Proxy + hev-socks5-tunnel 2.18.0 in src/main/jni): a local VPN for
  Netflix only, downloads paced to 8 Mbps so Netflix picks 1080p itself. Measured 19 Oct 2026: 11 Mbps
  (5.2 GB/h, 4K) uncapped -> 6.4-8.1 Mbps (~3.2 GB/h), sharp, no buffering; at 6 Mbps it chose a soft
  720p. ~7% of one core. Fail-open, always-on VPN, started by Home too; toggle in the data panel.
  Only Netflix's uid (10077) is routed into it; DNS relayed to the router, other UDP dropped.
- Home shows "N GB used this month" under the weather (DataUsage.month, background, 10 min at most);
  the data panel adds a 7-day bar chart with the week's top app shaded (WeekBars, drawn in onDraw).
- The Wi-Fi pill's badge is the cap light: green "Cap" while the limiter runs with the cap on, red
  "Cap off" otherwise (a real VPN app's badge still takes precedence).
- HDMI input tiles (config input_tiles, "key|input|style|name"): the DStv decoder on HDMI 3 takes the DStv
  app's top-row slot ("decoder" in dock_apps), drawn as the plain DStv logo on navy (res/drawable/dstv_logo.xml,
  Wikimedia Commons PD-textlogo), no pill; "DStv" also names HDMI 3 in the Inputs menu. The app moved to the grid.
- Console tile (Entry.CONSOLE, ConsoleArt): sixth top-row slot ("console" in dock_apps), PlayStation-blue
  "PS5" face with an On/Standby pill from the CEC device on config console_input (HDMI 2, HW16). OK switches
  to HDMI 2, which also wakes the PS5 over CEC. Redraws only on TvInputCallback changes.
- PS5 picture: Game Master forced On for HDMI 2 (was Auto). The panel is 4K 60 Hz (supported modes 60/30),
  and the PS5 already sends 3840x2160@60 HDR10, so the HDMI 2.1 (EDID) mode was left as it is.
- Features to keep: the time capsule top right holding Inputs, Wi-Fi and Settings, the Inputs dropdown
  with HDMI 1–3 and Live TV, the Clock screensaver, InputKeyService for the remote's Input key.
- Freeing other apps from the launcher does not work on this firmware: TCL ignores an app's
  killBackgroundProcesses silently (tested 19 Oct 2026), so it was removed. Android's own cached-process
  cap (device_config activity_manager max_cached_processes) does the job instead.
- TCL's TGuard app-boot control (ro.feature.tguard_app_boot) refuses to bind third-party accessibility
  services, so InputKeyService never runs: the remote's Input key does nothing outside Home.

## Brand Commitments
Personal product. No external brand. The home follows Apple TV (tvOS) closely: top shelf, a top
row of five favourites, 5:3 icons that lift with a shadow and sheen, labels only on focus, and Apple's
SF Pro typeface. A PS5-style blend was tried and rejected on 19 Oct 2026 (branch ps5-blend).
SF Pro is copied from the owner's Mac (/Library/Fonts/SF-Pro.ttf) into res/font/sf_pro.ttf for this TV
only; it is git-ignored and must never be committed or published.

## Product Principles
1. Content in one or two presses. Nothing stands between the remote and the app.
2. Feel faster than it is: respond to focus at once, never wait on animation.
3. Stay light. The TV's RAM goes to the app being watched, not to the home screen.
4. Calm, never promotional. No rows, banners or badges the owner didn't ask for.

## Accessibility & Inclusion
Readable from about 3 m away: type sizes and focus states must be obvious at TV distance. Must
respect the system setting that turns animations off.
