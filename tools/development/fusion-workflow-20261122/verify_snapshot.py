"""Read-only inventory/source/probe check of an unfinished FUSION handover."""
from pathlib import Path
import hashlib
import json
import stat
import sys

HERE = Path(__file__).resolve().parent


def require(condition, message):
    if not condition:
        raise ValueError(message)


def identity(path):
    info = path.lstat()
    require(stat.S_ISREG(info.st_mode) and not path.is_symlink(), "Not a regular file: " + str(path))
    with path.open('rb') as stream:
        return dict(bytes=info.st_size, sha256=hashlib.file_digest(stream, 'sha256').hexdigest())


def verify():
    status = json.loads((HERE / 'MANIFEST.json').read_bytes())
    require(status['status'] == 'UNQUALIFIED_FUSION_WORKFLOW_HANDOVER', 'Wrong scope')
    require(status['qualified_baseline'] == '833864a156d85578241f436e51128f16d0f97cf0', 'Wrong baseline')
    require(status['new_family_credit'] == status['new_reviews'] == 0 and
            not status['workflow_adapter_qualified'] and not status['whole_goal_complete'], 'False credit/completion')
    actual = set()
    for path in HERE.rglob('*'):
        require(not path.is_symlink(), 'Snapshot links are forbidden: ' + str(path))
        require(path.is_dir() or path.is_file(), 'Nonregular snapshot node: ' + str(path))
        if path.is_file() and path.name != 'MANIFEST.json':
            actual.add(path.relative_to(HERE).as_posix())
    require(actual == set(status['files']), 'Snapshot file inventory differs; run Python with -B')
    for name, expected in status['files'].items():
        address = Path(name)
        require(not address.is_absolute() and '..' not in address.parts and
                address.as_posix() == name, 'Unsafe inventory address')
        require(identity(HERE / address) == expected, 'Snapshot bytes differ: ' + name)
    source = status['original_source_paths']
    require(len(source) == 16, 'Draft source count differs')
    require(set(source) == {p.relative_to(HERE / 'source').as_posix()
                            for p in (HERE / 'source').rglob('*') if p.is_file()}, 'Source path inventory differs')
    for name, expected in source.items():
        require(identity(HERE / 'source' / name) == expected, 'Original draft byte mismatch: ' + name)
    folder = HERE / 'source/src/main/resources/org/jlinalg/omics/fusion-9346c122'
    profile = json.loads((folder / 'PROFILE.json').read_bytes())
    require(profile['source_revision'] == '9346c1222bffbb34499fa7a8e23c1b701b55cb05', 'Wrong upstream revision')
    require(len(profile['members']) == 8, 'Resource profile inventory differs')
    for entry in profile['members'].values():
        require(identity(folder / entry['resource']) == {k:entry[k] for k in ['bytes', 'sha256']},
                'Pinned original/supervisor bytes differ')
    cases = json.loads((HERE / 'evidence/training/RESULT.json').read_bytes())
    require(cases == [dict(case='all-models', exit_code=2, original_inputs_unchanged=True),
                      dict(case='estimated-h2', exit_code=0, original_inputs_unchanged=True)],
            'Actual training outcomes differ')
    failed = json.loads((HERE / 'evidence/training/all-models/FAILURE.json').read_bytes())
    require(failed['exit_code'] == 2 and not failed['results_admitted'], 'Failed training admitted results')
    message = (HERE / 'evidence/training/all-models/worker/original.stderr.log').read_text(encoding='utf-8')
    require('tmp/train.param.txt' in message and 'weights.bslmm' in message, 'Missing original failure')
    summary = json.loads((HERE / 'evidence/training/estimated-h2/summary.json').read_bytes())
    require(summary['operation'] == 'TRAIN' and summary['original_exit_code'] == 0 and
            summary['trained_models'] == ['top1'], 'Successful training scope differs')
    require((HERE / 'evidence/training/estimated-h2/worker/gcta-probe.original-exit').read_text().strip() == '1',
            'Modern GCTA original startup status lost')
    require(status['catalog_counts'] == dict(papers=250, scoped_families=150, native_families=27,
            upstream_families=123, reviewed_uses=1673, pending_uses=1050), 'Catalog scope differs')
    return dict(status=status['status'], files=len(actual), exact_draft_source_files=16,
                native_component_qualified_separately=True, workflow_adapter_qualified=False,
                actual_training_exit_codes=[2, 0], new_family_credit=0, new_reviews=0,
                network_executed=False, runtime_executed=False, whole_goal_complete=False)


if __name__ == '__main__':
    try:
        print(json.dumps(verify(), indent=2))
    except Exception as error:
        print('FAIL2: saved development snapshot differs: ' + str(error), file=sys.stderr)
        sys.exit(2)
