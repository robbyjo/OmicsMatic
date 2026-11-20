#!/usr/bin/env python3
"""Saved edgeR development evidence reader. No R, Git or JVM execution/hash refresh."""
from pathlib import Path, PurePosixPath
import argparse, hashlib, io, json, sys, tarfile, xml.etree.ElementTree as ET, zipfile

ROOT = Path(__file__).resolve().parents[2]
PACKET = ROOT / 'docs/literature/edger-model-checks'
STATUS = 'PASS_SAVED_ORIGINAL_EDGER_MODELS_SOURCE_COMPARISONS_FAILURES_AND_FILE_CONTROLS'


class Invalid(ValueError):
    pass


def need(condition, message):
    if not condition:
        raise Invalid(message)


def pin(raw):
    return dict(bytes=len(raw), sha256=hashlib.sha256(raw).hexdigest())


def safe(name):
    path = PurePosixPath(name)
    need(isinstance(name, str) and name and '\\' not in name and ':' not in name and
         not path.is_absolute() and str(path) == name and all(p not in ('.', '..') for p in path.parts),
         'Unsafe evidence path')
    return name


def read_packet(folder=PACKET):
    q = json.loads((folder / 'QUALIFICATION.json').read_bytes())
    need(q['schema_version'] == 1 and q['status'] == 'PASS_SCOPED_ORIGINAL_EDGER_PREPARED_COUNT_MODELS', 'Wrong qualification status')
    need(q['whole_tool_or_paper_qualification'] is False and q['new_catalog_credit'] == 0, 'Qualification scope changed')
    for name, expected in q['source_files'].items():
        need(pin((ROOT / safe(name)).read_bytes()) == expected, 'Live qualified source differs: ' + name)
    archive = (folder / 'evidence.zip').read_bytes()
    need(pin(archive) == q['archive'], 'Evidence archive identity differs')
    need(len(archive) <= 100_000_000, 'Evidence archive exceeds fixed capacity')
    blobs = {}
    with zipfile.ZipFile(io.BytesIO(archive)) as z:
        need(len(z.namelist()) == len(set(z.namelist())) == len(q['blobs']), 'Archive entry population differs')
        need(set(z.namelist()) == set(q['blobs']), 'Archive payload names differ')
        need(sum(info.file_size for info in z.infolist()) <= 500_000_000, 'Decoded archive exceeds fixed capacity')
        for name, expected in q['blobs'].items():
            need(safe(name) == 'blobs/' + expected['sha256'], 'Blob name differs from content identity')
            raw = z.read(name)
            need(pin(raw) == expected, 'Blob identity differs: ' + name)
            blobs[expected['sha256']] = raw
    files = {}
    need(len(q['logical_files']) <= 20000, 'Logical file population exceeds capacity')
    for name, expected in q['logical_files'].items():
        safe(name)
        raw = blobs[expected['sha256']]
        need(pin(raw) == expected, 'Logical file differs: ' + name)
        files[name] = raw
    need(set(blobs) == {p['sha256'] for p in q['logical_files'].values()}, 'Unreferenced blobs are not accepted')
    return q, files


