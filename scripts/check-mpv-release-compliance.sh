#!/usr/bin/env bash
set -euo pipefail
# Fail closed: never infer compliance from CI success or an upstream license label.
file="compliance/mpv-release-approval.txt"
if [[ ! -f "$file" ]]; then
  echo "::error::Native GPL compliance approval missing: $file"
  exit 1
fi
for key in native_source_verified application_license_compatible notices_complete distribution_source_access legal_review_completed; do
  if ! grep -qx "$key=yes" "$file"; then
    echo "::error::Required compliance evidence not approved: $key"
    exit 1
  fi
done
if [[ "${NEXTV_APK_APPROVED:-}" != "yes" ]]; then
  echo "::error::Explicit APK generation approval missing"
  exit 1
fi
echo "Approval flags present. Review the referenced evidence before distributing."
