#!/usr/bin/env python3
"""Check every rendered ExternalSecret against the platform secret naming contract.

Reads `helm template` output on stdin. For each ExternalSecret:
  - metadata.labels["app.kubernetes.io/name"] equals the chart's ServiceAccount name;
  - every spec.data[].remoteRef.key and spec.dataFrom[].extract.key starts with
    "<env>/<service account>/" (the ClusterSecretStore reads only <env>/* and IRSA
    scopes each workload to its own prefix).

Usage: helm template ... | scripts/ci/externalsecret-naming.py --env <env>
Exit 0 when all pass, 1 with one line per violation otherwise.
"""
import argparse
import sys

import yaml


def remote_keys(spec):
    for item in spec.get("data") or []:
        yield (item.get("remoteRef") or {}).get("key")
    for item in spec.get("dataFrom") or []:
        yield (item.get("extract") or {}).get("key")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--env", required=True, help="environment prefix, e.g. dev, prod, ci")
    args = parser.parse_args()

    docs = [d for d in yaml.safe_load_all(sys.stdin) if isinstance(d, dict)]
    accounts = [d["metadata"]["name"] for d in docs if d.get("kind") == "ServiceAccount"]
    secrets = [d for d in docs if d.get("kind") == "ExternalSecret"]
    errors = []
    if len(accounts) != 1:
        errors.append(f"expected exactly one ServiceAccount, rendered {len(accounts)}")
    if not secrets:
        errors.append("no ExternalSecret rendered")
    account = accounts[0] if len(accounts) == 1 else None
    prefix = f"{args.env}/{account}/"
    for secret in secrets:
        name = secret["metadata"]["name"]
        label = (secret["metadata"].get("labels") or {}).get("app.kubernetes.io/name")
        if account and label != account:
            errors.append(f"ExternalSecret {name}: app.kubernetes.io/name is {label!r}, service account is {account!r}")
        keys = list(remote_keys(secret.get("spec") or {}))
        if not keys:
            errors.append(f"ExternalSecret {name}: no remote keys")
        for key in keys:
            if account and not (isinstance(key, str) and key.startswith(prefix)):
                errors.append(f"ExternalSecret {name}: remoteRef.key {key!r} does not start with {prefix!r}")

    for error in errors:
        print(f"externalsecret-naming: {error}", file=sys.stderr)
    if errors:
        return 1
    print(f"externalsecret-naming: {len(secrets)} ExternalSecret(s) use {prefix} and label {account}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
