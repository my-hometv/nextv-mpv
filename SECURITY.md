# MyView / NexTV source security policy

## Repository visibility
This `my-hometv/nextv-mpv` GitHub repository is **currently public** (verified 17 November 2026).
Public source and Actions logs must contain **no** playlist credentials, activation
tokens, signing private keys, customer data or private upstream stream endpoints.
Changing repository visibility requires a separate, explicit owner action.

## Source and release safeguards
- Never commit real IPTV playlist passwords, licence records, API secrets, signing keys or tokens.
- Do not import the HomeTV Cloudflare Worker secrets, databases, or private account data.
- Keep licensing calls on HTTPS and retain the verified, device-bound signed licence lease.
- IPTV compatibility may require user-provided HTTP MPEG-TS streams; do not treat
  `android:usesCleartextTraffic=true` as permission to downgrade licensing HTTPS.
- Redact full stream URLs and sensitive query strings from diagnostics and CI output.
- Portable backup JSON may include user-provided playlist URLs and credentials;
  keep exported backups private even though license and PIN state are excluded.
- Source checks must compile and run automated tests without assembling an APK.
- MyView APK builds must be manually dispatched from `myview-full-app` after
  explicit owner approval. Do not dispatch publication or legacy APK workflows.
- Before any external distribution, review LibVLC and bundled dependencies'
  licensing obligations and security updates. Public code hosting is not
  equivalent to approval to distribute packaged applications.
