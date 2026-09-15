# Game Star Box 0.4.27 — Handheld Icon-First Polish v10.3

## Responsibility
Refine the Handheld HOME visual language without changing game discovery, launch semantics, Hardware Playground, or Core gates.

## Inputs
- Existing `InstalledGameCatalog` Android launcher icon data for auto-detected games.
- Existing `game-library.js` software rail and library data.
- Existing `theme-runtime.js` Console / Handheld presentation state.

## Outputs
- Warm neutral Handheld palette.
- Real Android launcher icon as the software tile's dominant visual.
- Reduced synthetic chrome, shadow, glow and abstract branding in Handheld HOME.

## Presentation rules
- Background: warm neutral, not blue-tech.
- Primary text: graphite.
- Focus accent: warm coral (`#f16450`), used only for focus/state.
- Software card: transparent system shell; launcher icon remains the artwork.
- Synthetic radial/gradient layers remain available to Console but are suppressed by Handheld CSS.
- Handheld hides the old circular top brand mark and keeps the wordmark/status chrome shallow.
- Library tiles use the same icon-first treatment for consistency.

## Platform implementation
No new Android API is introduced. `InstalledGameCatalog` remains read-only and continues to pre-rasterize icons only for auto-detected games. Generic launchables remain metadata-only during catalog prewarm to avoid reintroducing startup stalls.

## Safety / performance
- No third-party console logos, proprietary system icons, fonts or screenshots are bundled.
- No all-launcher icon rasterization during first-surface prewarm.
- Manual generic launchables that do not already carry icon data are not faked with generated artwork; a future narrow lazy-icon bridge may fill that gap.

## Regression boundaries
The following are unchanged and were covered by CI:
- FAST Wake v10.2 terminal-state handling.
- Thermal Rotor v10.1 pointer capture / inertia.
- v9 Core ignition gate and fresh final re-grip.
- Model Physical Event contract.

## CI / artifact
- Run: `34939134869`
- Artifact: `10384003046`
- Artifact ZIP digest: `sha256:2ddcd8e089e4785e4233ed8f6ce575436370435f6db35ad74ff1f4e78df7c319`
- APK SHA-256: `9c6738761954d57b517748e3981e50c1412cf99774855cb9897676860e6e2e13`

## Acceptance
1. Handheld primary focus is warm coral, not cyan/blue.
2. Auto-detected game tiles visibly use the Android launcher icon as their main artwork.
3. Handheld software cards do not add the old synthetic blue gradient/radial treatment.
4. Console theme remains functionally unchanged.
5. FAST wake, Thermal and Core gate regressions stay green.

## Changelog
- v10.3: icon-first Handheld presentation and warm-neutral palette.
