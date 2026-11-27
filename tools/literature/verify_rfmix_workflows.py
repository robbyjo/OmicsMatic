"""Verify the exact RFMix qualification context and retained private evidence.

Default checks are read-only evidence checks, not fresh inference. Use
--execute-replays with a NEW scratch root to invoke actual original replay.
"""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[2]
FOLDER = 'docs/literature/rfmix-validation'


def require(value, message):
    if not value:
        raise ValueError(message)


def read(path):
    return json.loads(Path(path).read_bytes())


def pin(path):
    path = Path(path)
    require(path.is_file() and not path.is_symlink(), 'Required evidence must be a regular unlinked file: ' + str(path))
    before = path.stat()
    digest = hashlib.sha256()
    size = 0
    with path.open('rb') as stream:
        for block in iter(lambda: stream.read(1048576), b''):
            size += len(block)
            digest.update(block)
    after = path.stat()
    require((before.st_size, before.st_mtime_ns, before.st_ino) == (after.st_size, after.st_mtime_ns, after.st_ino)
            and size == before.st_size, 'Evidence changed during hashing: ' + str(path))
    return dict(bytes=size, sha256=digest.hexdigest())


def safe(name):
    p = Path(name)
    require(not p.is_absolute() and '\\' not in name and ':' not in name and all(x not in ('', '.', '..') for x in name.split('/')), 'Unsafe evidence member')
    return p


def source_record(root):
    q = read(root / FOLDER / 'QUALIFICATION.json')
    require(q['status'] == 'PASS_SCOPED_ORIGINAL_RFMIX_MSP_FB_Q_WORKFLOW' and q['whole_goal_complete'] is False
            and q['whole_package_qualified'] is False and q['new_native_family_credit'] == 0
            and q['new_upstream_family_credit'] == 1, 'Qualification scope differs')
    archive = root / FOLDER / 'source-context.zip'
    require(pin(archive) == q['source_context_archive'], 'Retained source context archive changed')
    with zipfile.ZipFile(archive) as stream:
        require(len(stream.namelist()) == len(q['source_context']) and set(stream.namelist()) == set(q['source_context']), 'Source archive population differs')
        for name, expected in q['source_context'].items():
            safe(name)
            raw = stream.read(name)
            require(dict(bytes=len(raw), sha256=hashlib.sha256(raw).hexdigest()) == expected, 'Archived source changed: ' + name)
            require(pin(root / name) == expected, 'Current source differs from qualification. Restore the archived matching context in a NEW private directory; do not refresh seals: ' + name)
    profile = read(root / 'src/main/resources/org/jlinalg/omics/rfmix/profiles.json')['profiles']['RFMIX-2.03-r0-09599c19']
    upstream = read(root / 'docs/literature/rfmix-source-audit/UPSTREAM.json')
    require(profile['commit'] == upstream['commit'] and profile['source_files'] == upstream['files']
            and len(profile['source_files']) == 38, 'Original source/profile population differs')
    require(pin(root / 'docs/literature/rfmix-native-audit/QUALIFICATION.json') == q['baseline_native_qualification'], 'Retained baseline qualification changed')
    require(q['windows_native_tests'] == q['linux_native_tests'] == 36 and len(q['models']) == 5
            and q['fresh_capture_cases'] == q['fresh_replay_cases'] == 8
            and q['sis_dependent_analysis'] == 'SKIPPED_ORIGINAL_WRITER_FINAL_ITERATION_UNQUALIFIED', 'Qualification evidence populations differ')
    return q


