# MyView approved original artwork — asset replacement gate

This branch uses `R.drawable.tv_banner` for startup/full-screen license checking, the Home hero, the Android TV launcher banner, and the right-hand portion of channel information. It uses `R.drawable.nextv_launcher` for the application launcher icon. The user's approved original shows seven people (parents, grandparents, two girls, one boy). Do not substitute other generated imagery.

## Exact user-supplied source files to install
| Android drawable | Source | Dimensions | SHA-256 |
|---|---|---|---|
| `app/src/main/res/drawable/tv_banner.png` | Exact user-uploaded seven-person full image | 1671×941 PNG | `aa26291bf879dca3d7cf6619557d17429c29b710974efb27ca48f0e6ed22d6e1` |
| `app/src/main/res/drawable/nextv_launcher.png` | User-provided matching square launcher resource | 512×512 PNG | `42adbea165166c7d78b49573f8cab9b9c7aa96d9cb220669d28205610bc52dae` |

**Binary replacement is a separate required step.** The source code does not prove those exact bytes have been replaced. The final build must verify the SHA-256 of both files in GitHub, and must not be approved if either differs. Keep all seven figures visible: FIT_CENTER is used on startup, Home, and channel-info surfaces to avoid cropping family members. On Android TV, fit behavior may show letterboxing rather than stretching.

## What is currently complete
- The shared resource references are wired to the correct Android UI areas.
- Source-only workflow remains configured and does not assemble an APK.
- No licensing, provider data, or video engine source was changed.

## Remaining
1. Replace both drawable files on `myview-cinematic-home` with the exact source PNGs supplied in the approved branding archive; confirm the SHA-256 values above.
2. Run source-only compilation/regression tests on the new commit.
3. Inspect actual device rendering, including banner visibility, launcher icon legibility and channel info size.
4. Do not build/publish production APK without explicit release approval.
