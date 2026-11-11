# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Retain original Ballgown shared/unknown-strand feature behavior, without generic interval substitution."""
import argparse
import json
import platform
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'tools/literature'))
from collect_stringtie_controls import invoke, pin, save, VERSIONS


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args()
    out = args.out.resolve()
    assert platform.system() == 'Linux' and platform.machine() == 'x86_64'
    base = ROOT / 'build/literature-review/stringtie-runtime'
    assert out.is_relative_to(base.resolve()) and not out.exists()
    out.mkdir()
    inputs = out / 'inputs'
    inputs.mkdir()
    intake = base / 'source-intake-1'
    original = json.loads((intake / 'COMPLETED.json').read_bytes())
    bam = base / 'original-controls-1/inputs/reads.bam'
    before_bam = pin(bam)
    cases = []
    orders = dict(unknown_first=[('.', 'unknown'), ('+', 'plus'), ('-', 'minus')],
                  known_first=[('+', 'plus'), ('-', 'minus'), ('.', 'unknown')])
    for name, order in orders.items():
        rows = []
        for strand, identifier in order + [('+', 'other_contig')]:
            contig = 'chrB' if identifier == 'other_contig' else 'chrA'
            attrs = 'gene_id "g.' + identifier + '"; transcript_id "t.' + identifier + '";'
            for feature, start, end in [('transcript', 101, 550), ('exon', 101, 250), ('exon', 401, 550)]:
                rows.append(f'{contig}\tcontrol\t{feature}\t{start}\t{end}\t.\t{strand}\t.\t{attrs}\n')
        (inputs / (name + '.gtf')).write_text(''.join(rows), encoding='ascii', newline='\n')
    for edition in original['editions']:
        version = edition['version']
        binary = intake / edition['executable']
        assert pin(binary) == edition['executable_pin']
        for name in orders:
            folder = out / version / name
            folder.mkdir(parents=True)
            command = [str(binary), str(bam), '-G', str(inputs / (name + '.gtf')), '-e', '-B', '-A', 'genes.tsv', '-p', '1', '-o', 'transcripts.gtf']
            receipt = invoke(command, folder, 'fit')
            cases.append(dict(version=version, case=name, process=receipt,
                              files={p.name: pin(p) for p in sorted(folder.iterdir()) if p.is_file()}))
            save(out / 'PROGRESS.json', cases)
        assert pin(binary) == edition['executable_pin']
    assert len(cases) == 14 and pin(bam) == before_bam
    save(out / 'CAPTURED.json', dict(status='captured-original-StringTie-shared-feature-boundaries-audit-required',
                                   cases=cases, inputs={p.name: pin(p) for p in sorted(inputs.iterdir())},
                                   bam=before_bam, original_intake=pin(intake / 'COMPLETED.json'),
                                   implementation_credit=False,
                                   warning='Unknown strand is a compatibility wildcard in original feature de-duplication. Original transcript, feature, relation, gene and abundance populations require exact source-aware audit; do not silently relabel feature strands or infer transcript zeros.'))
    print(json.dumps(dict(cases=len(cases), successful=sum(c['process']['exit_code'] == 0 for c in cases), timed_out=sum(c['process']['timed_out'] for c in cases))))


if __name__ == '__main__':
    if not __debug__:
        raise RuntimeError('Never disable original boundary identity checks')
    main()
