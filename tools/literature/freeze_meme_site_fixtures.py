# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Freeze original complete MEME XML, independently checked raw inputs and exact-polynomial oracles."""
import hashlib
import json
from pathlib import Path
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[2]
BASE = ROOT / 'build/literature-review/meme-runtime'
DEST = ROOT / 'src/test/resources/org/jlinalg/sequence/meme-discovery-sites'


def byte_pin(data):
    return dict(bytes=len(data), sha256=hashlib.sha256(data).hexdigest())


def fasta(data):
    records = []; weights = None
    for line in data.decode('ascii').splitlines():
        if line.startswith('>WEIGHTS '):
            assert not records and weights is None
            weights = list(map(float, line[9:].split()))
        elif line.startswith('>'):
            records.append(dict(name=line[1:].split()[0], bases='', weight=1.))
        elif line:
            assert records
            records[-1]['bases'] += line
    if weights is not None:
        assert len(weights) == len(records)
        for row, weight in zip(records, weights):
            row['weight'] = weight
    assert all(r['bases'] and set(r['bases']) <= set('ACGT') and r['weight'] > 0 for r in records)
    return records


def main():
    assert not DEST.exists()
    audit_path = BASE / 'site-independent-audit-2/COMPLETED.json'
    audit = json.loads(audit_path.read_bytes())
    assert audit['status'] == 'passed-independent-original-MEME-site-matrix-score-and-tiling-audit'
    assert audit['original_families'] == len(audit['families']) == 42
    files = {'EXPECTED.json': audit_path.read_bytes()}; links = []; original = {}
    for family in audit['families']:
        xml = BASE / family['xml']; data = xml.read_bytes(); assert byte_pin(data) == family['source_xml']
        files['xml/' + family['name'] + '.xml'] = data; original[family['xml']] = byte_pin(data)
        folder = BASE / family['xml'].split('/')[0]
        if xml.parent.name == 'meme_out':
            raw_input = xml.parent.parent / 'seqs-centered'; bg_input = folder / 'captured/inputs/background.txt'
        else:
            process = json.loads((xml.parent.parent / 'PROCESS.json').read_bytes())
            filename = process['command'][1].rsplit('/', 1)[1]
            raw_input = folder / 'captured/inputs' / filename
            bg_filename = process['command'][process['command'].index('-bfile') + 1].rsplit('/', 1)[1]
            bg_input = folder / 'captured/inputs' / bg_filename
        raw = raw_input.read_bytes(); parsed = fasta(raw)
        expected = [{k: row[k] for k in ['name', 'bases', 'weight']} for row in family['expected']['input_records']]
        assert parsed == expected, family['name']
        background = {}
        for row in bg_input.read_text().splitlines():
            b, p = row.split(); assert b in 'ACGT' and b not in background; background[b] = float(p)
        supplied = [background[b] for b in 'ACGT']
        resolved = [(supplied[b] + supplied[3 - b]) / 2 for b in range(4)] if family['expected']['both_strands'] else supplied
        assert resolved == family['expected']['resolved_background'], family['name']
        for path in [raw_input, bg_input]:
            name = path.relative_to(BASE).as_posix(); files['inputs/' + name] = path.read_bytes(); original[name] = byte_pin(files['inputs/' + name])
        links.append(dict(family=family['name'], raw_fasta='inputs/' + raw_input.relative_to(BASE).as_posix(),
                          raw_background='inputs/' + bg_input.relative_to(BASE).as_posix(), exact_prepared_records_match=True))
    files['RAW-INPUT-LINKS.json'] = (json.dumps(links, indent=2) + '\n').encode()
    files['README.txt'] = (
        'Complete original XML and independently checked literal FASTA/WEIGHTS/background inputs.\n'
        'EXPECTED.json uses independent complete-word half-polynomial enumeration with integer masses,\n'
        'not the Java sequential floating-point convolution. Original site/diagram probabilities agree\n'
        'at printed precision, and every PWM and prior score is independently reconstructed.\n'
        'Source pipeline metadata warnings remain unresolved; only their MEME subrun is audited here.\n'
        'No EM fit, motif-significance derivation, complete report admission, biological validity or execution authenticity is claimed.\n').encode()
    DEST.mkdir(parents=True)
    with zipfile.ZipFile(DEST / 'original-site-contracts.zip', 'x', zipfile.ZIP_DEFLATED) as archive:
        for name, data in sorted(files.items()):
            info = zipfile.ZipInfo(name, (2026, 11, 6, 0, 0, 0)); info.compress_type = zipfile.ZIP_DEFLATED; info.external_attr = 0o100644 << 16
            archive.writestr(info, data)
    manifest = dict(schema_version=1, original_families=42, motifs=sum(len(f['motifs']) for f in audit['families']),
                    contributing_sites=audit['contributing_sites'], scanned_hits=audit['scanned_hits'],
                    sequence_diagrams=sum(len(f['scans']) for f in audit['families']),
                    complete_null_tail_cells=sum(len(m['score_model']['tails']) for f in audit['families'] for m in f['motifs']),
                    source_pins=original, files={n: byte_pin(data) for n, data in files.items()},
                    freezer=byte_pin(Path(__file__).read_bytes()), archive=byte_pin((DEST / 'original-site-contracts.zip').read_bytes()),
                    implementation_credit=False)
    (DEST / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n', encoding='utf-8', newline='\n')
    print(json.dumps({k: manifest[k] for k in ['original_families', 'motifs', 'contributing_sites', 'scanned_hits', 'sequence_diagrams', 'complete_null_tail_cells', 'archive']}))


if __name__ == '__main__':
    if not __debug__:
        raise RuntimeError('Never disable raw-input/oracle fixture bindings')
    main()
