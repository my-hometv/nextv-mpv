# NexTV

Standalone Android TV IPTV application.

## Project status

Source audit and pre-release preparation on the `development` branch. **Do not build, upload, or publish APKs until the owner explicitly authorizes it.**

- Android application ID: `com.softppj.nextv` (configured)
- Independent from HomeTV, HomeTV-V6, and HomeTV-Next
- Approved branding: hand-drawn house-and-trees logo, thin purple circle, white background
- Source baseline: selected, reviewed files from the HomeTV-Next development branch; do not copy credentials or deploy existing licensing backends
- Manual compile, JVM tests and debug APK workflows exist; APK construction and publication still require explicit approval.

## Security gates before release

1. Audit IPTV URLs, credentials, storage, and log redaction.
2. Restrict cleartext HTTP and audit TLS behavior.
3. Audit licensing gateway authorization, signing-key isolation, and rate limits.
4. Review Android target SDK, exported components, and dependency vulnerabilities.
5. Run unit and device tests only when authorized; APK build/release requires separate explicit approval.

Existing HomeTV repositories are not to be modified as part of this project.
