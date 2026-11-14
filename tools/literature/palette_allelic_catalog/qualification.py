# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Check complete saved controls without rerunning or weakening scientific verifiers."""
import hashlib, importlib.util, json, os, tempfile, zipfile
from pathlib import Path
from palette_allelic_catalog.model import pin, rawpin

def qualification(root):
    base = Path(os.fspath(root / 'build/literature-review/monocle-runtime')).resolve()
    base.mkdir(parents=True, exist_ok=True)
    folder = 'docs/literature/wesanderson037-workflow-development'
    wes = json.loads((root / folder / 'VALIDATION.json').read_bytes())
    for name, wanted in wes['sources'].items():
        if pin(root / name) != wanted: raise ValueError('Qualified scientific source differs: ' + name)
    file = Path(os.fspath(root / 'tools/literature/wesanderson037/verify.py'))
    spec = importlib.util.spec_from_file_location('retained_palette_original',file)
    module = importlib.util.module_from_spec(spec); spec.loader.exec_module(module)
    with tempfile.TemporaryDirectory(prefix='palette-catalog-',dir=base) as directory:
        scratch = Path(directory).resolve()
        if not scratch.is_relative_to(base): raise ValueError('Evidence scratch escaped owned workspace')
        module.evidence(scratch)
    if (wes['complete_original_API_cases'],wes['complete_original_returned_colors'],wes['original_failures_retained'],wes['original_palette_vectors']) != (6108,224445,3397,24):
        raise ValueError('Incomplete native palette qualification')
    archives = []
    for name, count, status in [
        ('allelicseries0112-development-checkpoint',1529,'PASS_ALLELICSERIES0112_DEVELOPMENT_CHECKPOINT_NOT_CATALOG_ADMISSION'),
        ('allelicseries0112-batch-development',19411,'PASS_ALLELICSERIES0112_BATCH_AND_SCALAR_ADMISSION_DEVELOPMENT')]:
        data = root / ('docs/literature/' + name)
        proof = json.loads((data / 'VALIDATION.json').read_bytes())
        if proof['status'] != status or proof['paper_build_equivalence_proven'] or proof['new_tool_credit'] != 0 or proof['whole_goal_complete']:
            raise ValueError('Unsupported candidate edition or scope')
        if pin(data / 'controls.zip') != proof['archive'] or pin(data / 'CONTROLS_LAYOUT.json') != proof['controls_layout']:
            raise ValueError('Candidate complete archive or inventory differs')
        members = json.loads((data / 'CONTROLS_LAYOUT.json').read_bytes())['files']
        if len(members) != count: raise ValueError('Incomplete candidate evidence population')
        for line in (data / 'SHA256SUMS').read_text(encoding='utf8').splitlines():
            if line.strip():
                sha, member = line.split(maxsplit=1)
                if pin(data / member.lstrip('*'))['sha256'] != sha: raise ValueError('Candidate checkpoint record differs')
        with zipfile.ZipFile(data / 'controls.zip') as archive:
            names = archive.namelist()
            if len(names) != len(set(names)) or set(names) != set(members): raise ValueError('Incomplete candidate archive members')
            for member, wanted in members.items():
                if rawpin(archive.read(member)) != wanted: raise ValueError('Candidate evidence member differs: ' + member)
            for source, wanted in proof['sources'].items():
                if rawpin(archive.read('current-source/' + source)) != wanted: raise ValueError('Historical source snapshot differs: ' + source)
                if name.endswith('batch-development') and pin(root / source) != wanted:
                    raise ValueError('Qualified scientific source differs: ' + source)
        archives.append(dict(checkpoint=name,complete_members=count,original_analysis_repeated=False))
    if proof['complete_batch_comparisons'] != 1026 or proof['complete_original_returned_objects'] != 1022 or proof['original_error_class_message_comparisons'] != 4:
        raise ValueError('Incomplete original batch comparisons')
    intake = json.loads((root / 'tools/literature/allelicseries0112/paper-use-intake.json').read_bytes())
    paper = json.loads((root / folder / 'PAPER_USE.json').read_bytes())
    if len(intake['reviews']) != 26 or paper['paragraph_index'] != 93 or paper['version_reported'] != '0.3.7' or paper['palette_name_recovered']:
        raise ValueError('Primary reading evidence scope differs')
    return dict(wesanderson037=dict(original_API_cases=6108,complete_returned_colors=224445,original_failures_retained=3397,
        palettes=24,qualified_platforms=3,CLI_cases_per_platform=72,complete_replays_per_platform=63,
        native_R_graphics_ported=False,paper_palette_inferred=False,new_scoped_integration_credit=1),
        allelicseries_candidate=dict(archives=archives,actual_version='0.1.1.2',reported_version='1.1.2',
        complete_batch_calls=1026,returned_objects=1022,original_errors=4,single_exports=16,native_cases_per_platform=539,
        candidate_available=True,reported_edition_available=False,new_scoped_integration_credit=0),
        scientific_sources_changed=False,scientific_verifiers_changed=False,original_analysis_executed=False)
