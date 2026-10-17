#!/usr/bin/env python3
"""Check rendered Helm manifests (stdin) against the platform ExternalSecret scope.

Platform contract "Secret stores and deploy supply chain" (admission policy
fintechbankx-externalsecret-scope): a service ExternalSecret carries the label
app.kubernetes.io/name=<service account>, reads only keys under
<env>/<service account>/ and uses neither dataFrom.find nor sourceRef.

usage: helm template ... | python3 scripts/ci/externalsecret-scope.py <service account> <env>
Exits 1 with one line per violation; 0 if every ExternalSecret complies (and at least one exists).
"""
import sys

import yaml


def main() -> int:
    service_account, env = sys.argv[1], sys.argv[2]
    prefix = f"{env}/{service_account}/"
    problems, seen = [], 0
    for doc in yaml.safe_load_all(sys.stdin):
        if not doc or doc.get("kind") != "ExternalSecret":
            continue
        seen += 1
        name = doc.get("metadata", {}).get("name", "?")
        labels = doc.get("metadata", {}).get("labels") or {}
        if labels.get("app.kubernetes.io/name") != service_account:
            problems.append(f"{name}: label app.kubernetes.io/name must be {service_account}")
        spec = doc.get("spec") or {}
        if spec.get("dataFrom"):
            problems.append(f"{name}: dataFrom is not allowed")
        for item in spec.get("data") or []:
            if item.get("sourceRef"):
                problems.append(f"{name}: sourceRef is not allowed ({item.get('secretKey')})")
            key = (item.get("remoteRef") or {}).get("key", "")
            if not key.startswith(prefix):
                problems.append(f"{name}: remoteRef key {key!r} is outside {prefix}")
    if seen == 0:
        problems.append("no ExternalSecret rendered")
    for problem in problems:
        print(problem, file=sys.stderr)
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
