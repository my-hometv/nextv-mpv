# MyView public source review — first pass (16 Nov 2026)

Scope: private `my-hometv/nextv` branch `dash-standalone`.
Inventory inspected: 165 tree entries; key sources inspected: MainActivity.kt,
SignedLicenseCache.kt, YouTubeStreamResolver.kt, M3uParser.kt and
DashWebPlayerActivity.kt.

Findings:
- MainActivity.kt is approximately 544 KB and contains token/password-related
  handling. The string-match review is NOT sufficient to establish that its
  contents are safe for public release.
- SignedLicenseCache.kt handles licensing-related tokens; exclude from public
  transfer pending line-by-line audit and design review.
- Private app uses Media3 + LibVLC. Existing public MPV app does not contain
  that implementation. Rebranding the public MPV app would be misleading.
- Private source also contains Android resources, workflows, native code and
  unit tests requiring independent review.
- No production stream URL, DRM license endpoint, token, PSSH or private
  source file is copied into this public repository by this review.

**Gate remains CLOSED.** No private source may be copied to this public branch
until a complete review, including all relevant code and assets, passes.
A separate clean-room public implementation may be developed instead, but
must be explicitly described as a different implementation, not a compiled
copy of private MyView.

Build readiness: NOT READY; no MyView APK has been compiled.
