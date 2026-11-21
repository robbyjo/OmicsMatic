"""Check the saved limma execution packet and exact current source; no R/model execution."""
from pathlib import Path, PurePosixPath
import hashlib
import json
import zipfile


def pin(raw):
    return dict(bytes=len(raw), sha256=hashlib.sha256(raw).hexdigest())


def safe(name):
    path = PurePosixPath(name)
    if path.is_absolute() or '..' in path.parts or '\\' in name or not name:
        raise ValueError('Unsafe packet path: ' + name)
    return path.parts


def verify(root):
    folder = root / 'docs/literature/limma-workflow-checkpoint'
    checks = json.loads((folder / 'CHECKS.json').read_bytes())
    if checks['whole_goal_complete'] or checks['new_catalog_credit']:
        raise ValueError('Unexpected full-goal or catalog claim')
    for name, expected in checks['source_inputs'].items():
        if pin(root.joinpath(*safe(name)).read_bytes()) != expected:
            raise ValueError('Current source differs: ' + name)
    raw = (folder / 'checks.zip').read_bytes()
    if pin(raw) != checks['archive']:
        raise ValueError('Saved archive differs')
    inventory = (folder / 'INVENTORY.json').read_bytes()
    if pin(inventory) != checks['inventory']:
        raise ValueError('Saved inventory differs')
    files = json.loads(inventory)['files']
    with zipfile.ZipFile(folder / 'checks.zip') as archive:
        names = archive.namelist()
        if len(names) != len(set(names)) or set(names) != set(files):
            raise ValueError('Saved file population differs')
        total = 0
        for name in names:
            safe(name)
            entry = archive.getinfo(name)
            total += entry.file_size
            if entry.file_size > 32 * 1024 * 1024 or total > 256 * 1024 * 1024:
                raise ValueError('Saved packet exceeds declared reader capacity')
            if pin(archive.read(name)) != files[name]:
                raise ValueError('Saved file differs: ' + name)
    result = dict(status='PASS_SAVED_LIMMA_WORKFLOW_CHECKPOINT_BYTES_ONLY',
                  saved_files=len(files), source_files=len(checks['source_inputs']),
                  original_compositions=checks['original_compositions'],
                  identical_original_object_comparisons=checks['original_object_comparisons'],
                  packaged_tests_per_JVM=checks['packaged_tests_per_JVM'],
                  test_JVMs=checks['test_JVMs'],
                  fresh_model_execution=False, full_workflow_or_paper_claim=False,
                  new_catalog_credit=0, whole_goal_complete=False)
    print(json.dumps(result, indent=2))
    return result


if __name__ == '__main__':
    verify(Path(__file__).resolve().parents[2])
