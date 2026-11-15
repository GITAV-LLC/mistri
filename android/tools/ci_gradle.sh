#!/bin/bash
# Runs Gradle tasks in CI and, if they fail, repeats the compiler's and the tests'
# own messages as GitHub annotations so the cause is easy to find.
# Usage (from android/): tools/ci_gradle.sh <gradle tasks...>
set -o pipefail
log="gradle-$1.log"
if ./gradlew --no-daemon --console=plain "$@" 2>&1 | tee "$log"; then
  exit 0
fi
grep -E "^(e: |FAILURE|\* What went wrong|> )|FAILED|AssertionError|Exception|Timed out waiting" "$log" \
  | grep -v "^\s*at androidx\|^\s*at android\.\|^\s*at java" | head -54 | split -l 6 - chunk_ || true
for f in chunk_*; do
  [ -f "$f" ] || continue
  msg=$(sed 's/%/%25/g' "$f" | sed ':a;N;$!ba;s/\n/%0A/g')
  echo "::error title=Android $1::$msg"
  rm -f "$f"
done
exit 1
