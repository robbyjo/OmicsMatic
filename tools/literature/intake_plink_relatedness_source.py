# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Pin official original PLINK relatedness sources and documentation without executing software."""
import argparse, datetime, json, urllib.request
from pathlib import Path
from setup_deseq2_reference import ROOT, pin, save
BASE = ROOT/'build/literature-review/plink-relatedness-runtime'


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--out', required=True, type=Path)
    p.add_argument('--commit', default='master')
    a = p.parse_args()
    out = a.out.resolve()
    assert not out.exists() and out.is_relative_to(BASE)
    out.mkdir(parents=True)
    records = []

    def fetch(url, name, maximum=8000000):
        req = urllib.request.Request(url, headers={'User-Agent': 'OmicsMatic-source-review', 'Accept': 'application/vnd.github+json' if 'api.github.com' in url else '*/*'})
        with urllib.request.urlopen(req, timeout=90) as response:
            raw = response.read(maximum+1)
            assert len(raw) <= maximum
            final = response.geturl()
        (out/name).write_bytes(raw)
        record = dict(url=url, final_url=final, path=name, fetched_utc=datetime.datetime.now(datetime.timezone.utc).isoformat(), **pin(out/name))
        records.append(record)
        save(out/(name+'.receipt.json'), record)
        return raw

    api = json.loads(fetch('https://api.github.com/repos/chrchang/plink-ng/commits/'+a.commit, 'commit.json'))
    commit = api['sha']
    assert len(commit) == 40 and all(c in '0123456789abcdef' for c in commit)
    for name, relative in [('plink2_matrix_calc.cc', '2.0/plink2_matrix_calc.cc'),
        ('plink2_matrix_calc.h', '2.0/plink2_matrix_calc.h'), ('plink_calculation.c', '1.9/plink_calc.c'),
        ('LICENSE-plink1', '1.9/LICENSE'), ('COPYING-plink2', '2.0/COPYING')]:
        fetch(f'https://raw.githubusercontent.com/chrchang/plink-ng/{commit}/{relative}', name)
    for name, url in [('plink2-distance.html', 'https://www.cog-genomics.org/plink/2.0/distance'),
        ('plink2-formats.html', 'https://www.cog-genomics.org/plink/2.0/formats'),
        ('plink1-genome.html', 'https://www.cog-genomics.org/plink/1.9/ibd'),
        ('plink1-formats.html', 'https://www.cog-genomics.org/plink/1.9/formats')]:
        fetch(url, name)
    save(out/'COMPLETED.json', dict(status='PASS_OFFICIAL_PLINK_RELATEDNESS_SOURCE_INTAKE_ONLY',
        commit=commit, source_records=records, analysis_performed=False, implementation_credit=False,
        scope='Immutable current official source and dated documentation; not a historical paper or earlier binary build identification.', whole_goal_complete=False))
    print('Pinned official relatedness source', commit, '; no software execution')


if __name__ == '__main__':
    if not __debug__:
        raise RuntimeError('Never disable official-source length/identity checks')
    main()
