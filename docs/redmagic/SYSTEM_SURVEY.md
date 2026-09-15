# REDMAGIC System Survey

Date: 2026-09-16

## Purpose

This survey exists to answer one architecture question before Game Star Box becomes system-like:

> When a registered game is launched on REDMAGIC 9 Pro+, should Game Star Box reuse an observable/callable REDMAGIC game-session boundary, or should it maintain its own session detection/control chain?

The visual theme and current WebView UI are explicitly out of scope. This phase only studies the system entry chain.

## Existing evidence in Game Star Box

The current backend already has:

- platform-neutral `GameSessionManager`;
- platform-neutral `GameEntryCoordinator`;
- read-only observation of:
  - `gcs_need_kill_game_launcher`;
  - `nubia_game_scene`;
  - `nubia_game_mode`;
- package visibility for:
  - `cn.nubia.gamelauncher`;
  - `cn.nubia.gameassist`;
- read-only exported-component auditing for the REDMAGIC/Nubia game packages;
- Shizuku support whose production privileged surface remains restricted to verified Game Star Box self-update.

The current entry hypothesis translates a REDMAGIC competitive-switch edge from non-zero to zero into `REDMAGIC_COMPETITIVE_SWITCH`. This is still an evidence-backed hypothesis, not a final system integration contract.

## External evidence worth verifying on REDMAGIC 9 Pro+

The open-source RedTrigger project reports the following behavior on newer REDMAGIC hardware:

- `nubia_game_scene=1` activates the shoulder-trigger sensor path without launching the full Game Space UI;
- `nubia_game_mode` is part of the game-state path;
- `cc_game_mis_operate` participates in game gesture behavior;
- `virtual_game_key` can invoke Game Space behavior and therefore must not be written casually;
- Nubia `SystemMgr` resets game-scene state on activity-resume notifications;
- shoulder sensors may appear as input devices containing `nubia_tgk_aw_sar`.

Reference only; no RedTrigger source code is copied into Game Star Box:

- https://github.com/zampierilucas/RedTrigger
- MIT License

These observations are not assumed to be identical on REDMAGIC 9 Pro+. The survey exists specifically to verify the 9 Pro+ ROM behavior.

## Safety boundary

`tools/redmagic-survey.sh` is an external developer research tool, not part of the APK runtime.

It is intentionally read-only:

- no `settings put` / `settings delete`;
- no sysfs writes;
- no package enable/disable/install/uninstall;
- no app-data access;
- no arbitrary command input from WebView/UI/network;
- no expansion of the production Shizuku AIDL surface.

The production privileged whitelist remains unchanged.

## Prerequisites

On the REDMAGIC 9 Pro+:

1. Start Shizuku.
2. In Shizuku, open **Use Shizuku in terminal apps** and finish `rish` setup for Termux.
3. Confirm that `rish -c 'id'` returns either `uid=2000(shell)` or `uid=0(root)`.
4. Use a game that is already registered in the stock REDMAGIC Game Space.

No root is required when Shizuku itself is running from ADB/wireless debugging.

## One-command survey

From the repository root:

```bash
bash tools/redmagic-survey.sh snapshot
```

This captures the static system inventory only.

For the launch-chain experiment:

```bash
bash tools/redmagic-survey.sh trace 60
```

During the 60-second trace window:

1. leave Termux;
2. launch exactly one game already registered in REDMAGIC Game Space;
3. wait until the game reaches its first interactive screen;
4. return to the launcher before the timer ends;
5. do not toggle unrelated system/game settings during the trace.

The tool writes a `redmagic-survey-YYYYMMDD-HHMMSS/` directory and, when `tar` is available, a matching `.tar.gz` archive.

## Collected evidence

### Device

Basic manufacturer/model/Android/build information.

### REDMAGIC packages

Filtered Nubia/REDMAGIC package inventory plus fixed `dumpsys package` captures for:

