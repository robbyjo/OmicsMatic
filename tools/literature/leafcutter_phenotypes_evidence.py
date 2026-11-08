# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Recheck every retained original/native phenotype, original PCA state, source and runtime identity; execute no scientific dependency."""
import gzip, hashlib, json, math, re, sys, zipfile
from pathlib import Path
from signac_scan_evidence import byte_pin, strict_json, unpack
from validate_leafcutter_phenotypes import compare
from audit_leafcutter_pca import audit

ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / 'docs/literature/leafcutter-phenotypes-development'


def original_family(files, home, case, source, pca):
    """Bind typed expectations back to every original physical output family."""
    text = 'chrom ' + ' '.join(case['samples']) + '\n' + ''.join(
        row['id'] + ' ' + ' '.join(str(num) + '/' + str(den) for num, den in row['counts']) + '\n'
        for row in case['rows'])
    assert gzip.decompress(files[home + '/input.counts.gz']) == text.encode('utf8')
    assert source['samples'] == case['samples'] and source['original_rows'] == case['rows']
    chromosomes = list(dict.fromkeys(r['id'].split(':')[0] for r in case['rows']))
    assert source['chromosomes_seen'] == chromosomes
    assert len(source['decisions']) == len(case['rows'])
    kept = {row['id']: row for row in source['kept']}
    assert len(kept) == len(source['kept']) > 0
    assert [r['ordinal'] for r in source['decisions'] if r['disposition'] == 'KEPT'] == [r['input_ordinal'] for r in source['kept']]
    for i, (row, decision) in enumerate(zip(case['rows'], source['decisions'])):
        assert decision['id'] == row['id'] and decision['ordinal'] == i
        if row['id'].split(':')[0] in case['blacklist']:
            assert decision['disposition'] == 'SOURCE_BLACKLISTED_CHROMOSOME' and decision['source_ratios'] == [] and decision['missing'] is None
        else:
            ratios = [None if den < 1 else (float(num) + .5) / (float(den) + .5) for num, den in row['counts']]
            assert decision['source_ratios'] == ratios and decision['missing'] == ratios.count(None)
            if ratios.count(None) > len(case['samples']) * .4:
                assert decision['disposition'] == 'SOURCE_MISSINGNESS_FILTER'
            else:
                assert decision['disposition'] == ('SOURCE_LOW_VARIATION_FILTER' if decision['population_stddev'] < .005 else 'KEPT')
    ids = []
    names = {'blacklist.txt', 'input.counts.gz', 'input.counts.gz.ave', 'input.counts.gz_prepare.sh', 'original-result.json', 'original-PCA.json', 'original.process.json', 'original.stdout', 'original.stderr'}
    for chromosome in sorted(chromosomes):
        original_order = [r for r in source['kept'] if r['chromosome'] == chromosome]
        sorted_order = sorted(original_order, key=lambda r: (r['start'], r['chromosome'] + '\t' + r['start_text'] + '\t' + r['end_text'] + '\t' + r['id']))
        for family, key in [('phen_', 'imputed_ratios'), ('qqnorm_', 'inverse_normal_scores')]:
            expected = original_order if family == 'phen_' else sorted_order
            name = 'input.counts.gz.' + family + chromosome; names.add(name)
            lines = files[home + '/' + name].decode('utf8').splitlines()
            assert lines[0].split() == ['#Chr', 'start', 'end', 'ID', *case['samples']] and len(lines) == len(expected) + 1
            for line, row in zip(lines[1:], expected):
                fields = line.split(); assert fields[:4] == [row['chromosome'], row['start_text'], row['end_text'], row['id']]
                assert [float(v) for v in fields[4:]] == row[key]
        ids.extend(r['id'] for r in sorted_order)
    assert ids == source['sorted_ids']
    average = files[home + '/input.counts.gz.ave'].decode('utf8').splitlines()
    assert len(average) == len(source['kept'])
    for line, row in zip(average, source['kept']):
        fields = line.split(); assert fields[0] == row['id']
        assert [float(v) for v in fields[1:]] == [row['observed_minimum'], row['observed_maximum'], row['observed_mean']]
    shell = files[home + '/input.counts.gz_prepare.sh'].decode('utf8').splitlines()
    assert len(shell) == 2 * len(chromosomes) and set(shell) == {line for chrom in chromosomes for line in ['bgzip -f input.counts.gz.qqnorm_' + chrom, 'tabix -p bed input.counts.gz.qqnorm_' + chrom + '.gz']}
    if pca is not None:
        names.add('input.counts.gz.PCs'); k = len(pca['components']); n = len(source['kept']); m = len(case['samples'])
        assert k == min(case['pcs'], m) and len(pca['singular_values']) == len(pca['explained_variance']) == len(pca['explained_variance_ratio']) == k
        assert len(pca['scores']) == n and all(len(row) == k for row in pca['scores'])
        assert pca['seed'] == case['seed']
        lines = files[home + '/input.counts.gz.PCs'].decode('utf8').splitlines()
        assert lines[0].split() == ['id', *case['samples']] and len(lines) == k + 1
        for i, (line, values) in enumerate(zip(lines[1:], pca['components']), 1):
            fields = line.split(); assert fields[0] == str(i) and [float(v) for v in fields[1:]] == values
    else:
        assert case['pcs'] == 0
    actual = {name.removeprefix(home + '/') for name in files if name.startswith(home + '/') and '/' not in name.removeprefix(home + '/')}
    assert actual == names, (home, actual ^ names)


