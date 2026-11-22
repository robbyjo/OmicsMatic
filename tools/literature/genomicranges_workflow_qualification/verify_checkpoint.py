"""Verify saved original comparisons and source bytes; never run Java or R."""
from pathlib import Path, PurePosixPath
import argparse
import datetime
import hashlib
import json
import re
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[3]
PACKET = ROOT / 'docs/literature/genomicranges-qualification'


def require(value, message):
    if not value:
        raise ValueError(message)


def pin(raw):
    return dict(bytes=len(raw), sha256=hashlib.sha256(raw).hexdigest())


def instant_nanoseconds(value):
    require(isinstance(value, str), 'Missing process creation instant')
    match = re.fullmatch(r'(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2})(?:\.(\d{1,9}))?Z', value)
    require(match is not None, 'Unverifiable UTC creation instant')
    whole = datetime.datetime.strptime(match[1], '%Y-%m-%dT%H:%M:%S').replace(tzinfo=datetime.timezone.utc)
    delta = whole - datetime.datetime(1970, 1, 1, tzinfo=datetime.timezone.utc)
    return (delta.days * 86400 + delta.seconds) * 10**9 + int((match[2] or '').ljust(9, '0'))


def verify_owned_manifest(manifest):
    rows = manifest.get('owned_processes', [])
    roots = [r for r in rows if r['pid'] == manifest.get('worker_pid')]
    require(len(roots) == 1 and 'cleanup_error' not in manifest, 'Missing/contradictory owned root')
    started = instant_nanoseconds(roots[0]['start'])
    for row in rows:
        require(row.get('same_identity_alive') is False and instant_nanoseconds(row['start']) >= started,
                'Older or unclosed recorded process candidate')


