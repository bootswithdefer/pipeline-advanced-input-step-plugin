#!/usr/bin/env -S uv run --quiet --script
# /// script
# requires-python = ">=3.12"
# dependencies = []
# ///
"""Generate and sign a Jenkins ``update-center.json`` for a single plugin .hpi.

Jenkins validates an update center by the detached signature on the manifest,
not by transport, so the output is usable over plain HTTP (e.g. an in-cluster
nginx) as long as the signing CA root is trusted by the controllers.

The plugin metadata (version, required core, dependencies) is read from the
.hpi's ``META-INF/MANIFEST.MF`` so the manifest never drifts from the artifact.

Signing mirrors what the Jenkins update-center tooling produces:
  * ``correct_digest``  : base64(SHA-1 of canonical body)       [legacy]
  * ``correct_digest512``: hex(SHA-512 of canonical body)
  * ``correct_signature``/``correct_signature512``: base64 RSA signature of the body
  * ``certificates``    : base64(DER) chain, leaf first
The "body" is the JSON object with the ``signature`` block removed, serialized
compactly and deterministically (sorted keys) so signer and verifier agree.

Usage:
  make-update-center.py --hpi target/x.hpi --base-url https://uc/ \
      --key signing/uc.key --cert signing/uc.crt [--out update-center.json]
"""

from __future__ import annotations

import argparse
import base64
import hashlib
import json
import subprocess
import sys
import tempfile
import zipfile
from pathlib import Path


def read_manifest(hpi: Path) -> dict[str, str]:
    """Return the .hpi's MANIFEST.MF as a dict, unfolding 72-col line wraps."""
    with zipfile.ZipFile(hpi) as z:
        raw = z.read("META-INF/MANIFEST.MF").decode("utf-8")
    # Manifest continuation lines begin with a single leading space.
    unfolded = raw.replace("\r\n", "\n").replace("\n ", "")
    fields: dict[str, str] = {}
    for line in unfolded.splitlines():
        if ":" in line:
            key, _, value = line.partition(":")
            fields[key.strip()] = value.strip()
    return fields


def parse_dependencies(spec: str) -> list[dict[str, object]]:
    """Parse ``Plugin-Dependencies`` (``name:ver,other:ver;resolution:optional``)."""
    deps: list[dict[str, object]] = []
    for token in filter(None, (t.strip() for t in spec.split(","))):
        optional = token.endswith(";resolution:optional")
        token = token.removesuffix(";resolution:optional")
        name, _, version = token.partition(":")
        deps.append({"name": name, "version": version, "optional": optional})
    return deps


def clean_version(raw: str) -> str:
    """Strip the ``(private-...)`` suffix a dev build appends to Plugin-Version."""
    return raw.split(" ", 1)[0].strip()


def build_plugin_entry(hpi: Path, base_url: str) -> dict[str, object]:
    m = read_manifest(hpi)
    name = m["Short-Name"]
    version = clean_version(m["Plugin-Version"])
    data = hpi.read_bytes()
    url = f"{base_url.rstrip('/')}/plugins/{name}/{version}/{name}.hpi"
    return {
        "name": name,
        "version": version,
        "url": url,
        "sha1": base64.b64encode(hashlib.sha1(data).digest()).decode(),
        "sha256": base64.b64encode(hashlib.sha256(data).digest()).decode(),
        "sha512": base64.b64encode(hashlib.sha512(data).digest()).decode(),
        "requiredCore": m.get("Jenkins-Version", ""),
        "dependencies": parse_dependencies(m.get("Plugin-Dependencies", "")),
        "title": m.get("Long-Name", name),
        "excerpt": m.get("Specification-Title", ""),
    }


def canonical_body(doc: dict[str, object]) -> bytes:
    """Serialize the manifest without its signature, deterministically."""
    body = {k: v for k, v in doc.items() if k != "signature"}
    return json.dumps(body, sort_keys=True, separators=(",", ":")).encode("utf-8")


def sign(body: bytes, key: Path, cert: Path) -> dict[str, object]:
    """Produce the Jenkins ``signature`` block via openssl over the body."""

    def openssl(args: list[str], stdin: bytes) -> bytes:
        return subprocess.run(["openssl", *args], input=stdin, capture_output=True, check=True).stdout

    with tempfile.TemporaryDirectory() as td:
        bodyfile = Path(td) / "body"
        bodyfile.write_bytes(body)
        sig1 = openssl(["dgst", "-sha1", "-sign", str(key), str(bodyfile)], b"")
        sig512 = openssl(["dgst", "-sha512", "-sign", str(key), str(bodyfile)], b"")

    # DER form of the PEM cert, base64'd, as the single-element chain.
    der = openssl(["x509", "-in", str(cert), "-outform", "DER"], b"")

    return {
        "certificates": [base64.b64encode(der).decode()],
        "correct_digest": base64.b64encode(hashlib.sha1(body).digest()).decode(),
        "correct_digest512": hashlib.sha512(body).hexdigest(),
        "correct_signature": base64.b64encode(sig1).decode(),
        "correct_signature512": base64.b64encode(sig512).decode(),
    }


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--hpi", required=True, type=Path, help="Path to the built .hpi")
    ap.add_argument("--base-url", required=True, help="Base URL the UC is served from")
    ap.add_argument("--key", required=True, type=Path, help="Signing private key (PEM)")
    ap.add_argument("--cert", required=True, type=Path, help="Signing certificate (PEM)")
    ap.add_argument("--out", type=Path, default=Path("update-center.json"))
    ap.add_argument("--id", default="advanced-input", help="Update center id")
    args = ap.parse_args()

    if not args.hpi.exists():
        print(f"hpi not found: {args.hpi}", file=sys.stderr)
        return 1

    entry = build_plugin_entry(args.hpi, args.base_url)
    doc: dict[str, object] = {
        "updateCenterVersion": "1",
        "id": args.id,
        "plugins": {entry["name"]: entry},
        "signature": {},
    }
    doc["signature"] = sign(canonical_body(doc), args.key, args.cert)

    # Jenkins accepts either bare JSON or the JSONP envelope; the envelope is
    # what the UI fetch expects from an "update site" URL.
    payload = json.dumps(doc, indent=2)
    args.out.write_text(f"updateCenter.post(\n{payload}\n);")
    print(f"wrote {args.out} for {entry['name']} {entry['version']}", file=sys.stderr)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