def verify(root=ROOT, data=DATA, emit=True):
    if not __debug__: raise RuntimeError('Never disable complete evidence checks')
    proof = strict_json((data / 'VALIDATION.json').read_bytes())
    assert proof['status'] == 'passed-native-LeafCutter-phenotype-preprocessing-component-only'
    assert all(proof[k] is False for k in ['implementation_credit', 'full_LeafCutter_workflow_admitted', 'whole_goal_complete', 'released_or_deployed'])
    assert byte_pin((data / 'controls.zip').read_bytes()) == proof['archive']
    with zipfile.ZipFile(data / 'controls.zip') as z:
        assert len(z.namelist()) < 30000 and sum(i.file_size for i in z.infolist()) < 134217728
    files = unpack(data / 'controls.zip', proof['packing']); obj = lambda name: strict_json(files[name])
    for name, want in proof['source_pins'].items():
        assert byte_pin((root / name).read_bytes()) == byte_pin(files['current-source/' + name]) == want, name
    build = obj('build/COMPLETED.json'); validation = obj('validation/COMPLETED.json'); reference = obj('reference/COMPLETED.json'); intake = obj('source-intake/COMPLETED.json'); runtime = obj('runtime/COMPLETED.json')
    assert build['status'] == 'compiled-native-LeafCutter-phenotypes-not-original-validation'
    assert validation['status'] == 'PASS_COMPLETE_ORIGINAL_NATIVE_LEAFCUTTER_PHENOTYPES_THREE_JVM_COMPONENT_ONLY'
    assert reference['status'] == 'PASS_COMPLETE_ORIGINAL_LEAFCUTTER_PHENOTYPE_AND_PCA_CONTROLS_ONLY'
    assert intake['status'] == 'PASS_ORIGINAL_LEAFCUTTER_PHENOTYPE_AND_NUMERIC_SUPPORT_SOURCES_ONLY'
    assert runtime['status'] == 'PASS_PRIVATE_LEAFCUTTER_DEPENDENCY_CANDIDATE_ONLY'
    assert proof['artifact'] == build['artifact'] == validation['artifact']
    assert proof['canonical_executable_unchanged'] == build['canonical_executable_unchanged'] == validation['canonical_executable_unchanged']
    assert build['parent_artifact'] == strict_json((root / 'docs/literature/leafcutter-clusters-development/VALIDATION.json').read_bytes())['artifact'] and build['unchanged_parent_entries'] == 20772
    for report in [build, validation, reference, runtime, obj('PCA-audit/COMPLETED.json')]:
        for name, want in report['source_pins'].items(): assert proof['source_pins'][name] == want
    for name, want in build['source_pins'].items(): assert byte_pin(files['build/source-snapshot/' + name]) == want
    assert len(build['added_entries']) == 17 and set(build['added_entries']) == set(proof['packaged_entries'])
    for name, want in proof['packaged_entries'].items(): assert byte_pin(files['candidate-added-entries/' + name]) == want
    parent_data = root / 'docs/literature/leafcutter-clusters-development'; parent = strict_json((parent_data / 'VALIDATION.json').read_bytes())
    assert byte_pin((parent_data / 'controls.zip').read_bytes()) == parent['archive']; old = unpack(parent_data / 'controls.zip', parent['packing'])
    raw = files['source-intake/prepare_phenotype_table.py']; assert raw == old['source-intake/source-2c9907ef66ad/source/scripts/prepare_phenotype_table.py']
    assert byte_pin(raw) == {k: intake['original_script'][k] for k in ['bytes', 'sha256']}
    assert hashlib.sha1(b'blob ' + str(len(raw)).encode() + b'\0' + raw).hexdigest() == intake['original_script']['git_blob_sha1']
    profile = obj('current-source/src/main/resources/org/jlinalg/omics/leafcutter-phenotypes/SOURCE-PROFILE.json')
    assert profile['original_script'] == intake['original_script'] and profile['numeric_support_sources'] == intake['records']
    for label, record in intake['records'].items():
        home = 'source-intake/' + label; ref = obj(home + '/official-ref.json')['object']
        if ref['type'] == 'tag': ref = obj(home + '/official-annotated-tag.json')['object']
        assert ref['type'] == 'commit' and ref['sha'] == record['revision']
        for name, want in record['members'].items(): assert byte_pin(files[home + '/' + name]) == {k: want[k] for k in ['bytes', 'sha256']}
    assert reference['packages'] == dict(numpy='1.26.4', scipy='1.11.4', sklearn='1.3.2')
    assert reference['namespace_patched'] is False and reference['analysis_installed_software'] is False and reference['global_seed_is_explicit'] is True
    assert reference['runtime_trees_unchanged'] is True and runtime['runtime_trees_unchanged'] is True and runtime['shared_runtime_unchanged'] is True
    inventory = obj('runtime/runtime-before.json')
    assert inventory == obj('runtime/runtime-after.json') == obj('reference/runtime-before.json') == obj('reference/runtime-after.json')
    assert byte_pin(files['runtime/runtime-before.json']) == runtime['runtime_inventory'] and byte_pin(files['runtime/complete-conda-package-records.json']) == runtime['conda_records']
    assert len(obj('runtime/complete-conda-package-records.json')) == 239
    assert obj('runtime/shared-before.json') == obj('runtime/shared-after.json')
    for process in runtime['processes']: assert process['exit_code'] == 0 and process['owned_live_after_cleanup'] == []
    assert byte_pin(files['runtime/COMPLETED.json']) == {k: reference['runtime_manifest'][k] for k in ['bytes', 'sha256']}
    assert byte_pin(files['reference/COMPLETED.json']) == {k: validation['original_reference'][k] for k in ['bytes', 'sha256']}
    assert files['reference/cases.json'] == files['fixtures/cases.json'] and byte_pin(files['reference/cases.json']) == reference['fixtures']
    cases = obj('fixtures/cases.json')['cases']; assert len(cases) == len(reference['cases']) == 57
    totals = {}; errors = []; checks = {}
    for case, row in zip(cases, reference['cases']):
        assert case['id'] == row['id']; home = 'reference/' + case['id']; process = obj(home + '/original.process.json')
        assert process['exit_code'] == 0 and process['owned_live_after_cleanup'] == [] and '-I' in process['command'] and '-B' in process['command']
        assert byte_pin(files[home + '/input.counts.gz']) == row['input']
        source = obj(home + '/original-result.json'); pca = obj(home + '/original-PCA.json')
        original_family(files, home, case, source, pca)
        for key, value in audit(source, pca).items(): totals[key] = totals.get(key, 0) + value
    assert totals == proof['original_PCA_checks'] == obj('PCA-audit/COMPLETED.json')['checks']
    assert {k: v for k, v in totals.items() if k != 'variance_ratio_equations'} == validation['source_PCA_audit']
    for jvm in ['windows21', 'windows25', 'linux21']:
        version = (files['validation/' + jvm + '.version.stdout'] + files['validation/' + jvm + '.version.stderr']).decode('utf8')
        assert version == validation['jvm_versions'][jvm] and re.search(r'version "' + ('25' if jvm == 'windows25' else '21') + r'(?:\.|\")', version)
        assert obj('validation/' + jvm + '.process.json')['exit_code'] == 0
        report = obj('validation/' + jvm + '/COMPLETED.json'); assert report['status'] == 'PASS_NATIVE_LEAFCUTTER_PHENOTYPE_COMPONENT_ONLY' and len(report['cases']) == 57 and len(set(report['expected_native_rejections'])) == 18
        count = dict(cases=57, source_rows=0, retained_rows=0, floating_values=0, exact_values=0, maximum_absolute_error=0.0, expected_native_rejections=18)
        for case in cases:
            expected = obj('reference/' + case['id'] + '/original-result.json'); actual = obj('validation/' + jvm + '/' + case['id'] + '.json')
            compare(expected, {k: v for k, v in actual.items() if k != 'warnings'}, jvm + '/' + case['id'], errors, count)
            count['source_rows'] += len(case['rows']); count['retained_rows'] += len(actual['kept'])
        assert count == validation['checks'][jvm] == proof['checks'][jvm]; checks[jvm] = count
    assert errors == obj('validation/complete-mismatches.json') == []
    test = obj('comparison-tests/process.json'); assert test['exit_code'] == 0 and test['source_pin'] == proof['source_pins']['tools/literature/test_leafcutter_phenotype_comparison.py']
    assert b'Ran 11 tests' in files['comparison-tests/stderr'] and b'OK' in files['comparison-tests/stderr']
    assert 'history/native-preview-1/COMPLETED.json' not in files and obj('history/native-preview-2/COMPLETED.json')['status'] == 'PASS_NATIVE_LEAFCUTTER_PHENOTYPE_COMPONENT_ONLY'
    result = dict(status='passed-retained-native-LeafCutter-phenotype-component-evidence', checks=checks, original_PCA_checks=totals, logical_files=len(files), implementation_credit=False, full_LeafCutter_workflow_admitted=False, whole_goal_complete=False)
    if emit: print(json.dumps(result, indent=2))
    return result


if __name__ == '__main__':
    try: verify()
    except Exception as failure:
        print('LEAFCUTTER PHENOTYPE EVIDENCE FAIL2: ' + repr(failure) + '\nRequired action: preserve all source counts, states, original/native families, PCA diagnostics and failures. Correct the named mismatch and repeat into NEW paths. Never drop a case/field, repair NaN/IDs/counts or refresh frozen scientific hashes. Full LeafCutter remains missing.', file=sys.stderr)
        raise SystemExit(2)
