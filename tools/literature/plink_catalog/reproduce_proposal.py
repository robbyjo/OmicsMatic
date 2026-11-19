# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Reproduce a source-review proposal without applying it or running scientific code."""
from pathlib import Path
import argparse
import hashlib
import json
import sys
import zipfile

sys.dont_write_bytecode = True
ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT / 'tools/literature'))
from plink_catalog import primary, model


def pin(path):
    with path.open('rb') as stream:
        return dict(bytes=path.stat().st_size,
                    sha256=hashlib.file_digest(stream, 'sha256').hexdigest())


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, required=True)
    parser.add_argument('--source-cache', type=Path)
    parser.add_argument('--author-archive', type=Path)
    args = parser.parse_args()
    primary.require(bool(args.source_cache) == bool(args.author_archive),
                    'Supply both exact source inputs for a fresh primary audit, or neither for frozen proposal readback')
    packet = ROOT / 'docs/literature/plink-source-review'
    qualification = json.loads((packet / 'QUALIFICATION.json').read_bytes())
    for name, wanted in qualification['source_pins'].items():
        primary.require(pin(ROOT / name) == wanted, 'Checkpoint source changed: ' + name)
    for name, wanted in qualification['artifacts'].items():
        primary.require(pin(packet / name) == wanted, 'Checkpoint artifact changed: ' + name)
    census = json.loads((packet / 'CENSUS.json').read_bytes())
    before = {}
    retained_after = {}
    with zipfile.ZipFile(packet / 'proposal.zip') as bundle:
        primary.require(len(bundle.namelist()) == len(set(bundle.namelist())), 'Duplicate proposal member')
        primary.require(set(bundle.namelist()) == set(qualification['packet_members']), 'Proposal packet population differs')
        for name, wanted in qualification['packet_members'].items():
            raw = bundle.read(name)
            primary.require(dict(bytes=len(raw), sha256=primary.digest(raw)) == wanted, 'Proposal member differs: ' + name)
            if name.startswith('before/'):
                before[name.removeprefix('before/')] = raw
            elif name.startswith('after/'):
                retained_after[name.removeprefix('after/')] = raw
        for name, wanted in qualification['source_pins'].items():
            primary.require(pin(ROOT / name) == wanted and bundle.read('source/' + name) == (ROOT / name).read_bytes(),
                            'Frozen/current source identity differs: ' + name)
    fresh = False
    if args.source_cache:
        primary.require(primary.audit(args.source_cache.resolve(), args.author_archive.resolve()) == census,
                        'Fresh primary source audit differs from the checkpoint')
        fresh = True
    after = model.transition(before, census)
    primary.require(after == retained_after, 'Deterministic proposal bytes differ')
    result = model.check(before, after, census)
    # Only new owned output receives files. Canonical paths are never written.
    output = args.out.resolve()
    output.mkdir(parents=True, exist_ok=False)
    for name, raw in after.items():
        destination = output / 'proposed-core' / name
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_bytes(raw)
    for name, wanted in qualification['source_pins'].items():
        primary.require(pin(ROOT / name) == wanted, 'Source changed during proposal readback: ' + name)
    for name, wanted in qualification['artifacts'].items():
        primary.require(pin(packet / name) == wanted, 'Evidence changed during proposal readback: ' + name)
    result.update(status='PASS_FROZEN_SOURCE_PROPOSAL_REPRODUCTION', fresh_primary_audit=fresh,
                  retained_evidence_unchanged=True, proposal_files_written=len(after),
                  canonical_metadata_written=False, author_code_executed=False,
                  note='Reproduces proposed metadata only; no cumulative catalog/family admission or scientific reproduction.')
    (output / 'COMPLETED.json').write_bytes(primary.encoded(result))
    print(json.dumps(result), flush=True)
    return 0


if __name__ == '__main__':
    try:
        raise SystemExit(main())
    except primary.MissingSource as error:
        print('SKIP3: ' + str(error) + '. ' + primary.ACTION, file=sys.stderr)
        raise SystemExit(3)
    except Exception as error:
        print('FAIL2: no catalog admission; retain the failed attempt and retry NEW paths: ' +
              repr(error) + '. ' + primary.ACTION, file=sys.stderr)
        raise SystemExit(2)
