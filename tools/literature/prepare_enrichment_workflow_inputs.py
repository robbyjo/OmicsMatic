"""Prepare the synthetic original-program workflow inputs on a fresh checkout.

No R runtime, download, analysis or input replacement is performed. Matching
existing LF/CRLF inputs can be reused without changing any byte of the originals.
"""
import argparse
import hashlib
import json
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[2]


def canonical_sha(data):
    return hashlib.sha256(data.replace(b'\r\n', b'\n')).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--destination', type=Path,
        default=ROOT/'build/literature-review/enrichment-runtime/original-clusterprofiler-probe')
    args = parser.parse_args()
    source = Path(__file__).with_name('fixtures')/'enrichment'
    metadata = json.loads((source/'provenance.json').read_text(encoding='utf-8'))
    assert metadata['schema'] == 'jlinalg-enrichment-workflow-inputs-v1'
    data = {}
    for name, expected in metadata['files'].items():
        assert Path(name).name == name and name.endswith('.tsv')
        data[name] = (source/name).read_bytes().replace(b'\r\n', b'\n')
        if canonical_sha(data[name]) != expected:
            raise ValueError('Bundled synthetic input differs: '+name)
    destination = args.destination.resolve()
    if destination.exists():
        if not destination.is_dir():
            raise ValueError('Destination is not a directory; preserve it and choose a new path')
        for name, expected in metadata['files'].items():
            target = destination/name
            if not target.is_file() or canonical_sha(target.read_bytes()) != expected:
                raise ValueError('Existing input is incomplete or changed: '+str(target)+
                    '; preserve it and select a NEW --destination')
        print('Matching synthetic workflow inputs reused; existing bytes and outputs preserved:', destination)
    else:
        destination.mkdir(parents=True)
        for name, value in data.items():
            (destination/name).write_bytes(value)
        print('Synthetic workflow inputs prepared; no scientific analysis was run:', destination)
    return 0


if __name__ == '__main__':
    try:
        raise SystemExit(main())
    except (OSError, ValueError, AssertionError) as error:
        print('INPUT PREPARATION FAILED:', error, file=sys.stderr)
        raise SystemExit(2)
