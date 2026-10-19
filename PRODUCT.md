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
- Top left: "Welcome, Wevs" (config owner_name) and Accra weather in °C from Open-Meteo (no key; one
  request per 30 min, cached; config weather_*). TCL HOME (com.tcl.dashboard) is disabled.
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
