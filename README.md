# Unreel

Companion Android app that enforces session timers, daily caps, active-day schedules, and feature-level blocks inside the official Instagram app (`com.instagram.android`) without root or APK patching.

## Features
- **Granular Controls:** Set **Home Feed & Stories**, **Reels**, and **Explore** independently to `Allowed`, `Timed`, or `Blocked`, plus a master **Block All Features** toggle.
- **Targeted Feature Navigation:** When a blocked or expired feature (like Reels) is open, Unreel navigates back to the Home feed tab and displays a popup banner while keeping Instagram open and other features unaffected.
- **Anti-DM Reel Backdoor Guard:** Single Reels shared inside DM threads remain playable, while scrolling to the next Reel inside the Reels viewer triggers a return to Home.
- **Session & Daily Budgets:** Adjust session and daily limits together on a single slider per timed feature, with clear countdown warnings at 2:00 and 0:30 remaining.
- **Testing Mode Lock Toggle:** Top lock toggle is disabled by default during testing so you can freely adjust settings.
