#!/usr/bin/env python3
"""Run the public Beagle recipes and independently check original calls and posteriors."""
import argparse
import csv
import datetime as dt
from decimal import Decimal as D
import gzip
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]
FIXTURES = ROOT / 'src/test/resources/org/jlinalg/genetics/beagle-reference'
EXAMPLES = ROOT / 'examples/beagle-tools'


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def table(path):
    with Path(path).open(encoding='utf-8', newline='') as stream:
        rows = list(csv.DictReader(stream, delimiter='\t'))
    assert all(None not in row and all(v is not None for v in row.values()) for row in rows)
    return rows


def vcf(path):
    opener = gzip.open if str(path).endswith('.gz') else open
    samples, rows = None, {}
    with opener(path, 'rt', encoding='utf-8') as stream:
        for line in stream:
            fields = line.rstrip('\r\n').split('\t')
            if line.startswith('##'):
                continue
            if fields[0] == '#CHROM':
                assert samples is None
                samples = fields[9:]
                continue
            assert samples is not None and len(fields) == len(samples) + 9
            position = int(fields[1])
            assert position not in rows
            rows[position] = fields
    assert samples and rows
    return samples, rows


def interval(literal, maximum=D(1)):
    value = D(literal)
    assert value.is_finite() and 0 <= value <= maximum
    assert value.as_tuple().exponent >= -2, ('unexpected precision', literal)
    return max(D(0), value - D('.005')), min(maximum, value + D('.005'))


def overlap(a, b):
    return max(a[0], b[0]) <= min(a[1], b[1])


