# Hardware Playground Core Gate — 0.4.27 v9

## Responsibility
Move Hardware Playground progression and final ignition authorization out of presentation-only state and into platform-neutral Core.

## Inputs
- Semantic shoulder input from the existing verified shoulder adapter.
- Semantic stage completion from the first-boot presentation: SHOULDERS, MOTION, GLASS, MECHANICS, THERMAL.
- Hidden first-surface provisioning readiness.
- Explicit boot session mode: INTERACTIVE or FAST.

## Outputs
- Ordered playground progress snapshot.
- Final-grip authorization.
- Three-input ignition gate state.
- Stable Physical Event vocabulary for high-render scene production.

## State Machine
Interactive progression is strictly ordered:

`SHOULDERS -> MOTION -> GLASS -> MECHANICS -> THERMAL -> FINAL_GRIP -> COMPLETE`

Out-of-order stage completion is rejected and logged as:

`GSB-PLAYGROUND-STAGE-ORDER-REJECTED`

The shoulder state machine may enter `BOTH_HOLD` after left/right calibration, but ignition hold timing remains disabled until Core reaches `FINAL_GRIP`.

When `FINAL_GRIP` is enabled while either shoulder is already held, Core sets `freshBothRequired=true`. Both shoulders must be fully released before a new L+R hold may count.

## Ignition Gate
Interactive ignition now requires:

`hardwareReady && playgroundReady && provisioningReady`

FAST mode is a typed session mode. It explicitly bypasses interactive playground completion while still requiring hardware ARMED and provisioning readiness.

No UI delay, raw-input suppression, timeout hack, or presentation-only boolean opens ignition.

## Platform Implementation
`BootMainActivity` owns the Android bridges and translates semantic presentation completion into `HardwarePlaygroundProgress` calls.

`ShoulderBootStateMachine` remains platform-neutral and contains no Android APIs.

`IgnitionGate` remains platform-neutral and owns the final three-input AND gate.

## Physical Event Contract
Core now owns the stable event vocabulary:

- `SHOULDER_LEFT_FOCUS`
- `SHOULDER_RIGHT_FOCUS`
- `MOTION_TILT`
- `GLASS_REVEAL`
- `DETENT_STEP`
- `RATCHET_TOOTH`
- `LATCH_RELEASE`
- `THERMAL_SPINUP`
- `IGNITION`

`hardware-model/model-manifest.json` binds every event to a preferred render mode, runtime driver and DCC source object. Android and Gradle both validate the event set.

## Safety / Privileged Boundary
No privileged surface changed.

The existing REDMAGIC shoulder reader, temporary game-scene handling, Shizuku boundary, F7/F8 filtering and restoration behavior are unchanged.

Core contains no Android, Shizuku, Settings, shell, `/system/bin`, or `/sys` dependency.

## Performance
The new progress and ignition gates are synchronized in-memory state machines. No polling, allocation-heavy rendering, network access, storage access or new background thread is introduced.

## Logs
- `[GSB-PLAYGROUND]`
- `[GSB-BOOT]`
- `[GSB-MODEL]`
- `[GSB-SHOULDER]`

## Error Codes
- `GSB-PLAYGROUND-STAGE-ORDER-REJECTED`
- `GSB-PLAYGROUND-ARMED-BEFORE-FINAL-GRIP`
- `GSB-MODEL-PHYSICAL-EVENT-CONTRACT-INVALID`
- Existing model-pack and motion/haptic error codes remain unchanged.

## Testing
CI run `34931109261` passed:

- Core boundary guard.
- Model Physical Event contract validation.
- JUnit Core tests.
- Signed Android API 35 release build.
- APK staging and artifact upload.

Core tests cover:

1. Interactive ignition requires hardware + playground + provisioning.
2. FAST bypasses only the interactive playground requirement.
3. Out-of-order stage completion is rejected.
4. Early L+R cannot arm ignition and final grip requires a fresh release/re-press.

Artifact:

- GitHub artifact ID: `10381607609`
- Artifact ZIP digest: `sha256:0e9f57acc6ec63f49911f3ab8a2cc1fbe6a81c301bf152fe220547d27f3dbf8b`
- APK SHA-256: `e50ff690ca9bd7747a75c06f1e26d418b38af39955516742430390fea2897885`

## REDMAGIC 9 Pro+ Acceptance Focus
On install-over, the v9 migration resets the calibration flag once so the full interactive path is exercised.

Acceptance should verify:

- pressing L+R during Motion/Glass/Mechanics/Thermal never starts ignition progress;
- entering Final while already holding a shoulder shows a release/re-grip requirement;
- only a fresh L+R hold in Final reaches ARMED;
- ignition still waits for hidden provisioning readiness;
- physical shoulder, motion and vibrator behavior remain unchanged otherwise.

## Changelog
- Added `HardwarePlaygroundProgress` Core state machine.
- Added `playgroundReady` to `IgnitionGate`.
- Added typed FAST/INTERACTIVE gate semantics.
- Hardened `ShoulderBootStateMachine` final grip.
- Added Physical Event Core enum and model-scene validation.
- Added v9 install-over migration.
- Added Core unit tests.