def verify(actual_jar=None):
    require(__debug__, 'Run Python without -O')
    stamp = json.loads((PACKET / 'STATUS-v2.json').read_bytes())
    for name, expected in stamp['file_pins'].items():
        path = ROOT / name
        require(path.is_file() and not path.is_symlink() and pin(path.read_bytes()) == expected,
                'Changed qualified source/evidence: ' + name)
    q = json.loads((PACKET / 'QUALIFICATION.json').read_bytes())
    require(q['status'] == 'PASS_ORIGINAL_GENOMICRANGES_PREPARED_WORKFLOWS_FOR_ADMISSION', 'Different scope')
    require(q['normal_tests'] == 783 and q['matrix_cases'] == 168 and q['replays'] == 27
            and q['full_RDS_comparisons'] == 159 and q['original_error_comparisons'] == 9,
            'Incomplete original or normal-build evidence')
    require(q['public_resealed_controls'] == 21 and q['runtime_controls'] == 9
            and q['intake_replay_controls'] == 7 and q['fresh_primary_sources'] == 250,
            'Incomplete refusal/source evidence')
    require(not q['whole_tool_qualified'] and not q['whole_paper_reproduced']
            and not q['whole_goal_complete'], 'False broad qualification claim')
    info = json.loads((PACKET / 'EVIDENCE.json').read_bytes())
    archive = PACKET / info['archive']
    require(archive.stat().st_size <= 256 * 1024 * 1024 and pin(archive.read_bytes()) == info['archive_pin'],
            'Changed archive bytes')
    with zipfile.ZipFile(archive) as z:
        entries = z.infolist()
        require(len(entries) <= 100000 and len({x.filename for x in entries}) == len(entries), 'Duplicate/excess ZIP members')
        require(all(x.file_size <= 64 * 1024 * 1024 for x in entries)
                and sum(x.file_size for x in entries) <= 2 * 1024**3, 'Archive exceeds capacity')
        manifest = z.read('manifest.json')
        require(pin(manifest) == info['manifest_pin'], 'Changed archive logical map')
        files = json.loads(manifest)['files']
        for name in files:
            path = PurePosixPath(name)
            require(not path.is_absolute() and '..' not in path.parts and '\\' not in name, 'Unsafe logical path')
        objects = {row['sha256'] for row in files.values()}
        require({x.filename for x in entries} == {'manifest.json'} | {'objects/' + x for x in objects}, 'Unexpected ZIP member')
        cached = {digest: z.read('objects/' + digest) for digest in objects}
        for name, expected in files.items():
            require(pin(cached[expected['sha256']]) == expected, 'Changed logical evidence: ' + name)

        def read(name):
            return cached[files[name]['sha256']]

        require(json.loads(read('QUALIFICATION.json')) == q, 'Archive/public qualification differ')
        checked = 0
        for name in files:
            if name.startswith('matrix/') and name.endswith('/manifest.json'):
                verify_owned_manifest(json.loads(read(name)))
                checked += 1
        require(checked == 195, 'Missing typed ownership capture audit')
        normal = json.loads(read('normal/RESULT.json'))
        require(normal['tests'] == 783 and normal['exit_code'] == normal['failures'] == normal['skipped'] == 0
                and normal['development_jar'] == q['producer'], 'Wrong measured producer/build')
        matrix = json.loads(read('matrix/RESULT.json'))
        require(matrix['all_matrix_checks_passed'] is True and matrix['requests'] == matrix['passed'] == 168
                and not matrix['errors'] and not matrix['failed'], 'Matrix failure')
        original_rows = rds_rows = error_rows = 0
        for profile in ['313', '320', '321']:
            lines = read('matrix/' + profile + '/original-comparisons.tsv').decode().splitlines()
            require(lines[0] == 'case\tfull_RDS_identical\toriginal_error_identical\twarnings_identical', 'Changed comparison schema')
            require(len(lines) == 66, 'Missing profile comparison')
            for line in lines[1:]:
                case, same, error, warnings = line.split('\t')
                require(warnings == 'TRUE' and ((same == 'TRUE') != (error == 'TRUE')), 'Original comparison failed: ' + case)
                original_rows += 1
                rds_rows += same == 'TRUE'
                error_rows += error == 'TRUE'
        require((original_rows, rds_rows, error_rows) == (195, 186, 9), 'Wrong result/error/replay comparison totals')
        rejected = json.loads(read('verifier/RESULT.json'))
        runtime = json.loads(read('runtime/RESULT.json'))
        intake = json.loads(read('intake/RESULT.json'))
        require(rejected['rejected'] == len(rejected['controls']) == 21 and not rejected['accepted_invalid_controls'], 'Accepted invalid capture')
        require(runtime['passed'] == len(runtime['controls']) == 9 and not runtime['failures'], 'Runtime control failed')
        require(intake['passed'] == len(intake['controls']) == 7 and all(r['passed'] for r in intake['controls']), 'Intake/replay control failed')
        ownership = json.loads(read('OWNERSHIP-AUDIT.json'))
        require(not ownership['older_or_unclosed_candidates'] and not ownership['nonterminal_outer_processes'], 'Unresolved ownership observation')
        runtime_files = json.loads(read('RUNTIME-PINS.json'))
    if actual_jar is not None:
        actual_jar = Path(actual_jar)
        require(pin(actual_jar.read_bytes()) == q['producer'], 'Supply unchanged actual producer JAR')
        for name, expected in runtime_files.items():
            path = Path(name)
            require(pin(path.read_bytes()) == expected, 'Private runtime bytes changed: ' + name)
    return dict(status='PASS_SAVED_GENOMICRANGES_SCOPE_AND_BYTES', matrix_cases=168,
                original_RDS_and_replay_comparisons=186, original_errors=9,
                runtime_files=len(runtime_files), actual_runtime_rechecked=actual_jar is not None,
                typed_creation_instant_captures=195, original_operations_rerun=False, whole_goal_complete=False)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jar', type=Path)
    args = parser.parse_args()
    try:
        print(json.dumps(verify(args.jar)))
        return 0
    except Exception as error:
        code = 3 if isinstance(error, (FileNotFoundError, PermissionError)) else 2
        print(('SKIP3: supply the exact named permitted source/runtime file; dependent recheck skipped. '
               if code == 3 else 'FAIL2: source/evidence/integrity mismatch; no admitted recheck. ')
              + str(error) + ' Preserve this evidence; resolve the cause and retry with the declared baseline.', file=sys.stderr)
        return code


if __name__ == '__main__':
    raise SystemExit(main())
