#!/usr/bin/env python3
"""Compare packaged original QC with independent integer-call and exact HWE calculations."""
from pathlib import Path
import argparse
import csv
import datetime as dt
from decimal import Decimal
from fractions import Fraction
import gzip
import hashlib
import json
import math
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(Path(__file__).resolve().parent))
import install_plink


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def table(path):
    with Path(path).open(encoding='utf-8', newline='') as stream:
        reader = csv.DictReader(stream, delimiter='\t')
        result = list(reader)
        assert all(None not in row and all(v is not None for v in row.values()) for row in result), path
        return result


def hwe_counts(calls):
    counts = [calls.count(g) for g in [0, 1, 2]]
    n = sum(counts)
    if not n:
        return dict(n=0, exact=None, midp=None, degenerate=False, excess=False)
    m = 2 * min(counts[0], counts[2]) + counts[1]
    # Integer conditional weights from the multinomial formula. No recurrence,
    # floating-point threshold, project HWE code, or original implementation is used.
    weights = {}
    for het in range(m % 2, m + 1, 2):
        rare = (m - het) // 2
        common = n - rare - het
        if common >= 0:
            weights[het] = math.comb(n, het) * math.comb(n - het, rare) * (2 ** het)
    observed = weights[counts[1]]
    total = sum(weights.values())
    tail = sum(value for value in weights.values() if value <= observed)
    tied = sum(value for value in weights.values() if value == observed)
    # Both upstream engines use half the entire equal-probability boundary.
    # E.g. n=63, genotypes6/29/28 has two tied configurations (hets27 and29).
    return dict(n=n, exact=Fraction(tail, total), midp=Fraction(2 * tail - tied, 2 * total),
                degenerate=m < 2, excess=2 * n * counts[1] > m * (2 * n - m))


