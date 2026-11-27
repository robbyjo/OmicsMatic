"""Verify the new range qualification and preserve the published predecessor seals.

Default validation checks source/evidence only. --execute-replays runs each
current saved capture again with its exact producing JAR on the bound Linux host.
"""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import sys
import zipfile

from rfmix_range_derivation import FOLDER, REPLACEMENTS, derive
from verify_rfmix_workflows import pin, read, require, safe

ROOT = Path(__file__).resolve().parents[2]
PREDECESSOR = 'docs/literature/rfmix-validation'
ADDITIONS = {
    'src/main/java/org/jlinalg/cli/RfmixRange.java',
    'src/test/java/org/jlinalg/cli/RfmixRangeTest.java',
    'tools/literature/rfmix_range_derivation.py',
    'tools/literature/verify_rfmix_range_workflows.py',
    FOLDER + '/README.md',
}


def identity(raw):
    return dict(bytes=len(raw), sha256=hashlib.sha256(raw).hexdigest())


def archive(root, folder, q):
    path = root / folder / 'source-context.zip'
    require(pin(path) == q['source_context_archive'], 'Retained source archive changed: ' + folder)
    with zipfile.ZipFile(path) as stream:
        require(len(stream.namelist()) == len(q['source_context']) and set(stream.namelist()) == set(q['source_context']), 'Archived source population differs')
        result = {}
        for name, expected in q['source_context'].items():
            safe(name)
            raw = stream.read(name)
            require(identity(raw) == expected, 'Archived source bytes changed: ' + name)
            result[name] = raw
    return result


def source_record(root):
    root = Path(root)
    q = read(root / FOLDER / 'QUALIFICATION.json')
    require(q['status'] == 'PASS_ORIGINAL_RFMIX_PLAIN_DECIMAL_RANGES_AND_FULL_WORKFLOW_REGRESSION'
            and q['new_family_credit'] == 0 and q['whole_goal_complete'] is False
            and q['whole_package_qualified'] is False, 'Range qualification scope changed')
    require(pin(root / PREDECESSOR / 'QUALIFICATION.json') == q['predecessor_qualification'], 'Published predecessor qualification changed')
    old = read(root / PREDECESSOR / 'QUALIFICATION.json')
    require(old['status'] == 'PASS_SCOPED_ORIGINAL_RFMIX_MSP_FB_Q_WORKFLOW' and old['new_upstream_family_credit'] == 1
            and old['new_native_family_credit'] == 0 and old['whole_goal_complete'] is False, 'Predecessor scope changed')
    require(old['windows_native_tests'] == old['linux_native_tests'] == 36 and len(old['models']) == 5
            and old['fresh_capture_cases'] == old['fresh_replay_cases'] == 8
            and old['sis_dependent_analysis'] == 'SKIPPED_ORIGINAL_WRITER_FINAL_ITERATION_UNQUALIFIED', 'Historical test/case scope changed')
    before = archive(root, PREDECESSOR, old)
    after = archive(root, FOLDER, q)
    require(set(after) == set(before) | ADDITIONS, 'Successor context adds or omits unexpected sources')
    changes = set(REPLACEMENTS) | {'.gitattributes'}
    require(set(q['exact_source_derivations']) == changes, 'Unreviewed predecessor edits')
    for name, raw in before.items():
        expected = derive(name, raw) if name in changes else raw
        require(after[name] == expected, 'Source differs from the exact reviewed range/notice/forward-verifier change: ' + name)
        if name in changes:
            require(q['exact_source_derivations'][name] == dict(before=identity(raw), after=identity(expected)), 'Source derivation identity differs')
    for name, raw in after.items():
        require(pin(root / name) == identity(raw), 'Current source differs; restore the archived matching context in a NEW directory, never refresh old seals: ' + name)
    profile = read(root / 'src/main/resources/org/jlinalg/omics/rfmix/profiles.json')['profiles']['RFMIX-2.03-r0-09599c19']
    upstream = read(root / 'docs/literature/rfmix-source-audit/UPSTREAM.json')
    require(profile['commit'] == q['source_commit'] == upstream['commit'] and profile['source_files'] == upstream['files']
            and len(profile['source_files']) == 38, 'Original source profile differs')
    require(pin(root / 'docs/literature/rfmix-native-audit/QUALIFICATION.json') == old['baseline_native_qualification'], 'Historical native baseline changed')
    require(q['Windows_native_tests'] == q['Linux_native_tests'] == 46 and q['native_test_skips'] == 0
            and q['full_capture_cases'] == q['full_fresh_replay_cases'] == 13
            and len(q['range_cases']) == 5 and len(q['core_model_cases']) == 5
            and q['sis_dependent_analysis'] == 'SKIPPED_ORIGINAL_WRITER_FINAL_ITERATION_UNQUALIFIED', 'Current complete test/case populations differ')
    require({x['case']: x['original_files'] for x in q['core_model_cases']}
            == {x['case']: x['original_files'] for x in old['models']}, 'Original five-model output parity changed')
    range_names = {'small-start', 'fractional-endpoints', 'subnormal-start', 'negative-zero-start', 'short-both-exponents'}
    require({x['case'] for x in q['range_cases']} == range_names and len(q['captures']) == 13
            and len({x['case'] for x in q['captures']}) == 13
            and {x['case'] for x in q['captures']} == range_names | {x['case'] for x in old['models']} | {'gzip', 'bcf', 'selection'}, 'A current case was omitted or duplicated')
    return dict(current=q, predecessor=old)


