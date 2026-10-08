#!/usr/bin/env bash
set -euo pipefail

BASE_REF="${BASE_REF:-origin/main}"

if ! command -v oasdiff >/dev/null 2>&1; then
  echo "oasdiff binary is required but not found in PATH"
  exit 1
fi

if ! git rev-parse --verify "$BASE_REF" >/dev/null 2>&1; then
  git fetch origin main --depth=1
fi

specs=()
while IFS= read -r line; do
  specs+=("$line")
done < <(git ls-files 'openapi/*.yaml' 'api/openapi/*.yaml' 'src/main/resources/openapi/*.yaml' 2>/dev/null || true)

if [ "${#specs[@]}" -eq 0 ]; then
  echo "No OpenAPI specs found; skipping breaking-change check."
  exit 0
fi

tmpdir="$(mktemp -d)"
trap 'rm -rf "$tmpdir"' EXIT

fail=0
for spec in "${specs[@]}"; do
  if ! git cat-file -e "$BASE_REF:$spec" 2>/dev/null; then
    echo "[oasdiff] new spec file in branch, skipping baseline diff: $spec"
    continue
  fi

  base_file="$tmpdir/base-$(basename "$spec")"
  cp_file="$tmpdir/rev-$(basename "$spec")"

  git show "$BASE_REF:$spec" > "$base_file"
  cp "$spec" "$cp_file"

  echo "[oasdiff] checking $spec"
  # A reviewed, line-by-line list of accepted breaking changes for this spec
  # (<spec>.accepted-breaking.txt next to it). Each line is one oasdiff error;
  # anything not listed still fails. Waivers belong to one change only: the
  # file must not exist on the base branch (delete it in the PR after the one
  # that merged it), it must start with a "# Accepted" line saying why, and
  # CODEOWNERS makes its owners review every edit.
  ignore_args=()
  accepted="${spec%.yaml}.accepted-breaking.txt"
  if [ -f "$accepted" ]; then
    if git cat-file -e "$BASE_REF:$accepted" 2>/dev/null; then
      echo "[oasdiff] $accepted is already on $BASE_REF; waivers apply to one change, delete it" >&2
      fail=1
      continue
    fi
    if ! head -n 1 "$accepted" | grep -q '^# Accepted '; then
      echo "[oasdiff] $accepted must start with a '# Accepted <date> ...: <reason>' line" >&2
      fail=1
      continue
    fi
    echo "[oasdiff] applying accepted breaking changes from $accepted:"
    grep -v '^#' "$accepted" | sed 's/^/[oasdiff]   waived: /'
    if [ -n "${GITHUB_STEP_SUMMARY:-}" ]; then
      { echo "### Accepted breaking changes in $spec"; echo; grep -v '^#' "$accepted" | sed 's/^/- /'; } >> "$GITHUB_STEP_SUMMARY"
    fi
    ignore_args=(--err-ignore "$accepted")
  fi
  if ! oasdiff breaking --fail-on ERR "${ignore_args[@]}" "$base_file" "$cp_file"; then
    echo "[oasdiff] breaking change detected in $spec"
    fail=1
  fi
done

if [ "$fail" -ne 0 ]; then
  echo "OpenAPI breaking-change check failed."
  exit 1
fi

echo "OpenAPI breaking-change check passed."
