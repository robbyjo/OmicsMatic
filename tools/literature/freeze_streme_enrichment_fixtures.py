# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Freeze complete original STREME XML and independent math.comb/Fraction right-tail oracles."""
from fractions import Fraction
import hashlib
import io
import json
from math import comb, log10
from pathlib import Path
import random
import zipfile

ROOT = Path(__file__).resolve().parents[2]
BASE = ROOT / 'build/literature-review/meme-runtime'
DEST = ROOT / 'src/test/resources/org/jlinalg/sequence/streme-enrichment'


def byte_pin(data):
    return dict(bytes=len(data), sha256=hashlib.sha256(data).hexdigest())


def main():
    if DEST.exists():
        raise ValueError('Original fixture freeze is single-use; preserve all existing evidence')
    files, sources = {}, {}
    for version in ['5.5.4', '5.5.9']:
        base = BASE / ('discovery-original-' + version + '-1')
        record = json.loads((base / 'COMPLETED.json').read_bytes())
        assert record['unchanged_inputs_and_runtime'] and record['successful_processes'] == 12
        for job in ['streme-supplied', 'streme-shuffled', 'streme-no-holdout', 'streme-no-motifs',
                    'streme-binomial', 'streme-central-distance']:
            path = base / 'captured' / job / 'result/streme.xml'
            data = path.read_bytes()
            files[version + '/' + job + '.xml'] = data
            sources[path.relative_to(ROOT).as_posix()] = byte_pin(data)
    population = [(p, n, a, b) for p in range(9) for n in range(9)
                  for a in range(p + 1) for b in range(n + 1)]
    rng = random.Random(7011)
    for _ in range(150):
        p, n = rng.randrange(1, 501), rng.randrange(901)
        population.append((p, n, rng.randrange(p + 1), rng.randrange(n + 1)))
    population += [(4096, 4096, 4096, 0), (4096, 4096, 0, 4096), (1000, 1000, 1000, 0),
                   (1, 4096, 1, 0), (4096, 1, 1, 0), (1000, 0, 1000, 0), (0, 1000, 0, 1000)]
    rows = ['positives\tnegatives\tpositive_pass\tnegative_pass\tnumerator\tdenominator\tprobability\tlog10_probability']
    for p, n, a, b in population:
        exact = sum((Fraction(comb(p, k) * comb(n, a + b - k), comb(p + n, a + b))
                     for k in range(a, min(p, a + b) + 1)), Fraction(0))
        assert 0 < exact <= 1
        rows.append('\t'.join(map(str, [p, n, a, b, exact.numerator, exact.denominator, float(exact),
                                       log10(exact.numerator) - log10(exact.denominator)])))
    files['independent-right-tails.tsv'] = ('\n'.join(rows) + '\n').encode()
    files['ORACLE-README.txt'] = (
        'Independent Python math.comb and fractions.Fraction enumeration, not the Java recurrence.\n'
        'Complete small-table population grid plus seeded uneven larger tables, degenerate margins,\n'
        'near-one tails and binary64 underflow. These are mathematical tables, not biological data.\n'
        'Original XML retains complete source output, paths and printed precision. The historically named\n'
        'streme-no-motifs case actually reports ONE motif above the stringent threshold; it is not an empty-result fixture.\n'
        'The binomial and central-distance files are unsupported Fisher controls, not Fisher fits.\n').encode()
    DEST.mkdir(parents=True)
    with zipfile.ZipFile(DEST / 'original-and-independent.zip', 'x', zipfile.ZIP_DEFLATED) as archive:
        for name, data in sorted(files.items()):
            info = zipfile.ZipInfo(name, (2026, 11, 6, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o100644 << 16
            archive.writestr(info, data)
    manifest = dict(schema_version=1, independent_tables=len(population), original_XML_documents=12,
                    files={name: byte_pin(data) for name, data in sorted(files.items())},
                    original_source_pins=sources, freezer=byte_pin(Path(__file__).read_bytes()),
                    archive=byte_pin((DEST / 'original-and-independent.zip').read_bytes()),
                    runtime_admission=False, implementation_credit=False)
    (DEST / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n', encoding='utf-8', newline='\n')
    print(json.dumps({k: manifest[k] for k in ['independent_tables', 'original_XML_documents', 'archive']}))


if __name__ == '__main__':
    if not __debug__:
        raise RuntimeError('Never disable fixture source checks')
    main()