def private_evidence(root, private):
    records = source_record(root)
    q = records['current']
    private = private.resolve(strict=True)
    require(pin(private / 'QUALIFICATION-PROOF.json') == q['private_proof'], 'Current private proof changed')
    proof = read(private / 'QUALIFICATION-PROOF.json')
    for name in proof['complete_roots']:
        folder = private / safe(name)
        expected = {n for n in proof['retained_files'] if n.startswith(name + '/')}
        actual = {p.relative_to(private).as_posix() for p in folder.rglob('*') if p.is_file() or p.is_symlink()}
        require(expected == actual, 'Complete current evidence population changed: ' + name)
    for name, expected in proof['retained_files'].items():
        path = private / safe(name)
        require(path.resolve(strict=True).is_relative_to(private) and pin(path) == expected, 'Current retained evidence differs: ' + name)
    require(proof['producer'] == q['producer'], 'Current producer differs')
    core = read(private / 'qualification-Linux-1/COMPLETED.json')
    ranges = read(private / 'ranges-Linux-1/COMPLETED.json')
    require(core['status'] == 'PASS_ACTUAL_ORIGINAL_RFMIX_CAPTURE_FRESH_REPLAY_AND_REFUSALS' and core['producer'] == q['producer']
            and ranges['status'] == 'PASS_FIVE_ORIGINAL_RESTRICTED_RANGE_CAPTURES_REPLAYS_AND_INDEPENDENT_ORACLES'
            and ranges['producer'] == q['producer'] and ranges['cases'] == q['range_cases'], 'Actual current execution evidence differs')
    require(ranges['predecessor_bug_reproduced_with_exit2_no_complete_result'] is True, 'Original failure reproduction missing')
    require(len(q['captures']) == 13, 'Current capture population differs')
    for item in q['captures']:
        capture, replay = private / safe(item['capture']), private / safe(item['replay'])
        require(read(capture / 'COMPLETED.json')['status'] == 'COMPLETE_RFMIX_MSP_FB_Q_WORKFLOW_SIS_UNQUALIFIED'
                and read(replay / 'COMPLETED.json')['status'] == 'PASS_COMPLETE_RFMIX_BINARY_AND_NUMERICAL_REPLAY', 'Missing complete current capture/replay')
        require(read(capture / 'canonical.json') == read(replay / 'canonical.json') == item['canonical'], 'Complete original output identities differ')
    return q, dict(status='PASS_CURRENT_RFMIX_RANGE_RETAINED_EVIDENCE_ONLY_NO_NEW_INFERENCE',
                   retained_files=len(proof['retained_files']), original_captures=13, fresh_replays=13,
                   restricted_range_cases=5, direct_original_range_oracles=5, new_family_credit=0, whole_goal_complete=False)


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
        q = source_record(root)['current']
        result = dict(status='PASS_CURRENT_RFMIX_RANGE_SOURCE_CONTEXT_ONLY_NO_RUNTIME_OR_NEW_INFERENCE', new_family_credit=0, whole_goal_complete=False)
        if args.private:
            q, result = private_evidence(root, args.private)
        if args.xml_cache or args.upstream_source:
            require(args.xml_cache and args.upstream_source, 'Supply both original XML cache and exact original source')
            from rfmix_source_audit import audit
            result['primary_source_review'] = audit(root, args.xml_cache.absolute(), args.upstream_source.absolute())
        if args.execute_replays:
            require(args.private and args.scratch and args.scratch.is_absolute() and not args.scratch.exists(), 'Fresh replay requires current private evidence and NEW absolute scratch root')
            private = args.private.resolve(strict=True)
            scratch = args.scratch.absolute()
            require(not scratch.is_relative_to(private) and not private.is_relative_to(scratch), 'Replay scratch must be separate from retained evidence')
            scratch.parent.resolve(strict=True)
            scratch.mkdir()
            jar = private / safe(q['producer_path'])
            require(pin(jar) == q['producer'], 'Restore the exact current producing JAR')
            for item in q['captures']:
                name = item['case']
                require(name and all(c.isalnum() or c in '-_' for c in name), 'Unsafe case label')
                command = [args.java, '-Xmx2g', '-jar', str(jar), 'rfmix-tools', '--verify', str(private / safe(item['capture'])), '--scratch', str(scratch / name)]
                with (scratch / (name + '-stdout')).open('xb') as out, (scratch / (name + '-stderr')).open('xb') as err:
                    process = subprocess.run(command, stdin=subprocess.DEVNULL, stdout=out, stderr=err)
                if process.returncode == 3:
                    raise FileNotFoundError('Replay prerequisite unavailable. Inspect ' + str(scratch / (name + '-stderr')))
                require(process.returncode == 0, 'Original replay failed; preserve ' + str(scratch))
            result['status'] = 'PASS_THIRTEEN_FRESH_CURRENT_ORIGINAL_RFMIX_REPLAYS'
        print(json.dumps(result, sort_keys=True))
        return 0
    except Exception as error:
        code = 3 if isinstance(error, (FileNotFoundError, PermissionError)) else 2
        print(('SKIP3 DEPENDENT RFMIX RANGE CHECK SKIPPED: ' if code == 3 else 'FAIL2 NO COMPLETE RFMIX RANGE CHECK: ')
              + str(error) + '. Preserve attempts, restore exact permitted source/evidence/runtime/producer and retry NEW paths. '
              'Never refresh predecessor seals. SIS and unavailable legacy1.5.4 analyses remain skipped.', file=sys.stderr)
        return code


if __name__ == '__main__':
    raise SystemExit(main())
