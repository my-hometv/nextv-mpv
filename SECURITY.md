# NexTV security policy

This project is private. Do not commit credentials, IPTV playlist passwords, licence records, signing keys, customer data, or live upstream endpoints.

## Before integrating source

- Review every imported file for secrets and hardcoded endpoints.
- Do not import the HomeTV Cloudflare Worker configuration or licence database.
- Use an independent NexTV licensing configuration when explicitly authorized.
- Prefer HTTPS; allow HTTP only where required for IPTV compatibility and narrowly scoped.
- Never log playlist tokens, licence tokens, authorization headers, or personally identifying data.
- Manual compilation, unit-test and debug APK workflows may exist, but only compilation/unit tests are authorized so far. Never run the APK workflow or publish a release without the owner's approval.
- Require security review and owner approval before any APK build or release.
