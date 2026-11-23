"""Check source/saved identities only. Java VERIFY performs fresh preparation."""
from pathlib import Path
import argparse
import hashlib
import json
import zipfile


def pin(path):
    digest, size = hashlib.sha256(), 0
    with Path(path).open('rb') as stream:
        for block in iter(lambda:stream.read(1048576), b''):
            digest.update(block)
            size += len(block)
    return dict(bytes=size, sha256=digest.hexdigest())


def require(condition, message):
    if not condition:
        raise ValueError(message)


def verify(root, jar=None, private_root=None):
    status = json.loads((root / 'docs/literature/scenicplus-prepare-checkpoint-20261123/STATUS.json').read_bytes())
    require(status['status'] == 'UNPAIRED_PREPARATION_TESTED_FULL_SCENICPLUS_ADMISSION_UNFINISHED', 'Wrong checkpoint kind')
    require(status['new_family_credit'] == status['new_reviews'] == 0 and not status['whole_goal_complete'], 'Expanded admission claim')
    require(status['normal_tests'] == dict(tests=2069, failures=0, errors=0, skipped=0), 'Changed regression summary')
    require(status['old_payloads_unchanged'] == 23055 and status['old_payloads_modified'] == 0, 'Changed predecessor boundary')
    require(status['independent_original_matrix_comparisons'] == 10 and status['actual_modes'] == ['RUN', 'VERIFY', 'REPLAY'], 'Changed original comparison scope')
    require(not status['global_dispatch_integrated'] and not status['full_SCENICplus_family_qualified'], 'Changed admission scope')
    for name, identity in status['source_files'].items():
        require(not Path(name).is_absolute() and '..' not in Path(name).parts, 'Unsafe source path')
        require(pin(root / name) == identity, 'Changed source: ' + name)
    source = root / 'tools/literature/scenicplus_prepare'
    packaged = root / 'src/main/resources/org/jlinalg/omics/scenicplus-prepare'
    for name in ('worker.py', 'r2g_runtime.py', 'SOURCE-PROFILE.json'):
        require((source / name).read_bytes() == (packaged / name).read_bytes(), 'Caller/profile mirror differs')
    require((source / 'r2g_runtime.py').read_bytes() == (root / 'tools/literature/scenicplus_workflow/worker.py').read_bytes(), 'Previous own runtime helper changed')
    profile = json.loads((source / 'SOURCE-PROFILE.json').read_bytes())
    require(len(profile['original_python_sources']) == 41 and len(profile['pinned_git_python_sources']) == 73, 'Changed source member population')
    require(len(profile['base_distributions']) == 228 and len(profile['loaded_distributions']) == 231, 'Changed metadata-origin population')
    if jar is not None:
        require(pin(jar) == status['producer'], 'Restore the exact retained producer; no historical hash refresh')
        with zipfile.ZipFile(jar) as archive:
            for name in ('worker.py', 'r2g_runtime.py', 'SOURCE-PROFILE.json'):
                require(archive.read('org/jlinalg/omics/scenicplus-prepare/' + name) == (source / name).read_bytes(), 'Packaged caller/profile differs')
    if private_root is not None:
        for name, identity in status['private_evidence'].items():
            require(not Path(name).is_absolute() and '..' not in Path(name).parts, 'Unsafe private evidence path')
            require(pin(private_root / name) == identity, 'Changed private evidence: ' + name)
    return dict(status='PASS_SOURCE_SAVED_IDENTITIES_ONLY', source_files=len(status['source_files']),
                private_evidence_checked=len(status['private_evidence']) if private_root is not None else 0,
                exact_JAR_checked=jar is not None, fresh_scientific_preparation_executed=False,
                tests_rerun=False, new_family_credit=0, new_reviews=0, whole_goal_complete=False)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', type=Path, default=Path(__file__).resolve().parents[2])
    parser.add_argument('--jar', type=Path)
    parser.add_argument('--private-root', type=Path)
    args = parser.parse_args()
    print(json.dumps(verify(args.root, args.jar, args.private_root), indent=2))
