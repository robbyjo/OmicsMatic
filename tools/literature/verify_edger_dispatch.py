"""Read saved global edgeR qualification; no R/Git/JVM execution or RDS parsing."""
from pathlib import Path, PurePosixPath
import argparse
import hashlib
import io
import json
import math
import sys
import tarfile
import zipfile
from edger_dispatch_transition import BEFORE, DISPATCH, dispatcher_before
from edger_catalog.primary import validate, require
from edger_catalog.coverage import build
from verify_edger_models import verify as verify_models
from verify_edger_normalization import verify as verify_native

ROOT = Path(__file__).resolve().parents[2]
PACKET = ROOT / 'docs/literature/edger-dispatch-checks'
STATUS = 'PASS_SAVED_GLOBAL_EDGER_ORIGINAL_NATIVE_SOURCE_COUNTERPARTS_AND_PARENT_GUARDS'
OWN = ['tools/literature/' + n for n in ['edger_dispatch_transition.py', 'edger_role_crosswalk.py',
    'verify_edger_dispatch.py', 'edger_catalog/__init__.py', 'edger_catalog/primary.py',
    'edger_catalog/coverage.py', 'edger_catalog/test_coverage.py']]
OWN += ['docs/literature/edger-role-crosswalk/' + n for n in ['.gitattributes', 'PRIMARY.json',
    'COVERAGE.json', 'CONTROLS.json', 'README.md']]
OWN += ['docs/literature/edger-dispatch-checks/README.md',
    'docs/literature/edger-admission-handover/CHECKPOINT.json',
    'docs/literature/edger-admission-handover/dispatch.patch']


def pin(raw):
    return dict(bytes=len(raw), sha256=hashlib.sha256(raw).hexdigest())


def safe(name):
    require(isinstance(name, str) and bool(name) and '\\' not in name and ':' not in name,
        'Unsafe saved evidence path')
    p = PurePosixPath(name)
    require(not p.is_absolute() and all(s not in ['', '.', '..'] for s in p.parts) and str(p) == name,
        'Unsafe saved evidence path')
    return name


def read_packet(folder=PACKET):
    q = json.loads((folder / 'QUALIFICATION.json').read_bytes())
    require(q['schema_version'] == 1 and q['status'] == STATUS, 'Different global edgeR proof schema/status')
    require(set(q['source_code']) == set(OWN), 'Different qualified control-source population')
    for name, wanted in q['source_code'].items():
        require(pin((ROOT / safe(name)).read_bytes()) == wanted, 'Changed qualified source: ' + name)
    require(q['preceding_model_qualification'] == pin((ROOT / 'docs/literature/edger-model-checks/QUALIFICATION.json').read_bytes())
        and q['preceding_native_qualification'] == pin((ROOT / 'docs/literature/edger-normalization-checks/QUALIFICATION.json').read_bytes()),
        'Different preceding edgeR qualification identities')
    raw = (ROOT / DISPATCH).read_bytes()
    require(q['dispatcher_before'] == BEFORE, 'Different original dispatcher')
    installed = pin(raw) == q['dispatcher_after']
    require((pin(dispatcher_before(raw)) if installed else pin(raw)) == BEFORE,
        'Dispatcher differs from the exact original or six-line proposed state')
    previous_result, previous = verify_models()
    verify_native()
    old_blobs = {pin(data)['sha256']: data for data in previous.values()}
    archive = (folder / 'evidence.zip').read_bytes()
    require(pin(archive) == q['archive'], 'Changed global edgeR evidence archive')
    files = {}
    with zipfile.ZipFile(io.BytesIO(archive)) as z:
        names = z.namelist()
        require(len(names) == len(set(names)), 'Duplicate proof ZIP members')
        index = json.loads(z.read('index.json'))
        require(index['schema_version'] == 1 and len(index['files']) == q['logical_files'] <= 50000,
            'Different logical evidence population')
        new_names = set()
        for name, row in index['files'].items():
            safe(name)
            require(set(row) == {'bytes', 'sha256', 'provider'} and type(row['bytes']) is int
                and 0 <= row['bytes'] <= 100000000 and len(row['sha256']) == 64
                and all(c in '0123456789abcdef' for c in row['sha256']), 'Invalid proof blob declaration')
            if row['provider'] == 'previous-edgeR-model-proof':
                require(row['sha256'] in old_blobs, 'Missing exact preceding model-proof blob')
                data = old_blobs[row['sha256']]
            else:
                require(row['provider'] == 'this-proof', 'Unknown proof blob provider')
                blob = 'blobs/' + row['sha256']
                new_names.add(blob)
                data = z.read(blob)
            require(pin(data) == {k: row[k] for k in ['bytes', 'sha256']}, 'Changed logical proof file: ' + name)
            files[name] = data
        require(set(names) == {'index.json'} | new_names, 'Extra/missing proof archive member')
        require(len(new_names) == q['new_unique_blobs'], 'Different content-addressed blob count')
    return q, files, previous, installed


