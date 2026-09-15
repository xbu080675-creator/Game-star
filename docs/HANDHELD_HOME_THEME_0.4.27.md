# Game Star Box 0.4.27 — Handheld Home Theme v10

## Responsibility

Add a second, handheld-first HOME presentation to Game Star Box without replacing the existing console-oriented theme or forking the game/session data layer.

The new theme is informed by the interaction hierarchy of modern handheld HOME menus: user/status information stays shallow, installed software becomes the dominant horizontal rail, and system destinations move into a compact bottom dock. It does not bundle Nintendo logos, Nintendo artwork, proprietary icons, screenshots, fonts, or copied assets.

## Inputs

- Existing `InstalledGameCatalog` / `GSBGames` catalog.
- Existing game card DOM rendered by `game-library.js`.
- Existing `GameSession` state.
- Pointer/touch, keyboard/gamepad-style key events and current WebView runtime.
- Theme selection stored locally in WebView localStorage.

## Outputs

Two persistent UI themes:

- `console`: existing Game Star Box large-screen console layout.
- `handheld`: square software rail + status-first top chrome + circular system dock.

Theme selection is exposed by `window.GSBTheme` and persisted under `gsb.ui.theme.v10`.

## State Model

`console <-> handheld`

Theme changes do not mutate game/session state and do not restart the Activity.

For v10 testing, when no previous v10 selection exists the runtime defaults to `handheld`, so install-over testers immediately see the new direction. Once the user chooses a theme, the preference is persistent.

## Handheld HOME Information Architecture

Top:

- Game Star Box user identity/avatar treatment.
- Local clock.
- Existing connectivity/display status.
- Explicit theme switch.

Center:

- Installed games as large square software icons.
- One focused item at a time.
- Focused game title shown independently from the tile.
- The same real app icon and launch bridge used by the existing catalog.
- A/Enter launches the selected item; touch on an unfocused tile focuses it, a second touch launches it.

Bottom system dock:

- Game Library
- Esports
- Screenshot guidance
- Controllers
- Game Console / Quick Menu
- Theme
- System
- Sleep guidance

Screenshot and sleep are deliberately not faked as native controls. Until typed Android adapters exist, they only explain that the action remains owned by Android/REDMAGIC.

## Navigation

Handheld HOME:

- Left/Right on software row: existing game-card selection.
- Down: enter system dock.
- Left/Right in dock: move dock focus.
- Up: return to software row.
- A/Enter: activate current software/dock item.
- B/Escape from secondary scenes: return HOME.
- Brand tap from a secondary handheld scene: return HOME.

Console theme keeps the existing navigation model unchanged.

## Platform Implementation

Assets:

- `app/src/main/assets/handheld-theme.css`
- `app/src/main/assets/theme-runtime.js`

Hydration:

`native-update.js` injects `handheld-theme.css` and `theme-runtime.js` during hidden first-surface prewarm, before `mainSurfaceReady()`. Therefore the user should not see the console layout flash and then reflow after ignition.

The theme runtime is presentation-only. It does not query packages, alter REDMAGIC settings, control cooling, capture screenshots, suspend the device, or widen any privileged bridge.

## Safety / Product Honesty

- No Nintendo trademarks are presented as Game Star Box identity.
- No Nintendo UI assets are bundled.
- No screenshot function is claimed without a native adapter.
- No sleep/power function is claimed without a native adapter.
- Existing typed Core/Adapter boundaries remain unchanged.
- Theme selection never affects the Hardware Playground ignition gate.

## Performance

- No new network fetches.
- No new bitmap bundle for the theme.
- Installed app icons are reused from the existing game catalog.
- CSS layout changes are applied while the main WebView is hidden during first-boot hydration.
- Secondary scenes reuse the existing DOM instead of maintaining a second full application tree.

## Logs / Error Codes

Theme runtime currently has no native log dependency. Failures are fail-soft presentation failures: the console DOM remains present and usable.

Future native theme persistence, if added, should use `[GSB-THEME]` and typed error codes rather than a generic bridge.

## Testing

Acceptance:

1. Fresh v10 theme preference opens Handheld HOME.
2. Theme button switches Handheld <-> Console without Activity restart.
3. Selection survives only as presentation state; installed-game/session sources remain authoritative.
4. Real installed app icons remain visible in Handheld tiles.
5. A/Enter launches selected games through the existing `GSBGames.launch` path.
6. Dock routes Library / Esports / Console / System correctly.
7. Screenshot/Sleep do not execute invented platform behavior.
8. Theme preference persists across WebView reloads.
9. Hidden hydration loads theme CSS/runtime before `mainSurfaceReady()`.
10. Hardware Playground / REDMAGIC shoulder / haptic / model pipelines remain untouched.

## Design Research Record

Nintendo's public Switch 2 HOME support documentation describes a shallow HOME hierarchy with My Page at the upper-left and system destinations along the bottom, including online services, GameChat, News, eShop, Album, GameShare, Controllers, Virtual Game Cards, System Settings and Sleep Mode. Game Star Box uses that general handheld information-design lesson but maps the dock to its own functions and does not copy Nintendo assets.

## Changelog

### v10

- Added persistent `console` / `handheld` theme runtime.
- Added handheld square software rail.
- Added selected-software title treatment and local clock.
- Added compact circular system dock.
- Added explicit theme switch in top status, system settings and handheld dock.
- Added handheld key-navigation semantics.
- Kept unsupported screenshot/sleep operations informational only.
- Loaded theme during hidden first-surface hydration.
