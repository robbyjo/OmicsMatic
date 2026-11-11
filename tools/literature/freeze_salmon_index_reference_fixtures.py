# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Freeze complete original Cereal reference tables with an independent input-FASTA population."""
import hashlib
import json
from pathlib import Path
import struct
import zipfile

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / 'docs/literature/salmon-native-development'
OUT = ROOT / 'src/test/resources/org/jlinalg/sequence/salmon-index-references'


def pin(path):
    raw = path.read_bytes()
    return dict(bytes=len(raw), sha256=hashlib.sha256(raw).hexdigest())


def main():
    if not __debug__:
        raise RuntimeError('Never disable original fixture provenance checks')
    assert not OUT.exists()
    proof = json.loads((SOURCE / 'VALIDATION.json').read_bytes())
    assert pin(SOURCE / 'controls.zip') == {k: proof['archive'][k] for k in ['bytes', 'sha256']}
    source = ROOT / 'src/main/resources/org/jlinalg/omics/salmon-runtime-setup/health-inputs/gentrome.fa'
    health = json.loads((source.parent.parent / 'health-lock.json').read_bytes())
    assert pin(source) == health['files']['gentrome.fa']
    expected = []
    for line in source.read_text().splitlines():
        if line.startswith('>'):
            expected.append([line[1:].split()[0], ''])
        else:
            expected[-1][1] += line
    assert len(expected) == 41 and all(seq and set(seq) <= set('ACGT') and not seq.endswith('A' * 10) for name, seq in expected)
    OUT.mkdir(parents=True)
    fixture = OUT / 'original-reference-tables.zip'
    rows = []
    with zipfile.ZipFile(SOURCE / 'controls.zip') as original, zipfile.ZipFile(fixture, 'x', compression=zipfile.ZIP_DEFLATED, compresslevel=9) as output:
        output.writestr('input-gentrome.fa', source.read_bytes())
        output.writestr('expected-references.tsv', 'name\toriginal_length\tnormalized_length\n' + ''.join(f'{name}\t{len(seq)}\t{len(seq)}\n' for name, seq in expected))
        for version in ['1.0.0', '1.1.0', '1.5.2', '1.8.0', '1.9.0']:
            names = []
            for name in ['ctable.bin', 'complete_ref_lens.bin', 'reflengths.bin']:
                member = 'evidence/reference-protocols-3/' + version + '/index/' + name
                raw = original.read(member)
                saved = proof['archive_files'][member]
                assert len(raw) == saved['bytes'] and hashlib.sha256(raw).hexdigest() == saved['sha256']
                output.writestr(version + '/' + name, raw)
                names.append(dict(member=member, file=name, bytes=len(raw), sha256=hashlib.sha256(raw).hexdigest()))
            raw = original.read('evidence/reference-protocols-3/' + version + '/index/ctable.bin')
            count = struct.unpack_from('<Q', raw)[0]
            assert count == len(expected)
            at = 8
            for name, seq in expected:
                size = struct.unpack_from('<Q', raw, at)[0]
                at += 8
                assert raw[at:at + size].decode('utf-8') == name
                at += size
            indexed = struct.unpack_from('<Q', raw, at)[0]
            at += 8
            assert indexed == count
            extensions = struct.unpack_from('<' + 'I' * indexed, raw, at)
            assert extensions == (0,) * indexed
            at += 4 * indexed
            positions = struct.unpack_from('<Q', raw, at)[0]
            at += 8
            assert len(raw) == at + positions * 8
            text = 'ordinal\tcontig_ref\tfull_ref\tname\tstart\tforward\n'
            for ordinal in range(positions):
                ref, packed = struct.unpack_from('<II', raw, at + ordinal * 8)
                assert ref < count and packed & 0x7fffffff < len(expected[ref][1])
                text += f'{ordinal}\t{ref}\t{ref}\t{expected[ref][0]}\t{packed & 0x7fffffff}\t{str(bool(packed & 0x80000000)).lower()}\n'
            output.writestr(version + '/expected-positions.tsv', text)
            rows.append(dict(version=version, references=count, contig_references=indexed, positions=positions, complete_original_files=names))
    provenance = dict(status='complete-original-Salmon-reference-tables-with-independent-input-FASTA-identities',
                      source_checkpoint=pin(SOURCE / 'VALIDATION.json'), source_archive=pin(SOURCE / 'controls.zip'), input_fasta=pin(source),
                      fixture_producer=pin(Path(__file__)), archive=pin(fixture), cases=rows,
                      scope='Five original indexes reuse one41-reference synthetic input population. Complete original files are copied unchanged; '
                            'expected names/original lengths come from independently pinned input FASTA. This input is uppercase ACGT without clip-eligible tails. '
                            'Expected serialized positions are decoded independently in Python. No short-reference/decoy-exclusion/dedup/raw-normalization edge protocol is claimed here.',
                      whole_index_or_user_analysis_admitted=False)
    (OUT / 'provenance.json').write_text(json.dumps(provenance, indent=2) + '\n', encoding='utf-8', newline='\n')
    (OUT / '.gitattributes').write_text('* -text\n', encoding='ascii', newline='\n')
    print(json.dumps(dict(cases=len(rows), complete_original_references=sum(r['references'] for r in rows), complete_original_positions=sum(r['positions'] for r in rows), archive=pin(fixture))))


if __name__ == '__main__':
    main()
