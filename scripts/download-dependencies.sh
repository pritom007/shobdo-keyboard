#!/usr/bin/env bash
# Fetches the large binary dependencies that git deliberately does not track:
#
#   1. Whisper base (multilingual) int8 ONNX model — encoder + decoder + tokens
#      (~197 MB bundle, extracted to ~160 MB). Used by the offline voice leg.
#   2. sherpa-onnx 1.13.4 AAR (+ POM) — the on-device speech runtime.
#      (~47 MB). Hosted as a local Maven artifact under android/local-repo/.
#
# Why these aren't committed: GitHub rejects files > 100 MB (the decoder alone
# is 130 MB), and keeping binaries out of git keeps clones fast + CI cheap.
#
# The script is idempotent: it skips any file that already exists with the
# correct SHA-256 checksum. Safe to re-run. Works on macOS (dev) and Linux (CI).
#
# Run once after cloning:
#     ./scripts/download-dependencies.sh
# Then build normally:
#     cd android && ./gradlew :app:assembleDebug
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
MODEL_DIR="$ROOT/android/app/src/main/assets/sherpa-whisper-base"
AAR_DIR="$ROOT/android/local-repo/com/k2fsa/sherpa/onnx/sherpa-onnx/1.13.4"

MODEL_URL="https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-whisper-base.tar.bz2"
AAR_URL="https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.4/sherpa-onnx-1.13.4.aar"

# SHA-256 checksums (pinned at the versions this project targets). If you bump
# a model/AAR version, update both the URL and its checksum here — CI verifies
# these, so a mismatch fails the build loudly rather than silently.
DECODER_SHA="9759d217388a01b3a4c7c15533201067b48ae819c4daafc8624e64b9409dc02d"
ENCODER_SHA="0b8fb1304b6109976038efff5ace81720e00386f3ff6b54ee8c75291ca0a1e11"
TOKENS_SHA="b34b360dbb493e781e479794586d661700670d65564001f23024971d1f2fa126"
AAR_SHA="03f9c4df965f21c71269365a7951a7f23b5696fddd093fa318c80d65550ab780"

sha256() {  # sha256 <file>
  if command -v shasum >/dev/null 2>&1; then
    shasum -a 256 "$1" | awk '{print $1}'
  else
    sha256sum "$1" | awk '{print $1}'
  fi
}

check() {  # check <file> <expected_sha>  → 0 if ok, 1 if mismatch/missing
  local file="$1" expected="$2" actual
  [[ -f "$file" ]] || return 1
  actual="$(sha256 "$file")"
  [[ "$actual" == "$expected" ]]
}

verify() {  # verify <file> <expected_sha>  → prints error + exits on mismatch
  local file="$1" expected="$2" actual
  actual="$(sha256 "$file")"
  if [[ "$actual" != "$expected" ]]; then
    echo "✗ checksum mismatch for $file" >&2
    echo "  expected: $expected" >&2
    echo "  actual:   $actual" >&2
    echo "  The download may have been corrupted or the pinned version is wrong." >&2
    return 1
  fi
}

fetch_model() {
  mkdir -p "$MODEL_DIR"
  if check "$MODEL_DIR/base-decoder.int8.onnx" "$DECODER_SHA" &&
     check "$MODEL_DIR/base-encoder.int8.onnx" "$ENCODER_SHA" &&
     check "$MODEL_DIR/base-tokens.txt" "$TOKENS_SHA"; then
    echo "✓ Whisper base model already present (checksums ok)"
    return 0
  fi
  echo "↓ Downloading Whisper base model (~197 MB)…"
  local tmp; tmp="$(mktemp -d)"
  trap 'rm -rf "$tmp"' RETURN
  curl -fL --retry 3 --retry-delay 2 -o "$tmp/whisper-base.tar.bz2" "$MODEL_URL"
  tar xjf "$tmp/whisper-base.tar.bz2" -C "$tmp" \
    sherpa-onnx-whisper-base/base-encoder.int8.onnx \
    sherpa-onnx-whisper-base/base-decoder.int8.onnx \
    sherpa-onnx-whisper-base/base-tokens.txt
  cp "$tmp/sherpa-onnx-whisper-base/base-encoder.int8.onnx" "$MODEL_DIR/"
  cp "$tmp/sherpa-onnx-whisper-base/base-decoder.int8.onnx" "$MODEL_DIR/"
  cp "$tmp/sherpa-onnx-whisper-base/base-tokens.txt" "$MODEL_DIR/"
  verify "$MODEL_DIR/base-decoder.int8.onnx" "$DECODER_SHA"
  verify "$MODEL_DIR/base-encoder.int8.onnx" "$ENCODER_SHA"
  verify "$MODEL_DIR/base-tokens.txt" "$TOKENS_SHA"
  echo "✓ Whisper base model ready"
}

fetch_aar() {
  mkdir -p "$AAR_DIR"
  if check "$AAR_DIR/sherpa-onnx-1.13.4.aar" "$AAR_SHA"; then
    echo "✓ sherpa-onnx AAR already present (checksum ok)"
  else
    echo "↓ Downloading sherpa-onnx 1.13.4 AAR (~47 MB)…"
    curl -fL --retry 3 --retry-delay 2 -o "$AAR_DIR/sherpa-onnx-1.13.4.aar" "$AAR_URL"
    verify "$AAR_DIR/sherpa-onnx-1.13.4.aar" "$AAR_SHA"
  fi
  # The POM is tiny (~400 B) and required for Maven resolution. We generate it
  # rather than commit it so the whole local-repo stays out of git.
  if [[ ! -f "$AAR_DIR/sherpa-onnx-1.13.4.pom" ]]; then
    cat > "$AAR_DIR/sherpa-onnx-1.13.4.pom" <<'POM'
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>com.k2fsa.sherpa.onnx</groupId>
  <artifactId>sherpa-onnx</artifactId>
  <version>1.13.4</version>
  <packaging>aar</packaging>
</project>
POM
    echo "✓ sherpa-onnx POM written"
  fi
  echo "✓ sherpa-onnx AAR ready"
}

echo "Fetching Shohojakkhor Keyboard large dependencies…"
fetch_model
fetch_aar
echo ""
echo "✓ All dependencies ready. You can now build:"
echo "    cd android && ./gradlew :app:assembleDebug"
