# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Check unchanged QuPath scientific controls and complete auxiliary refusal evidence."""
import importlib.util,json,os,tempfile,zipfile
from pathlib import Path
from qupath_catalog.model import pin,rawpin

COMPONENT = 'docs/literature/qupath051-workflow-development'

def qualification(root):
    proof = json.loads((root / COMPONENT / 'VALIDATION.json').read_bytes())
    # Validate live bytes before importing any qualified verifier. Never overlay scientific code.
    for name,wanted in proof['sources'].items():
        if pin(root / name) != wanted: raise ValueError('Qualified scientific source differs: ' + name)
    file = Path(os.fspath(root / 'tools/literature/qupath051/verify.py')).resolve()
    spec = importlib.util.spec_from_file_location('retained_qupath051_original',file)
    module = importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
    base = Path(os.fspath(root / 'build/literature-review/monocle-runtime')).resolve();base.mkdir(parents=True,exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='qupath-catalog-',dir=base) as directory:
        scratch = Path(directory).resolve()
        if not scratch.is_relative_to(base): raise ValueError('Evidence scratch escaped owned workspace')
        result = module.evidence(scratch)
    if (result['complete_archive_files'],result['source_files'],result['reviewed_papers'],result['named_primary_anchors']) != (1381,1097,12,16):
        raise ValueError('Incomplete qualified QuPath population')
    boundaries = json.loads((root / COMPONENT / 'VERIFIER_BOUNDARIES.json').read_bytes())
    if boundaries['status'] != 'PASS_ADDITIONAL_FROZEN_QUPATH_VERIFIER_REFUSALS_WITHOUT_SOURCE_CHANGES' or boundaries['controls'] != 4:
        raise ValueError('Incomplete frozen-verifier refusal population')
    if boundaries['base_validation'] != pin(root / COMPONENT / 'VALIDATION.json') or boundaries['base_scientific_archive'] != proof['archive']:
        raise ValueError('Frozen-verifier refusal baseline differs')
    archive_path = root / COMPONENT / 'verifier-boundary-controls.zip'
    if pin(archive_path) != boundaries['additional_archive']: raise ValueError('Complete verifier refusal archive differs')
    with zipfile.ZipFile(archive_path) as archive:
        names = archive.namelist()
        if len(names) != len(set(names)) or set(names) != set(boundaries['additional_files']): raise ValueError('Incomplete verifier refusal archive')
        for name,wanted in boundaries['additional_files'].items():
            if rawpin(archive.read(name)) != wanted: raise ValueError('Verifier refusal member differs: ' + name)
    if [(r['case'],r['exit_code'],r['expected_exit']) for r in boundaries['calls']] != [
        ('changed-current-source',2,2),('missing-artifact',3,3),('resealed-portable-result',2,2),('resealed-missing-raw-hierarchy',2,2)]:
        raise ValueError('Incomplete original refusal exits')
    if boundaries['frozen_verifier_modified'] or boundaries['actual_scientific_data_modified'] or boundaries['new_original_analysis_executed']:
        raise ValueError('Unsupported scientific modification')
    return dict(original_component=result,additional_frozen_verifier_refusals=4,
        complete_auxiliary_archive_files=len(boundaries['additional_files']),scientific_sources_changed=False,
        scientific_verifiers_changed=False,original_analysis_executed=False)
