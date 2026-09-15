# Game Star Box 0.4.27 — FAST Wake Fix v10.2

## Responsibility

Fix subsequent-launch FAST boot getting stuck on `SYSTEM / WAKE` after first-run shoulder calibration.

## Root Cause

The native FAST path was correct: `startFast()` moved the shoulder Core to `ARMED`, `IgnitionGate` still waited for hardware + provisioning, and then released `ARMED -> IGNITING`.

The presentation callback in `shoulder-boot.html` returned early on every state whenever `fast === true`, so both `ARMED` and `IGNITING` snapshots were discarded. The visual layer therefore never ran ignition and never called `visualComplete()`.

## Fix

FAST mode now skips only interactive playground states. It still consumes terminal boot states:

- `ARMED`
- `IGNITING`

Terminal presentation is idempotent:

- `ARMED` animation/cue runs only when `play != ARMED`.
- `IGNITING` animation/timers run only when `play != IGNITE`.

This prevents the 40 ms shoulder tick from repeatedly scheduling cues or ignition timers.

## Core Boundary

No Core semantics changed.

FAST remains a typed `IgnitionGate.SessionMode.FAST` session:

- playground readiness is satisfied by the typed session mode;
- hardware readiness is still required;
- provisioning readiness is still required;
- only the gate may release `ARMED -> IGNITING`.

## Regression Coverage

CI guards:

1. rejects legacy `if(fast)return` presentation logic;
2. requires FAST to allow `ARMED` and `IGNITING`;
3. requires idempotent terminal-state guards;
4. runs embedded JS syntax validation;
5. preserves Thermal pointer-capture/inertia fix;
6. preserves v9 Core gate contract;
7. validates model Physical Event contract;
8. runs Core unit tests;
9. builds signed Android release and verifies final APK asset contents.

## Verification

Successful temporary CI run: `34936618490`.

Artifact: `10384065005`.

Artifact ZIP digest: `sha256:38d90e95dd03696dbe68b52bddfd723976aad51a73897dda52ce9f3bbef0ca88`.

Final APK SHA-256: `c620e15990ceb9e3573be3a0c3999a01437b61659cbcba23f719a788c98d321e`.

## Changelog

### v10.2

- Fixed second-launch FAST boot stuck at WAKE.
- Preserved gate ownership of ignition.
- Made ARMED / IGNITING presentation idempotent.
- Kept Thermal rotor v10.1 behavior unchanged.
