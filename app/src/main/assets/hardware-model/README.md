# Hardware Model Render Pack

## Responsibility

This directory is the APK-facing contract for high-render model outputs used by Hardware Playground v8 and later. It is not a model-authoring directory and must not contain fake vector replacements pretending to be production renders.

## Inputs

- DCC-authored source models from the repository's model-source workspace.
- Approved render exports generated outside the Android runtime.
- `model-manifest.json` as the only authoritative logical-asset mapping.

## Outputs

- Production-ready still renders under `render-v8/`.
- Optional event-bound shot media under `shots-v8/`.
- Stable logical replacement of the v7 prototype assets through `HardwareModelAssetAdapter`.

## State

`pack.productionReady=false`

The repository intentionally ships with production model replacement disabled until all required high-render outputs exist and pass validation. No missing production render is silently substituted with another production-looking placeholder.

## Platform Implementation

`HardwareModelAssetAdapter` validates the manifest atomically. When `productionReady=true`, every required render must exist and use an allowed raster MIME type before any substitution is activated.

The page continues to request stable logical asset URLs such as `hardware-v7/chassis-shell.svg`. The Android adapter intercepts those requests and serves the validated high-render asset. This keeps presentation code independent of Blender/C4D/Unreal or any particular renderer.

## Render Contract

Required v8 still outputs:

- `render-v8/chassis-shell.webp`
- `render-v8/pcb-board.webp`
- `render-v8/cooling-fan.webp`
- `render-v8/linear-motor.webp`
- `render-v8/mechanism-detent.webp`
- `render-v8/mechanism-ratchet.webp`
- `render-v8/mechanism-latch.webp`
- `render-v8/maintenance-core.webp`

Production outputs must originate from high-render model assets. SVG is explicitly forbidden as a production render path.

Optional event media may use `still`, `clip`, `scrub-clip`, `pose-grid`, or `layer-stack` modes. Event IDs are defined by the manifest and are semantic physical events, not UI screen names.

## Safety

- No network download at runtime.
- No dynamic executable code from asset packs.
- No generic file-system probing.
- Paths must be relative, packaged Android assets.
- Incomplete production packs fail closed to the full v7 prototype pack; mixed packs are prohibited.

## Performance

- Static hero renders should prefer WebP where visual quality is acceptable.
- Large animated sequences should use short bounded clips or pose grids rather than unconstrained frame dumps.
- DCC source files never ship inside the APK.
- Runtime asset resolution must not block the UI thread on network or external storage.

## Logs

Stable prefix: `[GSB-MODEL]`

## Error Codes

- `GSB-MODEL-MANIFEST-SCHEMA-UNSUPPORTED`
- `GSB-MODEL-MANIFEST-INVALID`
- `GSB-MODEL-MANIFEST-READ-FAILED`
- `GSB-MODEL-PACK-INCOMPLETE`
- `GSB-MODEL-PRODUCTION-VECTOR-FORBIDDEN`
- `GSB-MODEL-ASSET-READ-FAILED`

## Testing

A production pack may only be enabled when all required entries exist, no production entry resolves to SVG/v7 prototype assets, and release packaging confirms the render files are present under `assets/hardware-model/`.

## Changelog

### v8

- Added a typed high-render model asset contract.
- Added atomic full-pack activation and full-pack fallback.
- Kept DCC choice outside Android/Core architecture.
- Explicitly prohibited vector files from masquerading as production high-render output.
