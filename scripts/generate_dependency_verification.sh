#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

# Generate SHA-256 verification metadata from dependencies actually resolved by
# the supported phone variants and the standalone feature modules. Commit the
# resulting gradle/verification-metadata.xml only after reviewing its diff.
./gradlew \
  --write-verification-metadata sha256 \
  :app:compileUniversalFossDebugKotlin \
  :app:compileUniversalGmsDebugKotlin \
  :app:assembleUniversalFossDebug \
  :app:assembleUniversalGmsDebug \
  :app:testUniversalFossDebugUnitTest \
  :app:lintUniversalFossDebug \
  :betterlyrics:testDebugUnitTest \
  :unison:test \
  :wear:testDebugUnitTest \
  :wear:assembleDebug \
  :innertube:testDebugUnitTest \
  :letras:test \
  :canvas:test \
  --no-daemon

test -s gradle/verification-metadata.xml

printf '%s\n' \
  'Generated gradle/verification-metadata.xml.' \
  'Review the file before committing it; do not hand-edit artifact checksums.'