def fixture(directory, order=False, no_controls=False, final_failure=False, deficiency=False):
    directory.mkdir()
    resource = ROOT / 'src/test/resources/org/jlinalg/genetics/plink-associations'
    with gzip.open(resource / 'input-design.json.gz', 'rt', encoding='utf-8') as stream:
        frozen = json.load(stream)
    g = [row[:] for row in frozen['a1_genotypes']]
    assert len(g) == 96 and all(len(row) == 128 for row in g)
    variants = []
    with gzip.open(resource / 'variants.tsv.gz', 'rt', encoding='utf-8') as stream:
        meta = list(csv.DictReader(stream, delimiter='\t'))
    for v in range(96):
        alleles = list(meta[v].values())
        variants.append(['X' if v == 95 else '1', alleles[0], '0', str((v + 1) * 1000),
                         '0' if v == 3 else alleles[1], alleles[2]])
        for i in range(128):
            if v == 0:
                g[v][i] = 1 if i % 2 == 0 else 0 if i % 4 == 1 else 2
            if v == 1:
                g[v][i] = (0 if i % 8 == 0 else 2 if i % 8 == 2 else 1) if i % 2 == 0 else 1
            if v == 2:
                g[v][i] = -1
            if v == 3:
                g[v][i] = 0
            if v == 4:
                g[v][i] = int(i == 8)
            if order and 5 <= v <= 10:
                g[v][i] = 0
            if final_failure and v >= 15:
                g[v][i] = 0
            if no_controls and v == 12 and i % 2 == 0:
                g[v][i] = -1
            if deficiency and v == 94:
                g[v][i] = 2 if i % 4 < 2 else 0
            if i < 2 and v < 45:
                g[v][i] = -1
            if order and i == 4 and v <= 12:
                g[v][i] = -1
            if final_failure and i == 6 and v in [5, 6]:
                g[v][i] = -1
    fam = [[str(pair[0]), str(pair[1]), '0', '0', str(i % 2 + 1), '2']
           for i, pair in enumerate(frozen['samples'])]
    for i, pair in enumerate([['A_B', 'C'], ['A', 'B_C'], ['家族', '成员']]):
        fam[i][:2] = pair
    fam[2][4] = '0'
    fam[10][2:4] = ['S008', 'S009']
    fam[12][2] = 'UNSAMPLED_FATHER'
    prefix = directory / 'known'
    for suffix, rows in [('fam', fam), ('bim', variants)]:
        Path(str(prefix) + '.' + suffix).write_text(''.join(' '.join(row) + '\n' for row in rows), encoding='utf-8')
    bed = bytearray([0x6c, 0x1b, 1])
    for row in g:
        for start in range(0, 128, 4):
            bed.append(sum((1 if value < 0 else {0: 3, 1: 2, 2: 0}[value]) << (2 * j)
                           for j, value in enumerate(row[start:start + 4])))
    Path(str(prefix) + '.bed').write_bytes(bed)
    cohort = directory / 'qc-samples.tsv'
    with cohort.open('w', encoding='utf-8', newline='') as stream:
        writer = csv.writer(stream, delimiter='\t', lineterminator='\n')
        writer.writerow(['fid', 'iid', 'include', 'group'])
        writer.writerows([*row[:2], 'no' if i == 127 else 'yes', 'control' if i % 2 == 0 else 'case']
                         for i, row in enumerate(fam))
    design = dict(prefix=str(prefix), samples=str(cohort), genotypes=g, fam=fam, variants=variants,
                  source_sha256={p.name: sha(p) for p in directory.iterdir() if p.is_file()},
                  frozen_design_sha256=sha(resource / 'input-design.json.gz'),
                  scope='Synthetic diploid autosomal QC contrasts; chrX explicitly excluded before missingness. '
                        'No patient data or genetic population realism claim.')
    (directory / 'design.json').write_text(json.dumps(design, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    return design


def policy(home, design, engine, out):
    values = dict(task='qc', engine=engine, home=str(home), bfile=design['prefix'], samples=design['samples'],
                  out=str(out), **{'genome-build': 'unspecified', 'chromosome-scope': 'autosomes',
                  'qc-order': 'samples-then-variants', 'unresolved-alleles': 'exclude',
                  'maximum-sample-missing': '.1', 'maximum-variant-missing': '.2', 'minimum-maf': '.01',
                  'minimum-mac': '2', 'frequency-population': 'all', 'hwe-mode': 'exact', 'hwe-p': '1e-6',
                  'hwe-population': 'controls', 'hwe-founders': 'all', 'hwe-unreportable': 'exclude', 'final-check': 'fail'})
    if engine == 'plink2':
        values.update({'hwe-k': '0', 'hwe-keep-fewhet': 'no'})
    return values


def independent(design, options):
    g, fam = design['genotypes'], design['fam']
    samples, variants = list(range(127)), [v for v in range(95) if v != 3 or options['unresolved-alleles'] != 'exclude']
    sample_at, variant_at, freq_at, hwe_at = {}, {}, {}, {}

    def reference(scope, controls):
        return [i for i in samples if (scope != 'founders' or fam[i][2:4] == ['0', '0'])
                and (not controls or i % 2 == 0)]

    def missing_samples():
        return {i: (sum(g[v][i] < 0 for v in variants), len(variants)) for i in samples}

    def missing_variants():
        return {v: (sum(g[v][i] < 0 for i in samples), len(samples)) for v in variants}

    def frequencies():
        population = reference(options['frequency-population'], False)
        result = {}
        for v in variants:
            observed = [g[v][i] for i in population if g[v][i] >= 0]
            copies, total = sum(observed), 2 * len(observed)
            result[v] = (min(copies, total - copies), total)
        return result

    def hwe():
        population = reference(options['hwe-founders'], options['hwe-population'] == 'controls')
        return {v: hwe_counts([g[v][i] for i in population]) for v in variants}

    def pass_missing(value, key):
        return Fraction(*value) <= Fraction(options[key])

    def pass_frequency(value):
        mac, total = value
        return mac >= int(options['minimum-mac']) and (Fraction(options['minimum-maf']) == 0
            or (total > 0 and Fraction(mac, total) >= Fraction(options['minimum-maf'])))

    def pass_hwe(v, value):
        if value['n'] == 0 or v == 3:
            if options['hwe-unreportable'] == 'error':
                raise ValueError('expected unreportable HWE failure')
            return options['hwe-unreportable'] == 'retain-with-warning'
        threshold = float(options['hwe-p']) * 10 ** (-value['n'] * float(options.get('hwe-k', '0')))
        return (options.get('hwe-keep-fewhet') == 'yes' and not value['excess']) or float(value[options['hwe-mode']]) >= threshold

    def sample_filter():
        nonlocal samples, sample_at
        sample_at = missing_samples()
        samples = [i for i in samples if pass_missing(sample_at[i], 'maximum-sample-missing')]

    def variant_filter():
        nonlocal variants, variant_at, freq_at, hwe_at
        variant_at = missing_variants()
        variants = [v for v in variants if pass_missing(variant_at[v], 'maximum-variant-missing')]
        freq_at = frequencies()
        variants = [v for v in variants if pass_frequency(freq_at[v])]
        if options['hwe-mode'] != 'off':
            hwe_at = hwe()
            variants = [v for v in variants if pass_hwe(v, hwe_at[v])]

    if options['qc-order'] == 'samples-then-variants':
        sample_filter(); variant_filter()
    else:
        variant_filter(); sample_filter()
    sm, vm, freq = missing_samples(), missing_variants(), frequencies()
    hw = hwe() if options['hwe-mode'] != 'off' else {}
    sf = {i for i in samples if not pass_missing(sm[i], 'maximum-sample-missing')}
    vf = {v for v in variants if not pass_missing(vm[v], 'maximum-variant-missing') or not pass_frequency(freq[v])
          or (hw and not pass_hwe(v, hw[v]))}
    return dict(samples=samples, variants=variants, sample_at=sample_at, variant_at=variant_at,
                frequency_at=freq_at, hwe_at=hwe_at, final_sm=sm, final_vm=vm, final_freq=freq, final_hwe=hw,
                sample_failures=sf, variant_failures=vf)


def verify_output(out, design, options, expected):
    sm, vm = table(out / 'sample-decisions.tsv'), table(out / 'variant-decisions.tsv')
    assert len(sm) == 128 and len(vm) == 96
    assert [i for i, row in enumerate(sm) if row['retained'] == 'true'] == expected['samples']
    assert [v for v, row in enumerate(vm) if row['retained'] == 'true'] == expected['variants']
    for i, row in enumerate(sm):
        assert [row['fid'], row['iid']] == design['fam'][i][:2]
        for prefix, values in [('decision', expected['sample_at']), ('final', expected['final_sm'])]:
            assert [row[prefix + '_missing'], row[prefix + '_opportunities']] == (list(map(str, values[i])) if i in values else ['', ''])
        assert (row['final_threshold_failure'] == 'true') == (i in expected['sample_failures'])
    hwe_values = 0
    for v, row in enumerate(vm):
        assert row['variant'] == design['variants'][v][1]
        for prefix, values in [('decision', expected['variant_at']), ('final', expected['final_vm'])]:
            assert [row[prefix + '_missing'], row[prefix + '_opportunities']] == (list(map(str, values[v])) if v in values else ['', ''])
        frequency = expected['final_freq'].get(v, expected['frequency_at'].get(v))
        if frequency is not None:
            mac, total = frequency
            assert int(row['frequency_ref_allele_observations']) == total
            assert int(row['frequency_ref_MAC']) == mac
            if total:
                assert math.isclose(float(row['frequency_ref_MAF']), mac / total, rel_tol=2e-15)
            else:
                assert row['frequency_ref_MAF'] == ''
        hwe = expected['final_hwe'].get(v, expected['hwe_at'].get(v))
        if hwe is not None:
            assert int(row['hwe_ref_called']) == hwe['n']
            if not hwe['n']:
                assert row['hwe_reportability'] == 'NO_CALLS' and Decimal(row['hwe_raw_p']) == 1
            else:
                target = Decimal(hwe[options['hwe-mode']].numerator) / Decimal(hwe[options['hwe-mode']].denominator)
                printed = Decimal(row['hwe_raw_p'])
                # Original HWE output uses4significant figures in PLINK1 and6
                # in PLINK2; omitted trailing zeros do not reduce precision.
                digits = 4 if options['engine'] == 'plink1' else 6
                tolerance = (Decimal(10) ** (min(printed.adjusted(), target.adjusted()) - digits + 1)) / 2
                assert abs(printed - target) <= tolerance + abs(target) * Decimal('2e-12'), (v, printed, target, tolerance)
                hwe_values += 1
        else:
            assert row['hwe_reportability'] == 'NOT_EVALUATED'
    final_fam = [line.split() for line in (out / 'genotypes.fam').read_text(encoding='utf-8').splitlines()]
    final_bim = [line.split() for line in (out / 'genotypes.bim').read_text(encoding='utf-8').splitlines()]
    assert final_fam == [design['fam'][i] for i in expected['samples']]
    assert len(final_bim) == len(expected['variants'])
    raw = (out / 'genotypes.bed').read_bytes(); n = len(final_fam); stride = (n + 3) // 4
    assert raw[:3] == b'\x6c\x1b\x01' and len(raw) == 3 + stride * len(final_bim)
    cells = 0
    for ordinal, v in enumerate(expected['variants']):
        original, result = design['variants'][v], final_bim[ordinal]
        assert result[:4] == original[:4]
        original_alleles = ['0' if a in ['0', '.'] else a for a in original[4:]]
        result_alleles = ['0' if a in ['0', '.'] else a for a in result[4:]]
        same, flip = result_alleles == original_alleles, result_alleles == original_alleles[::-1]
        assert same or flip
        for j, i in enumerate(expected['samples']):
            code = (raw[3 + ordinal * stride + j // 4] >> (2 * (j % 4))) & 3
            actual = {0: 2, 2: 1, 3: 0, 1: -1}[code]
            source = design['genotypes'][v][i]
            if flip and not same and source >= 0:
                source = 2 - source
            assert actual == source, (v, i, source, actual)
            cells += 1
        if n % 4:
            assert raw[3 + (ordinal + 1) * stride - 1] >> (2 * (n % 4)) == 0
    return dict(retained_samples=n, retained_variants=len(final_bim), genotype_cells=cells,
                independent_reported_HWE_values=hwe_values, independent_sample_decisions=len(sm),
                independent_variant_decisions=len(vm))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jar', type=Path, required=True)
    parser.add_argument('--home', type=Path, required=True)
    parser.add_argument('--work', type=Path, required=True)
    parser.add_argument('--java', action='append', required=True)
    args = parser.parse_args()
    jar, home, work = args.jar.resolve(), args.home.resolve(), args.work.resolve()
    if work.is_relative_to(home) or home.is_relative_to(work):
        parser.error('--work must be outside the admitted runtime home')
    work.mkdir(parents=True, exist_ok=False)
    record = dict(status='running', jar_sha256=sha(jar), producer_sha256=sha(__file__),
                  started_utc=dt.datetime.now(dt.timezone.utc).isoformat(), steps=[], workflows=[], negative_controls=0,
                  scope='Synthetic typed original autosomal hard-call QC; independent exact integer HWE enumeration, '
                        'missingness/MAF/MAC decisions and every retained call. No full-project gate or whole-PLINK claim.')
    shutil.copy2(__file__, work / 'producer-snapshot.py')

    def save():
        (work / 'verification.json').write_text(json.dumps(record, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')

    def run(java, options, label, code=0):
        recipe = work / (label + '-options.json')
        recipe.write_text(json.dumps(options, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
        command = [java, '-jar', str(jar), 'plink-tools', '--options-file', str(recipe)]
        result = subprocess.run(command, cwd=ROOT, capture_output=True, timeout=600)
        (work / (label + '.stdout')).write_bytes(result.stdout)
        (work / (label + '.stderr')).write_bytes(result.stderr)
        record['steps'].append(dict(command=command, code=result.returncode, expected=code, recipe_sha256=sha(recipe)))
        save()
        assert result.returncode == code, (label, result.returncode, result.stderr.decode(errors='replace'))
        out = Path(options['out'])
        if code:
            assert not (out / 'genotypes.bed').exists() and (out / 'WARNINGS.md').is_file()
            record['negative_controls'] += 1
        return out

    save()
    try:
        assert install_plink.available(home), 'The original private runtime is unavailable or changed'
        designs = dict(base=fixture(work / '输入-base'), order=fixture(work / '输入-order', order=True),
                       no_calls=fixture(work / '输入-zero-controls', no_controls=True),
                       final=fixture(work / '输入-final', final_failure=True),
                       deficiency=fixture(work / '输入-fewhet', deficiency=True))
        scenarios = [
            ('base', 'base', {}), ('all', 'base', {'hwe-population': 'all'}),
            ('founders', 'base', {'frequency-population': 'founders', 'hwe-founders': 'founders'}),
            ('midp', 'base', {'hwe-mode': 'midp'}),
            ('disabled-thresholds', 'base', {'maximum-sample-missing': '1', 'maximum-variant-missing': '1',
                                           'minimum-maf': '0', 'minimum-mac': '0', 'hwe-mode': 'off',
                                           'unresolved-alleles': 'retain-with-warning'}),
            ('unresolved-retain', 'base', {'unresolved-alleles': 'retain-with-warning', 'minimum-maf': '0',
                                         'minimum-mac': '0', 'hwe-unreportable': 'retain-with-warning'}),
            ('order-sv', 'order', {}), ('order-vs', 'order', {'qc-order': 'variants-then-samples'}),
            ('no-call-exclude', 'no_calls', {'maximum-variant-missing': '.6'}),
            ('no-call-retain', 'no_calls', {'maximum-variant-missing': '.6', 'hwe-unreportable': 'retain-with-warning'}),
            ('final-warn', 'final', {'hwe-mode': 'off', 'final-check': 'warn'}),
        ]
        for j, java in enumerate(args.java):
            for engine in ['plink1', 'plink2']:
                chosen = scenarios + ([('fewhet-drop', 'deficiency', {'hwe-population': 'all'}),
                                       ('fewhet-keep', 'deficiency', {'hwe-population': 'all', 'hwe-keep-fewhet': 'yes'}),
                                       ('scaled-k', 'deficiency', {'hwe-population': 'all', 'hwe-k': '.4'})]
                                      if engine == 'plink2' else [])
                for name, which, changes in chosen:
                    label = f'jvm{j}-{engine}-{name}'
                    options = policy(home, designs[which], engine, work / ('输出-' + label))
                    options.update(changes)
                    if options['hwe-mode'] == 'off':
                        options = {k: v for k, v in options.items() if not k.startswith('hwe-') or k == 'hwe-mode'}
                    prediction = independent(designs[which], options)
                    out = run(java, options, label)
                    result = verify_output(out, designs[which], options, prediction)
                    record['workflows'].append(dict(label=label, engine=engine, java=java, **result));save()
                # Dependency failure happens before nonexistent scientific input is opened.
                for name, changes, which, code in [
                    ('missing-home', {'home': str(work / 'not-installed'), 'bfile': 'not-read'}, 'base', 3),
                    ('no-call-error', {'maximum-variant-missing': '.6', 'hwe-unreportable': 'error'}, 'no_calls', 2),
                    ('final-fail', {'hwe-mode': 'off'}, 'final', 2),
                ]:
                    label = f'jvm{j}-{engine}-{name}'
                    options = policy(home, designs[which], engine, work / ('输出-' + label));options.update(changes)
                    if options['hwe-mode'] == 'off':
                        options = {k: v for k, v in options.items() if not k.startswith('hwe-') or k == 'hwe-mode'}
                    run(java, options, label, code)
            for design in designs.values():
                for name, digest in design['source_sha256'].items():
                    assert sha(Path(design['prefix']).parent / name) == digest
            assert sha(jar) == record['jar_sha256'] and sha(__file__) == record['producer_sha256']
        assert install_plink.available(home), 'The original private runtime changed during verification'
        record['status'] = 'passed';save()
        print(json.dumps(dict(status='passed', workflows=len(record['workflows']), negative_controls=record['negative_controls'],
                              genotype_cells=sum(w['genotype_cells'] for w in record['workflows']),
                              independent_HWE_values=sum(w['independent_reported_HWE_values'] for w in record['workflows']))))
        return 0
    except Exception as failure:
        record.update(status='failed-retained', error=repr(failure));save()
        (work / 'WARNINGS.md').write_text('# PACKAGED QC VALIDATION FAILED\n\n' + repr(failure) +
            '\n\nRequired action: inspect verification.json, the producer snapshot, recipes, raw original reports '
            'and retained stdout/stderr. Determine whether the cause is the verifier, input, original program '
            'or adapter; correct it and rerun to a new --work directory. This failed attempt does not establish '
            'a working scientific implementation.\n', encoding='utf-8')
        raise


if __name__ == '__main__':
    raise SystemExit(main())
