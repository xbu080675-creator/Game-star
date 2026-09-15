# Game Star Box 0.4.27 — Handheld Secondary UX v10.4

## Responsibility
Bring secondary Handheld surfaces into the same presentation and navigation language as Handheld HOME without changing Console theme behavior or Core boot semantics.

## Inputs
- Existing `.libraryScene`, `.esportsScene`, `.systemScene` surfaces.
- Existing `game-library.js` picker DOM and launchable-app metadata.
- Existing Handheld theme state from `theme-runtime.js`.

## Outputs
- Explicit touch-visible back control on every non-HOME Handheld scene.
- Handheld-specific application picker presentation.
- Search/filter and count feedback for long application lists.
- B / Escape closes an open picker first, then returns a secondary scene to HOME.

## State / navigation
- HOME -> secondary scene: existing dock/tab navigation.
- Secondary scene -> HOME: `← 返回`, B, or Escape.
- Picker open -> close picker: picker return control, B, or Escape.
- Picker open never causes B / Escape to skip directly to HOME.

## Presentation
- Secondary surfaces reserve a shallow top lane for `← 返回`.
- Picker uses warm neutral Handheld materials rather than the legacy dark Console modal.
- Landscape picker uses a two-column 64 px application list and collapses to one column on narrow widths.
- Search filters by visible app label/package text and exposes live result count.
- Existing real Android launcher icons are preserved when already supplied by the catalog; generic launcher candidates remain metadata-only until the lazy icon capability is wired through the WebView bridge. The picker does not synthesize branded artwork.

## Platform implementation
- `handheld-secondary-v10_4.css` owns presentation only.
- `handheld-secondary-v10_4.js` owns secondary navigation and picker enhancement only.
- Both are hydrated by `native-update.js` before the main surface is marked ready.
- No Android API, Shizuku capability, REDMAGIC control, or Core dependency is introduced.

## Safety
- No Nintendo/Switch assets, logos, fonts, screenshots, or proprietary system graphics are bundled.
- Console theme behavior is not modified.
- No generic shell or input injection is introduced.

## Performance
- Picker remains DOM-based and reuses the already-enumerated launchable catalog.
- Search is local filtering; no repeated package scan is triggered.
- Secondary assets are small static CSS/JS and are preloaded during the hidden main-surface hydrate.

## Logs / error codes
No new platform error code is required because the feature is presentation-only and fails closed to the existing secondary surfaces.

## Testing
CI run `34953971411` passed:
- Handheld secondary UX guard.
- Embedded JS syntax checks.
- FAST Wake v10.2 regression.
- Thermal Rotor v10.1 regression.
- v9 Core ignition-gate regression.
- Model event contract validation.
- Core JUnit.
- Signed release build.
- Final APK asset staging.

Artifact:
- ID: `10390741600`
- ZIP digest: `sha256:ece971a7233d55d1ca3c131265b12037f37c056c56d2a45637c4f7cca2887a95`
- APK SHA-256: `92bb52de15020641a563004e6b15cf452ce07112738a86a6262fbf46c12c04b8`

## Acceptance
1. Handheld application picker no longer renders as the dark Console modal.
2. Long application lists are compact, scrollable, searchable, and suitable for landscape touch use.
3. Library, Esports, and System secondary scenes expose a visible `← 返回` control.
4. B / Escape closes the picker before navigating away from the scene.
5. Existing boot, shoulder, Thermal, model and Console-theme behavior remains intact.

## Changelog
- v10.4: secondary-scene back affordance and Handheld application-picker redesign.
