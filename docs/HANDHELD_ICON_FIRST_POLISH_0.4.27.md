# Game Star Box 0.4.27 — Handheld Icon-First Polish v10.3.1

## Responsibility
Refine the Handheld HOME visual language without changing game discovery, launch semantics, Hardware Playground, or Core gates.

## Inputs
- `InstalledGameCatalog` Android launcher icon data.
- Existing `game-library.js` software rail and library data.
- Existing `theme-runtime.js` Console / Handheld presentation state.

## Outputs
- Warm neutral Handheld palette.
- Real Android launcher icon as the software tile's dominant visual.
- Reduced synthetic chrome, shadow, glow and abstract branding in Handheld HOME.
- One consistent line-glyph language for the Handheld system dock.

## Presentation rules
- Background: warm neutral, not blue-tech.
- Primary text: graphite.
- Focus accent: warm coral (`#f16450`), used only for focus/state.
- Software card: transparent system shell; launcher icon remains the artwork.
- Synthetic radial/gradient layers remain available to Console but are suppressed by Handheld CSS.
- Handheld hides the old circular top brand mark and keeps the wordmark/status chrome shallow.
- Library tiles use the same icon-first treatment for consistency.
- Dock glyphs use `fill:none`, `stroke:currentColor`, one stroke weight, round caps and round joins; filled abstract blobs are prohibited.

## Platform implementation
`InstalledGameCatalog` keeps the initial prewarm rule: auto-detected games receive real launcher icons while generic launchables stay metadata-only so first-surface hydration does not rasterize the entire launcher inventory.

A narrow `iconDataUrl(packageName)` capability now exists inside the Android catalog adapter for future on-demand/manual icon retrieval. It validates that the package is a visible launcher target, resolves the package's launcher activity/icon, rasterizes only that requested icon at 192 px, and caches the result. The current Handheld HOME path continues to consume the existing catalog icon field; no all-app eager rasterization was introduced.

## Safety / performance
- No third-party console logos, proprietary system screenshots or fonts are bundled.
- No all-launcher icon rasterization during first-surface prewarm.
- Real Android launcher art is preserved instead of replacing software identity with generated artwork.
- The lazy icon capability is read-only and only accepts visible launcher packages.

## Regression boundaries
The following are unchanged and were covered by CI:
- FAST Wake v10.2 terminal-state handling.
- Thermal Rotor v10.1 pointer capture / inertia.
- v9 Core ignition gate and fresh final re-grip.
- Model Physical Event contract.

## CI / artifact
- Run: `34944461621`
- Artifact: `10386995030`
- Artifact ZIP digest: `sha256:6e60052e213527d771af6d4955d3b39125853578e289a9b9bca1c332345c89b5`
- APK SHA-256: `562d855f98c93844a08602258c9170870d923ba95b9f984582b9316a47401a79`

## Acceptance
1. Handheld primary focus is warm coral, not cyan/blue.
2. Auto-detected game tiles visibly use the Android launcher icon as their main artwork.
3. Handheld software cards do not add the old synthetic blue gradient/radial treatment.
4. Handheld dock glyphs render as a coherent line-icon family, not default-filled SVG blobs.
5. Console theme remains functionally unchanged.
6. FAST wake, Thermal and Core gate regressions stay green.

## Changelog
- v10.3: icon-first Handheld presentation and warm-neutral palette.
- v10.3.1: unified dock glyph rendering; added narrowly scoped lazy launcher-icon capability without enabling eager all-app rasterization.
