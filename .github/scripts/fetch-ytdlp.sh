#!/usr/bin/env bash
# Download the official yt-dlp release binary and VERIFY it before it is bundled or executed.
#
# Usage:
#   fetch-ytdlp.sh <output-path>
#   fetch-ytdlp.sh --verify <file> <SHA2-256SUMS file> <asset-name> [pinned-sha256]
#
# Environment (all optional):
#   YTDLP_ASSET    Release asset to fetch: "yt-dlp" (default, Linux/Android) or "yt-dlp.exe" (Windows).
#   YTDLP_VERSION  Release tag to use (e.g. 2026.11.01). Empty = newest official release.
#   YTDLP_SHA256   Pin: the exact SHA-256 the downloaded asset MUST have. Empty = not pinned.
#                  Set both YTDLP_VERSION and YTDLP_SHA256 to get fully reproducible builds.
#
# What it checks (any failure aborts the build with a non-zero exit code):
#   1. The release tag looks like a yt-dlp version (no URL injection through the tag).
#   2. Everything is downloaded over HTTPS only (TLS >= 1.2, redirects stay on HTTPS).
#   3. The asset's SHA-256 equals the entry for that asset in the release's SHA2-256SUMS file
#      (exactly one matching entry is required).
#   4. If YTDLP_SHA256 is set, the asset's SHA-256 also equals that pinned value.
#   5. Sanity: not suspiciously small (e.g. an HTML error page) and has the right file magic.
#
# Note: SHA2-256SUMS comes from the same release, so (3) protects against corrupted/partial/
# swapped downloads, while (4) is what protects against a compromised release.
set -euo pipefail

REPO="yt-dlp/yt-dlp"
TAG_RE='^[0-9]{4}\.[0-9]{2}\.[0-9]{2}(\.[0-9]+)?$'
MIN_BYTES=1000000

die() {
  echo "::error::fetch-ytdlp: $*" >&2
  exit 1
}

sha256_of() {
  sha256sum "$1" | awk '{print tolower($1)}'
}

# Print the checksum listed for <asset> in <sums-file>; fail unless exactly one entry matches.
expected_from_sums() {
  local sums="$1" asset="$2"
  local -a found=()
  mapfile -t found < <(awk -v a="$asset" '
    NF == 2 {
      name = $2
      sub(/^\*/, "", name)
      if (name == a && $1 ~ /^[0-9a-fA-F]{64}$/) print tolower($1)
    }' "$sums")
  [ "${#found[@]}" -eq 1 ] \
    || die "expected exactly one checksum entry for '$asset' in SHA2-256SUMS, found ${#found[@]}"
  printf '%s' "${found[0]}"
}

verify_file() {
  local file="${1:?file}" sums="${2:?sums file}" asset="${3:?asset name}" pinned="${4:-}"
  [ -f "$file" ] || die "file not found: $file"
  [ -f "$sums" ] || die "checksum list not found: $sums"

  local actual expected size magic
  actual="$(sha256_of "$file")"
  expected="$(expected_from_sums "$sums" "$asset")"
  [ "$actual" = "$expected" ] \
    || die "SHA-256 mismatch for $asset: downloaded file is $actual but SHA2-256SUMS lists $expected"

  if [ -n "$pinned" ]; then
    [[ "$pinned" =~ ^[0-9a-fA-F]{64}$ ]] || die "YTDLP_SHA256 is not a 64-character hex SHA-256"
    pinned="$(printf '%s' "$pinned" | tr 'A-F' 'a-f')"
    [ "$actual" = "$pinned" ] \
      || die "SHA-256 of $asset is $actual but the pinned YTDLP_SHA256 is $pinned"
  fi

  size="$(wc -c < "$file" | tr -d '[:space:]')"
  [ "$size" -ge "$MIN_BYTES" ] || die "$asset is only $size bytes; refusing to use it"

  magic="$(head -c 2 "$file")"
  case "$asset" in
    yt-dlp)     [ "$magic" = "#!" ] || die "$asset does not start with a script header" ;;
    yt-dlp.exe) [ "$magic" = "MZ" ] || die "$asset is not a Windows executable" ;;
    *)          die "unsupported asset '$asset'" ;;
  esac
}

main() {
  if [ "${1:-}" = "--verify" ]; then
    shift
    verify_file "$@"
    echo "OK: $3 verified"
    return 0
  fi

  local out="${1:?usage: fetch-ytdlp.sh <output-path>}"
  local asset="${YTDLP_ASSET:-yt-dlp}"
  case "$asset" in
    yt-dlp | yt-dlp.exe) ;;
    *) die "unsupported YTDLP_ASSET '$asset' (use yt-dlp or yt-dlp.exe)" ;;
  esac

  local -a curl_opts=(
    --fail --silent --show-error --location
    --proto '=https' --proto-redir '=https' --tlsv1.2
    --retry 3 --retry-all-errors --connect-timeout 20 --max-time 300
  )

  local tag="${YTDLP_VERSION:-}"
  if [ -z "$tag" ]; then
    local final
    final="$(curl "${curl_opts[@]}" -I -o /dev/null -w '%{url_effective}' \
      "https://github.com/$REPO/releases/latest")" || die "could not resolve the latest yt-dlp release"
    tag="${final##*/}"
  fi
  [[ "$tag" =~ $TAG_RE ]] || die "unexpected yt-dlp release tag '$tag'"

  local base="https://github.com/$REPO/releases/download/$tag"
  # Global (not local) so the EXIT trap can still see it after this function returns.
  TMPDIR_YTDLP="$(mktemp -d)"
  trap 'rm -rf "${TMPDIR_YTDLP:-}"' EXIT
  local tmp="$TMPDIR_YTDLP"

  echo "Downloading yt-dlp release $tag ($asset)..."
  curl "${curl_opts[@]}" -o "$tmp/$asset" "$base/$asset" || die "download of $asset failed"
  curl "${curl_opts[@]}" -o "$tmp/SHA2-256SUMS" "$base/SHA2-256SUMS" || die "download of SHA2-256SUMS failed"

  verify_file "$tmp/$asset" "$tmp/SHA2-256SUMS" "$asset" "${YTDLP_SHA256:-}"

  local digest size
  digest="$(sha256_of "$tmp/$asset")"
  size="$(wc -c < "$tmp/$asset" | tr -d '[:space:]')"

  mkdir -p "$(dirname "$out")"
  cp "$tmp/$asset" "$out"
  chmod 644 "$out"

  echo "Verified $asset $tag: $size bytes, sha256=$digest"
  echo "Installed to $out"

  if [ -n "${GITHUB_ENV:-}" ]; then
    {
      echo "YTDLP_RESOLVED_VERSION=$tag"
      echo "YTDLP_RESOLVED_SHA256=$digest"
    } >> "$GITHUB_ENV"
  fi
  if [ -n "${GITHUB_STEP_SUMMARY:-}" ]; then
    {
      echo "### yt-dlp bundled ($asset)"
      echo "- Release: \`$tag\`"
      echo "- SHA-256: \`$digest\` (matches the release's SHA2-256SUMS${YTDLP_SHA256:+ and the pinned YTDLP_SHA256})"
    } >> "$GITHUB_STEP_SUMMARY"
  fi
}

main "$@"
