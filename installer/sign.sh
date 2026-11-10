#!/usr/bin/env bash
# Sign one Windows executable with Azure Artifact Signing (formerly Trusted Signing), using jsign.
# Called by makensis for the uninstaller (!uninstfinalize) and the installer (!finalize).
#
# Needs these environment variables (set by the release workflow):
#   JSIGN_JAR                 path to jsign-<ver>.jar
#   AZURE_SIGNING_ENDPOINT    e.g. eus.codesigning.azure.net (the region you created the account in)
#   AZURE_SIGNING_ACCOUNT     Artifact Signing account name
#   AZURE_SIGNING_PROFILE     certificate profile name
#   AZURE_SIGNING_TOKEN       access token: az account get-access-token --resource https://codesigning.azure.net
set -euo pipefail
f="$1"
: "${JSIGN_JAR:?}" "${AZURE_SIGNING_ENDPOINT:?}" "${AZURE_SIGNING_ACCOUNT:?}" "${AZURE_SIGNING_PROFILE:?}" "${AZURE_SIGNING_TOKEN:?}"
# makensis hands the uninstaller over as a temp file with no extension; jsign wants a .exe name.
tmp="$(mktemp --suffix=.exe)"
cp "$f" "$tmp"
java -jar "$JSIGN_JAR" --storetype TRUSTEDSIGNING \
  --keystore "$AZURE_SIGNING_ENDPOINT" \
  --storepass "$AZURE_SIGNING_TOKEN" \
  --alias "$AZURE_SIGNING_ACCOUNT/$AZURE_SIGNING_PROFILE" \
  --name "mistri" --url "https://github.com/GITAV-LLC/mistri" \
  "$tmp" >&2
cp "$tmp" "$f"
rm -f "$tmp"
echo "signed: $f" >&2
