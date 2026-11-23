"""Verify a byte-pinned, deliberately unqualified development handover offline."""
from pathlib import Path
import hashlib
import json


def identity(path):
    raw = path.read_bytes()
    return dict(bytes=len(raw), sha256=hashlib.sha256(raw).hexdigest())


def main():
    directory = Path(__file__).resolve().parent
    root = directory.parents[2]
    manifest = json.loads((directory / 'FILES.json').read_bytes())
    actual = {p.relative_to(directory).as_posix() for p in directory.rglob('*')
              if p.is_file() and p.name != 'FILES.json' and '__pycache__' not in p.parts}
    assert actual == set(manifest), 'Snapshot membership changed'
    for name, expected in manifest.items():
        relative = Path(name)
        assert not relative.is_absolute() and '..' not in relative.parts
        assert identity(directory / relative) == expected, name
    captured = json.loads((directory / 'CAPTURED-FILES.json').read_bytes())
    for name, expected in captured.items():
        assert identity(directory / name) == {k: expected[k] for k in ('bytes', 'sha256')}, name
    restore = json.loads((directory / 'DRAFT-RESTORE.json').read_bytes())
    for destination, expected in restore['files'].items():
        assert identity(directory / expected['saved_as']) == {k: expected[k] for k in ('bytes', 'sha256')}
        assert not (root / destination).exists(), 'Draft was enabled; use its new qualification context'
    summary = json.loads((root / 'docs/literature/omics-2023-2026/summary.json').read_bytes())
    assert summary['papers'] == 250 and summary['paper_tool_observations'] == 2722
    assert summary['integration_family_counts'] == dict(scoped=151, native=27, upstream=124,
        including_existing_adapters=154, validated_alias_labels_excluded_from_extra_credit=3)
    audit = json.loads((directory / 'SOURCE-AUDIT.json').read_bytes())
    assert audit['primary_XML_checked'] == 250 and not audit['canonical_review_or_family_credit_added']
    assert {p['paper_id'] for p in audit['candidate_papers']} == {'P008','P009','P010','P024','P094','P161'}
    result = json.loads((directory / 'receipts/native-probe-2/RESULT.json').read_bytes())
    assert result['original_1_8_4_finite_rows_compared'] == 64
    assert result['undefined_constant_rows_explicitly_not_probability_parity'] == 32
    assert not result['differences'] and result['family_credit'] == result['new_reviews'] == 0
    assert not result['normal_project_build_run'] and not result['full_adapter_present']
    assert not result['legacy_profiles_compared_to_original_runtime']
    assert not result['junit_or_full_packaged_workflow_qualification']
    print(json.dumps(dict(status='SAVED_UNQUALIFIED_HANDOVER_VERIFIED', files=len(manifest),
        captured_files=len(captured), draft_restore_files=len(restore['files']),
        scoped_families=151, family_credit_added=0, original_finite_probe_rows=64,
        undefined_rows_not_probability_parity=32, scientific_workflow_executed=False)))


if __name__ == '__main__':
    main()
