#!/usr/bin/env bash
# Generate the update-center signing CA and upload it to GitHub Actions.
#
# Produces a long-lived self-signed RSA key/cert pair used to sign
# update-center.json (see scripts/make-update-center.py), then stores the key
# and cert as repository secrets and records the serving base URL as a
# repository variable, so .github/workflows/release.yml can sign releases.
#
# The CA is long-lived on purpose: its certificate must be trusted by every
# Jenkins controller (mounted into $JENKINS_HOME/update-center-rootCAs/), and
# rotating it means re-distributing trust everywhere. Default validity 10 years.
#
# The generated cert (uc.crt) is ALSO written to the output directory so you can
# mount it into the controllers. The private key never needs to leave GitHub;
# keep or destroy the local copy per your key-handling policy.
#
# Usage:
#   scripts/setup-signing.sh --base-url <URL> [options]
#
# Options:
#   --base-url URL     Update-center serving base URL (required), e.g.
#                      http://advanced-input-uc.jenkins-uc.svc.cluster.local
#   --repo  OWNER/NAME Target repo (default: current repo via gh)
#   --out   DIR        Where to write uc.key / uc.crt (default: ./signing)
#   --cn    NAME       Certificate common name (default: advanced-input-uc)
#   --days  N          Validity in days (default: 3650)
#   --force            Overwrite existing local key/cert and existing secrets
#   -h, --help         Show this help

set -euo pipefail

base_url=""
repo=""
out_dir="signing"
cn="advanced-input-uc"
days="3650"
force="false"

die() {
  echo "error: $*" >&2
  exit 1
}

usage() {
  sed -n '2,/^set -euo/p' "$0" | sed 's/^# \{0,1\}//; /^set -euo/d'
  exit "${1:-0}"
}

while [ $# -gt 0 ]; do
  case "$1" in
    --base-url) base_url="${2:?}"; shift 2 ;;
    --repo) repo="${2:?}"; shift 2 ;;
    --out) out_dir="${2:?}"; shift 2 ;;
    --cn) cn="${2:?}"; shift 2 ;;
    --days) days="${2:?}"; shift 2 ;;
    --force) force="true"; shift ;;
    -h|--help) usage 0 ;;
    *) die "unknown argument: $1 (see --help)" ;;
  esac
done

[ -n "$base_url" ] || die "--base-url is required (see --help)"
command -v openssl >/dev/null || die "openssl not found"
command -v gh >/dev/null || die "gh (GitHub CLI) not found"
gh auth status >/dev/null 2>&1 || die "gh is not authenticated; run 'gh auth login'"

# Resolve the target repo through gh if not given, so secrets land on the right place.
if [ -z "$repo" ]; then
  repo="$(gh repo view --json nameWithOwner --jq .nameWithOwner)"
fi
echo "Target repository: $repo" >&2

key="$out_dir/uc.key"
cert="$out_dir/uc.crt"

if [ -e "$key" ] || [ -e "$cert" ]; then
  [ "$force" = "true" ] || die "$out_dir already contains uc.key/uc.crt; pass --force to overwrite"
fi

mkdir -p "$out_dir"
chmod 700 "$out_dir"
umask 077

echo "Generating self-signed signing CA (CN=$cn, ${days}d)..." >&2
openssl req -x509 -newkey rsa:4096 -nodes \
  -keyout "$key" -out "$cert" \
  -days "$days" -subj "/CN=$cn"

echo "Uploading secrets and variable to $repo..." >&2
# Redirecting the file to gh's stdin is the reliable form for multiline secrets;
# the "--body -" form has mis-stored multiline PEMs on some gh versions.
gh secret set UC_SIGNING_KEY  --repo "$repo" < "$key"
gh secret set UC_SIGNING_CERT --repo "$repo" < "$cert"
gh variable set UC_BASE_URL   --repo "$repo" --body "$base_url"

cat >&2 <<EOF

Done.
  secret   UC_SIGNING_KEY   <- $key
  secret   UC_SIGNING_CERT  <- $cert
  variable UC_BASE_URL      =  $base_url

Next steps:
  1. Mount the signing certificate into each Jenkins controller so it trusts the
     update center:
       \$JENKINS_HOME/update-center-rootCAs/$cn.crt  <-  $cert
  2. Tag a release (e.g. 'git tag v1.0 && git push origin v1.0') to run
     .github/workflows/release.yml, which will publish a signed update-center.json.

The private key ($key) is now stored in GitHub. Handle the local copy per your
key policy; the cert ($cert) is needed to configure the controllers.
EOF