- `cn.nubia.gamelauncher`;
- `cn.nubia.gameassist`;
- `cn.nubia.systemmanager` when present.

`dumpsys package` is retained because it exposes package metadata, intent/component information, requested permissions, granted permissions and package flags without invoking those components.

### Services

Filtered Binder/dumpsys service names and relevant running vendor processes.

### Known game-state settings

Fixed read-only probes for:

- `gcs_need_kill_game_launcher`;
- `nubia_game_scene`;
- `nubia_game_mode`;
- `cc_game_mis_operate`;
- `virtual_game_key`.

### Trace

`trace` mode samples the five known settings twice per second and saves a filtered framework/vendor logcat slice beginning at the trace start timestamp.

The log filter keeps evidence around:

- `ActivityTaskManager`;
- `ActivityManager`;
- `WindowManager`;
- Nubia/REDMAGIC/Game Space packages;
- `SystemMgr`;
- activity-resume notifications;
- game-scene/mode/switch keys;
- performance/thermal/fan/trigger/QoS terms.

It does not archive an unrelated full logcat buffer.

## What to inspect first

For each trace, inspect in this order:

1. `trace/settings-timeline.txt`
2. `trace/settings.diff`
3. `trace/logcat.txt`
4. `packages/gamelauncher.txt`
5. `packages/gameassist.txt`
6. `services/binder.txt`
7. `services/dumpsys.txt`

The important question is ordering, not merely presence.

Example evidence sequence to look for:

```text
launcher resumed
→ Nubia/SystemMgr event
→ one or more game-state setting transitions
→ target game activity resumed
→ REDMAGIC game/performance service activation
```

Then repeat the same experiment several times. A single observation is insufficient for an entry contract.

## Architecture decision gate

### Prefer `REUSE_REDMAGIC_CHAIN` only if all are true

- the same launch edge repeats reliably across multiple launches;
- the edge occurs early enough to establish Game Star Box session state before the game is meaningfully interactive;
- the edge identifies the target game or can be joined reliably with the ActivityTaskManager transition;
- the observable/callable boundary survives stock Game Space updates or has a safe capability probe;
- Game Star Box can consume it without disabling, replacing or racing stock REDMAGIC components;
- no broad privileged command channel is required.

If these conditions hold, the REDMAGIC adapter should translate the native event into a platform-neutral `GameEntryRequest`; Core must remain unaware of Nubia APIs.

### Prefer `OWN_SESSION_CHAIN` if any important condition fails

Use Game Star Box's own session chain when:

- REDMAGIC only exposes unstable private implementation details;
- the setting edge is delayed until after the game is already running;
- the edge cannot identify which game launched;
- the event disappears when stock Game Space is disabled/updated;
- consuming it requires continuous mutation/watchdog behavior;
- the only reliable path is a privileged hook that would violate the narrow adapter boundary.

In that case, REDMAGIC remains a capability adapter for performance/fan/trigger features, while game-session detection is owned by Game Star Box.

## Recommended target architecture after the survey

```text
Android / REDMAGIC evidence
          ↓
platform/redmagic or platform/android
          ↓ typed event only
GameEntryCoordinator
          ↓
GameSessionManager
          ↓
PREPARING → LAUNCHING → RUNNING → SUSPENDED → ENDED
          ↓
UI / boot transition / game tools react to session state
```

The theme UI should consume session state; it should never detect games or invoke vendor/private APIs itself.

## Exit criteria for this research phase

Do not implement automatic system takeover until the REDMAGIC 9 Pro+ survey establishes:

1. repeatable event ordering;
2. target-game identity source;
3. latency from launch to observed edge;
4. behavior when launching from launcher vs stock Game Space;
5. behavior when returning home and re-entering the game;
6. behavior across at least two games;
7. whether shoulder/performance/game-mode services activate on the same boundary;
8. whether stock Game Space must remain enabled.

Once these are known, the integration can be implemented without touching the accepted theme interface.
