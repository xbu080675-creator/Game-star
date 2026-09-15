# Hardware Playground Cinematic Director Cut — 0.4.27 v6

## Responsibility

Turn the first-run Hardware Playground from a diagnostic-looking sequence into a directed, playable hardware scene while preserving all already-verified REDMAGIC shoulder, Android motion-sensor, physical haptic and first-surface provisioning contracts.

The v6 presentation is intentionally not a prerecorded boot movie. Every major beat remains user-triggered.

## Inputs

- REDMAGIC semantic shoulder input: LEFT / RIGHT + DOWN / UP.
- Android native motion data from `HardwarePlaygroundMotionAdapter`.
- Touch input on the local first-run WebView.
- Physical haptic samples from `HardwarePlaygroundHapticAdapter`.
- Provisioning state: `CORE_RUNTIME / GAME_CATALOG / MAIN_SURFACE`.

## Outputs

- Cinematic camera and scale changes around one realistic gaming-phone chassis.
- A persistent maintenance core that visually connects every interaction chapter.
- User-driven mechanical events for motion, touch, haptic materials and thermal-model interaction.
- Final L+R ignition presentation.
- No new privileged operation and no new vendor control surface.

## Playable Sequence

```text
CONTACT
  LEFT shoulder close-up / capacitive chamber
  RIGHT shoulder close-up / opposite chamber

MOTION
  maintenance core enters PCB route
  tilt real phone → core slides across chassis
  hit left limit → physical detent feedback
  hit right limit → physical detent feedback

GLASS
  user wipes dead glass
  reveal the internal machine beneath the display layer
  maintenance core follows the user's touch path

MECHANICS
  DETENT  → visible indexed wheel + physical detent waveform
  RATCHET → visible toothed gear + physical ratchet waveform
  LATCH   → visible heavy latch + physical latch waveform

THERMAL
  user spins the thermal fan model
  airflow appears only after user-generated rotor motion
  maintenance core is carried by the airflow

IGNITION
  maintenance core docks near the compute area
  user holds physical L + R
  shoulder modules lock
  motor / fan / SoC / power rail return together
  display flashes as if electrically energized
  short blackout
  GAME STAR BOX / SYSTEM ONLINE
```

## State Model

The existing Core shoulder state machine is unchanged in v6:

`LEFT_TAP -> LEFT_HOLD -> RIGHT_TAP -> RIGHT_HOLD -> BOTH_HOLD -> ARMED -> IGNITING -> COMPLETE`

Presentation-side chapters remain:

`SHOULDERS -> MOTION -> TOUCH -> HAPTIC -> FAN -> FINAL -> ARMED -> IGNITE -> BRANDING`

Important prototype boundary: `Playground Complete` is not yet a third Core input to `IgnitionGate`. The user-visible sequence only exposes final L+R at `FINAL`, but deliberately premature physical L+R after shoulder calibration can still reach the existing Core `BOTH_HOLD` path. This must be hardened before merge to `main`; do not solve it with UI-only suppression, timers or raw-input dropping.

Target Core gate for the follow-up hardening pass:

`hardwareReady && playgroundReady && provisioningReady -> ignition release`

FAST boot must explicitly satisfy/bypass `playgroundReady` through typed session semantics rather than relying on presentation timing.

## Platform Implementation

### Motion

Existing `HardwarePlaygroundMotionAdapter` remains the source of native posture data:

- prefers `TYPE_GAME_ROTATION_VECTOR`;
- falls back to `TYPE_ROTATION_VECTOR`;
- dispatches semantic pitch/roll only;
- WebView drag remains a development fallback if native motion is unavailable.

### Haptic

Existing physical-only adapters remain unchanged:

- `HardwareAwakeningMotorDriver` for shoulder/ignition feedback;
- `HardwarePlaygroundHapticAdapter` for material samples.

No audio path is allowed to substitute for physical haptic output.

### Thermal

The fan remains a **THERMAL FAN MODEL** in v6. It is an interactive visual model only. v6 does not claim to control the REDMAGIC physical fan.

Any future real fan control requires a separate typed, reversible and verified vendor adapter. Do not guess Settings keys, sysfs paths or shell commands.

## Presentation Rules

