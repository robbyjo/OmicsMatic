"""Verify saved development handover bytes; performs no model execution."""
from pathlib import Path, PurePosixPath
import hashlib
import json


def pin(path):
    with path.open('rb') as stream:
        return dict(bytes=path.stat().st_size,
                    sha256=hashlib.file_digest(stream, 'sha256').hexdigest())


def verify(root):
    packet = root / 'docs/literature/limma-workflow-handover'
    checks = json.loads((packet / 'CHECKS.json').read_bytes())
    if checks['whole_goal_complete'] or checks['catalog_credit_added'] != 0:
        raise ValueError('Unexpected qualification or catalog claim')
    for group, base in [('source_inputs', root), ('saved_files', packet)]:
        for name, expected in checks[group].items():
            relative = PurePosixPath(name)
            if relative.is_absolute() or '..' in relative.parts or '\\' in name:
                raise ValueError('Unsafe inventory path')
            path = base.joinpath(*relative.parts)
            if pin(path) != expected:
                raise ValueError('Saved bytes differ: ' + str(path))
    result = dict(status='PASS_SAVED_DEVELOPMENT_HANDOVER_BYTES_ONLY',
                  source_files=len(checks['source_inputs']),
                  saved_files=len(checks['saved_files']),
                  original_compositions=checks['original_compositions'],
                  normal_focused_tests=checks['normal_focused_tests'],
                  whole_goal_complete=False, fresh_model_execution=False,
                  full_workflow_qualification=False, catalog_credit_added=0)
    print(json.dumps(result, indent=2))
    return result


if __name__ == '__main__':
    verify(Path(__file__).resolve().parents[2])
