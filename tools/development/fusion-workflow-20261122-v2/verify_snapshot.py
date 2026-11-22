"""Read-only check of the unfinished FUSION v2 handover; no R or network."""
from pathlib import Path
import argparse
import hashlib
import json
import stat
import sys
import xml.etree.ElementTree as ET

HERE = Path(__file__).resolve().parent


def require(condition, message):
    if not condition:
        raise ValueError(message)


def pin(path):
    info = path.lstat()
    require(stat.S_ISREG(info.st_mode) and not path.is_symlink(), 'Not a regular file: ' + str(path))
    with path.open('rb') as stream:
        return dict(bytes=info.st_size, sha256=hashlib.file_digest(stream, 'sha256').hexdigest())


def verify(source_tree=None):
    status = json.loads((HERE / 'MANIFEST.json').read_bytes())
    require(status['status'] == 'UNQUALIFIED_FUSION_WORKFLOW_HANDOVER_V2', 'Wrong snapshot scope')
    require(status['new_family_credit'] == status['new_reviews'] == 0 and
            not status['workflow_adapter_qualified'] and not status['whole_goal_complete'], 'False completion/credit')
    actual = set()
    for path in HERE.rglob('*'):
        require(not path.is_symlink(), 'Snapshot link: ' + str(path))
        require(path.is_file() or path.is_dir(), 'Nonregular node: ' + str(path))
        if path.is_file() and path != HERE / 'MANIFEST.json':
            actual.add(path.relative_to(HERE).as_posix())
    require(actual == set(status['files']), 'Closed snapshot inventory differs; run Python with -B')
    for name, expected in status['files'].items():
        address = Path(name)
        require(not address.is_absolute() and '..' not in address.parts and address.as_posix() == name,
                'Unsafe inventory path')
        require(pin(HERE / address) == expected, 'Snapshot bytes differ: ' + name)
    sources = status['original_source_paths']
    require(len(sources) == 39 and set(sources) == {
        p.relative_to(HERE / 'source').as_posix() for p in (HERE / 'source').rglob('*') if p.is_file()
    }, 'Draft source inventory differs')
    for name, expected in sources.items():
        require(pin(HERE / 'source' / name) == expected, 'Draft identity differs: ' + name)
        if source_tree is not None:
            require(pin(source_tree / name) == expected, 'Restored source differs: ' + name)
    resources = HERE / 'source/src/main/resources/org/jlinalg/omics/fusion-9346c122'
    profile = json.loads((resources / 'PROFILE.json').read_bytes())
    require(profile['source_revision'] == '9346c1222bffbb34499fa7a8e23c1b701b55cb05' and
            len(profile['members']) == 9, 'Source profile differs')
    for entry in profile['members'].values():
        require(pin(resources / entry['resource']) == {k: entry[k] for k in ['bytes', 'sha256']},
                'Original/wrapper resource identity differs: ' + entry['resource'])
    evidence = HERE / 'evidence'
    latest = json.loads((evidence / 'workflow-normal-build-9/RESULT.json').read_bytes())
    require(latest['exit_code'] == 0 and latest['source_inputs_unchanged'] and
            latest['development_jar'] == status['latest_development_producer'], 'Latest producer receipt differs')
    tests = [ET.parse(p).getroot() for p in
             (evidence / 'workflow-normal-build-9/project-build/test-results/test').glob('TEST-*.xml')]
    totals = {k: sum(int(t.get(k, '0')) for t in tests) for k in ['tests', 'failures', 'errors', 'skipped']}
    require(totals == dict(tests=75, failures=0, errors=0, skipped=0), 'Actual test scope differs')
    packaged = json.loads((evidence / 'workflow-packaged-6/RESULT.json').read_bytes())
    previous = json.loads((evidence / 'workflow-normal-build-8/RESULT.json').read_bytes())
    require(packaged['jar'] == previous['development_jar'] != latest['development_jar'] and
            len(packaged['cases']) == 29 and packaged['all_expected'] and
            all(c['exit_code'] == c['expected'] for c in packaged['cases']), 'Preceding producer case scope differs')
    boundary = json.loads((evidence / 'workflow-rounded-gate-control-3/RESULT.json').read_bytes())
    require(boundary['exit_code'] == 0 and boundary['original_missing_posterior_preserved'] and
            boundary['summary']['finite_result_rows'] == 3 and
            boundary['summary']['missing_requested_coloc_rows'] == 0 and
            boundary['summary']['coloc_rounded_gate_disagreements'] == 1 and
            boundary['summary']['source_csv_rounding_verified'], 'Latest real rounding regression differs')
    require(status['catalog_counts'] == dict(papers=250, scoped_families=150, native_families=27,
            upstream_families=123, reviewed_uses=1673, pending_uses=1050), 'Catalog scope differs')
    return dict(status=status['status'], source_files=39, saved_files=len(actual), normal_tests=totals,
                preceding_producer_packaged_cases=29, latest_producer_targeted_regression=True,
                restored_source_checked=source_tree is not None, workflow_adapter_qualified=False,
                new_family_credit=0, new_reviews=0, network_executed=False, R_executed=False,
                whole_goal_complete=False)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source-tree', type=Path)
    arguments = parser.parse_args()
    try:
        print(json.dumps(verify(arguments.source_tree), indent=2))
    except Exception as error:
        print('FAIL2: saved development snapshot differs: ' + str(error), file=sys.stderr)
        sys.exit(2)
