# Hardware Playground Asset Rebuild — 0.4.27 v7

## Responsibility

Replace the v6 CSS-drawn pseudo-hardware look with a standalone materialized hardware asset pack while preserving the verified REDMAGIC shoulder, native motion, physical haptic and hidden first-surface provisioning chains.

v7 is an art/asset rebuild, not a new privileged-integration pass.

## Inputs

- REDMAGIC semantic shoulder input.
- Native Android rotation sensor semantics from `HardwarePlaygroundMotionAdapter`.
- Touch input on the local first-run WebView.
- Physical haptic material samples from `HardwarePlaygroundHapticAdapter`.
- Existing first-boot provisioning state.

## Outputs

Standalone local assets under `app/src/main/assets/hardware-v7/`:

- `chassis-shell.svg`
- `pcb-board.svg`
- `cooling-fan.svg`
- `linear-motor.svg`
- `mechanism-detent.svg`
- `mechanism-ratchet.svg`
- `mechanism-latch.svg`
- `maintenance-core.svg`

The presentation layer now places and animates those assets instead of drawing the core hardware objects as CSS primitives.

## Material Language

Default palette is intentionally restrained:

- black glass;
- graphite / dark anodized metal;
- silver steel;
- copper;
- deep green PCB.

System blue is no longer the default sci-fi material. It appears only during the final power-on rail / ignition state.

## Playable Sequence

The verified interaction order remains:

`SHOULDERS -> MOTION -> GLASS -> MECHANICS -> THERMAL -> FINAL -> ARMED -> IGNITE -> BRANDING`

v7 changes staging:

- shoulder focus uses the standalone chassis asset at component scale;
- Motion places the standalone maintenance core over the PCB asset and gives it visible mass at hard stops;
- Glass reveal exposes the independent PCB / fan / motor asset stack;
- MECHANICS shows one large mechanism at a time instead of a three-button diagnostic layout;
- DETENT, RATCHET and LATCH each have an independent asset and physical haptic sample;
- THERMAL makes the standalone fan the hero object and carries the maintenance core through airflow;
- final ignition recalls chassis, PCB, fan, motor and maintenance core before the short blackout and `GAME STAR BOX` reveal.

## Platform Boundary

Unchanged:

- Core remains free of Android / WebView / REDMAGIC APIs.
- REDMAGIC SAR scene activation and F7/F8 reader remain typed and reversible.
- `HardwarePlaygroundMotionAdapter` remains the native posture source.
- `HardwareAwakeningMotorDriver` remains the shoulder / ignition physical motor path.
- `HardwarePlaygroundHapticAdapter` remains the material-sample physical motor path.
- Thermal fan remains a visual model only; v7 does not claim REDMAGIC physical fan control.
- OTA privilege surface is untouched.

## Safety

Forbidden paths remain forbidden:

- no arbitrary shell execution;
- no guessed REDMAGIC Settings/sysfs writes;
- no sendevent/input/uinput injection;
- no speaker fake-haptic;
- no persistent ordinary-app `WRITE_SECURE_SETTINGS`.

## One-time Migration

v7 adds:

`hardware_playground_asset_rebuild_migration_0427_v7`

Install-over testing resets `shoulder_calibrated_v1=false` once so the full asset-rebuilt Hardware Playground can be seen without clearing app data. After successful completion normal FAST behavior resumes.

## Testing

Successful temporary CI run:

- workflow run: `34906732148`
- build head: `3e427d7ad3a7278f4cb937707bccd6fcbd042a68`
- artifact: `10372832229`
- artifact archive digest: `sha256:85c9b4dfe7d235c526593e6e0355de3aa659d5e407d9ed3c44023e7cc6adca17`
- APK SHA-256: `ce07d7506e2cb2cbd5ccbfd7b77b570dfebee215cf8b3d0983239dae878d214b`

CI hard gates passed:

- all eight v7 assets exist and are non-empty;
- every v7 asset is referenced by `shoulder-boot.html`;
- v7 one-time migration exists;
- native SensorManager motion adapter remains present;
- physical playground / ignition haptic adapters remain present;
- `android.permission.VIBRATE` remains present;
- DETENT / RATCHET / LATCH visible mechanism staging remains present;
- final `powerRail` ignition recall remains present;
- fake-haptic negative guard passed;
- JDK 17 / API 35 / Gradle 8.9 release build passed;
- fixed DEV signing / artifact upload passed.

Post-build APK inspection confirmed all eight files exist under `assets/hardware-v7/` in the final APK.

Temporary v7 CI workflow was deleted after successful capture.

## REDMAGIC 9 Pro+ Acceptance Focus

1. First impression must no longer read as a web/CSS hardware mockup.
2. Chassis material should read as metal/glass before any system-blue lighting appears.
3. PCB should have enough component density that a frame grab does not look like a green rectangle with labels.
4. Maintenance core should read as a physical object with shell/lens/mass, not a glowing cursor.
5. DETENT / RATCHET / LATCH should each occupy the stage individually and feel materially different before reading labels.
6. Thermal should read as a hardware chamber, not a progress spinner.
7. Final power-on is the first time system blue becomes visually valuable.
8. Main surface must remain already hydrated when branding exits.

## Known Prototype Boundary

`playgroundReady` is still not a third platform-neutral Core input in v7. The target before merge remains:

`hardwareReady && playgroundReady && provisioningReady -> ignition release`

Do not fix this with UI timing, raw-input suppression or presentation-only flags.

## Changelog

### v7

- Added eight standalone hardware assets.
- Removed core hardware illustration responsibility from CSS primitives.
- Rebuilt the material palette around glass / graphite / steel / copper / deep-green PCB.
- Reserved system blue for final ignition.
- Re-staged haptic materials as one-at-a-time hero mechanical objects.
- Rebuilt maintenance core as a materialized mechanical probe with shell and lens.
- Kept all verified hardware / privilege / provisioning contracts unchanged.
- Added one-time migration for install-over testing.
