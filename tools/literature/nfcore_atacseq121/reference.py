#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Run the unchanged original check_design.py on a complete deterministic control family."""
import argparse
import hashlib
import json
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]


def pin(path):
    raw = path.read_bytes()
    return dict(bytes=len(raw), sha256=hashlib.sha256(raw).hexdigest())


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', type=Path, required=True)
    parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args()
    source = args.source.resolve(strict=True)
    lock = json.loads((ROOT/'src/main/resources/org/jlinalg/omics/nfcore-atacseq121/source-lock.json').read_bytes())
    expected = lock['files']['bin/check_design.py']
    assert pin(source) == {key: expected[key] for key in ['bytes', 'sha256']}
    out = args.out.absolute()
    out.mkdir(parents=True, exist_ok=False)
    header = 'group,replicate,fastq_1,fastq_2\n'
    cases = []
    for groups in [1, 2, 3]:
        for reps in [1, 2, 3]:
            for paired in [False, True]:
                rows = []
                for group in reversed(['G_'+str(i) for i in range(groups)]):
                    for rep in reversed(range(1, reps+1)):
                        for technical in range(1, 3):
                            rows.append(f'{group},{rep},/{group}-{rep}-{technical}.fq.gz,'+(f'/{group}-{rep}-{technical}-R2.fastq.gz' if paired else ''))
                cases.append((f'g{groups}-r{reps}-p{int(paired)}', header+'\n'.join(rows)+'\n', True))
    cases += [
        ('python-unicode-order', header+'\U00010000,1,/s.fq.gz,/t.fq.gz\n\ue000,1,/u.fq.gz,/v.fq.gz\nβ,1,/w.fq.gz,/x.fq.gz\n', True),
        ('source-strip-leading-zero-crlf', header.replace('\n', '\r\n')+'\u00a0G\u00a0,001, /a.fq.gz , /b.fq.gz \r\nG,2,/c.fq.gz,/d.fq.gz\r\n', True),
        ('source-empty-cell-collapse', header+'G,,1,/a.fq.gz,/b.fq.gz\n', True),
        ('source-single-trailing-empty', header+'G,1,/a.fq.gz,,,\n', True),
        ('unbalanced', header+'A,1,/a.fq.gz,\nB,1,/b.fq.gz,\nB,2,/c.fq.gz,\n', True),
        ('noncontiguous', header+'G,1,/a.fq.gz,\nG,3,/b.fq.gz,\n', False),
        ('zero-replicate', header+'G,0,/a.fq.gz,\n', False),
        ('negative-replicate', header+'G,-1,/a.fq.gz,\n', False),
        ('bad-extension', header+'G,1,/a.fastq,\n', False),
        ('mixed', header+'G,1,/a.fq.gz,\nG,2,/b.fq.gz,/c.fq.gz\n', False),
        ('group-space', header+'A B,1,/a.fq.gz,\n', False),
        ('blank-line', header+'G,1,/a.fq.gz,\n\n', False),
        ('empty', header, False),
        ('BOM-header', '\ufeff'+header+'G,1,/a.fq.gz,\n', False),
        ('comment-line', header+'# comment\nG,1,/a.fq.gz,\n', False),
    ]
    results = []
    for label, design, valid in cases:
        folder = out/label
        folder.mkdir()
        (folder/'design.csv').write_text(design, encoding='utf-8', newline='')
        command = [sys.executable, '-X', 'utf8', '-B', str(source), str(folder/'design.csv'), str(folder/'checked.csv')]
        process = subprocess.run(command, stdin=subprocess.DEVNULL, capture_output=True, timeout=30)
        (folder/'stdout').write_bytes(process.stdout)
        (folder/'stderr').write_bytes(process.stderr)
        assert (process.returncode == 0) == valid, (label, process.stderr)
        results.append(dict(id=label, design=design, valid=valid, original_exit=process.returncode,
                            checked=(folder/'checked.csv').read_text(encoding='utf-8') if valid else None,
                            stdout=process.stdout.decode('utf-8'), stderr=process.stderr.decode('utf-8')))
    assert pin(source) == {key: expected[key] for key in ['bytes', 'sha256']}
    fixture = dict(source_commit=lock['commit'], original_checker=pin(source), cases=results)
    (out/'original-designs.json').write_text(json.dumps(fixture, ensure_ascii=False, indent=2)+'\n', encoding='utf-8', newline='\n')
    (out/'COMPLETED.json').write_text(json.dumps(dict(status='PASS_UNCHANGED_ORIGINAL_ATAC_DESIGN_CONTROLS_ONLY',
        cases=len(results), valid=sum(row['valid'] for row in results), fixture=pin(out/'original-designs.json'), original=pin(source)), indent=2)+'\n')
    print('PASS', len(results), 'original design cases')


if __name__ == '__main__':
    if not __debug__:
        raise RuntimeError('Keep reference assertions enabled')
    main()
