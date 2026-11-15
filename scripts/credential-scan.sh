#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
pattern='([0-9]{6,}:[A-Za-z0-9_-]{30,}|sk-ant-[A-Za-z0-9_-]{20,}|sk-(proj-)?[A-Za-z0-9_-]{24,}|(?i:api[_-]?hash)["'"'"']?[[:space:]]*[:=][[:space:]]*["'"'"']?[a-fA-F0-9]{32})'
found=0

while IFS= read -r -d '' relative_path; do
    if rg --no-messages --files-with-matches --text --regexp "$pattern" -- "$repo_root/$relative_path" >/dev/null; then
        printf 'Potential credential-like value found in %s\n' "$relative_path"
        found=1
    fi
done < <(git -C "$repo_root" ls-files --cached --others --exclude-standard -z)

if (( found )); then
    printf 'Review the reported files and remove any real credentials before committing.\n' >&2
    exit 1
fi

printf 'Credential scan passed for tracked and non-ignored files.\n'