def check(out, options, case, aliases=None):
    aliases = aliases or {}
    samples, target = vcf(Path(options['input']))
    ref_samples, reference = vcf(Path(options['reference'])) if 'reference' in options else ([], {})
    expected_samples, expected = vcf(FIXTURES / case['output_file'])
    actual_samples, actual = vcf(out / 'genotypes.vcf')
    assert actual_samples == samples
    assert actual_samples == [aliases.get(s, s) for s in expected_samples]
    family = set(target) if not reference else set(reference) if options['method'] == 'impute' else set(target) & set(reference)
    assert set(actual) == family == set(expected) and list(actual) == sorted(family)
    observed = completed = changes = posterior = naive = nonmax = 0
    per_variant = {}
    for position, row in actual.items():
        original = expected[position]
        source = target.get(position, reference.get(position))
        assert row[:5] == source[:5]
        assert row[2] == aliases.get(original[2], original[2])
        assert row[8:] == original[8:], (case['case'], position, 'original GT/DS/AP/GP differ')
        expected_format = ['GT'] if options['method'] == 'phase' else ['GT', 'DS', 'GP'] if options['profile'].startswith('4.1') else ['GT', 'DS', 'AP1', 'AP2', 'GP']
        assert row[8].split(':') == expected_format
        local = dict(observed=0, completed=0, changes=0, naive=0, nonmax=0)
        for index, text in enumerate(row[9:]):
            fields = dict(zip(expected_format, text.split(':'), strict=True))
            gt = fields['GT'].split('|')
            assert len(gt) == 2 and all(a in ['0', '1'] for a in gt)
            count = sum(map(int, gt))
            before = target[position][index + 9].split(':')[0] if position in target else './.'
            before_alleles = before.replace('|', '/').split('/')
            if '.' in before_alleles:
                local['completed'] += 1
            else:
                local['observed'] += 1
                assert sorted(before_alleles) == sorted(gt)
                if '|' in before and before_alleles != gt:
                    local['changes'] += 1
                    assert options['phase-policy'] == 'allow-rephase'
            if 'GP' not in fields:
                continue
            literals = fields['GP'].split(',')
            gp = [interval(value) for value in literals]
            assert len(gp) == 3 and sum(p[0] for p in gp) <= 1 <= sum(p[1] for p in gp)
            ds = interval(fields['DS'], D(2))
            assert overlap(ds, (gp[1][0] + 2*gp[2][0], gp[1][1] + 2*gp[2][1]))
            best = max(range(3), key=lambda j: D(literals[j]))
            local['naive'] += best != count
            local['nonmax'] += D(literals[count]) < D(literals[best])
            posterior += 4
            if 'AP1' in fields:
                a, b = interval(fields['AP1']), interval(fields['AP2'])
                assert overlap(ds, (a[0] + b[0], a[1] + b[1]))
                het = [x + y - 2*x*y for x in a for y in b]
                bounds = [((1-a[1])*(1-b[1]), (1-a[0])*(1-b[0])), (min(het), max(het)), (a[0]*b[0], a[1]*b[1])]
                assert all(overlap(p, q) for p, q in zip(gp, bounds, strict=True))
                for allele, p in zip(gt, [a, b], strict=True):
                    assert p[0] <= D('.5') if allele == '0' else p[1] >= D('.5')
                posterior += 2
        observed += local['observed']
        completed += local['completed']
        changes += local['changes']
        naive += local['naive']
        nonmax += local['nonmax']
        per_variant[position] = local
    summary = table(out / 'variant-results.tsv')
    assert len(summary) == len(actual)
    for row in summary:
        position = int(row['position'])
        local = per_variant[position]
        assert row['variant'] == actual[position][2] and row['chromosome'] == actual[position][0]
        assert [int(row[k]) for k in ['original_input_called', 'completed_calls', 'provided_phase_order_changes', 'naive_GP_argmax_differences', 'strict_GP_nonmax_genotypes']] == [local[k] for k in ['observed', 'completed', 'changes', 'naive', 'nonmax']]
    ledger = table(out / 'variant-ledger.tsv')
    assert {int(r['position']) for r in ledger} == set(target) | set(reference)
    for row in ledger:
        position = int(row['position'])
        assert (row['output_expected'] == 'true') == (position in family)
        assert (row['target_present'] == 'true') == (position in target)
        assert (row['reference_present'] == 'true') == (position in reference)
        if position in target and position not in family:
            assert row['decision'] == 'EXPLICIT_TARGET_ONLY_EXCLUSION'
        assert row['upstream_id'] == 'V' + str(position)
    sample_rows = table(out / 'sample-ledger.tsv')
    assert [r['sample'] for r in sample_rows if r['source'] == 'target'] == samples
    assert [r['sample'] for r in sample_rows if r['source'] == 'reference'] == ref_samples
    for index, row in enumerate(r for r in sample_rows if r['source'] == 'target'):
        called = sum('.' not in target[p][index+9].split(':')[0] for p in family & set(target))
        assert int(row['observed_retained_target_markers']) == called
        assert row['upstream_sample'] == 'T' + str(index+1)
    assert json.loads((out / 'analysis-status.json').read_text())['status'] == 'passed-with-review-warnings'
    assert 'Required action:' in (out / 'WARNINGS.md').read_text(encoding='utf-8')
    assert not (out / 'partial').exists()
    return dict(variants=len(actual), genotype_cells=len(actual)*len(samples), observed_calls_preserved=observed,
                completed_calls=completed, provided_phase_order_changes=changes, posterior_fields=posterior,
                naive_GP_argmax_differences=naive, strict_GP_nonmax_genotypes=nonmax,
                variant_ledger_rows=len(ledger), sample_ledger_rows=len(sample_rows))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jar', type=Path, required=True)
    parser.add_argument('--home', type=Path, required=True)
    parser.add_argument('--java', type=Path, action='append', required=True)
    parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args()
    jar, home, work = args.jar.resolve(), args.home.resolve(), args.out.resolve()
    for source in [home, jar.parent, EXAMPLES, FIXTURES]:
        if work.is_relative_to(source) or source.is_relative_to(work):
            parser.error('--out must be outside runtime, JAR, example and fixture directories')
    work.mkdir(parents=True, exist_ok=False)
    record = dict(status='running', producer_sha256=sha(__file__), jar_sha256=sha(jar),
                  started_utc=dt.datetime.now(dt.timezone.utc).isoformat(), steps=[], workflows=[], negative_controls=0,
                  scope='Actual packaged typed phasing/reference-imputation recipes, exact original data-row comparisons, independent Decimal posterior/call checks and complete source identity ledgers. Synthetic contract checks only; no biological calibration, historical cohort or whole-project parity.')
    shutil.copy2(__file__, work / 'producer-snapshot.py')

    def save():
        (work / 'verification.json').write_text(json.dumps(record, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')

    def run(java, values, label, expected=0, preflight=False, existing=False):
        recipe = work / (label + '.json')
        recipe.write_text(json.dumps(values, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
        command = [str(java), '-Dfile.encoding=UTF-8', '-jar', str(jar), 'beagle-tools', '--options-file', str(recipe)]
        env = os.environ.copy()
        for name in ['JAVA_TOOL_OPTIONS', '_JAVA_OPTIONS', 'JDK_JAVA_OPTIONS', 'CLASSPATH']:
            env.pop(name, None)
        result = subprocess.run(command, cwd=ROOT, env=env, capture_output=True, timeout=180)
        (work / (label + '.stdout')).write_bytes(result.stdout)
        (work / (label + '.stderr')).write_bytes(result.stderr)
        record['steps'].append(dict(label=label, command=command, exit_code=result.returncode, expected=expected,
                                    recipe_sha256=sha(recipe), stdout_sha256=sha(work/(label+'.stdout')),
                                    stderr_sha256=sha(work/(label+'.stderr'))))
        save()
        assert result.returncode == expected, (label, result.stderr.decode(errors='replace'))
        out = Path(values['out'])
        if expected:
            record['negative_controls'] += 1
            assert not (out/'genotypes.vcf').exists()
            if preflight:
                assert not out.exists()
            elif not existing:
                status = json.loads((out/'analysis-status.json').read_text())['status']
                assert status == ('skipped-dependency-unavailable' if expected == 3 else 'failed')
                assert 'Required action:' in (out/'WARNINGS.md').read_text(encoding='utf-8')
        else:
            assert b'BEAGLE WARNINGS' in result.stderr
        return out

    try:
        provenance = json.loads((FIXTURES/'provenance.json').read_text())
        for name, digest in provenance['input_and_output_files_sha256'].items():
            assert sha(FIXTURES/name) == digest, name
        examples = json.loads((EXAMPLES/'provenance.json').read_text())
        for name, digest in examples['files_sha256'].items():
            assert sha(EXAMPLES/name) == digest, name
        cases = [c for c in table(FIXTURES/'cases.tsv') if c['JVM'] != 'java25']
        assert len(cases) == 20
        for j, java in enumerate(args.java):
            java = java.resolve()
            for case in cases:
                label = f"j{j}-{case['profile']}-{case['task']}"
                values = json.loads((EXAMPLES/'recipes'/(case['profile']+'-'+case['task']+'.json')).read_text())
                values.update(home=str(home), java=str(java), out=str(work/label))
                out = run(java, values, label)
                record['workflows'].append(dict(label=label, **check(out, values, case)))
                save()
            template = json.loads((EXAMPLES/'recipes/5.4-29Oct24-impute.json').read_text())
            template.update(home=str(home), java=str(java))
            # Exact opaque identifiers and Unicode paths travel through UTF-8 JSON.
            unicode = work/f'j{j}-输入_α'
            shutil.copytree(EXAMPLES/'data', unicode)
            aliases = {'T1': '家庭_a', 'T2': '家庭_a_b', 'M1': '変異_α'}
            lines = (unicode/'target.vcf').read_text().splitlines()
            for i, line in enumerate(lines):
                if line.startswith('#CHROM\t'):
                    fields = line.split('\t'); fields[9:11] = [aliases['T1'], aliases['T2']]; lines[i] = '\t'.join(fields)
                elif line.startswith('1\t1000\t'):
                    fields = line.split('\t'); fields[2] = aliases['M1']; lines[i] = '\t'.join(fields)
            (unicode/'target.vcf').write_text('\n'.join(lines)+'\n', encoding='utf-8')
            values = dict(template, input=str(unicode/'target.vcf'), reference=str(unicode/'reference.vcf'), map=str(unicode/'map.txt'), out=str(work/f'j{j}-输出_β'))
            case = next(c for c in cases if c['case'] == '5.4-impute')
            out = run(java, values, f'j{j}-unicode')
            record['workflows'].append(dict(label=f'j{j}-unicode', **check(out, values, case, aliases)))
            # Twelve independent admission/process controls per JVM.
            bad = work/f'j{j}-bad-inputs'; shutil.copytree(EXAMPLES/'data', bad)
            wrong = (bad/'reference.vcf').read_text().replace('1\t1000\tM1\tA\tG\t', '1\t1000\tM1\tG\tA\t', 1)
            (bad/'wrong-reference.vcf').write_text(wrong)
            (bad/'bad-map.txt').write_text('1 first 0 50000\n1 last 1 50001\n')
            (bad/'polyploid.vcf').write_text((bad/'target.vcf').read_text().replace('\t1/0\t', '\t0/1/1\t', 1))
            changed = work/f'j{j}-changed-runtime'; shutil.copytree(home, changed)
            altered = changed/'beagle.29Oct24.c8e.jar'; raw = bytearray(altered.read_bytes()); raw[-1] ^= 1; altered.write_bytes(raw)
            controls = [
                ('missing-runtime', dict(home=str(work/'absent-runtime')), 3, False),
                ('allele-mismatch', dict(reference=str(bad/'wrong-reference.vcf')), 2, False),
                ('partial-phase', {'input': str(bad/'target-phased.vcf'), 'phase-policy': 'preserve-provided'}, 2, False),
                ('target-only', {'target-only-markers': 'error'}, 2, False),
                ('map-coverage', dict(map=str(bad/'bad-map.txt')), 2, False),
                ('window-unit', {'window-unit': 'markers'}, 2, True),
                ('cell-bound', {'maximum-cells': '1'}, 2, False),
                ('wrong-version-flag', dict(profile='5.1-18May20'), 2, True),
                ('changed-runtime', dict(home=str(changed)), 3, False),
                ('polyploid', dict(input=str(bad/'polyploid.vcf')), 2, False),
                ('timeout', {'iterations': '100000', 'timeout-seconds': '1'}, 2, False),
            ]
            for name, change, expected, preflight in controls:
                values = dict(template, out=str(work/f'j{j}-control-{name}')); values.update(change)
                run(java, values, f'j{j}-control-{name}', expected, preflight)
            assert sha(altered) == hashlib.sha256(raw).hexdigest()
            existing = work/f'j{j}-existing'; existing.mkdir(); (existing/'sentinel').write_text('retain')
            run(java, dict(template, out=str(existing)), f'j{j}-control-existing', 2, existing=True)
            assert (existing/'sentinel').read_text() == 'retain'
            save()
        assert sha(jar) == record['jar_sha256'] and sha(__file__) == record['producer_sha256']
        record['totals'] = {key: sum(row[key] for row in record['workflows']) for key in record['workflows'][0] if key != 'label'}
        record['status'] = 'passed'
        save()
        print(json.dumps(dict(status='passed', workflows=len(record['workflows']), negative_controls=record['negative_controls'], totals=record['totals'])))
    except Exception as failure:
        record.update(status='failed-retained', error=repr(failure)); save(); raise


if __name__ == '__main__':
    main()
