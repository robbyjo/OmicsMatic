"""Check source/saved-summary integrity, optionally private evidence and exact JAR.

This reader does not perform a new scientific fit. Use actual Java VERIFY for that.
"""
from pathlib import Path
import argparse
import hashlib
import json
import zipfile


def pin(path):
    digest = hashlib.sha256()
    size = 0
    with Path(path).open('rb') as stream:
        for block in iter(lambda:stream.read(1048576), b''):
            digest.update(block)
            size += len(block)
    return dict(bytes=size, sha256=digest.hexdigest())


def require(condition, message):
    if not condition:
        raise ValueError(message)


def verify(root, jar=None, private_root=None):
    packet = root / 'docs/literature/scenicplus-r2g-checkpoint-20261123'
    status = json.loads((packet / 'STATUS.json').read_bytes())
    require(status['status'] == 'SCOPED_R2G_IMPLEMENTED_MASKED_FAILURE_RETAINED_FULL_SCENICPLUS_UNFINISHED', 'Wrong checkpoint kind')
    require(status['new_family_credit'] == status['new_reviews'] == 0 and not status['whole_goal_complete'], 'Changed admission scope')
    require(status['normal_tests'] == dict(tests=2069, failures=0, errors=0, skipped=0), 'Changed normal-test summary')
    require(status['old_payloads_unchanged'] == 23044 and status['old_payloads_modified'] == 0, 'Changed predecessor boundary')
    require(status['original_successful_comparisons'] == 2 and status['independent_upstream_failure_witnesses'] == 1, 'Changed original comparison scope')
    require(status['actual_modes'] == ['RUN', 'VERIFY', 'REPLAY'] and status['fresh_numerical_tamper_refused'], 'Missing actual protocol summary')
    require(not status['masked_analysis_qualified'] and not status['full_eGRN_qualified'] and not status['global_dispatch_integrated'], 'Expanded qualification claim')
    for name, expected in status['source_files'].items():
        require(not Path(name).is_absolute() and '..' not in Path(name).parts, 'Invalid source path')
        require(pin(root / name) == expected, 'Changed current source: ' + name)
    source = root / 'tools/literature/scenicplus_workflow'
    packaged = root / 'src/main/resources/org/jlinalg/omics/scenicplus-workflow'
    for name in ('worker.py', 'SOURCE-PROFILE.json'):
        require((source / name).read_bytes() == (packaged / name).read_bytes(), 'Caller/profile mirrors differ')
    profile = json.loads((source / 'SOURCE-PROFILE.json').read_bytes())
    require(len(profile['base_distributions']) == 228 and len(profile['loaded_distributions']) == 231, 'Changed origin populations')
    require(len(profile['original_python_sources']) == 41 and len(profile['pinned_git_python_sources']) == 73, 'Changed source member populations')
    if jar is not None:
        require(pin(jar) == status['producer'], 'Restore exact complete producer JAR; do not refresh old hashes')
        with zipfile.ZipFile(jar) as archive:
            for name in ('worker.py', 'SOURCE-PROFILE.json'):
                require(archive.read('org/jlinalg/omics/scenicplus-workflow/' + name) == (source / name).read_bytes(), 'Packaged caller/profile differs')
    if private_root is not None:
        for name, expected in status['private_evidence'].items():
            require(not Path(name).is_absolute() and '..' not in Path(name).parts, 'Invalid evidence path')
            require(pin(private_root / name) == expected, 'Changed/missing PRIVATE evidence: ' + name)
    return dict(status='PASS_SOURCE_AND_SAVED_SUMMARY_INTEGRITY_ONLY', source_files=len(status['source_files']), exact_JAR_checked=jar is not None,
                private_evidence_checked=len(status['private_evidence']) if private_root is not None else 0,
                fresh_scientific_fit_executed=False, tests_rerun=False, new_family_credit=0, new_reviews=0, whole_goal_complete=False)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', type=Path, default=Path(__file__).resolve().parents[2])
    parser.add_argument('--jar', type=Path)
    parser.add_argument('--private-root', type=Path)
    args = parser.parse_args()
    print(json.dumps(verify(args.root, args.jar, args.private_root), indent=2))
