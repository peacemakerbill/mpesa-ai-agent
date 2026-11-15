# The Daraja Agent-X logo

<img src="assets/logo-animated.svg" alt="Daraja Agent-X" width="320"/>

Daraja is Swahili for bridge. The mark is a white bridge arch woven with a red curve that dips below it. Where they cross they make an X, the X of Agent-X.

## Colours

The tile and the red curve use the colours of M-Pesa, on every theme of the app and the console. Only the colours come from M-Pesa. The drawing is original.

| Part | Colour |
|---|---|
| Tile, light end | `#5BC554` |
| Tile, deep end | `#0B7A38` |
| Curve and the X in the name | `#E4202D` |
| Arch and coin | white |
| Name in the lockup | `#0B5E2B` on light pages |

## Files

All in `docs/assets/`. They are plain SVG, with no script and no font to install, and each respects reduced motion.

| File | Use |
|---|---|
| `logo.svg`, `logo-animated.svg` | Mark and name side by side. The animated one draws the arch, then the curve, then a coin crosses the bridge. |
| `icon.svg`, `icon-animated.svg` | The mark alone, for avatars and favicons. |
| `loader.svg` | The mark drawing, resting and wiping off in a loop, for waiting. |

## Where it is used

- **The app** draws it with `BrandMark` in `agentx-app/lib/widgets/brand.dart`, not from an image, so it is sharp at every size and adds no package. `BrandMotion.still` is a plain mark, `intro` draws once and then rests, `loop` repeats. `BrandLoader` replaces the page-sized progress circle, and `BrandLockup` is the mark with the name. With the phone's "remove animations" setting on, every motion is a still mark.
- **The app icons** for Android (adaptive and themed), iOS, macOS, Windows and the web were made from the same drawing. The web page also shows the looping mark while the app downloads.
- **The console** shows the mark once at the start of a browser session (any key or click skips it), in the header, and as the loader on every "Loading" and "Sending" line. `dmMark()` in `console/console.html` makes it.

## Changing it

The drawing is two curves in a 512 by 512 square. They are written out in `brand.dart` (`_arch` and `_flow`), in `dmMark()` in the console, and in the SVG files. Change all three together, then make the icons again from the SVG at 1024 pixels: the launcher icons under `agentx-app/android`, `ios`, `macos`, `windows` and `web`.