def audit(q, files, previous, installed):
    def j(name):
        return json.loads(files[name])
    def capture(prefix, native=False):
        members = {n.removeprefix(prefix): raw for n, raw in files.items() if n.startswith(prefix)}
        seal = json.loads(members['capture-files.json'])
        inventory = seal if native else seal['files']
        require(set(members) == set(inventory) | {'capture-files.json'}, 'Capture has extra/missing members')
        for name, wanted in inventory.items():
            safe(name)
            require((pin(members[name]) if native else hashlib.sha256(members[name]).hexdigest()) == wanted,
                'Changed saved capture member: ' + name)
        return members
    original = j('originals/COMPLETED.json')
    require(original['model_compositions'] == len(original['cases']) == 33
        and original['exact_complete_object_comparisons'] == 338 and original['maximum_model_numeric_error'] == 0,
        'Different actual original global model coverage')
    compared = 0
    for case in original['cases']:
        name = case['name']
        members = capture('originals/' + name + '-capture/')
        require(json.loads(members['analysis-status.json'])['results_admitted'] is True
            and json.loads(members['manifest.json'])['process_exit_code'] == 0, 'Noncomplete global model admitted')
        ref = j('originals/' + name + '-reference/COMPLETED.json')
        require(ref == original['results'][name] and ref['maximum_numeric_error'] == 0
            and ref['source_worker_loaded'] is False and ref['stock_namespace_modified'] is False,
            'Independent stock API reference changed')
        require(all(c['identical'] is True for c in ref['comparisons']), 'Stock complete model comparison failed')
        require('originals/' + name + '-reference/original-reference.rds' in files,
            'Original stock model objects are not retained')
        compared += len(ref['comparisons'])
    require(compared == 338, 'Different exact complete-object comparison count')
    for case in original['negative']:
        members = capture('originals/' + case['name'] + '-capture/')
        require(json.loads(members['analysis-status.json'])['results_admitted'] is False,
            'Failed exhausted fit admitted a complete result')
        require('iteration budget' in json.loads(members['worker/report.json'])['error'],
            'Real source exhaustion counterexample missing')
        require(j('originals/' + case['name'] + '-reference/COMPLETED.json') == case['result'],
            'Independent source-exhaustion reference changed')
    require(len(original['negative']) == 2, 'Different real exhaustion population')
    parent = capture('originals/4.10.7-QLF-adjusted-auto-capture/')
    replay = capture('originals/model-replay/')
    require(parent['worker/objects.rds'] == replay['worker/objects.rds'], 'Original replay RDS bytes differ')
    require({n: d for n, d in parent.items() if n.startswith('results/')}
        == {n: d for n, d in replay.items() if n.startswith('results/')}, 'Original replay results differ')
    for label in ['TMM', 'TMMwsp']:
        prefix = 'originals/native-' + label + '/'
        members = capture(prefix, native=True)
        ref = j('originals/native-original-reference/' + label + '.json')
        rows = [line.split('\t') for line in members['expression.tsv'].decode().splitlines()]
        require(rows[0][1:] == ref['samples'] and [r[0] for r in rows[1:]] == ref['features'], 'Native literal IDs differ')
        require(len(rows) - 1 == len(ref['values']), 'Different native dimensions')
        for row, wanted in zip(rows[1:], ref['values'], strict=True):
            require(len(row) - 1 == len(wanted), 'Different native row width')
            for x, y in zip(row[1:], wanted, strict=True):
                actual, expected = float(x), float(y)
                require(math.isfinite(actual) and math.isfinite(expected)
                    and abs(actual - expected) <= 4e-13 * max(1, abs(expected)), 'Native expression differs from original')
        factors = [float(r.split('\t')[2]) for r in members['normalization.tsv'].decode().splitlines()[1:]]
        require(len(factors) == len(ref['factors']) and all(abs(x - float(y)) <= 4e-13 * max(1, abs(float(y)))
            for x, y in zip(factors, ref['factors'], strict=True)), 'Native factors differ from original')
    require(capture('originals/native-replay/', native=True)['expression.tsv']
        == files['originals/native-TMM/expression.tsv'], 'Native replay differs')
    for prefix in ['originals/', 'receivers/']:
        for name in files:
            if name.startswith(prefix) and name.endswith('.PROCESS.json'):
                row = j(name)
                require(row['exit_code'] == row['expected'], 'Unexpected actual process disposition: ' + name)
    receiver = j('receivers/COMPLETED.json')
    require(receiver['bundles_per_JVM'] == 40 and receiver['JVMs'] == 3
        and receiver['model_complete_captures'] == 34 and receiver['native_complete_captures'] == 3
        and receiver['failed_or_skipped_captures_refused'] == 3
        and receiver['nested_guards'] == 6 and receiver['Windows_junction_guards'] == 2
        and receiver['Linux_symlink_guards'] == 2 and receiver['R_absent_from_PATH'] is True,
        'Different global receiver/alias scope')
    require(set(receiver['results']) == {'windows21', 'windows25', 'linux21'}
        and all(len(rows) == 42 and all(r['exit_code'] == r['expected'] for r in rows)
            for rows in receiver['results'].values()), 'Actual receiving JVM calls differ')
    require(j('receivers/PARENTS-BEFORE.json') == j('receivers/PARENTS-AFTER.json'), 'Replay guard parents changed')
    with tarfile.open(fileobj=io.BytesIO(files['receivers/linux-aliases.tar']), mode='r:') as tar:
        stream = tar.extractfile('controls/COMPLETED.json')
        require(stream is not None, 'Missing archived Linux alias receipt')
        aliases = json.loads(stream.read(200000))
        require(len(aliases['results']) == 2 and all(r['exit_code'] == 2 and r['actual_symlink']
            and r['parent_unchanged'] for r in aliases['results']), 'Real Linux alias guards differ')
    require(j('originals/PRIVATE-RUNTIME-BEFORE.json') == j('originals/PRIVATE-RUNTIME-AFTER.json')
        and len(j('originals/PRIVATE-RUNTIME-AFTER.json')) == 1517, 'Shared original runtime changed')
    require(j('originals/PRIOR-INPUTS-BEFORE.json') == j('originals/PRIOR-INPUTS-AFTER.json')
        and len(j('originals/PRIOR-INPUTS-AFTER.json')) == 43, 'Original preceding input files changed')
    primary = validate(j('source-roles/PRIMARY.json'))
    require(build(primary) == j('source-roles/COVERAGE.json'), 'Source-role counterpart decisions differ')
    controls = j('role-controls/COMPLETED.json')
    require(controls['source_audit_fresh_and_exact'] and set(controls['results']) == {'windows', 'linux'}
        and all(r['tests'] == 15 and r['exit_code'] == 0 for r in controls['results'].values()), 'Source role controls differ')
    old = json.loads(previous['final/PRODUCER.json'])
    expected = dict(old['preceding_members'], **old['additions'])
    members = j('producer/MEMBERS.json')
    require(len(expected) == len(members) == 23485 and set(expected) == set(members), 'Different producer member population')
    changed = {n for n in members if members[n] != expected[n]}
    require(changed == {'org/jlinalg/cli/JLinAlgCli.class'}, 'Producer differs beyond exact root dispatcher')
    checkpoint = json.loads((ROOT / 'docs/literature/edger-admission-handover/CHECKPOINT.json').read_bytes())
    require(members['org/jlinalg/cli/JLinAlgCli.class']
        == checkpoint['changed_decoded_members']['org/jlinalg/cli/JLinAlgCli.class']['after'], 'Different qualified dispatcher class')
    require(original['producer'] == q['producer'] == checkpoint['producer'], 'Different original global producer')
    return dict(status=STATUS, original_model_compositions=33, exact_complete_object_comparisons=338,
        native_compositions=2, real_source_exhaustion_refusals=2, exact_unavailable_profile_skips=5,
        missing_exact_runtime_skips=1, complete_captures_per_JVM=37, failed_or_skipped_captures_refused_per_JVM=3,
        JVMs=3, actual_parent_alias_guards=10, original_RDS_replay_exact=True,
        preceding_decoded_members_unchanged=23484, changed_dispatcher_classes=1,
        source_roles=17, literal_function_mentions=len(primary['explicit_functions']),
        source_role_controls_per_host=15, source_role_control_hosts=2,
        global_dispatcher_currently_installed=installed, canonical_admission_complete=False,
        current_canonical_credit_added_by_reader=0, fresh_R_models_run_by_reader=False,
        binary_RDS_parsed=False, authentication=False, whole_tool_or_paper_reproduced=False)