def private_evidence(root, private):
    q = source_record(root)
    private = private.resolve(strict=True)
    require(pin(private / 'QUALIFICATION-PROOF.json') == q['private_proof'], 'Private proof changed')
    proof = read(private / 'QUALIFICATION-PROOF.json')
    for name in proof['complete_roots']:
        folder = private / safe(name)
        expected = {n for n in proof['retained_files'] if n.startswith(name + '/')}
        actual = {p.relative_to(private).as_posix() for p in folder.rglob('*') if p.is_file() or p.is_symlink()}
        require(actual == expected, 'Complete retained evidence population differs: ' + name)
    for name, expected in proof['retained_files'].items():
        path = private / safe(name)
        require(path.resolve(strict=True).is_relative_to(private) and pin(path) == expected, 'Retained private evidence differs: ' + name)
    require(proof['producer'] == q['producer'], 'Producing JAR identity differs')
    report = read(private / q['actual_qualification'] / 'COMPLETED.json')
    require(report['status'] == 'PASS_ACTUAL_ORIGINAL_RFMIX_CAPTURE_FRESH_REPLAY_AND_REFUSALS'
            and report['producer'] == q['producer'] and len(report['models']) == 5
            and report['owned_process_ledgers'] == q['owned_process_ledgers']
            and report['refusal_controls'] == q['refusal_controls'], 'Actual workflow proof differs')
    for case in q['case_names']:
        capture = private / q['actual_qualification'] / (case + '-capture')
        replay = private / q['actual_qualification'] / (case + '-replay')
        require(read(capture / 'COMPLETED.json')['status'] == 'COMPLETE_RFMIX_MSP_FB_Q_WORKFLOW_SIS_UNQUALIFIED'
                and read(replay / 'COMPLETED.json')['status'] == 'PASS_COMPLETE_RFMIX_BINARY_AND_NUMERICAL_REPLAY', 'Missing complete actual capture/replay')
        require(read(capture / 'canonical.json') == read(replay / 'canonical.json'), 'Retained original binary outputs differ')
    return q, dict(status='PASS_RETAINED_RFMIX_EVIDENCE_ONLY_NO_FRESH_INFERENCE', retained_files=len(proof['retained_files']),
                   full_original_capture_cases=8, full_fresh_replay_cases=8, producer=q['producer'], whole_goal_complete=False)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', type=Path, default=ROOT)
    parser.add_argument('--private', type=Path)
    parser.add_argument('--xml-cache', type=Path)
    parser.add_argument('--upstream-source', type=Path)
    parser.add_argument('--execute-replays', action='store_true')
    parser.add_argument('--scratch', type=Path)
    parser.add_argument('--java', default='/usr/bin/java')
    args = parser.parse_args()
    try:
        root = args.root.resolve(strict=True)
        q = source_record(root)
        result = dict(status='PASS_ARCHIVED_RFMIX_SOURCE_CONTEXT_ONLY_NO_RUNTIME_OR_NEW_INFERENCE', whole_goal_complete=False)
        if args.private:
            q, result = private_evidence(root, args.private)
        if args.xml_cache or args.upstream_source:
            require(args.xml_cache and args.upstream_source, 'Supply both original XML cache and exact original upstream source')
            from rfmix_source_audit import audit
            result['primary_source_review'] = audit(root, args.xml_cache, args.upstream_source)
        if args.execute_replays:
            require(args.private and args.scratch and not args.scratch.exists() and args.scratch.is_absolute(), 'Fresh execution requires retained private evidence and a NEW absolute scratch root')
            private = args.private.resolve(strict=True)
            scratch = args.scratch.absolute()
            require(not scratch.is_relative_to(private) and not private.is_relative_to(scratch), 'Replay root must be separate from retained evidence')
            scratch.parent.resolve(strict=True)
            scratch.mkdir()
            jar = private / q['producer_path']
            require(pin(jar) == q['producer'], 'Restore exact producing JAR')
            for case in q['case_names']:
                command = [args.java, '-Xmx2g', '-jar', str(jar), 'rfmix-tools', '--verify', str(private / q['actual_qualification'] / (case + '-capture')), '--scratch', str(scratch / case)]
                with (scratch / (case + '-stdout')).open('xb') as out, (scratch / (case + '-stderr')).open('xb') as err:
                    process = subprocess.run(command, stdin=subprocess.DEVNULL, stdout=out, stderr=err)
                if process.returncode == 3:
                    raise FileNotFoundError('Replay prerequisite unavailable. Inspect ' + str(scratch / (case + '-stderr')))
                require(process.returncode == 0, 'Fresh original replay failed; preserve ' + str(scratch))
            result['status'] = 'PASS_EIGHT_FRESH_ORIGINAL_RFMIX_REPLAYS'
        print(json.dumps(result, sort_keys=True))
        return 0
    except Exception as error:
        code = 3 if isinstance(error, (FileNotFoundError, PermissionError)) else 2
        print(('SKIP3 DEPENDENT RFMIX QUALIFICATION CHECK SKIPPED: ' if code == 3 else 'FAIL2 NO COMPLETE RFMIX QUALIFICATION CHECK: ')
              + str(error) + '. Required action: preserve attempts and restore exact permitted evidence, producing JAR, runtime and matching archived source; retry NEW paths. SIS-dependent analysis remains skipped. Do not refresh historical seals.', file=sys.stderr)
        return code


if __name__ == '__main__':
    raise SystemExit(main())
