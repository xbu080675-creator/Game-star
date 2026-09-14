# Game Star Box Hardware Model Source Workspace

## Responsibility

Authoritative workspace contract for the high-render Hardware Playground model sources. This directory is for DCC source files and hand-authored production materials only; it is not part of the Android assets directory and therefore does not ship in the APK.

## Inputs

Approved source models created in Blender, Cinema 4D, Maya/FBX, USD-capable tools, Houdini/Alembic workflows, or Unreal-derived offline render workflows.

## Outputs

Rendered APK-facing deliverables copied to:

`app/src/main/assets/hardware-model/render-v8/`

Optional event shots belong under:

`app/src/main/assets/hardware-model/shots-v8/`

The Android runtime consumes only rendered outputs defined by `model-manifest.json`; it never parses or renders DCC source files.

## Required Source Objects

- `GSB_CHASSIS_ASSEMBLY`
- `GSB_PCB_ASSEMBLY`
- `GSB_THERMAL_FAN`
- `GSB_0815_LINEAR_MOTOR`
- `GSB_MECH_DETENT`
- `GSB_MECH_RATCHET`
- `GSB_MECH_LATCH`
- `GSB_MAINTENANCE_CORE`

Names are semantic export anchors, not mandates on DCC hierarchy. A DCC project may contain arbitrary helper objects as long as the render/export pipeline can resolve these anchors.

## Material Direction

Base materials are physical, not UI-styled:

- black glass;
- graphite/anodized dark metal;
- stainless/silver mechanical parts;
- copper conductors;
- dark green PCB substrate;
- restrained emissive system light only when powered.

Large-area cyan/blue glow is not a base material. System blue is reserved for actual power-on/emission states.

## Camera / Render Direction

Assets must survive close-up cinematography. Geometry, bevels, roughness, reflections, contact shadows, thickness and mechanical clearances should be authored so a shoulder-key macro shot or latch impact still looks plausible.

## Safety

Do not import proprietary Sony/PlayStation models, textures, characters, controller geometry, audio, or extracted game resources. The design language must be original and device-specific.

## Performance

DCC source complexity is unconstrained by APK runtime because sources do not ship. Optimize the *render outputs* for device decode cost instead of destroying source fidelity prematurely.

## Versioning

Large binary DCC sources should be stored through Git LFS where enabled. Render outputs should be deterministic and traceable to a source revision and render settings preset.

## Changelog

### v8

- Established the non-shipping high-render model source workspace.
- Decoupled DCC authoring from Android runtime assets.
