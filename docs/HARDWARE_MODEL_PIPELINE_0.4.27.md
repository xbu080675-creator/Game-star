# Hardware Model Asset Pipeline — 0.4.27 v8

## Responsibility

v8 establishes the production pipeline that lets Hardware Playground consume high-render model outputs without coupling Android/Core code to Blender, Cinema 4D, Unreal, FBX, USD, or any particular DCC/render engine.

v8 is **not** a high-model visual release. No fake model art was generated for this pass. Until genuine high-render deliverables exist, the verified v7 asset pack remains the explicit prototype fallback.

## Inputs

- Non-shipping DCC source assets under `art/model-source/`.
- High-render raster outputs produced by an external DCC/render workflow.
- `app/src/main/assets/hardware-model/model-manifest.json`.
- Stable logical asset requests already used by Hardware Playground.

## Outputs

- Typed runtime model-asset substitution through `HardwareModelAssetAdapter`.
- Atomic full-pack activation/fallback.
- Build-time model-pack validation through `validateHardwareModelPack`.
- Git LFS policy for large DCC source formats.
- APK-facing model render contract independent from DCC choice.

## State Model

Two visual-pack states are allowed:

```text
PROTOTYPE_FALLBACK
  productionReady=false
  -> all v7 assets remain authoritative

PRODUCTION_MODEL_PACK
  productionReady=true
  -> validate every required high-render output
  -> if every entry is valid: activate all substitutions
  -> if any entry is missing/invalid: fail closed; do not activate a mixed pack
```

A half-v7 / half-high-render presentation is intentionally forbidden.

## Required Model Anchors

The source workspace defines eight semantic anchors:

- `GSB_CHASSIS_ASSEMBLY`
- `GSB_PCB_ASSEMBLY`
- `GSB_THERMAL_FAN`
- `GSB_0815_LINEAR_MOTOR`
- `GSB_MECH_DETENT`
- `GSB_MECH_RATCHET`
- `GSB_MECH_LATCH`
- `GSB_MAINTENANCE_CORE`

These are export/render anchors rather than mandates on internal DCC hierarchy.

## Required v8 Render Outputs

The production manifest maps the existing stable logical requests to these intended outputs:

- `hardware-model/render-v8/chassis-shell.webp`
- `hardware-model/render-v8/pcb-board.webp`
- `hardware-model/render-v8/cooling-fan.webp`
- `hardware-model/render-v8/linear-motor.webp`
- `hardware-model/render-v8/mechanism-detent.webp`
- `hardware-model/render-v8/mechanism-ratchet.webp`
- `hardware-model/render-v8/mechanism-latch.webp`
- `hardware-model/render-v8/maintenance-core.webp`

Production SVG paths are rejected. Accepted raster MIME types are WebP, PNG, and AVIF.

## Optional Shot Contract

The manifest reserves semantic event-bound shot media for later production:

- `SHOULDER_LEFT_FOCUS`
- `SHOULDER_RIGHT_FOCUS`
- `MOTION_TILT`
- `GLASS_REVEAL`
- `DETENT_STEP`
- `RATCHET_TOOTH`
- `LATCH_RELEASE`
- `THERMAL_SPINUP`
- `IGNITION`

Supported presentation forms are `still`, `clip`, `scrub-clip`, `pose-grid`, and `layer-stack`.

This keeps future high-render cinematography bound to physical events rather than UI screen names.

## Platform Implementation

### `HardwareModelAssetAdapter`

Location:

`app/src/main/java/com/xbu/esportscenter/platform/android/HardwareModelAssetAdapter.java`

Responsibilities:

- load and validate schema v1 manifest;
- keep stable logical requests from the current presentation;
- intercept packaged `/android_asset/` requests only when a complete production pack is active;
- serve the mapped high-render raster output;
- perform no network download and no DCC work at runtime;
- fail closed to the existing prototype pack when the production pack is disabled or invalid.

Stable log prefix: `[GSB-MODEL]`.

### Boot integration

`BootMainActivity` owns one `HardwareModelAssetAdapter` and installs it as the local Hardware Playground WebView client. Core remains unaware of model/render formats.

### Build integration

`app/model-pipeline.gradle` adds:

`validateHardwareModelPack`

and wires it into `preBuild`.

Validation includes:

- schema version;
- exactly eight required logical entries;
- unique asset IDs;
- v7 prototype fallback existence;
- production render-root containment;
- raster MIME allow-list;
- SVG/vector production rejection;
- complete/non-empty production output enforcement when `productionReady=true`.

## DCC Source Workspace

`art/model-source/` is intentionally outside Android assets and does not ship in the APK.

The project does not mandate one authoring package. Approved source workflows may use Blender, Cinema 4D, FBX/Maya-style exchange, USD, Alembic, or offline Unreal rendering as long as the APK-facing render contract is satisfied.

`.gitattributes` routes large binary DCC/source formats through Git LFS where available.

## Material Direction

