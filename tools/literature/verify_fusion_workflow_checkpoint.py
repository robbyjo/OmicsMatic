"""Read-only FUSION checkpoint audit. No R, Java, downloads or catalog writes."""
from pathlib import Path, PurePosixPath
import argparse
import csv
import gzip
import hashlib
import io
import json
import math
import re
import sys
import xml.etree.ElementTree as ET
import zipfile


def require(condition, message):
    if not condition:
        raise ValueError(message)


def pin(raw):
    return dict(bytes=len(raw), sha256=hashlib.sha256(raw).hexdigest())


def address(name):
    path = PurePosixPath(name)
    require(name and not path.is_absolute() and '..' not in path.parts and
            '\\' not in name and path.as_posix() == name, 'Unsafe evidence path: ' + name)
    return name


def finite(value):
    try:
        return math.isfinite(float(value))
    except (ValueError, TypeError):
        return False


def verify(root, jar=None):
    folder = root / 'docs/literature/fusion-workflow-qualification'
    status = json.loads((folder / 'STATUS.json').read_bytes())
    require(status['status'] == 'PASS_FUSION_ORIGINAL_WORKFLOW_SCOPED_CHECKPOINT' and
            not status['whole_tool_qualified'] and not status['whole_paper_reproduced'] and
            not status['whole_goal_complete'] and status['new_family_credit'] == status['new_reviews'] == 0,
            'Overbroad checkpoint scope/credit')
    index_raw = (folder / 'EVIDENCE.json').read_bytes()
    require(pin(index_raw) == status['evidence_index'], 'Evidence index identity differs')
    index = json.loads(index_raw)
    require(not index['private_runtime_binary_JAR_or_primary_papers_exported'] and
            not index['scientific_capture_bytes_transformed'], 'Publication declaration differs')
    require(pin((folder / 'evidence.zip').read_bytes()) == index['zip'], 'Evidence ZIP differs')
    logical = index['files']
    require(len(logical) <= 20000, 'Logical file capacity exceeded')
    expected = {'objects/' + v['sha256'] for v in logical.values()}
    with zipfile.ZipFile(folder / 'evidence.zip') as archive:
        entries = archive.infolist()
        require(len(entries) == len(expected) == len({e.filename for e in entries}) and
                {e.filename for e in entries} == expected, 'ZIP object inventory is not closed and unique')
        require(all(re.fullmatch(r'objects/[0-9a-f]{64}', e.filename) and
                    e.file_size <= 64 * 1024 * 1024 for e in entries) and
                sum(e.file_size for e in entries) <= 256 * 1024 * 1024, 'ZIP capacity exceeded')
        objects = {e.filename: archive.read(e) for e in entries}
    for name, identity in logical.items():
        address(name)
        require(pin(objects['objects/' + identity['sha256']]) == identity, 'Evidence bytes differ: ' + name)

    def data(name):
        return objects['objects/' + logical[address(name)]['sha256']]

    def load(name):
        return json.loads(data(name))

    def subset(prefix):
        return {n[len(prefix):]: v for n, v in logical.items() if n.startswith(prefix)}

    def table(name):
        return list(csv.DictReader(io.StringIO(data(name).decode('utf-8')), delimiter='\t'))

    for name, identity in status['sources'].items():
        require(pin((root / address(name)).read_bytes()) == identity ==
                logical['normal/source-context/' + name], 'Scoped production source differs: ' + name)
    dispatcher = (root / 'src/main/java/org/jlinalg/cli/JLinAlgCli.java').read_text(encoding='utf-8')
    require('arguments[0].equals("fusion-tools")' in dispatcher and
            'return FusionWorkflowCli.run(Arrays.copyOfRange(arguments, 1, arguments.length), output, errorOutput);' in dispatcher and
            'java -jar omicsmatic-<version>.jar fusion-tools --help' in dispatcher, 'Global command route/help missing')
    profile = json.loads((root / 'src/main/resources/org/jlinalg/omics/fusion-9346c122/PROFILE.json').read_bytes())
    require(profile['source_revision'] == '9346c1222bffbb34499fa7a8e23c1b701b55cb05' and
            len(profile['members']) == 9, 'Original source profile differs')
    for member in profile['members'].values():
        source = root / 'src/main/resources/org/jlinalg/omics/fusion-9346c122' / address(member['resource'])
        require(pin(source.read_bytes()) == {k: member[k] for k in ['bytes', 'sha256']}, 'Source/wrapper bytes differ')
    normal = load('normal/RESULT.json')
    require(normal['exit_code'] == 0 and normal['source_inputs_unchanged'] and
            normal['development_jar'] == status['producer'], 'Normal build receipt differs')
    reports = [ET.fromstring(data(n)) for n in logical if n.startswith('normal/junit/')]
    require(sum(int(r.get('tests', '0')) for r in reports) == 75 and
            all(int(r.get(k, '0')) == 0 for r in reports for k in ['failures', 'errors', 'skipped']), 'Normal tests differ')
    source_inputs = load('normal/SOURCE-INPUTS.json')
    require(all(source_inputs[n] == v for n, v in status['sources'].items()), 'Normal-build source binding differs')
    payloads = load('independent/PAYLOADS.json')
    prior, current = payloads['previous'], payloads['current']
    require(len(prior) == 22840 and len(current) == 22863 and
            sorted(n for n, v in prior.items() if current.get(n) != v) == ['org/jlinalg/cli/JLinAlgCli.class'],
            'Preceding complete payload preservation differs')
    if jar is not None:
        require(pin(jar.read_bytes()) == status['producer'], 'Actual producer JAR differs')
        with zipfile.ZipFile(jar) as archive:
            names = [n for n in archive.namelist() if not n.endswith('/')]
            require(len(names) == len(set(names)), 'Producer contains duplicate payloads')
            require({n: hashlib.sha256(archive.read(n)).hexdigest() for n in names} == current,
                    'Actual complete producer payload map differs')

    def capture(prefix):
        prefix = address(prefix).rstrip('/') + '/'
        manifest = load(prefix + 'manifest.json')
        marker = load(prefix + 'COMPLETED.json')
        require(marker['authentication'] is False and marker['manifest_sha256'] ==
                logical[prefix + 'manifest.json']['sha256'], 'Completion marker differs: ' + prefix)
        files = subset(prefix)
        for n in ['manifest.json', 'COMPLETED.json']:
            files.pop(n)
        require(manifest['inventory']['files'] == files and
                manifest['inventory']['directories'] == index['capture_directories'][prefix.rstrip('/')],
                'Capture inventory is not closed: ' + prefix)
        request = load(prefix + 'canonical-request.json')
        limits = request['limits']
        require(len(files) + 2 <= limits['maximum_files'] and
                sum(v['bytes'] for v in subset(prefix).values()) <= limits['maximum_output_bytes'] and
                all(v['bytes'] <= limits['maximum_member_bytes'] for v in subset(prefix).values()), 'Saved capacity differs')
        require(manifest['results_admitted'] and manifest['process_exit_code'] == 0 and
                manifest['workflow'] == 'fusion-original-workflow-v1', 'Invalid admitted status')
        processes = load(prefix + 'worker-process.json')
        require(all(manifest[k] == processes[k] for k in ['command', 'process_exit_code', 'owned_processes']) and
                processes['owned_processes'] and all(p['pid'] > 0 and p['same_identity_alive'] is False
                for p in processes['owned_processes']), 'Process result/identity binding differs')
        args = load(prefix + 'worker/original-arguments.json')
        original = load(prefix + 'worker/original-process.json')
        require(original['exit_code'] == 0 and original['arguments'] == args and
                manifest['command'] == [request['rscript'], '--vanilla', 'driver.R'] + args,
                'Actual original command differs')
        binding = load(prefix + 'request-binding.json')
        require(binding['canonical'] == request and binding['original'] == load(prefix + 'source-request.json'),
                'Scientific request binding differs')
        require(load(prefix + 'source-profile.json') == profile, 'Captured source profile differs')
        for name, identity in profile['members'].items():
            require(logical[prefix + 'worker/' + name] == {k: identity[k] for k in ['bytes', 'sha256']},
                    'Captured original source differs')
        inputs = load(prefix + 'input-inventory.json')
        require(inputs == {'inputs/' + n: v for n, v in subset(prefix + 'inputs/').items()} and
                sum(v['bytes'] for v in inputs.values()) <= limits['maximum_input_bytes'], 'Input population/budget differs')
        require(data(prefix + 'runtime-before.json') == data(prefix + 'runtime-after.json'), 'Runtime closure changed')
        runtime = load(prefix + 'worker/runtime.json')
        versions = {r['name']: r['version'] for r in runtime['packages']}
        require(runtime['R'] == request['expected_R'] and
                all(versions.get(n) == v for n, v in request['expected_packages'].items()), 'Runtime editions differ')
        require(data(prefix + 'worker/READY').strip() == b'FUSION_PREFLIGHT_9346C122_V1' and
                data(prefix + 'worker/ACK').strip() == b'FUSION_ACK_9346C122_V1', 'Preflight handshake differs')
        summary = load(prefix + 'summary.json')
        require(summary == load(prefix + 'worker/summary.json') and summary['original_exit_code'] == 0 and
                summary['operation'] == request['operation'] and
                summary['source_revision'] == profile['source_revision'], 'Source-derived summary differs')
        exports = summary['original_export_files']
        require(set(exports) == {'out/' + n for n in subset(prefix + 'worker/out/')} and
                set(subset(prefix + 'admitted/')) == set(exports) | {'audit.rds'}, 'Scientific export index differs')
        for name in exports + ['audit.rds']:
            require(logical[prefix + 'worker/' + name] == logical[prefix + 'admitted/' + name], 'Admitted copy differs')
        warnings = data(prefix + 'WARNINGS.md').decode('utf-8')
        require('SKIP3' in warnings and 'FAIL2' in warnings, 'Required warnings missing')
        if summary['no_result_rows'] + summary['missing_requested_coloc_rows']:
            require(request['admission']['allow_no_result_rows'] and 'ORIGINAL NO-RESULT COUNTS' in warnings,
                    'Source no-result policy/warnings differ')
        if request['operation'] == 'ASSOCIATE':
            rows = table(prefix + 'worker/out/result')
            if prefix + 'worker/out/result.MHC' in logical:
                rows += table(prefix + 'worker/out/result.MHC')
                require(logical[prefix + 'worker/out/result.MHC'] == logical[prefix + 'worker/source-format-mhc.tsv'],
                        'MHC source reconstruction differs')
            require(logical[prefix + 'worker/source-association-state.rds']['bytes'] > 0 and
                    logical[prefix + 'worker/out/result'] == logical[prefix + 'worker/source-format-main.tsv'] and
                    summary['source_csv_rounding_verified'] and summary['unrounded_source_state_captured'],
                    'Original export/source state binding differs')
            nfinite = sum(finite(r['TWAS.Z']) and finite(r['TWAS.P']) and 0 <= float(r['TWAS.P']) <= 1 for r in rows)
            require(nfinite == summary['finite_result_rows'] and len(rows) - nfinite == summary['no_result_rows'] and
                    len(rows) == summary['selected_features'], 'Independent TSV result/no-result counts differ')
            require(sum(finite(r['TWAS.P']) and float(r['TWAS.P']) == 0 for r in rows) == summary['original_p_zero'],
                    'Original zero-p count differs')
        if manifest['mode'] == 'REPLAY':
            comparison = load(prefix + 'replay-comparison.json')
            after = {n: logical[prefix + 'worker/' + n] for n in exports}
            equal = comparison['parent'] == after
            require(after == comparison['replay'] and equal == comparison['scientific_exports_byte_identical'] and
                    comparison['independent_refit'] is False and
                    (equal or request['admission']['allow_replay_differences']), 'Replay comparison/policy differs')
        return summary

    packaged = load('packaged/RESULT.json')
    require(packaged['jar'] == status['producer'] and len(packaged['cases']) == 30 and packaged['all_expected'],
            'Packaged current-producer scope differs')
    valid = 0
    for case in packaged['cases']:
        require(case['exit_code'] == case['expected'], 'Unexpected packaged case outcome')
        prefix = 'packaged/' + case['case']
        if case['exit_code'] == 0:
            capture(prefix)
            valid += 1
        else:
            failed = load(prefix + '/FAILURE.json')
            require(failed['exit_code'] == 2 and not failed['results_admitted'] and
                    prefix + '/COMPLETED.json' not in logical, 'Refused original result was admitted')
    require(valid == 28, 'Valid packaged population differs')
    mixed = capture('mixed/retain-mixed')
    require(mixed['finite_result_rows'] == 2 and mixed['no_result_rows'] == 1 and
            load('mixed/strict-refusal/FAILURE.json')['exit_code'] == 2, 'Heterogeneous original result regression differs')
    boundary = load('rounding/RESULT.json')
    gate = boundary['boundary']
    require(float(gate['EXPORTED']) < float(gate['THRESHOLD']) < float(gate['RAW']) and
            boundary['original_missing_posterior_preserved'], 'Real source rounding boundary differs')
    boundary_summary = capture('rounding/run')
    require(boundary_summary['coloc_rounded_gate_disagreements'] == 1 and
            boundary_summary['missing_requested_coloc_rows'] == 0, 'Raw-source gate was replaced by rounded export')
    controls = load('refusals/RESULT.json')['cases']
    require(len(controls) == 17 and sum(c['exit_code'] == 2 for c in controls) == 14 and
            all(c['exit_code'] == c['expected'] and c['pass_check'] for c in controls), 'Resealed controls differ')
    runtime_controls = load('runtime-controls/RESULT.json')['cases']
    require(len(runtime_controls) == 6 and sum(c['exit_code'] == 3 for c in runtime_controls) == 3 and
            all(c['exit_code'] == c['expected'] and c['no_admitted_result'] and
                c['all_recorded_identities_closed'] for c in runtime_controls), 'SKIP3/FAIL2 runtime controls differ')
    for case in runtime_controls:
        prefix = 'runtime-controls/' + case['case'] + '/'
        failure = load(prefix + 'FAILURE.json')
        require(failure['exit_code'] == case['exit_code'] and not failure['results_admitted'] and
                prefix + 'COMPLETED.json' not in logical and not subset(prefix + 'admitted/'), 'Failed result admitted')
        if case['case'] == 'timeout-training':
            worker = load(prefix + 'worker-process.json')
            require('timed out' in failure['cause'] and worker['owned_processes'] and
                    all(p['same_identity_alive'] is False for p in worker['owned_processes']), 'Owned timeout evidence differs')
    replays = load('training-replays/RESULT.json')
    require(replays['parents_unchanged'] and replays['source_RNG_settings_unchanged'] and
            not replays['independent_refit'], 'Training replay scope differs')
    for case in replays['cases']:
        prefix = 'training-replays/' + case['case']
        comparison = load(prefix + '/replay-comparison.json') if case['case'] != 'explicit-allow-parent' else None
        if case['exit_code'] == 0:
            capture(prefix)
        else:
            require(case['case'] == 'strict-replay' and case['exit_code'] == 2 and
                    not comparison['scientific_exports_byte_identical'], 'Strict replay refusal differs')
    require(load('training-cv/RESULT.json')['crossval'] == 2 and
            capture('training-cv/run')['trained_models'] == ['blup', 'lasso', 'top1', 'enet', 'bslmm'],
            'Original all-model cross-validation scope differs')
    examples = load('examples/RESULT.json')
    require(examples['producer'] == status['producer'] and len(examples['cases']) == 4 and
            all(c['exit_code'] == 0 for c in examples['cases']) and examples['source_inputs_unchanged'], 'Public examples differ')
    capture('examples/association')
    capture('examples/training')
    for case in ['correlation', 'eqtl-individual', 'scatter-individual']:
        capture('compatible-plots/' + case)
    original_checks = load('independent/RESULT.json')
    require(len(original_checks['association_comparisons']) == 5 and
            len(original_checks['multi_panel_original_export_comparisons']) == 3, 'Original reference scope differs')
    for case, original in [('assoc-w21', 'complete'), ('missing-w21', 'missing'), ('top1-w21', 'forced-top1'),
                           ('coloc-w21', 'coloc'), ('beta-se-w21', 'beta-se')]:
        actual = table('packaged/' + case + '/worker/out/result')
        expected_rows = table('original-association/' + original + '.out.tsv')
        require(len(actual) == len(expected_rows) == 3, 'Original association population differs')
        for a, b in zip(actual, expected_rows):
            require({k: v for k, v in a.items() if k != 'FILE'} == {k: v for k, v in b.items() if k != 'FILE'} and
                    a['FILE'].replace('\\', '/').rsplit('/', 1)[-1].strip() ==
                    b['FILE'].replace('\\', '/').rsplit('/', 1)[-1].strip(), 'Original association fields differ')
    for case, original, extension in [('post-correlation', 'correlation', '.CORR.dat'),
                                      ('post-omnibus', 'omnibus', '.CORR.dat'),
                                      ('post-omnibus', 'omnibus', '.omnibus.pv')]:
        require(data('packaged/' + case + '/worker/out/result' + extension) ==
                data('original-post/' + original + extension), 'Complete original panel export differs')
    return dict(status=status['status'], logical_files=len(logical), objects=len(objects), normal_tests=75,
                packaged_cases=30, resealed_offline_controls=17, runtime_controls=6,
                original_association_rows=15, complete_original_panel_exports=3,
                previous_payloads_preserved=22839, actual_producer_checked=jar is not None,
                R_or_Java_or_network_executed=False, independent_training_refit=False,
                opaque_RDS_decoded=False, catalog_admission_complete=False,
                new_family_credit=0, new_reviews=0, whole_goal_complete=False)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', type=Path, default=Path(__file__).resolve().parents[2])
    parser.add_argument('--jar', type=Path, help='Optional exact saved producer; never executed')
    args = parser.parse_args()
    try:
        print(json.dumps(verify(args.root, args.jar), indent=2))
        return 0
    except (OSError, ValueError, KeyError, TypeError, zipfile.BadZipFile, ET.ParseError) as error:
        print('FAIL2: saved FUSION checkpoint differs: ' + str(error), file=sys.stderr)
        return 2


if __name__ == '__main__':
    raise SystemExit(main())
