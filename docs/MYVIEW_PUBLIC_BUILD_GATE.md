# MyView public build staging

This public branch is an isolated build staging area, not yet the MyView application.

The private MyView source remains on `my-hometv/nextv:dash-standalone`.
This branch currently inherits the MPV experiment, which must NOT be
rebranded or shipped as MyView: it lacks the private Media3/LibVLC implementation.

Security gate before copying source:
- Exclude private IPTV playlist URLs, provider accounts, tokens, DRM license
  URLs and headers, signing keys, license validation secrets, personal data,
  local configuration, and repository history.
- Audit Kotlin/Java, native, resources, Gradle, workflows and assets, including
  hardcoded constants, remote endpoints and generated files.
- Keep any licensing enforcement intact, without publishing credentials.
- Confirm dependency and license compliance for all distributed native code.
- Copy only approved files into a clean public history; then set
  `applicationId = "com.softppj.myview"` and label `MyView`.
- Run compilation and unit tests before producing any MyView APK.

Current public branch policy tests do not validate MyView playback or DRM.