- The phone is not a static UI container; camera scale is allowed to move from whole-device to component-scale shots.
- Text is secondary to physical events. A chapter must remain understandable from motion and feedback even if copy is ignored.
- The maintenance core is a neutral original mechanical probe, not a copy of Astro Bot or any Sony character.
- Mechanical objects must visibly correspond to haptic material semantics.
- The final ignition must recall systems introduced earlier rather than introducing unrelated spectacle.
- No Sony logo, controller geometry, character art, audio or extracted resources are used.

## Safety / Privileged Boundary

Unchanged from the verified 0.4.27 shoulder contract:

- no `virtual_game_key`;
- no arbitrary shell execution;
- no sendevent/input/uinput injection;
- no persistent ordinary-app `WRITE_SECURE_SETTINGS`;
- REDMAGIC scene activation remains temporary and reversible;
- thermal fan remains visual-only until a vendor contract is verified;
- OTA privilege surface remains separate.

## Performance

- Presentation remains a local asset; no network dependency.
- Main WebView continues hidden hydration in parallel.
- No game catalog work is moved onto the UI thread.
- Motion adapter lifecycle remains foreground-scoped.
- Haptic samples are short, bounded effects rather than indefinite loops.

## Logs

Existing stable prefixes remain authoritative:

- `[GSB-BOOT]`
- `[GSB-SHOULDER]`
- `[GSB-HAPTIC]`
- `[GSB-PLAY-HAPTIC]`
- `[GSB-PLAYGROUND]`

v6 adds no generic presentation log surface.

## Error Codes

No new platform error family is introduced by the director pass. Existing motion errors remain:

- `GSB-PLAYGROUND-MOTION-UNAVAILABLE`
- `GSB-PLAYGROUND-MOTION-REGISTER-FAILED`
- `GSB-PLAYGROUND-MOTION-DECODE-FAILED`

Existing haptic/shoulder errors remain unchanged.

## Testing

Successful temporary CI run:

- workflow run: `34905269029`
- build head: `855225e65764f01938ec9bcd8e2d29947a786d2d`
- artifact: `10371904154`
- artifact archive digest: `sha256:1767f218d88a54201a93d6dbcd02c9c8eb51e08c9198a1281e28b11ba1b85de9`
- APK SHA-256: `0a615c1bdb9d25340fa57639cb15d1997a495a80e842a983994cf1856f3899d7`

CI hard gates passed:

- maintenance-core / cinematic-stage guard;
- detent / ratchet / latch visible-mechanism guard;
- internal-route / airflow / final-recall guard;
- v6 one-time migration guard;
- native SensorManager adapter guard;
- physical haptic adapter guard;
- REDMAGIC physical motor contract guard;
- fake-haptic negative guard;
- JDK 17 / API 35 / Gradle 8.9 release compile;
- fixed DEV signing / artifact staging / upload.

Temporary CI workflow was deleted after the successful artifact was captured.

## REDMAGIC 9 Pro+ Acceptance Focus

1. Shoulder close-up must feel like a camera move into hardware, not a zoomed settings page.
2. Motion stage: physical phone tilt must move the maintenance core with low perceived latency; limit impacts should feel synchronized with motor feedback.
3. Glass reveal should feel like opening the device rather than erasing a UI mask.
4. DETENT / RATCHET / LATCH must be visibly and tactilely distinguishable without reading labels.
5. Thermal stage should feel like a short physical toy, not a progress spinner.
6. Final L+R must clearly recall shoulders, motor, fan, SoC/power rail and display power-on.
7. The blackout before branding should increase impact without creating an apparent crash or blank hang.
8. Main surface must already be hydrated when branding exits.

## Changelog

### v6

- Re-directed Hardware Playground around camera scale changes and physical events.
- Added a persistent original maintenance core to connect all chapters.
- Replaced the v5 motion probe-on-line visual with a PCB route and physical limit impacts.
- Kept glass interaction but made the maintenance core follow the user's reveal path.
- Replaced three haptic buttons with visible detent wheel, ratchet gear and heavy latch mechanisms.
- Re-staged the thermal model as an airflow chamber that carries the maintenance core.
- Rebuilt final ignition to recall previously introduced subsystems before blackout and brand reveal.
- Added one-time migration `hardware_playground_cinematic_migration_0427_v6` for install-over testing.
- Kept all privileged, motion, haptic and provisioning contracts unchanged.
