# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Compare typed scDRS workflows with original CLI outputs and replay saved bundles on three JVMs."""
import argparse
import copy
import csv
import gzip
import json
import os
import re
import subprocess
import traceback
from pathlib import Path
from setup_deseq2_reference import ROOT, pin, save
from setup_scdrs_reference import BASE, linux

DISTRO = 'JLinAlg-Omics-SAIGE-20261026'
JAVA21 = 'C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/java.exe'
JAVA25 = 'C:/Program Files/Common Files/Oracle/Java/javapath/java.exe'


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--out', type=Path, required=True)
    p.add_argument('--build21', type=Path, required=True)
    p.add_argument('--build25', type=Path, required=True)
    p.add_argument('--cases-file', type=Path, help='Optional complete synthetic case list; original CLI comparison rules stay unchanged')
    a = p.parse_args()
    out = a.out.resolve()
    assert out.is_relative_to(BASE) and not out.exists()
    out.mkdir(parents=True)
    (out / 'validator-at-attempt.py').write_bytes(Path(__file__).read_bytes())
    state = dict(status='FAIL2_TYPED_SCDRS_WORKFLOW_VALIDATION', new_catalog_credit=False,
                 full_adapter_validated=False, whole_goal_complete=False)
    canonical = ROOT / 'build/cli/omicsmatic-0.3.7.jar'
    before = pin(canonical)
    save(out / 'STARTED.json', state)

    def run(label, command, expected=0, timeout=900, environment=None):
        save(out / (label + '.command.json'), dict(command=command))
        with (out / (label + '.stdout')).open('xb') as stdout, (out / (label + '.stderr')).open('xb') as stderr:
            result = subprocess.run(command, cwd=ROOT, env=environment, stdin=subprocess.DEVNULL,
                                    stdout=stdout, stderr=stderr, timeout=timeout)
        save(out / (label + '.process.json'), dict(exit_code=result.returncode))
        assert result.returncode == expected, (label, result.returncode, (out / (label + '.stderr')).read_bytes()[-3000:])

    def original(label, profile, args, home):
        source = profile['source']
        command = ['wsl.exe', '-d', DISTRO, '--', '/usr/bin/env', '-i', 'PATH=/usr/bin:/bin',
            'HOME=' + linux(home), 'LC_ALL=C.UTF-8', 'OPENBLAS_NUM_THREADS=1', 'OMP_NUM_THREADS=1',
            'NUMBA_NUM_THREADS=1', 'PYTHONNOUSERSITE=1', 'PYTHONDONTWRITEBYTECODE=1',
            'NUMBA_CACHE_DIR=' + linux(home / 'numba-cache'), 'MPLCONFIGDIR=' + linux(home / 'matplotlib-cache'),
            'PYTHONPATH=' + source, profile['python'], '-s', '-B', source + '/bin/scdrs', *args]
        run(label, command)

    def golden(case, request, profile, directory):
        directory.mkdir(parents=True)
        inputs = request['inputs']
        expression = request['expression']
        if request['operation'] == 'munge-gs':
            spec = request['gene_sets']
            args = ['munge-gs', '--out-file=' + linux(directory / 'gene_sets.gs'),
                '--' + ('pval-file' if spec['input_kind'] == 'pvalue' else 'zscore-file') + '=' + inputs['gene_statistics'],
                '--weight=' + spec['weighting'], '--n-min=' + str(spec['minimum_genes']), '--n-max=' + str(spec['maximum_genes'])]
            for option in ['fdr', 'fwer']:
                if spec[option] is not None: args.append('--' + option + '=' + str(spec[option]))
            original(case + '-original-munge', profile, args, directory.parent)
            return
        if request['scoring'] is not None:
            spec = request['scoring']
            args = ['compute-score', '--h5ad-file=' + inputs['h5ad'], '--h5ad-species=' + expression['species'],
                '--gs-file=' + inputs['gene_sets'], '--gs-species=' + spec['gene_set_species'], '--out-folder=' + linux(directory),
                '--flag-filter-data=' + str(expression['filter_data']), '--flag-raw-count=' + str(expression['raw_counts']),
                '--n-ctrl=' + str(spec['controls']), '--ctrl-match-opt=' + spec['match'], '--weight-opt=' + spec['weighting'],
                '--flag-return-ctrl-raw-score=' + str(spec['return_control_raw']),
                '--flag-return-ctrl-norm-score=' + str(spec['return_control_normalized'])]
            if inputs['covariates'] is not None: args.append('--cov-file=' + inputs['covariates'])
            if spec['adjust_proportions'] is not None: args.append('--adj-prop=' + spec['adjust_proportions'])
            original(case + '-original-score', profile, args, directory.parent)
        if request['downstream'] is not None:
            spec = request['downstream']
            score_file = linux(directory) + '/@.full_score.gz' if request['scoring'] is not None else inputs['scores']
            args = ['perform-downstream', '--h5ad-file=' + inputs['h5ad'], '--score-file=' + score_file,
                '--out-folder=' + linux(directory), '--flag-filter-data=' + str(expression['filter_data']),
                '--flag-raw-count=' + str(expression['raw_counts']), '--knn-n-neighbors=' + str(spec['knn_neighbors']),
                '--knn-n-pcs=' + str(spec['knn_pcs'])]
            if spec['groups']: args.append('--group-analysis=' + ','.join(spec['groups']))
            if spec['correlations']: args.append('--corr-analysis=' + ','.join(spec['correlations']))
            if spec['gene_analysis']: args.append('--gene-analysis=True')
            original(case + '-original-downstream', profile, args, directory.parent)

    try:
        artifacts = {}
        build_pins = {}
        for tag, directory in [('javac21', a.build21), ('javac25', a.build25)]:
            record = json.loads((directory / 'COMPLETED.json').read_bytes())
            assert record['status'] == 'PASS_STANDALONE_SCDRS_WORKFLOW_DEVELOPMENT_BUILD_ONLY' and record['compiler'] == tag
            for name, wanted in record['source_pins'].items(): assert pin(ROOT / name) == wanted, name
            artifact = ROOT / record['artifact']['path']
            assert pin(artifact) == {k: record['artifact'][k] for k in ['bytes', 'sha256']}
            artifacts[tag] = artifact
            build_pins[tag] = pin(directory / 'COMPLETED.json')
        profiles = {v: json.loads((BASE / ('workflow-profile-' + v + '-1/runtime.json')).read_bytes()) for v in ['1.0.0', '1.0.1', '1.0.2']}
        assert json.loads((BASE / 'workflow-controls-1/COMPLETED.json').read_bytes())['status'] == 'PASS_SYNTHETIC_SCDRS_WORKFLOW_CONTROLS_CREATED_ONLY'
        controls = BASE / 'workflow-controls-1'
        cases = []
        template = json.loads((BASE / 'typed-workflow-smoke-1/request.json').read_bytes())
        for version in profiles:
            request = copy.deepcopy(template)
            base = BASE / 'original-cli-foundation-2' / ('version-' + version)
            request.update(version=version, runtime_profile=linux(BASE / ('workflow-profile-' + version + '-1/runtime.json')))
            request['inputs'].update(h5ad=linux(base / 'synthetic.input.h5ad'), gene_sets=linux(base / 'synthetic.gs'),
                covariates=linux(base / 'synthetic.cov') if version == '1.0.2' else None)
            request['scoring']['controls'] = 200 if version == '1.0.1' else 1000
            cases.append(('historical-' + version, request, base / 'original-output'))
        baseline = copy.deepcopy(cases[2][1])

        def add(name, edit):
            request = copy.deepcopy(baseline)
            edit(request)
            cases.append((name, request, None))

        def score_only(r):
            r['operation'] = 'score'
            r['downstream'] = None
            r['scoring']['controls'] = 200

        def uniform(r):
            score_only(r)
            r['inputs']['covariates'] = None
            r['scoring'].update(weighting='uniform', match='mean', return_control_raw=True)
        add('uniform-mean-and-both-control-tables', uniform)

        def invstd(r):
            score_only(r)
            r['scoring'].update(weighting='inv_std', return_control_raw=True, return_control_normalized=False)
        add('inverse-standard-deviation-raw-controls', invstd)

        def overdispersion(r):
            score_only(r)
            r['scoring']['weighting'] = 'od'
        add('original-overdispersion-with-covariates', overdispersion)

        def adjustment(r):
            score_only(r)
            r['inputs']['h5ad'] = linux(controls / 'imbalanced.h5ad')
            r['scoring']['adjust_proportions'] = 'celltype'
        add('source-proportion-adjustment', adjustment)

        def raw_filter(r):
            r['inputs']['h5ad'] = linux(controls / 'raw-with-filtered-identities.h5ad')
            r['inputs']['covariates'] = None
            r['scoring']['controls'] = 200
            r['expression'].update(raw_counts=True, filter_data=True)
        add('raw-normalization-and-source-filtering', raw_filter)

        def unicode_case(r):
            r['inputs'].update(h5ad=linux(controls / 'quoted-unicode.h5ad'), gene_sets=linux(controls / 'quoted-unicode.gs'),
                covariates=linux(controls / 'quoted-unicode.cov'))
            r['scoring']['controls'] = 200
            r['downstream']['groups'] = ['cell type']
        add('quoted-and-unicode-identities', unicode_case)

        def downstream(r, partial=False):
            r['operation'] = 'downstream'
            r['inputs'].update(gene_sets=None, covariates=None,
                scores=linux(controls / 'trait-partial.full_score.gz') if partial else linux(BASE / 'original-cli-foundation-2/version-1.0.2/original-output/trait-synthetic.full_score.gz'))
            r['scoring'] = None
            r['downstream']['gene_analysis'] = not partial
        add('standalone-downstream-and-gene-prioritization', downstream)
        add('standalone-literal-cell-intersection', lambda r: downstream(r, True))

        def multiple(r):
            r['inputs']['gene_sets'] = linux(controls / 'two-traits.gs')
            r['scoring']['controls'] = 200
        add('two-trait-scoring-and-downstream', multiple)

        def munge(r, version, kind, weight, fdr=None, fwer=None):
            r.update(version=version, runtime_profile=linux(BASE / ('workflow-profile-' + version + '-1/runtime.json')),
                operation='munge-gs', expression=None, scoring=None, downstream=None,
                gene_sets=dict(input_kind=kind, weighting=weight, minimum_genes=100, maximum_genes=1000, fdr=fdr, fwer=fwer))
            r['inputs'] = dict(h5ad=None, gene_sets=None, covariates=None, scores=None,
                gene_statistics=linux(controls / ('gene-p.tsv' if kind == 'pvalue' else 'gene-z.tsv')))
        add('munge-z-historical-1.0.0', lambda r: munge(r, '1.0.0', 'zscore', 'uniform'))
        add('munge-p-fdr-1.0.2', lambda r: munge(r, '1.0.2', 'pvalue', 'zscore', fdr=.05))
        add('munge-p-fwer-1.0.1', lambda r: munge(r, '1.0.1', 'pvalue', 'uniform', fwer=.05))
        if a.cases_file:
            document = json.loads(a.cases_file.read_bytes())
            assert document['schema_version'] == 1 and document['cases']
            cases = [(c['case'], c['request'], Path(c['preexisting_original_output']) if c['preexisting_original_output'] else None) for c in document['cases']]
            assert all(re.fullmatch(r'[A-Za-z0-9_.-]+', name) for name, _, _ in cases) and len({c[0] for c in cases}) == len(cases)
        save(out / 'cases.json', [{'case': name, 'request': request, 'preexisting_original_output': str(gold) if gold else None} for name, request, gold in cases])
        comparisons = []
        for index, (name, request, gold) in enumerate(cases):
            directory = out / name
            directory.mkdir()
            request['output_dir'] = linux(directory / 'bundle')
            save(directory / 'request.json', request)
            if gold is None:
                gold = directory / 'golden/original-output'
                golden(name, request, profiles[request['version']], gold)
            run(name + '-typed', ['wsl.exe', '-d', DISTRO, '--', '/usr/bin/java', '-jar', linux(artifacts['javac21']),
                '--request', linux(directory / 'request.json')])
            count = 0
            expected_files = sorted(p.name for p in gold.iterdir())
            actual_files = sorted(p.name for p in (directory / 'bundle/original').iterdir())
            assert actual_files == expected_files, (name, actual_files, expected_files)
            for filename in expected_files:
                a_path, b_path = directory / 'bundle/original' / filename, gold / filename
                actual, expected = a_path.read_bytes(), b_path.read_bytes()
                if filename.endswith('.gz'):
                    actual, expected = gzip.decompress(actual), gzip.decompress(expected)
                assert actual == expected, ('original CLI data bytes differ', name, filename)
                rows = list(csv.reader(expected.decode('utf-8').splitlines(), delimiter='\t'))
                if filename.endswith('.gs'):
                    count += sum(len(row[1].split(',')) for row in rows[1:])
                else:
                    count += sum(len(row) - 1 for row in rows[1:])
            for platform, command in [
                ('windows21', [JAVA21, '-jar', str(artifacts['javac21']), '--verify', str(directory / 'bundle')]),
                ('windows25', [JAVA25, '-jar', str(artifacts['javac25']), '--verify', str(directory / 'bundle')]),
                ('linux21', ['wsl.exe', '-d', DISTRO, '--', '/usr/bin/java', '-jar', linux(artifacts['javac21']), '--verify', linux(directory / 'bundle')])]:
                run(name + '-verify-' + platform, command)
            comparisons.append(dict(case=name, version=request['version'], operation=request['operation'],
                original_output_files=len(expected_files), values_in_byte_identical_original_outputs=count,
                saved_platforms=['windows21', 'windows25', 'linux21']))
            save(out / 'progress.json', dict(status='RUNNING_TYPED_SCDRS_WORKFLOW_CONTROLS', passed_cases=comparisons))
            print('PASS %d/%d %s: %d original output values' % (index + 1, len(cases), name, count), flush=True)
        state.update(status='PASS_TYPED_SCDRS_WORKFLOWS_MATCH_ORIGINAL_CLI_DATA_AND_THREE_SAVED_JVMS',
            cases=comparisons, build_receipts=build_pins, original_data_bytes_identical=True,
            numerical_and_gene_weight_coordinates=sum(c['values_in_byte_identical_original_outputs'] for c in comparisons),
            saved_bundle_verifications=3 * len(comparisons), further_safety_bh_and_catalog_gates_pending=True)
    except Exception as error:
        state.update(cause=repr(error), traceback=traceback.format_exc())
    assert pin(canonical) == before
    state['canonical_executable_unchanged'] = before
    save(out / 'COMPLETED.json', state)
    (out / 'WARNINGS.md').write_text('# ' + state['status'] + '\n\n' + state.get('cause',
        'Workflow reference controls only; full safety, native BH, root dispatch, paper reviews and catalog admission remain unfinished.')
        + '\n\nREQUIRED ACTION: Preserve every attempt. Resolve the stated source, dependency, integrity, input, process or numerical cause and retry NEW paths. No failed or partial validation grants tool/catalog admission.\n',
        encoding='utf-8', newline='\n')
    print(state['status'], state.get('cause', ''), flush=True)
    return 0 if state['status'].startswith('PASS_') else 2


if __name__ == '__main__':
    if not __debug__: raise RuntimeError('Do not disable original-source/output/canonical checks')
    raise SystemExit(main())
