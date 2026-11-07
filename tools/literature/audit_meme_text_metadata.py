# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Audit original text score metadata against exact official C/header sources and all retained weighted edge controls."""
import argparse
from decimal import Decimal
import hashlib
import json
from pathlib import Path
import re
import tarfile
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
BASE = ROOT / 'build/literature-review/meme-runtime'


def pin(path):
    raw = path.read_bytes(); return dict(bytes=len(raw), sha256=hashlib.sha256(raw).hexdigest())


def main():
    parser = argparse.ArgumentParser(description=__doc__); parser.add_argument('--out', type=Path, required=True)
    out = parser.parse_args().out.resolve(); assert out.is_relative_to(BASE) and not out.exists(); out.mkdir()
    resource = ROOT / 'src/main/resources/org/jlinalg/omics/meme-discovery/source-profiles.json'
    profiles = json.loads(resource.read_bytes()); sources = []; cases = []
    names = [('MEME_5_5_9', ROOT / 'build/literature-review/fimo-runtime/meme-5.5.9.tar.gz', '5.5.9'),
        ('MEME_5_5_4', BASE / 'historical-source-intake-1/meme-5.5.4.tar.gz', '5.5.4'),
        ('MEME_4_9_1_BASE', BASE / 'historical-source-intake-1/meme_4.9.1.tar.gz', '4.9.1-base'),
        ('MEME_4_9_1_PATCH2', BASE / 'historical-source-intake-1/meme_4.9.1_2.tar.gz', '4.9.1-patch2')]
    for profile, archive, edition in names:
        spec = profiles[profile]; assert pin(archive) == {k: spec['archive'][k] for k in ['bytes', 'sha256']}
        selected = {}
        with tarfile.open(archive) as original:
            for relative in ['src/meme.h', 'src/display.c']:
                item = next(m for m in original.getmembers() if m.name.endswith('/' + relative)); raw = original.extractfile(item).read()
                actual = dict(bytes=len(raw), sha256=hashlib.sha256(raw).hexdigest()); assert actual == spec['scientific_sources'][relative]
                (out / (profile + '-' + Path(relative).name)).write_bytes(raw)
                selected[relative] = dict(pin=actual, relevant_lines=[dict(line=i + 1, text=line) for i,line in enumerate(raw.decode().splitlines())
                    if line.startswith('#define wps') or re.search(r'int n\s*=\s*wps\(', line)])
        sources.append(dict(profile=profile, official_archive=pin(archive), scientific_sources=selected))
        folder = BASE / ('sites-original-' + edition + '-1/captured')
        for xml in sorted(folder.glob('*/result/meme.xml')):
            root = ET.parse(xml).getroot(); records = list(root.findall('training_set/sequence')); motifs = list(root.find('motifs'))
            lines = xml.with_name('meme.txt').read_text().splitlines(); starts = [i for i,l in enumerate(lines) if l.strip().startswith('log-odds matrix:')]
            assert len(starts) == len(motifs)
            for motif, at in zip(motifs, starts):
                fields = dict(re.findall(r'([A-Za-z][A-Za-z0-9_-]*)\s*=\s*([^\s]+)', lines[at])); width = int(motif.attrib['width'])
                weighted_residues = sum(float(s.attrib['weight']) * int(s.attrib['length']) for s in records)
                expected = int(max(weighted_residues - len(records) * (width - 1), 2))
                ordinary_weighted_windows = int(sum(float(s.attrib['weight']) * (int(s.attrib['length']) - width + 1) for s in records))
                assert int(fields['n']) == expected
                assert Decimal(fields['bayes']) == Decimal(motif.attrib['bayes_threshold']) and Decimal(fields['E']) == Decimal(motif.attrib['e_value'])
                matrix = list(motif.find('scores/alphabet_matrix'))
                for i in range(width):
                    assert [Decimal(v) for v in lines[at + i + 1].split()] == [Decimal(v.text) for v in matrix[i]]
                cases.append(dict(profile=profile, family=xml.parent.parent.name, motif=motif.attrib['id'], original_XML=pin(xml), original_text=pin(xml.with_name('meme.txt')),
                    original_reported_n=int(fields['n']), source_formula_n=expected, ordinary_weighted_windows=ordinary_weighted_windows, centibit_cells=width * 4))
    assert len(cases) == 32 and sum(r['source_formula_n'] != r['ordinary_weighted_windows'] for r in cases) == 8
    result = dict(status='passed-exact-original-MEME-text-score-metadata-and-mixed-weight-source-convention', profiles=sources,
        original_motifs=32, original_families=16, centibit_cells=sum(r['centibit_cells'] for r in cases),
        ordinary_weighted_window_discrepancies_retained=8, cases=cases,
        source_pins={resource.relative_to(ROOT).as_posix(): pin(resource), Path(__file__).relative_to(ROOT).as_posix(): pin(Path(__file__))},
        scope='Original source text n uses max(weighted total residues minus unweighted sample count times (width-1),2), truncated to int. This is preserved, not repaired into a different weighted-window definition. Counts, scores and raw metadata are control evidence, not biological inference.')
    (out / 'COMPLETED.json').write_text(json.dumps(result, indent=2) + '\n', encoding='utf8', newline='\n')
    print(json.dumps({k: result[k] for k in ['status','original_motifs','original_families','centibit_cells','ordinary_weighted_window_discrepancies_retained']}))


if __name__ == '__main__':
    if not __debug__: raise RuntimeError('Never disable official-source metadata checks')
    main()