def verify(folder=PACKET, jar=None):
    q, files, previous, installed = read_packet(folder)
    result = audit(q, files, previous, installed)
    if jar is not None:
        require(pin(Path(jar).read_bytes()) == q['producer'], 'Different optional exact global producer')
        with zipfile.ZipFile(jar) as z:
            expected = json.loads(files['producer/MEMBERS.json'])
            require(len(z.namelist()) == len(expected) and set(z.namelist()) == set(expected), 'Different optional JAR population')
            for name, wanted in expected.items():
                require(pin(z.read(name)) == wanted, 'Different optional JAR member: ' + name)
        result['optional_exact_producer_members_checked'] = len(expected)
    return result, files


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', required=True, type=Path)
    parser.add_argument('--packet', type=Path, default=PACKET)
    parser.add_argument('--jar', type=Path)
    parser.add_argument('--materialize', action='store_true')
    args = parser.parse_args()
    try:
        require(__debug__ and sys.version_info >= (3, 11), 'Use Python3.11+ without -O')
        require(not args.out.exists() and not args.out.resolve().is_relative_to(args.packet.resolve()), 'Choose NEW output outside the proof packet')
        result, files = verify(args.packet, args.jar)
        args.out.mkdir(parents=True, exist_ok=False)
        if args.materialize:
            for name, raw in files.items():
                p = args.out / 'evidence' / safe(name)
                p.parent.mkdir(parents=True, exist_ok=True)
                with p.open('xb') as stream:
                    stream.write(raw)
            result['materialized_files'] = len(files)
        (args.out / 'READBACK.json').write_bytes((json.dumps(result, indent=2) + '\n').encode())
        print(json.dumps(result, indent=2))
        return 0
    except Exception as error:
        code = 3 if isinstance(error, (FileNotFoundError, PermissionError)) else 2
        print(('SKIP3: named exact prerequisite missing; dependent readback skipped. ' if code == 3 else 'FAIL2: no valid saved readback. ')
            + str(error) + '\nRestore the named permitted source/proof/producer or retain the skip. Preserve failures, correct integrity/schema/output causes and retry NEW output; do not refresh old hashes.', file=sys.stderr)
        return code


if __name__ == '__main__':
    raise SystemExit(main())
