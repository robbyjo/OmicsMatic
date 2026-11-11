# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Collect unchanged original KING/IBD reports on complete synthetic diploid hard calls."""
import argparse, copy, hashlib, json, random, shutil, subprocess, traceback
from pathlib import Path
from setup_deseq2_reference import ROOT, pin, save
BASE = ROOT/'build/literature-review/plink-relatedness-runtime'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args()
    out = args.out.resolve()
    assert not out.exists() and out.is_relative_to(BASE)
    out.mkdir(parents=True)
    shutil.copy2(Path(__file__), out/'collector-source.py')
    home = ROOT/'build/literature-review/plink-runtime/downloaded-reference-1'
    lock = json.loads((ROOT/'tools/literature/plink-runtime-lock.json').read_bytes())
    shutil.copy2(ROOT/'tools/literature/plink-runtime-lock.json', out/'source-runtime-lock.json')
    binaries = {name: home/row['executable'] for name, row in lock['engines'].items()}
    before = {name: pin(path) for name, path in binaries.items()}
    assert all(before[name]['sha256'] == lock['files'][lock['engines'][name]['executable']] for name in binaries)
    rng = random.Random(20261110)
    samples = [dict(fid='family' if i < 4 else 'other_'+str(i), iid='s'+str(i),
        father='s2' if i == 3 else '0', mother='0', sex=i%3) for i in range(16)]
    samples[14].update(fid='first_family', iid='repeated_iid')
    samples[15].update(fid='second_family', iid='repeated_iid')
    samples[13]['iid'] = 'sample_alpha_\u03b1'
    calls = []
    for variant in range(129):
        p = 0.05+(variant%17)*0.05
        row = [int(rng.random() < p)+int(rng.random() < p) for _ in samples]
        row[1] = row[0]
        row[3] = int(rng.random() < row[2]/2)+int(rng.random() < 0.45)
        row[4], row[5], row[6], row[7] = 0, 2, 1, -1
        row[8] = -1 if variant%3 == 0 else row[8]
        row[9] = -1 if variant%5 == 0 else row[9]
        row[10] = row[10] if variant < 64 else -1
        row[11] = row[11] if variant >= 64 else -1
        if variant == 128:
            row = [-1]*len(samples)
        calls.append(row)
    inputs = out/'inputs'
    inputs.mkdir()
    save(inputs/'SYNTHETIC.json', dict(seed=20261110, samples=samples, calls=calls,
        paper_data=False, biological_validation=False, genotype_coding='0/1/2 allele1 copies;-1 missing',
        intentional='Distinct repeated IIDs, UTF8 ID, nonfounder, exact duplicate, all-homozygote/all-heterozygote/all-missing and disjoint-missing samples; final marker all-missing.'))
    cases = []
    for length in [1, 2, 63, 64, 65, 127, 128, 129]:
        folder = inputs/('variants-'+str(length))
        folder.mkdir()
        prefix = folder/'cohort'
        prefix.with_suffix('.fam').write_text(''.join(f'{s["fid"]}\t{s["iid"]}\t{s["father"]}\t{s["mother"]}\t{s["sex"]}\t-9\n' for s in samples), encoding='utf8')
        prefix.with_suffix('.bim').write_text(''.join(f'{1+(j//64)}\tv{j}\t0\t{100+j*41}\tA\tC\n' for j in range(length)), encoding='utf8')
        raw = bytearray([0x6c, 0x1b, 1])
        for row in calls[:length]:
            packed = bytearray((len(samples)+3)//4)
            for sample, value in enumerate(row):
                packed[sample//4] |= {2:0, -1:1, 1:2, 0:3}[value] << (2*(sample%4))
            raw.extend(packed)
        prefix.with_suffix('.bed').write_bytes(raw)
        cases.append(dict(id=folder.name, variants=length, prefix=prefix.relative_to(out).as_posix()))
    save(inputs/'cases.json', cases)
    records = []
    state = dict(status='FAIL2_ORIGINAL_PLINK_RELATEDNESS_REFERENCE', analysis_admitted=False, implementation_credit=False, whole_goal_complete=False)
    try:
        def run(name, engine, prefix, options, expected_exit=0):
            folder = out/name
            folder.mkdir(parents=True)
            command = [str(binaries[engine]), '--bfile', str(prefix), *options,
                '--threads', '1', '--memory', '640' if engine == 'plink2' else '128', '--out', str(folder/'original')]
            with (folder/'stdout').open('xb') as stdout, (folder/'stderr').open('xb') as stderr:
                process = subprocess.run(command, stdin=subprocess.DEVNULL, stdout=stdout, stderr=stderr, timeout=180)
            record = dict(id=name, engine=engine, command=command, exit_code=process.returncode, expected_exit=expected_exit)
            save(folder/'process.json', record)
            records.append(record)
            print(name, process.returncode, flush=True)
            return process.returncode

        columns = 'cols=fid,id,nsnp,hethet,ibs0,ibs1,kinship'
        for case in cases:
            prefix = out/case['prefix']
            assert run(case['id']+'/counts', 'plink2', prefix, ['--make-king-table', 'counts', columns]) == 0
            assert run(case['id']+'/binary', 'plink2', prefix, ['--make-king', 'triangle', 'bin']) == 0
        prefix = out/cases[-1]['prefix']
        assert run('proportions', 'plink2', prefix, ['--make-king-table', columns]) == 0
        for threshold in ['0', '0.177', '0.5']:
            assert run('table-filter-'+threshold, 'plink2', prefix, ['--make-king-table', 'counts', columns, '--king-table-filter', threshold]) == 0
        for threshold in ['0.177', '0.5']:
            expected = 8 if threshold == '0.5' else 0
            assert run('king-cutoff-'+threshold, 'plink2', prefix, ['--king-cutoff', threshold], expected) == expected
        for mode in ['bounded', 'unbounded']:
            assert run('plink1-genome-'+mode, 'plink1', prefix, ['--genome', 'full']+(['unbounded'] if mode == 'unbounded' else [])) == 0
        # Preserve the initial source-rejected fixture as a separate negative control.
        split = BASE/'reference-controls-1/inputs/variants-63/cohort'
        assert run('rejected-split-chromosome', 'plink2', split, ['--make-king-table', 'counts', columns], 6) == 6
        assert all(pin(binaries[name]) == value for name, value in before.items())
        state.update(status='PASS_COMPLETE_ORIGINAL_PLINK_RELATEDNESS_REFERENCE_CONTROLS', records=records,
            binary_pins=before, input_pins={p.relative_to(out).as_posix(): pin(p) for p in inputs.rglob('*') if p.is_file()},
            source_profile=lock['profile'], original_processes=len(records), source_options_unchanged=True,
            scope='Original-only synthetic relatedness reports; no Java implementation, biological validation or historical paper reproduction.')
    except Exception as error:
        state.update(error=repr(error), records=records)
        (out/'error.traceback').write_text(traceback.format_exc(), encoding='utf8')
    save(out/'COMPLETED.json', state)
    print(state['status'], state.get('error', '')[:500], flush=True)
    return 0 if state['status'].startswith('PASS_') else 2


if __name__ == '__main__':
    if not __debug__:
        raise RuntimeError('Never disable original program/population preservation checks')
    raise SystemExit(main())
