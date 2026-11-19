"""Explicit-path wrapper around the unchanged exact-profile PLINK installer."""
import argparse
from pathlib import Path
import sys
import install_plink

def main():
    p = argparse.ArgumentParser(description=__doc__)
    for name in ['home', 'archives', 'report']:
        p.add_argument('--' + name, type=Path, required=True)
    p.add_argument('--allow-install', action='store_true')
    p.add_argument('--allow-download', action='store_true')
    args = p.parse_args()
    try:
        return install_plink.install(args)
    except OSError as error:
        print('SKIP3: exact private PLINK setup unavailable: ' + str(error) +
              '. No dependent analysis is admitted. Keep this attempt; supply permitted writable NEW paths and the exact archives, or leave analysis skipped.', file=sys.stderr)
        return 3

if __name__ == '__main__':
    raise SystemExit(main())
