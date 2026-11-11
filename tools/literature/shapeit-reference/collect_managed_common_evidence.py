#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Freeze complete ordinary-GT inputs, original joins and actual managed fitting outputs for portable replay."""
import argparse
import base64
import hashlib
import json
from pathlib import Path
import zipfile


def collect(source, output):
    members = {}
    sha = lambda raw: hashlib.sha256(raw).hexdigest()
    def add(name, raw):
        if name in members:
            assert members[name] == raw
        members[name] = raw
        return name
    def payload(path):
        raw = path.read_bytes()
        return add('data/' + sha(raw) + '-' + path.name, raw)
    cases = []
    directories = [('public-' + name, source / 'public-cli' / name) for name in [
        'cohort-default', 'cohort-map', 'reference-default', 'scaffold-default',
        'reference-scaffold-map', 'cohort-filters', 'cohort-duplicates', 'reference-duplicates']]
    directories.append(('policy-count-declared', source / 'policies/count-declared'))
    candidate = None
    for name, directory in directories:
        complete = json.loads((directory / 'COMPLETED.json').read_bytes())
        manifest = json.loads((directory / 'manifest.json').read_bytes())
        assert complete['manifest_sha256'] == sha((directory / 'manifest.json').read_bytes())
        assert complete['scientific_result_admitted'] and not complete['phase_accuracy_certified']
        for relative, identity in manifest['files'].items():
            path = directory / relative
            assert path.is_file() and not path.is_symlink()
            raw = path.read_bytes()
            assert len(raw) == identity['bytes'] and sha(raw) == identity['sha256']
        analysis = directory / 'analysis'
        record = json.loads((analysis / 'analysis.json').read_bytes())
        assert record['scientific_result_admitted'] and record['profile'] == 'shapeit5-c34d-common-linux-x86_64'
        request = record['request']
        capture = (analysis / 'original.join.tsv').read_bytes()
        before = [row.split('\t') for row in capture.decode().splitlines() if row.startswith('F\tB\t')]
        bindings = []
        roles = [role for role in ['TARGET', 'REFERENCE', 'SCAFFOLD'] if request[role.lower()] is not None]
        for panel, role in enumerate(roles):
            path = analysis / 'staged' / (role.lower() + '.bcf')
            for kind, local in [('B', path), ('I', Path(str(path) + '.csi'))]:
                entry = next(row for row in before if int(row[2]) == panel and row[3] == kind)
                raw = local.read_bytes()
                assert sha(raw) == entry[5] and len(raw) == int(entry[6])
                bindings.append(dict(panel=panel, role=role, kind=kind, member=payload(local),
                                     peer_path=base64.b64decode(entry[4], validate=True).decode(), sha256=sha(raw)))
        assert sha((analysis / 'phased.bcf').read_bytes()) == record['output_identity']['sha256']
        for label in ['join-process', 'fit-process']:
            for suffix in ['', '.loader-check']:
                process = json.loads((analysis / (label + suffix) / 'process.json').read_bytes())
                assert process['status'] == 'completed-process-only' and process['owned_live_after_cleanup'] == [] and process['exit_code'] == 0
        runtime = record['runtime']['files']
        assert runtime['bin/phase_common']['sha256'] == '74d6f951f6103e709ee3e754397a86b6b2cb4e0ab0bf543c1378cb4b7d894689'
        assert runtime['bin/joined-wire']['sha256'] == '032cf41c4f1b54536032b1e57a5115364a1a230c128c0a8594d56981953b2898'
        cases.append(dict(name=name, roles=roles, bindings=bindings, parameters={key:request[key] for key in ['contig','region','snpsOnly','minimumMaf','counts','missing']},
                          map_member=None if request['map'] is None else payload(analysis / 'staged/genetic.map.tsv'),
                          protocol_member=add('capture/' + name + '.join.tsv', capture), protocol_sha256=sha(capture),
                          output_member=payload(analysis / 'phased.bcf'), target_counts_member=payload(analysis / 'output-audit/target-allele-counts.tsv'),
                          input_counts_member=payload(analysis / 'input-count-review.tsv'), admission=record['admission'],
                          source_AN_includes_reference_rows=record['source_AN_includes_reference_rows'],
                          original_public_manifest_sha256=complete['manifest_sha256']))
    assert len(cases) == 9 and sum(c['admission']['selectedRows'] for c in cases) == 1131
    assert sum(c['admission']['selectedTargetCells'] for c in cases) == 41470
    assert sum(2*c['admission']['selectedReferenceCells'] for c in cases) == 30160
    assert sum(c['admission']['appliedScaffoldConstraints'] for c in cases) == 573
    add('cases.json', (json.dumps(cases, indent=2) + '\n').encode())
    add('protocol/collect_managed_common_evidence.py', Path(__file__).read_bytes())
    add('README.txt', b'Nine actual managed original c34d common-stage fits. Complete raw BCF/CSI inputs, bound original synchronized captures, map, original fitting output and both native count tables are retained. Portable replay checks data/output contracts without executing an upstream runtime. It does not independently certify runtime execution, phase accuracy or complete paper workflows; those are separate recorded evidence scopes.\n')
    add('SHA256SUMS', ''.join(sha(raw) + '  ' + name + '\n' for name, raw in sorted(members.items())).encode())
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open('xb') as stream, zipfile.ZipFile(stream, 'w', compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for name, raw in sorted(members.items()):
            info = zipfile.ZipInfo(name, (1980,1,1,0,0,0)); info.compress_type = zipfile.ZIP_DEFLATED; archive.writestr(info, raw)
    print(json.dumps(dict(status='frozen-nine-managed-common-output-replays',sha256=sha(output.read_bytes()),members=len(members),decoded_bytes=sum(map(len,members.values())))))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    collect(args.source, args.output)
