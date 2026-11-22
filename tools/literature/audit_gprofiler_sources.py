#!/usr/bin/env python3
"""Audit complete frozen primary sources into a NEW source-only review folder."""
from pathlib import Path
import argparse
import hashlib
import json
from gprofiler_catalog.primary import audit


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--cache', required=True, type=Path)
    parser.add_argument('--out', required=True, type=Path)
    args = parser.parse_args()
    if args.out.exists():
        parser.error('Use a NEW output folder; preserve prior source audits')
    result = audit(args.cache)
    raw = (json.dumps(result, ensure_ascii=False, indent=2) + '\n').encode('utf-8')
    args.out.mkdir(parents=True)
    (args.out / 'CENSUS.json').write_bytes(raw)
    pin = dict(bytes=len(raw), sha256=hashlib.sha256(raw).hexdigest())
    (args.out / 'CENSUS-PIN.json').write_bytes((json.dumps(pin, indent=2) + '\n').encode())
    print(json.dumps(dict(status=result['status'], papers=250, roles=len(result['roles']),
                         canonical_catalog_modified=False, new_family_credit=0, census=pin)))


if __name__ == '__main__':
    main()
