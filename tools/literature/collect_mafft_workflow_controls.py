#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Run complete synthetic typed MAFFT workflows; requires exact preinstalled private homes.

Linux only. No downloads, installations or overwrites. Preserves every attempted
protocol, including unexpected failures. Two independent owned workflows may run
concurrently; their output trees and process accounting roots remain separate.
"""
import argparse
import concurrent.futures
import copy
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import time
import zipfile

ROOT = Path(__file__).resolve().parents[2]
VERSIONS = ('7.313', '7.475', '7.487', '7.490', '7.505', '7.526')


def digest(path):
    h = hashlib.sha256()
    with path.open('rb') as stream:
        for block in iter(lambda: stream.read(1048576), b''):
            h.update(block)
    return h.hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jar', type=Path, required=True)
    parser.add_argument('--setup-root', type=Path, required=True)
    parser.add_argument('--out', type=Path, required=True)
    parser.add_argument('--work', type=Path, required=True)
    parser.add_argument('--java', type=Path, default=Path('/usr/bin/java'))
    parser.add_argument('--workers', type=int, choices=(1, 2), default=2)
    args = parser.parse_args()
    if sys.platform != 'linux' or not args.java.is_file():
        print('MAFFT CONTROL SKIP3: Linux Java is unavailable. Required action: '
              'skip dependent analysis and use a permitted Linux Java installation.', file=sys.stderr)
        return 3
    for path in (args.out, args.work):
        if not path.is_absolute() or not path.parent.is_dir() or path.parent.resolve() != path.parent or path.exists():
            parser.error('Require NEW absolute output/work directories with existing canonical parents')
    if args.out == args.work or args.out in args.work.parents or args.work in args.out.parents:
        parser.error('Observation and owned-work trees must be separate')
    args.out.mkdir(mode=0o700); args.work.mkdir(mode=0o700)
    inputs = args.out / 'inputs'; inputs.mkdir()
    jar_sha = digest(args.jar)
    fixture = ROOT / 'src/test/resources/org/jlinalg/sequence/mafft-original/original-controls.zip'
    originals = zipfile.ZipFile(fixture)
    headers = {'T000000001': 'α taxon complete description', 'T000000002': 'β second taxon',
               'T000000003': '第三 quoted "taxon"', 'T000000004': 'domain fourth taxon'}

    def data(name, raw):
        path = inputs / name
        if isinstance(raw, str):
            for opaque, original in headers.items():
                raw = raw.replace('>' + opaque + '\n', '>' + original + '\n')
            raw = raw.encode('utf-8')
        path.write_bytes(raw)
        return str(path)

    def frozen(role, name='input.fasta'):
        return originals.read('original/7.526/' + role + '/' + name).decode('utf-8')

    dna = data('nucleotide α.fasta', frozen('DNA-auto'))
    aa = data('protein β.fasta', frozen('AA-local-1000'))
    backbone = data('backbone.fasta', frozen('AA-add-keep-map'))
    added = data('added.fasta', frozen('AA-add-keep-map', 'added.fasta'))
    gapped = data('gapped-DNA.fasta', frozen('DNA-gapped'))
    ambiguous = data('ambiguous-DNA.fasta', frozen('DNA-alphabet'))
    unusual = data('unusual-protein.fasta', frozen('AA-alphabet-any'))
    lower = data('mixed-case-protein.fasta', frozen('AA-local-1000').replace('ACDEFGHIKL', 'AcDEfgHIkL'))
    single = data('single-backbone.fasta', '>T000000001\nACcDEFGHI-K\n')
    two_added = data('two-added.fasta', '>T000000003\nACCDPQRSTEFG\n>T000000004\nA-CDEFGHIK\n')
    nt_backbone = data('nt-backbone.fasta', '>T000000001\nACGT-ACGT\n>T000000002\nA-GT-ACGT\n')
    nt_added = data('nt-added.fasta', '>T000000003\nACGTTTACGT\n>T000000004\nACGTACGT\n')
    bad = data('late-invalid.fasta', '>T000000001\nACGT\n>T000000002\nACZT\n')
    bad_utf8 = data('late-invalid-utf8.fasta', b'>valid\nACGT\n>late\nACG\xffT\n')
    duplicate = data('duplicate-fields.fasta', '>same first\nACGT\n>same second\nACGTT\n')

    def recipe(version='7.526', protein=False):
        return dict(schema_version=1, profile='linux-x86_64-mafft-' + version, operation='DE_NOVO',
                    input=aa if protein else dna, alphabet='PROTEIN' if protein else 'NUCLEOTIDE',
                    symbols='BIOLOGICAL', headers='WHOLE_HEADER', input_gaps='FORBID', strategy='AUTO',
                    scoring=dict(source_defaults=True),
                    parameters=dict(threads=1, output_order='INPUT', keep_length=False))

    def addition(version='7.526', nt=False):
        r = recipe(version, not nt)
        r.update(operation='ADD', input=nt_backbone if nt else backbone,
                 additions=nt_added if nt else added, input_gaps='PRESERVE_ALIGNMENT', addition_gaps='REMOVE')
        r['parameters']['keep_length'] = True
        return r

    cases = []

    def add(name, request, expected=0, golden=None, fault=None, home=None):
        cases.append(dict(name=name, recipe=copy.deepcopy(request), expected=expected, golden=golden,
                          fault=fault, home=str(home or args.setup_root / ('homes/' + request['profile'].removeprefix('linux-x86_64-mafft-')))))

    for version in VERSIONS:
        add('v' + version + '-DNA-auto', recipe(version), golden='DNA-auto')
        r = recipe(version, True); r.update(strategy='LOCAL_PAIR', maximum_iterations=1000)
        r['parameters'].update(progressive_threads=1, iteration_threads=0)
        add('v' + version + '-AA-local1000', r, golden='AA-local-1000')
        add('v' + version + '-AA-keep-map', addition(version), golden='AA-add-keep-map')
    for name, version, strategy, maximum in [
            ('DNA-global100', '7.487', 'GLOBAL_PAIR', 100),
            ('DNA-genaf100', '7.526', 'GENAFPAIR', 100),
            ('DNA-fft1', '7.313', 'FFT_NS_1', None),
            ('DNA-fft2', '7.526', 'FFT_NS_2', None),
            ('DNA-ffti2-warning', '7.475', 'FFT_NS_I', 2),
            ('DNA-source-default', '7.490', 'SOURCE_DEFAULT', None)]:
        r = recipe(version); r.update(strategy=strategy, input=gapped, input_gaps='REMOVE')
        r['parameters']['output_order'] = 'SOURCE'
        if maximum is not None: r['maximum_iterations'] = maximum
        add(name, r)
    for name, matrix, strategy, maximum, threads, iteration in [
            ('AA-auto-bl62', 62, 'AUTO', None, 1, None),
            ('AA-local-multithread-bl45', 45, 'LOCAL_PAIR', 2, 2, 2),
            ('AA-global-threadit0-bl30', 30, 'GLOBAL_PAIR', 100, 2, 0),
            ('AA-genaf-bl80', 80, 'GENAFPAIR', 100, 1, None)]:
        r = recipe('7.505' if matrix == 45 else '7.526', True)
        r.update(input=lower, strategy=strategy, scoring=dict(source_defaults=False, blosum=matrix, gap_open=1.53, offset=0.0))
        if maximum is not None: r['maximum_iterations'] = maximum
        r['parameters'].update(threads=threads, output_order='SOURCE', progressive_threads=1)
        if iteration is not None: r['parameters']['iteration_threads'] = iteration
        add(name, r)
    r = recipe(protein=True); r.update(input=unusual, symbols='ANY_PRINTABLE'); add('AA-literal-unusual', r)
    r = recipe(); r['input'] = ambiguous; add('DNA-literal-ambiguity', r)
    r = addition('7.505'); r.update(strategy='GLOBAL_PAIR', maximum_iterations=100); r['parameters']['keep_length'] = False; add('AA-expanding-global100', r)
    add('DNA-keep-map', addition(nt=True))
    r = addition(nt=True); r['strategy'] = 'SOURCE_DEFAULT'; r['parameters'].update(keep_length=False, output_order='SOURCE'); add('DNA-expanding', r)
    r = addition(); r.update(input=single, additions=two_added, scoring=dict(source_defaults=False, blosum=62, gap_open=1.53, offset=0.0)); add('AA-single-reference-explicit', r)
    r = recipe(protein=True); r.update(headers='FIRST_FIELD', strategy='GLOBAL_PAIR', maximum_iterations=100); add('AA-first-field', r)

    add('missing-runtime', recipe(), 3, home=args.setup_root / 'homes/absent')
    for version in ['7', '7.525']:
        add('unresolved-edition-' + version, recipe(version), 3)
    for capability in ['batch', 'rna_structure', 'dash', 'custom_matrix', 'codon_translation', 'adjust_direction', 'add_fragments', 'add_profile']:
        r = recipe(); r[capability] = dict(requested=True); add('unsupported-' + capability, r, 3)
    r = addition(); r.update(strategy='LOCAL_PAIR', maximum_iterations=1000); add('unsupported-iterative-keep', r, 3)
    r = addition('7.313'); r.update(strategy='GLOBAL_PAIR', maximum_iterations=100); r['parameters']['keep_length'] = False; add('unsupported-legacy-iterative-add', r, 3)
    for name, path in [('late-invalid', bad), ('late-invalid-utf8', bad_utf8), ('forbidden-gaps', gapped)]:
        r = recipe(); r['input'] = path; add(name, r, 2)
    r = recipe(); r.update(input=duplicate, headers='FIRST_FIELD'); add('duplicate-first-fields', r, 2)
    r = recipe(); r['unknown_setting'] = True; add('unknown-setting', r, 2)
    r = recipe(); r['limits'] = dict(fasta=dict(cells=31)); add('whole-population-capacity', r, 2)
    r = recipe(); r['limits'] = dict(process=dict(wallMillis=1)); add('owned-process-timeout', r, 2)
    r = recipe(); r['limits'] = dict(host=dict(dependencies=dict(logicalBytes=1))); add('host-capacity', r, 2)
    add('controlled-finalization-failure', recipe(), 2, fault='provisional-directory')
    (args.out / 'cases.json').write_text(json.dumps(cases, indent=2, ensure_ascii=False) + '\n', encoding='utf-8')

    def execute(case):
        name = case['name']; request = args.out / (name + '.json'); request.write_text(json.dumps(case['recipe'], indent=2) + '\n')
        work = args.work / name
        command = [str(args.java), '-Xmx512m', '-XX:ActiveProcessorCount=2', '-jar', str(args.jar), 'mafft-tools',
                   '--recipe', str(request), '--home', case['home'], '--out', str(work)]
        with (args.out / (name + '.stdout')).open('xb') as out, (args.out / (name + '.stderr')).open('xb') as err:
            process = subprocess.Popen(command, stdout=out, stderr=err)
            injected = False; deadline = time.monotonic() + 1200
            while process.poll() is None:
                if case['fault'] and work.is_dir() and not injected:
                    (work / 'COMPLETION-PROVISIONAL.json').mkdir(mode=0o700); injected = True
                if time.monotonic() > deadline:
                    # Do not terminate an unobserved scientific descendant or retry blindly.
                    raise TimeoutError('Controller observation limit: retain live Java PID ' + str(process.pid) + ' and reobserve the same case')
                time.sleep(.05)
        row = dict(name=name, expected=case['expected'], exit_code=process.returncode, output=str(work),
                   command=command, controlled_fault_injected=injected, golden_comparisons=0)
        if row['exit_code'] == 0 and case['golden']:
            prefix = 'original/' + case['recipe']['profile'].removeprefix('linux-x86_64-mafft-') + '/' + case['golden'] + '/'
            for actual, reference in [('process/process.stdout', 'process.stdout')] + ([('staged/added.fasta.map', 'added.fasta.map')] if case['golden'] == 'AA-add-keep-map' else []):
                if (work / actual).read_bytes() != originals.read(prefix + reference):
                    row['golden_mismatch'] = actual
                row['golden_comparisons'] += 1
        (args.out / (name + '.observed.json')).write_text(json.dumps(row, indent=2) + '\n')
        print(name, row['exit_code'], 'golden-mismatch' if 'golden_mismatch' in row else '', flush=True)
        return row

    rows = []
    with concurrent.futures.ThreadPoolExecutor(max_workers=args.workers) as pool:
        for future in concurrent.futures.as_completed([pool.submit(execute, case) for case in cases]):
            rows.append(future.result())
            (args.out / 'observed.json').write_text(json.dumps(rows, indent=2) + '\n')
    assert digest(args.jar) == jar_sha, 'Executed JAR changed during controls'
    failed = [r for r in rows if r['exit_code'] != r['expected'] or 'golden_mismatch' in r]
    result = dict(status='failed-retained-controls' if failed else 'passed-complete-synthetic-workflow-controls',
                  jar_sha256=jar_sha, fixture_sha256=digest(fixture), protocols=len(rows),
                  observed_codes={str(code): sum(r['exit_code'] == code for r in rows) for code in [0, 2, 3]},
                  golden_comparisons=sum(r['golden_comparisons'] for r in rows), unexpected=[r['name'] for r in failed],
                  biological_accuracy_certified=False, whole_paper_reproduced=False)
    (args.out / 'summary.json').write_text(json.dumps(result, indent=2) + '\n')
    print(json.dumps(result), flush=True)
    return 1 if failed else 0


if __name__ == '__main__':
    try:
        sys.exit(main())
    except Exception as failure:
        print('MAFFT CONTROL FAIL2: ' + str(failure) + '\nRequired action: preserve all attempts, '
              'inspect the raw diagnostics and any live controller handle; never admit a partial result or overwrite a prior tree.', file=sys.stderr)
        raise
