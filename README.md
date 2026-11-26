# Unreel

Companion Android app that enforces session timers, daily caps, active-day schedules, and feature-level blocks inside the official Instagram app (`com.instagram.android`) without root or APK patching.

## Features
- **Granular Controls:** Set **Home Feed & Stories**, **Reels**, and **Explore** independently to `Allowed`, `Timed`, or `Blocked`, plus a master **Block All Features** toggle.
- **Session & Daily Budgets:** Enforce per-session limits (e.g., 15 min) with mandatory cooldowns alongside daily caps (e.g., 60 min/day) and active days (`Mon–Sun`).
- **Anti-DM Reel Backdoor Guard:** Single Reels shared inside DM threads remain playable, while scrolling to the next Reel inside the Reels viewer immediately triggers a back action.
- **Countdown & Expiry Alerts:** Floating overlay pill warnings at 2:00 and 0:30 remaining, followed by automatic block enforcement.
- **Anti-Tamper Lock:** Prevents weakening restrictions or extending timers during an active lock window (architected with `UnlockProvider` for v2 NFC tag unlock).

## Setup
1. Extract this archive into `C:\Users\donal\unreel` (or your project folder).
2. Open the folder in **Android Studio** and let Gradle sync.
3. Build and install onto your Android device.
4. Open **Unreel** and grant **Accessibility** and **Display over other apps** permissions.
