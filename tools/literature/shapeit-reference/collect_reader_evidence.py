#!/usr/bin/env python3
"""Retain complete synthetic original SHAPEIT reader and cohort-control evidence."""
import argparse
import csv
import hashlib
import io
import json
from pathlib import Path
import zipfile

ROOT = Path(__file__).resolve().parents[3]


def sha(raw):
    return hashlib.sha256(raw).hexdigest()


def collect(runtime, output):
    runtime, output = runtime.resolve(), output.resolve()
    if output.exists():
        raise ValueError('Preserve existing evidence; choose a NEW output archive')
    reader = runtime / 'reader-protocol-attempt-1'
    phase = runtime / 'cohort-controls-attempt-1'
    proof = json.loads((reader / 'observed.json').read_bytes())
    phased = json.loads((phase / 'observed.json').read_bytes())
    assert proof['status'] == 'passed-800-initial-target-reader-cells-from-two-original-source-profiles'
    assert phased['status'] == 'completed-four-fresh-original-cohort-phasing-controls'
    assert len(proof['cases']) == len(phased['cases']) == 4
    members = {}

    def put(name, raw):
        assert name not in members and not name.startswith('/') and '..' not in Path(name).parts
        members[name] = raw

    reader_cells = 0
    for case in proof['cases']:
        raw = (reader / case['result']).read_bytes()
        assert sha(raw) == case['sha256']
        rows = list(csv.DictReader(io.StringIO(raw.decode()), delimiter='\t'))
        assert len(rows) == 200 and len({(r['variant'], r['sample']) for r in rows}) == 200
        assert {r['variant'] for r in rows} == {'0', '1', '2', '3'}
        assert {r['sample'] for r in rows} == {'S' + str(i) for i in range(1000, 1050)}
        put('captures/' + case['result'], raw)
        reader_cells += len(rows)
    for case in phased['cases']:
        name = case['profile'] + '-' + case['format']
        assert case['source_sha256'] == sha((reader / ('wire.bcf' if case['format'] == 'bcf' else 'wire.vcf.gz')).read_bytes())
        assert case['output_sha256'] == sha((phase / (name + '.phased.bcf')).read_bytes())
        assert case['decoded_sha256'] == sha((phase / (name + '.phased.vcf')).read_bytes())
        for suffix in ['.phased.bcf', '.phased.vcf', '.original.log', '.stdout', '.stderr']:
            put('captures/' + name + suffix, (phase / (name + suffix)).read_bytes())
    for name in ['wire.bcf', 'wire.bcf.csi', 'wire.vcf.gz', 'wire.vcf.gz.csi']:
        put('inputs/' + name, (reader / name).read_bytes())
    for directory, label in [(reader, 'reader'), (phase, 'cohort'), (runtime / 'original-build-attempt-1', 'build'), (runtime / 'toolchain-attempt-1', 'toolchain')]:
        for path in sorted(directory.glob('*')):
            if path.is_file() and path.suffix in {'.json', '.stdout', '.stderr', '.lock', '.toml'}:
                put('records/' + label + '/' + path.name, path.read_bytes())
    for name in ['ReaderProtocol.cpp', 'MakeWireFixture.cpp', 'collect_reader_evidence.py']:
        put('protocols/' + name, (Path(__file__).parent / name).read_bytes())
    excluded = []
    for profile, manifest_name in [('4.1.3', '4.1.3-selected-source.json'), ('5-c34d', '5-c34d-selected-source.json')]:
        raw = (runtime / 'upstream' / manifest_name).read_bytes()
        manifest = json.loads(raw)
        put('records/' + manifest_name, raw)
        for item in manifest['files']:
            raw = (runtime / 'upstream' / manifest['source'] / item['path']).read_bytes()
            assert sha(raw) == item['sha256']
            assert hashlib.sha1(b'blob ' + str(len(raw)).encode() + b'\0' + raw).hexdigest() == item['git_blob_sha1']
            if raw.startswith(b'\x7fELF'):
                excluded.append(dict(profile=profile, **item))
            else:
                put('upstream/' + profile + '/' + item['path'], raw)
    provenance = dict(schema_version=1, status='complete-original-initial-reader-and-cohort-reference-capture',
                      original_reader_cells=reader_cells, original_cohort_phasing_cells=sum(c['cells'] for c in phased['cases']),
                      original_profiles={'4.1.3': 'ca896a27d7d6650c56f516f7de8d537fdf114a1d', '5-c34d': 'c34d4db3e99a2f7e23deb727671ae260901a5886'},
                      excluded_original_prebuilt_ELF_members=excluded,
                      scope='Complete synthetic original reader populations and four actual original cohort-phasing outputs, with raw input wire representations, logs, source snapshots and build configuration. No runtime binary, phase-accuracy claim, reference/scaffold/haploid/rare/ligation certification or public adapter is supplied. SHAPEIT2 was not downloaded or executed; its separate license and exact historical build remain unresolved.')
    put('provenance.json', (json.dumps(provenance, indent=2) + '\n').encode())
    put('SHA256SUMS', ''.join(sha(raw) + '  ' + name + '\n' for name, raw in sorted(members.items())).encode())
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open('xb') as stream, zipfile.ZipFile(stream, 'w', compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for name, raw in sorted(members.items()):
            info = zipfile.ZipInfo(name, (2026, 11, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o100644 << 16
            archive.writestr(info, raw)
    with zipfile.ZipFile(output) as archive:
        assert archive.testzip() is None
        assert set(archive.namelist()) == set(members)
        for name, raw in members.items():
            assert archive.read(name) == raw
    result = dict(path=str(output), sha256=sha(output.read_bytes()), bytes=output.stat().st_size,
                  members=len(members), decoded_bytes=sum(map(len, members.values())), original_reader_cells=reader_cells,
                  original_cohort_phasing_cells=provenance['original_cohort_phasing_cells'], excluded_original_prebuilt_ELFs=len(excluded))
    print(json.dumps(result, indent=2))
    return result


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--runtime', type=Path, required=True)
    parser.add_argument('--out', type=Path, required=True)
    arguments = parser.parse_args()
    collect(arguments.runtime, arguments.out)
