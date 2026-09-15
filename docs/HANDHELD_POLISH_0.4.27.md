# Game Star Box 0.4.27 — Handheld Polish v10.3

## Responsibility
Refine the Handheld presentation language without changing catalog, launch, session, Core boot or REDMAGIC hardware behavior.

## Inputs
- `InstalledGameCatalog` real Android launcher/game metadata.
- `ResolveInfo.loadIcon(PackageManager)` rasterized to 192×192 PNG for immediately visible games.
- Existing `theme-runtime.js` Handheld/Console presentation state.

## Outputs
- `handheld-theme-polish-v10_3.css`, loaded after the base Handheld stylesheet during hidden main-surface hydration.
- Real application icons become the primary software rail artwork.
- New Handheld palette: ice-gray system surfaces, graphite text and restrained indigo focus accent.

## State / Navigation
No state-machine changes. Theme selection still uses `gsb.ui.theme.v10`; HOME focus/navigation semantics are unchanged.

## Presentation
- Remove the old circular abstract brand mark in Handheld mode.
- Remove the white cover-card look around installed games.
- Increase native app-icon artwork to 92% of the software tile and suppress the synthetic decorative layers.
- Keep the focus ring outside the icon so selection state does not replace the artwork.
- Keep bottom system-action labels visible instead of relying on glyph interpretation.
- Apply the same icon-first treatment to the Library grid.

## Platform implementation
No new Android API. The icon source remains `InstalledGameCatalog`; presentation is CSS-only plus the existing hidden-surface stylesheet loader.

## Safety / Boundaries
- No Nintendo branding, copyrighted game artwork, proprietary icons or screenshots are bundled.
- No Core dependency on presentation/theme state.
- No new native privilege surface.

## Performance
The polish layer is a small static CSS asset. No additional icon decoding, canvas analysis or dominant-color extraction is performed at runtime.

## Regression coverage
CI verifies:
- Handheld polish CSS is bundled and preloaded.
- Real app icons still originate from `ResolveInfo.loadIcon` at 192px.
- FAST Wake v10.2 remains fixed.
- Thermal Rotor v10.1 remains fixed.
- v9 three-input Core Gate remains intact.
- Model Physical Event contract and Core unit tests remain green.

## Artifact
- CI run: `34939082556`
- Artifact: `10383989770`
- Artifact ZIP digest: `sha256:67c90a53ed8013fbad29a81fa23cdee1d7087470f3c30c164fbced12a17a9deb`
- APK SHA-256: `3b058bb44340b2479267b13807af5dbb0d84b2b216ab09c02311394e324f7dc2`

## Changelog
### v10.3
- Reworked Handheld color system.
- Made real Android app icons the visual object instead of a cover-card decoration.
- Reduced abstract chrome and made bottom labels persistent.
- Preserved all v10.2/v10.1/v9 functional behavior.
