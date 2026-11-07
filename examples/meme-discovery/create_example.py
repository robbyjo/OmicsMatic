# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Prepare a small synthetic MEME discovery request in Linux; install and execute nothing."""
import argparse
import hashlib
import json
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[2]
PROFILES = ['MEME_4_9_1_BASE', 'MEME_4_9_1_PATCH2', 'MEME_5_5_4', 'MEME_5_5_9']


def save(path, value):
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf8', newline='\n')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--profile', choices=PROFILES, default='MEME_5_5_9')
    parser.add_argument('--build-evidence', type=Path, required=True)
    parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args()
    if sys.platform != 'linux':
        print('SKIP3: prepare execution paths with permitted Linux Python. Windows supports VERIFY of a completed bundle. No analysis was run.', file=sys.stderr)
        raise SystemExit(3)
    evidence, out = args.build_evidence.resolve(), args.out.resolve()
    if out.exists(): parser.error('Require a NEW example directory; preserve existing attempts')
    proof = json.loads((evidence / 'COMPLETED.json').read_bytes()); legacy = args.profile.startswith('MEME_4_')
    expected_version = '4.9.1' if legacy else '5.5.4' if args.profile.endswith('_4') else '5.5.9'
    if proof['version'] != expected_version: parser.error('The completed source-build edition differs from --profile')
    if legacy and proof['edition'] != ('4.9.1-patch2' if args.profile.endswith('PATCH2') else '4.9.1-base'):
        parser.error('Original 4.9.1 base and patch2 build evidence cannot alias')
    resource = ROOT / 'src/main/resources/org/jlinalg/omics/meme-discovery'
    inputs = [('health-primary.fa', 'primary.fa', 6470, '20167f245b832891a9f5c1392f821a8b852010c18382727b11a651078ef353c5'),
        ('health-background.bg', 'background.bg', 28, '9baf37e722bec2132b06aac3c3c353e628d9d5cdb631c34804cc186c4e523805')]
    raw = {}
    for original, name, length, sha in inputs:
        value = (resource / original).read_bytes()
        if len(value) != length or hashlib.sha256(value).hexdigest() != sha:
            parser.error('The synthetic input resource changed; preserve it and resolve the named source mismatch')
        raw[name] = value
    out.mkdir(parents=True)
    for name, value in raw.items(): (out / name).write_bytes(value)
    save(out / 'recipe.json', dict(schema_version=1, profile=args.profile, primary=str(out / 'primary.fa'), background=str(out / 'background.bg'),
        occurrence='ZOOPS', both_strands=True, motifs=2, minimum_width=10, maximum_width=10,
        seed=None if legacy else 17, search_size=None if legacy else 0, dirichlet_beta=0.01,
        maximum_iterations=50, convergence_distance=0.00001, maximum_motif_evalue=None,
        minimum_sites=None, maximum_sites=None, limits=dict(wall_seconds=120, cpu_seconds=120)))
    save(out / 'discover.json', dict(schema_version=1, operation='DISCOVER', recipe=str(out / 'recipe.json'),
        runtime_home=proof['prefix'], build_evidence=str(evidence), out=str(out / 'result')))
    save(out / 'verify.json', dict(schema_version=1, operation='VERIFY', bundle=str(out / 'result')))
    save(out / 'PROVENANCE.json', dict(scope='Synthetic 60-sequence canonical-DNA planted-motif control. Reuses the small prepared health population; not an independent biological dataset or paper reconstruction.',
        profile=args.profile, original_build_evidence=str(evidence), input_pins={name: dict(bytes=len(value), sha256=hashlib.sha256(value).hexdigest()) for name,value in raw.items()},
        analysis_run=False, installation_performed=False))
    print('Prepared synthetic requests at ' + str(out))
    print('No analysis was run. Execute discover.json using the validated development JAR, read WARNINGS.md, then require VERIFY before using results.')


if __name__ == '__main__': main()