def audit(q, files):
    def j(name):
        return json.loads(files[name])
    model = j('final/COMPLETED.json')
    need(model['status'] == 'PASS_FINAL_NORMAL_EDGER_33_INDEPENDENT_STOCK_COMPOSITIONS_AND_TWO_REAL_EXHAUSTION_REFUSALS', 'Original model qualification failed')
    need(len(model['cases']) == len(model['results']) == 33 and model['exact_model_comparisons'] == 338 and model['maximum_numeric_error'] == 0, 'Original comparison population changed')
    need(model['private_runtime_files_unchanged'] == 1517 and model['unchanged_preceding_members'] == 23471 and model['new_members'] == 14, 'Original runtime/producer scope changed')
    need(j('final/PRIVATE-RUNTIME-BEFORE.json') == j('final/PRIVATE-RUNTIME-AFTER.json'), 'Declared original libraries changed')
    producer = j('final/PRODUCER.json')
    need(len(producer['preceding_members']) == 23471 and len(producer['additions']) == 14 and producer['all_members'] == 23485, 'Producer member inventory differs')
    need(producer['jar_pin'] == q['producer'], 'Qualified producer differs')
    member_files = j('producer/NEW-MEMBERS.json')
    need(set(member_files) == set(producer['additions']), 'New producer payload population differs')
    for name, path in member_files.items():
        need(pin(files[safe(path)]) == producer['additions'][name], 'New producer payload differs: ' + name)
    comparisons = 0
    conditions = 0
    for case in model['cases']:
        name = case['name']
        prefix = 'final/' + name
        need(j(prefix + '.PROCESS.json')['exit_code'] == 0 and j(prefix + '-reference.PROCESS.json')['exit_code'] == 0, 'Original/reference process failed')
        reference = j(prefix + '-reference/COMPLETED.json')
        need(reference == model['results'][name], 'Saved original report changed')
        need(reference['maximum_numeric_error'] == 0 and reference['source_worker_loaded'] is False and reference['stock_namespace_modified'] is False, 'Independent original scope changed')
        need(all(c['identical'] is True for c in reference['comparisons']), 'Original complete object comparison failed')
        comparisons += len(reference['comparisons'])
        capture = prefix + '-capture/'
        need(j(capture + 'analysis-status.json')['results_admitted'] is True, 'Positive capture is incomplete')
        report = j(capture + 'worker/report.json')
        need(report['source_function_guards'] == 24 and report['results_admitted'] is True, 'Original body/formal guards differ')
        seal = j(capture + 'capture-files.json')
        need(seal['authentication'] is False, 'Capture seal is not authentication')
        names = {path[len(capture):] for path in files if path.startswith(capture)} - {'capture-files.json'}
        need(names == set(seal['files']), 'Positive capture file population changed')
        for path, digest in seal['files'].items():
            need(pin(files[capture + safe(path)])['sha256'] == digest, 'Positive capture seal differs')
        original_messages = ''.join(message + '\n' for message in reference['messages'])
        actual_messages = files[capture + 'worker/messages.txt'].decode('utf-8').replace('\r\n', '\n')
        need(actual_messages == original_messages, 'Original messages changed')
        original_warnings = ''.join(warning + '\n' for warning in reference['warnings'])
        actual_warnings = files[capture + 'worker/warnings.txt'].decode('utf-8').replace('\r\n', '\n')
        need(not original_warnings or actual_warnings.endswith(original_warnings), 'Original warnings were lost or changed')
        conditions += 1
    need(comparisons == 338, 'Complete object comparison total differs')
    need(len(model['negative']) == 2, 'Actual nonconvergence controls differ')
    for control in model['negative']:
        name = control['name']
        reference = j('final/' + name + '-reference/COMPLETED.json')
        need(reference == control['result'] and reference['maximum_iterations'] == 251 and reference['failed_flags_reported'] == 0, 'Real original exhaustion evidence differs')
        need(reference['original_null_identical'] and reference['original_full_fit_identical'] and reference['previous_stock_test_identical'] and reference['original_stock_test_table_finite'], 'Independent exhaustion comparison failed')
        need(reference['current_result_admitted'] is False, 'Exhausted source model was admitted')
        need(j('final/' + name + '.PROCESS.json')['exit_code'] == 2, 'Exhausted original request was not refused')
        need(j('final/' + name + '-capture/analysis-status.json')['results_admitted'] is False, 'Exhausted capture admits a result')
    runtime = j('worker-controls/COMPLETED.json')
    need(runtime['status'] == 'PASS_OWNED_PROCESS_TIMEOUT_OUTPUT_CAP_AND_EXACT_VERSION_SOURCE_INTEGRITY_REFUSALS', 'Worker controls failed')
    for name in ('timeout', 'cap'):
        control = runtime['controls'][name]
        need(control['results_admitted'] is False and len(control['cleanup']) >= 2, 'Process cleanup scope differs')
        need(all(not p['alive_after_cleanup'] for p in control['cleanup']) and not any(control['independent_OS_liveness'].values()), 'Owned process remains alive')
        error = j('worker-controls/' + name + '-capture/manifest.json')['error']
        need(('timed out' if name == 'timeout' else 'maximum_output_bytes') in error, 'Wrong process refusal cause')
    corrupt = runtime['controls']['exact-version-corrupt-body']
    need(corrupt['original_loader_unchanged'] and corrupt['no_fit_executed'] and corrupt['results_admitted'] is False, 'Source integrity control differs')
    need('source integrity failure' in corrupt['error'], 'Wrong source integrity refusal')
    totals = {}
    for host in ('windows21', 'windows25', 'linux21'):
        report = j('tests/COMPLETED.json')['results'][host]
        need(report == dict(tests=55, failures=0, errors=0, skipped=0, PATH_empty=True, R_HOME_missing=True), 'Host test scope differs: ' + host)
        total = [0, 0, 0, 0]
        for path, raw in files.items():
            if path.startswith('tests/reports-' + host + '/TEST-') and path.endswith('.xml'):
                suite = ET.fromstring(raw)
                for i, key in enumerate(('tests', 'failures', 'errors', 'skipped')):
                    total[i] += int(suite.attrib[key])
        need(total == [55, 0, 0, 0], 'Saved XML tests failed or changed: ' + host)
        totals[host] = total[0]
    cross = j('cross-host/COMPLETED.json')
    need(cross['cases'] == 33 and cross['receiving_JVMs'] == 2 and cross['refitting_performed'] is False, 'Cross-host scope differs')
    for runs in cross['results'].values():
        need(len(runs) == 33 and all(r['exit_code'] == 0 and r['original_R_not_run'] for r in runs), 'Cross-host readback failed')
    example = j('example/COMPLETED.json')
    need(all(example[key] for key in ('results_exactly_equal', 'complete_original_RDS_exactly_equal', 'nested_replay_refused', 'Windows_junction_refused', 'Linux_symlink_refused', 'parent_unchanged', 'public_NEW_paths_unconsumed', 'Linux_control_archived_before_exit')), 'Example/replay/parent controls failed')
    need(j('example/PARENT-BEFORE.json') == j('example/PARENT-AFTER.json'), 'Example parent changed')
    with tarfile.open(fileobj=io.BytesIO(files['example/linux-alias-control.tar']), mode='r:') as tar:
        raw = tar.extractfile('control/COMPLETED.json').read()
        need(json.loads(raw)['status'] == 'PASS_REAL_LINUX_SYMLINK_REPLAY_REFUSAL_AND_PARENT_PRESERVATION', 'Actual Linux alias control failed')
    source = json.loads((ROOT / 'src/main/resources/org/jlinalg/omics/edger-workflow/SOURCE-PROFILE.json').read_bytes())
    need(len(source['original_files']) == 425, 'Original archive source population differs')
    with zipfile.ZipFile(ROOT / 'src/main/resources/org/jlinalg/omics/edger-workflow/source.zip') as z:
        need(set(z.namelist()) == set(source['original_files']) and len(z.namelist()) == 425, 'Original source ZIP population differs')
        for path, expected in source['original_files'].items():
            need(pin(z.read(path)) == expected, 'Original source changed: ' + path)
    predecessor = j('predecessor/COMPLETED.json')
    need(predecessor['canonical_files_unchanged'] == 15 and predecessor['manifest_entries'] == 820 and predecessor['previous_native_reader_passed'], 'Predecessor preservation failed')
    need(j('predecessor/CANONICAL-BEFORE.json') == j('predecessor/CANONICAL-AFTER.json'), 'Checkpoint canonical files changed')
    return dict(status=STATUS, original_profiles=3, original_archive_files=425, normal_original_compositions=33,
                exact_complete_object_comparisons=338, original_conditions_retained=conditions, maximum_numeric_error=0,
                real_old_edition_exhaustion_refusals=2, current_same_input_convergence_preserved=True,
                tests_per_JVM=55, JVMs=3, R_free_cross_host_captures=33, receiving_JVMs=2,
                actual_replay_parent_guards=3, owned_process_controls=2, exact_version_source_integrity_refusals=1,
                previous_producer_members_unchanged=23471, new_producer_members=14, canonical_files_unchanged_at_checkpoint=15,
                manifest_entries_at_checkpoint=820, new_catalog_credit=0, independent_R_refitting_performed_by_reader=False,
                binary_RDS_parsed_by_reader=False, authentication=False, whole_tool_or_paper_qualification=False)