The pipeline contract preserves the established physical-material direction:

- black glass;
- graphite/anodized dark metal;
- stainless/silver mechanical parts;
- copper;
- dark green PCB substrate;
- restrained emissive system light only for powered states.

Large-area cyan/blue glow is not a base material. System blue should gain value by appearing as a power-state consequence.

## Safety / Boundary

- No runtime network asset download.
- No executable model-pack payload.
- No arbitrary file-system probing.
- DCC sources never ship inside the APK.
- No proprietary Sony/PlayStation models, textures, characters, controller geometry, audio, or extracted game resources.
- Thermal fan remains a visual model; v8 adds no REDMAGIC fan-control claim.
- Existing shoulder, motion, haptic, Shizuku, provisioning and OTA privilege boundaries are unchanged.

## Fallback Rule

Current committed manifest state:

`productionReady=false`

This is deliberate. Genuine high-render files have not yet been supplied, so v8 must continue to display the v7 prototype assets.

The runtime log should report the production model pack as unavailable and use prototype fallback. That is success, not an error, for this pipeline-only revision.

## Error Codes

- `GSB-MODEL-MANIFEST-SCHEMA-UNSUPPORTED`
- `GSB-MODEL-MANIFEST-INVALID`
- `GSB-MODEL-MANIFEST-READ-FAILED`
- `GSB-MODEL-PACK-INCOMPLETE`
- `GSB-MODEL-PRODUCTION-VECTOR-FORBIDDEN`
- `GSB-MODEL-ASSET-READ-FAILED`

Build-time model errors use the same `GSB-MODEL-*` family where applicable.

## Performance

- DCC source complexity does not affect APK runtime because sources do not ship.
- Hero stills should prefer WebP when quality is sufficient.
- Interactive motion should use bounded clips, pose grids or layer stacks instead of uncontrolled raw frame dumps.
- Runtime substitution is local packaged-asset I/O only.
- Main-surface hidden hydration remains unchanged.

## Testing

Final successful temporary CI run:

- workflow run: `34911061713`
- build head: `4aa2d8684f218a120ba21b9aa0cbb1f6c7e077d8`
- artifact: `10374293315`
- artifact ZIP digest: `sha256:1022bca9d6f219ac8a4f1e960b4b9a6c9e588c6a3782f338ba25e2d0d49512fc`
- APK SHA-256: `ad7400c01a47d61ba6943e584144381cb49180c1833f1e1ada48dd410cacb054`

Successful gates:

- runner Android SDK/API 35 setup: PASS;
- v8 model pipeline contract guard: PASS;
- `validateHardwareModelPack`: PASS;
- `assembleRelease`: PASS;
- fixed DEV signing: PASS;
- artifact staging/upload: PASS;
- APK inspection: `assets/hardware-model/model-manifest.json` present;
- APK inspection: v7 prototype fallback assets remain present;
- packaged manifest confirms `productionReady=false`.

Two earlier temporary CI attempts failed before any project code ran because the 2026 Ubuntu runner no longer exposes the obsolete Android SDK `tools` package expected by the initial setup path. The final workflow bypassed that obsolete action behavior and used the runner's preinstalled command-line SDK directly. The temporary workflow was deleted after successful verification.

## Install-over Test

v8 adds one-time migration:

`hardware_playground_model_pipeline_migration_0427_v8`

Its purpose is to exercise the model-adapter/fallback path once on a real REDMAGIC 9 Pro+ after an in-place install. It does not claim that production model renders are present.

## Known Architecture Boundary

The pre-existing prototype limitation remains outside the model pipeline:

`playgroundReady` has not yet become a third platform-independent input to the ignition gate.

Before merge to `main`, target semantics remain:

`hardwareReady && playgroundReady && provisioningReady -> ignition release`

Do not solve this with UI delays, raw-input suppression, or presentation-only flags.

## Production Activation Procedure

1. Author the real high-detail models in the DCC workspace.
2. Render all eight required APK-facing outputs at the approved camera/material settings.
3. Place outputs under `app/src/main/assets/hardware-model/render-v8/` with the exact manifest names.
4. Run `gradle :app:validateHardwareModelPack` while `productionReady=false` for path/contract checks.
5. Set `pack.productionReady=true` only when every required output is genuine and final for the candidate pack.
6. Run the validator again; any missing/invalid output must block the build.
7. Build the release APK and inspect packaged assets.
8. Validate visual fidelity, decode latency and memory behavior on REDMAGIC 9 Pro+.

## Changelog

### v8

- Added typed high-render model-pack manifest.
- Added non-shipping DCC source workspace.
- Added Git LFS policy for large model/source files.
- Added build-time atomic model-pack validator.
- Added Android `HardwareModelAssetAdapter` with full-pack runtime substitution.
- Wired Hardware Playground WebView through the model adapter.
- Added one-time install-over model-pipeline migration.
- Kept production render activation intentionally disabled until real high-render assets are supplied.
- Generated no fake high-model art in this development pass.
