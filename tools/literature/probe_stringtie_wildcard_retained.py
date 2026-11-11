# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Test original feature sharing when the retained representative itself has unknown strand."""
import argparse
import json
from pathlib import Path
import platform
import sys

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'tools/literature'))
from collect_stringtie_controls import invoke, pin, save


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, required=True)
    out = parser.parse_args().out.resolve()
    base = ROOT / 'build/literature-review/stringtie-runtime'
    assert platform.system() == 'Linux' and out.is_relative_to(base.resolve()) and not out.exists()
    out.mkdir(); inputs = out / 'inputs'; inputs.mkdir()
    rows = []
    for strand, name, exons in [('.', 'unknown', [(101, 250), (401, 550)]),
                                ('+', 'plus', [(101, 250), (401, 550), (601, 650)]),
                                ('-', 'minus', [(101, 250), (401, 550), (601, 650)]),
                                ('+', 'other_contig', [(101, 250), (401, 550)])]:
        contig = 'chrB' if name == 'other_contig' else 'chrA'
        attributes = f'gene_id "g.{name}"; transcript_id "t.{name}";'
        for kind, start, end in [('transcript', exons[0][0], exons[-1][1]), *[('exon', a, b) for a, b in exons]]:
            rows.append(f'{contig}\tcontrol\t{kind}\t{start}\t{end}\t.\t{strand}\t.\t{attributes}\n')
    guide = inputs / 'guide.gtf'; guide.write_text(''.join(rows), encoding='ascii', newline='\n')
    intake = base / 'source-intake-1'; original = json.loads((intake / 'COMPLETED.json').read_bytes())
    bam = base / 'original-controls-1/inputs/reads.bam'; bam_pin = pin(bam); cases = []
    for edition in original['editions']:
        binary = intake / edition['executable']; assert pin(binary) == edition['executable_pin']
        folder = out / edition['version']; folder.mkdir()
        process = invoke([str(binary), str(bam), '-G', str(guide), '-e', '-B', '-A', 'genes.tsv', '-p', '1', '-o', 'transcripts.gtf'], folder, 'fit')
        cases.append(dict(version=edition['version'], process=process, files={p.name: pin(p) for p in sorted(folder.iterdir()) if p.is_file()}))
        save(out / 'PROGRESS.json', cases); assert pin(binary) == edition['executable_pin']
    assert pin(bam) == bam_pin
    save(out / 'CAPTURED.json', dict(status='captured-original-StringTie-retained-unknown-strand-boundary-audit-required',
                                   cases=cases, guide=pin(guide), bam=bam_pin, original_intake=pin(intake / 'COMPLETED.json'),
                                   collector=pin(Path(__file__)), implementation_credit=False))
    print(json.dumps(dict(cases=len(cases), successful=sum(c['process']['exit_code'] == 0 for c in cases))))


if __name__ == '__main__':
    if not __debug__:
        raise RuntimeError('Never disable original wildcard boundary checks')
    main()
