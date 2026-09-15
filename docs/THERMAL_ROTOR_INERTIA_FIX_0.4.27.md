# Game Star Box 0.4.27 — Thermal Rotor Inertia Fix v10.1

## Responsibility

Repair the Hardware Playground Thermal interaction on Android WebView and replace the old raw-angle completion gate with a small inertial rotor model.

## Inputs

- One foreground pointer interacting with `#fanGesture`.
- Pointer angular movement around the visual rotor center.
- Existing `HardwarePlaygroundProgress` stage order.
- Existing native Playground haptic adapter.

## Outputs

- Continuous rotor angle.
- Signed angular velocity.
- Inertial free-spin after release.
- Airflow energy derived from rotor speed.
- Accumulated airflow work used for Thermal completion.
- Maintenance-core displacement driven by airflow progress.

## State Model

`IDLE -> CAPTURED -> SPINNING / COASTING -> AIRFLOW_STABLE -> FINAL`

Thermal may only begin after the Core accepts `MECHANICS`. Thermal completion still reports `stageComplete("THERMAL")`; the v9 Core gate remains authoritative for entering Final Grip.

## Pointer Capture

The previous implementation listened for `pointermove` only on `#fanGesture` without capturing the active pointer. Android WebView could therefore stop delivering movement when the finger drifted outside the invisible circular hit region.

v10.1 calls `setPointerCapture(pointerId)` on pointer down and releases capture on pointer up/cancel. `lostpointercapture` also clears local drag state.

## Rotor Physics

The old implementation accumulated absolute drag angle and completed after roughly 660 degrees. That behaved like a calibration gesture rather than a physical rotor.

The new model:

- converts angular pointer delta / delta-time into signed angular velocity;
- clamps pathological input spikes;
- applies exponential drag after release;
- derives airflow energy from absolute angular velocity;
- applies separate rise/decay response to airflow;
- integrates useful airflow energy over time as `airflowWork`;
- completes only after sufficient effective airflow work.

The user may now flick the rotor and release it. Continued free-spin still contributes airflow until drag removes the stored angular momentum.

## Presentation

- Prompt changed from continuous turning to a flick/inertia instruction.
- Airflow visibility follows current airflow energy.
- Maintenance core is carried progressively across the chamber as useful airflow work accumulates.
- A single light native haptic cue marks meaningful airflow spin-up.
- No real REDMAGIC cooling-fan control is claimed or performed.

## Platform Implementation

Presentation remains in `app/src/main/assets/shoulder-boot.html`.

No Android API was added to Core. No generic privileged execution or vendor fan write was introduced.

## Migration

`hardware_playground_thermal_rotor_fix_migration_0427_v10_1`

The migration resets `shoulder_calibrated_v1` once so install-over real-device validation reaches the repaired interactive Thermal stage without requiring data clearing.

## Safety

- The rotor is a visual Thermal model only.
- Pointer capture is limited to the active foreground WebView interaction.
- Native haptics remain vibrator-backed; audio is not used as tactile output.
- Existing v9 `hardwareReady && playgroundReady && provisioningReady` ignition gate remains unchanged.

## Performance

- One `requestAnimationFrame` loop exists only while `play === "THERMAL"`.
- The loop is cancelled when entering Final.
- Updates are transform / opacity / CSS-variable based.
- No network, bitmap decoding or background worker is added.

## Logs / Error Codes

No new native log surface is required. Existing `[GSB-PLAYGROUND]` stage-order diagnostics remain authoritative.

A future native physics implementation should retain typed errors rather than add a generic JS/native command bridge.

## Testing

Acceptance:

1. Rotor begins responding immediately on Android WebView pointer down/move.
2. Drag continues after the finger leaves the original circular hit region.
3. Pointer up/cancel releases capture safely.
4. Rotor visibly coasts after release.
5. Airflow rises with speed and falls as the rotor slows.
6. Thermal cannot complete from a stationary tap.
7. Completion no longer depends on a hard-coded 660-degree drag total.
8. v9 Core Gate / fresh L+R re-grip behavior remains unchanged.
9. Install-over forces one complete Interactive run for validation.

## Changelog

### v10.1

- Added Pointer Events capture for the Thermal rotor.
- Replaced raw degree accumulation with angular velocity + inertial drag.
- Added airflow energy/work integration.
- Added one-time install-over validation migration.