def verify(folder=PACKET, jar=None):
    q, files = read_packet(folder)
    result = audit(q, files)
    if jar is not None:
        producer = json.loads(files['final/PRODUCER.json'])
        expected = dict(producer['preceding_members'], **producer['additions'])
        with zipfile.ZipFile(jar) as z:
            need(len(z.namelist()) == len(expected) and set(z.namelist()) == set(expected), 'Optional exact producer member population differs')
            for name, wanted in expected.items():
                need(pin(z.read(name)) == wanted, 'Optional exact producer member differs: ' + name)
        result['optional_exact_producer_members_checked'] = len(expected)
    return result, files


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', required=True, type=Path, help='NEW output directory, outside the saved proof folder')
    parser.add_argument('--jar', type=Path, help='Optional exact qualified normal producer; no JVM execution')
    parser.add_argument('--materialize', action='store_true', help='Also reconstruct all saved logical evidence files under NEW_OUT/evidence')
    args = parser.parse_args()
    output = args.out.resolve()
    try:
        need(not output.exists() and not output.is_relative_to(PACKET.resolve()), 'Preserve existing/proof paths; choose NEW output outside the saved proof folder')
        result, files = verify(jar=args.jar)
        output.mkdir(parents=True, exist_ok=False)
        if args.materialize:
            for name, raw in files.items():
                target = output / 'evidence' / safe(name)
                target.parent.mkdir(parents=True, exist_ok=True)
                with target.open('xb') as stream:
                    stream.write(raw)
            result['materialized_files'] = len(files)
        (output / 'READBACK.json').write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
        (output / 'WARNINGS.md').write_text('# Saved edgeR proof readback\n\nNo R/Git/JVM or model refitting was used. This validates saved scoped evidence and integrity, not authentication, binary RDS contents, whole-tool parity or biological findings. Full old 3.32.1/3.36.0 packages and historical author hosts remain outside this model deployment. Read the method guide and checkpoint.\n', encoding='utf-8')
        print(json.dumps(result, indent=2))
        return 0
    except FileNotFoundError as error:
        print('SKIP3: required saved source/proof/producer is missing; dependent readback skipped. Restore the named exact file and retry NEW output: ' + str(error), file=sys.stderr)
        return 3
    except (Invalid, KeyError, ValueError, TypeError, OSError, zipfile.BadZipFile, ET.ParseError, tarfile.TarError) as error:
        print('FAIL2: no valid saved readback. Preserve originals and correct the named source, archive, XML, schema, integrity or output issue; retry NEW output. No hashes were refreshed: ' + str(error), file=sys.stderr)
        return 2


if __name__ == '__main__':
    sys.exit(main())
