# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Run unchanged original MTAG1.0.8 on declared synthetic summary statistics; no project workflow credit."""
import argparse
import csv
import gzip
import json
import math
import os
import random
import subprocess
from pathlib import Path
from setup_deseq2_reference import ROOT, pin, save
from setup_mtag_reference import BASE, COMMIT, linux


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, required=True)
    parser.add_argument('--setup', type=Path, required=True)
    parser.add_argument('--snps', type=int, default=20000)
    args = parser.parse_args()
    out = args.out.resolve()
    assert out.is_relative_to(BASE) and not out.exists() and 1000 <= args.snps <= 100000
    out.mkdir(parents=True)
    (out / 'probe-at-attempt.py').write_bytes(Path(__file__).read_bytes())
    state = dict(status='FAIL2_ORIGINAL_MTAG_WORKFLOW_PROBE', new_catalog_credit=False, synthetic_data=True,
                 project_adapter_validated=False, whole_goal_complete=False)
    try:
        setup = json.loads((args.setup / 'COMPLETED.json').read_bytes())
        assert setup['status'] == 'PASS_ORIGINAL_MTAG108_REFERENCE_PREPARED_ONLY' and setup['source_commit'] == COMMIT
        source = BASE / 'source-intake-1/source'
        for name, wanted in setup['source_inventory'].items():
            assert pin(source / name) == wanted
        prefix = ['wsl.exe', '-d', setup['distribution'], '--']
        check = subprocess.run(prefix + ['/usr/bin/test', '-x', setup['python']], stdin=subprocess.DEVNULL)
        if check.returncode:
            state['status'] = 'SKIP3_ORIGINAL_MTAG_REFERENCE_UNAVAILABLE'
            raise RuntimeError('Recorded private Python2 executable is unavailable; skip dependent analysis and recreate the permitted runtime')
        inputs = out / 'inputs'
        inputs.mkdir()
        rows = []
        for chromosome in range(1, 23):
            path = source / 'ld_ref_panel/eur_w_ld_chr' / (str(chromosome) + '.l2.ldscore.gz')
            with gzip.open(path, 'rt') as stream:
                for row in csv.DictReader(stream, delimiter='\t'):
                    rows.append(row)
                    if len(rows) == args.snps:
                        break
            if len(rows) == args.snps:
                break
        assert len(rows) == args.snps and len({r['SNP'] for r in rows}) == len(rows)
        rng = random.Random(20261110)
        traits = [[], [], []]
        for row in rows:
            shared_error, shared_genetic = rng.gauss(0, 1), rng.gauss(0, 1)
            ld = float(row['L2'])
            for t in range(3):
                n = [100000, 120000, 90000][t]
                z = .3 * shared_error + math.sqrt(.91) * rng.gauss(0, 1) + math.sqrt(.005 * ld * n / 100000) * (.6 * shared_genetic + .8 * rng.gauss(0, 1))
                freq = .1 + .3 * rng.random()
                traits[t].append([row['SNP'], row['CHR'], row['BP'], 'A', 'C', z, n, freq, math.erfc(abs(z) / math.sqrt(2))])
        paths = []
        for t, values in enumerate(traits):
            path = inputs / ('trait%d.tsv' % (t + 1))
            with path.open('x', encoding='utf8', newline='') as stream:
                writer = csv.writer(stream, delimiter='\t', lineterminator='\n')
                writer.writerow(['snpid', 'chr', 'bpos', 'a1', 'a2', 'z', 'n', 'freq', 'p'])
                writer.writerows(values)
            paths.append(path)
        save(out / 'input-manifest.json', dict(seed=20261110, synthetic=True, rows=args.snps, traits=3, files={p.name: pin(p) for p in paths},
            reference_snp_ids_only_not_real_gwas=True, maf_min=.01, n_min=1, median_z_cutoff=.1, input_effect_allele='A', input_other_allele='C'))
        raw = out / 'original'
        raw.mkdir()
        command = prefix + ['/usr/bin/env', '-i', 'PATH=/usr/bin:/bin', 'LC_ALL=C', 'OPENBLAS_NUM_THREADS=1', 'OMP_NUM_THREADS=1',
            'MKL_NUM_THREADS=1', 'PYTHONHASHSEED=0', 'PYTHONDONTWRITEBYTECODE=1', setup['python'], '-B', linux(source / 'mtag.py'),
            '--sumstats', ','.join(map(linux, paths)), '--out', linux(raw / 'mtag'), '--n_min', '1', '--maf_min', '.01',
            '--median_z_cutoff', '.1', '--fdr', '--intervals', '2', '--cores', '1', '--verbose', '--stream_stdout']
        save(out / 'original.command.json', dict(command=command))
        with (out / 'original.stdout').open('xb') as stdout, (out / 'original.stderr').open('xb') as stderr:
            process = subprocess.run(command, cwd=ROOT, env=dict(os.environ), stdin=subprocess.DEVNULL, stdout=stdout, stderr=stderr, timeout=900)
        save(out / 'original.process.json', dict(exit_code=process.returncode))
        state['original_files'] = {p.relative_to(raw).as_posix(): pin(p) for p in raw.rglob('*') if p.is_file()}
        assert process.returncode == 0, 'Original MTAG failed; inspect full retained stdout/stderr/log and partial results; no result admitted'
        state.update(status='PASS_ORIGINAL_MTAG_WORKFLOW_SYNTHETIC_PROBE_ONLY', source_commit=COMMIT, input_rows=args.snps, traits=3,
                     original_full_ldsc_and_fdr_executed=True)
        print(state['status'])
    except Exception as error:
        state['error'] = repr(error)
        print(state['status'] + ': ' + repr(error))
    save(out / 'COMPLETED.json', state)
    (out / 'WARNINGS.md').write_text('# ' + state['status'] + '\n\n' + state.get('error', 'Synthetic original-runtime probe only; no project result admission.')
        + '\n\nREQUIRED ACTION: preserve original inputs, logs and partial files. Skip dependent analysis when the permitted exact runtime is unavailable. Resolve named failures and retry NEW paths without repairing source outputs or changing scientific choices to hide errors. Full typed adapter and saved verification are still required.\n', encoding='utf8', newline='\n')
    return 0 if state['status'].startswith('PASS_') else 3 if state['status'].startswith('SKIP3_') else 2


if __name__ == '__main__':
    if not __debug__:
        raise RuntimeError('Never disable source/input/outcome checks')
    raise SystemExit(main())
