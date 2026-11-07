# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Verify the immutable Salmon setup checkpoint, current source pins and unchanged prior evidence."""
import argparse
import hashlib
import json
from pathlib import Path, PurePosixPath
import zipfile

ROOT = Path(__file__).resolve().parents[2]


def pin(path):
    with path.open('rb') as stream:
        return dict(bytes=path.stat().st_size, sha256=hashlib.file_digest(stream, 'sha256').hexdigest())


def main():
    if not __debug__:
        raise RuntimeError('Never disable checkpoint verification')
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--checkpoint', type=Path, default=ROOT / 'docs/literature/salmon-setup-development')
    args = parser.parse_args()
    folder = args.checkpoint.resolve()
    proof = json.loads((folder / 'VALIDATION.json').read_bytes())
    assert proof['status'] == 'passed-native-Salmon-table-and-portable-setup-audits-public-workflow-open'
    sums = (folder / 'SHA256SUMS').read_text().splitlines()
    assert len(sums) == 2
    for line in sums:
        digest, name = line.split('  ', 1)
        assert name in {'controls.zip', 'VALIDATION.json'} and pin(folder / name)['sha256'] == digest
    archive = folder / 'controls.zip'
    assert pin(archive) == proof['archive'] and archive.stat().st_size < 100 * 1024 * 1024
    with zipfile.ZipFile(archive) as saved:
        assert saved.testzip() is None
        assert len(saved.infolist()) == len(set(saved.namelist())) == len(proof['archive_entries'])
        assert set(saved.namelist()) == set(proof['archive_entries'])
        logical = 0
        for name, value in proof['archive_entries'].items():
            member = PurePosixPath(name)
            assert not member.is_absolute() and '..' not in member.parts and '\\' not in name
            assert saved.getinfo(name).file_size == value['bytes'] <= 20000000
            raw = saved.read(name)
            logical += len(raw)
            assert logical <= 100000000
            assert len(raw) == value['bytes'] and hashlib.sha256(raw).hexdigest() == value['sha256']
        for name, value in proof['source_pins'].items():
            assert pin(ROOT / name) == value, name
            assert saved.read('source/' + name) == (ROOT / name).read_bytes(), name
        old = json.loads(saved.read('prior-checkpoint/VALIDATION.json'))
    old_path = ROOT / 'docs/literature/salmon-native-development/VALIDATION.json'
    assert pin(old_path) == proof['earlier_original_quantifications']['checkpoint_pin']
    for name, value in old['source_pins'].items():
        assert pin(ROOT / name) == value, name
    assert proof['claims']['portable_native_setup_report_reader'] is True
    assert all(proof['claims'][key] is False for key in ['typed_public_quantification_command_available', 'scientific_runtime_admitted',
               'user_analysis_admitted', 'whole_index_semantics_certified', 'complete_public_quantification_output_admission',
               'Salmon_catalog_promoted', 'whole_paper_reproduced', 'biological_accuracy_certified', 'whole_goal_complete', 'released_or_deployed'])
    assert len(proof['native_matrix']) == 3 and all(row['passed'] == row['tests'] == 139 for row in proof['native_matrix'])
    candidate = ROOT / proof['artifact']['path']
    if candidate.exists():
        assert pin(candidate) == {k: proof['artifact'][k] for k in ['bytes', 'sha256']}
    canonical = ROOT / 'build/cli/omicsmatic-0.3.7.jar'
    if canonical.exists():
        assert pin(canonical) == proof['canonical_executable_unchanged']
    data = ROOT / 'docs/literature/omics-2023-2026'
    catalog = json.loads((data / 'summary.json').read_bytes())
    assert catalog['implementation_status_counts']['missing'] == 256
    assert catalog['implementation_status_counts']['native-subset-validated'] == 15
    assert catalog['implementation_status_counts']['upstream-adapter-validated'] == 49
    assert catalog['papers'] == 250 and catalog['paper_tool_observations'] == 2430
    print(json.dumps(dict(status='passed-current-source-and-frozen-Salmon-setup-checkpoint-integrity', source_pins=len(proof['source_pins']),
                         archive_entries=len(proof['archive_entries']), archive=proof['archive'], previous_checkpoint_unchanged=True,
                         canonical_executable_unchanged=canonical.exists(), Salmon_catalog_status='missing', new_scoped_integrations=64)))


if __name__ == '__main__':
    main()
