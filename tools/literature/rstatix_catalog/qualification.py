# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Validate unchanged full original workflow and native/provider evidence; no new analysis."""
from pathlib import Path
import importlib.util,json,os,zipfile
from rstatix_catalog.model import pin,rawpin

WORKFLOW='docs/literature/rstatix072-workflow-development'
NATIVE='docs/literature/rstatix072-development-checkpoint'
def qualification(root):
 proof=json.loads((root/WORKFLOW/'VALIDATION.json').read_bytes())
 for n,wanted in proof['sources'].items():
  if pin(root/n)!=wanted:raise ValueError('Qualified original-workflow scientific source differs: '+n)
 file=Path(os.fspath(root/'tools/literature/rstatix072_workflow/verify.py')).resolve()
 spec=importlib.util.spec_from_file_location('retained_rstatix072_complete_workflow',file);module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
 result=module.evidence()
 if (result['complete_archive_files'],result['complete_original_calls'],result['complete_returned_rows'],result['complete_returned_cells'],result['complete_result_files'])!=(2411,248,444,5664,250):raise ValueError('Incomplete original scientific control population')
 auxiliaries=[]
 for folder,status in [(WORKFLOW,'PASS_FROZEN_ORIGINAL_RSTATIX_WORKFLOW_VERIFIER_REFUSALS_WITHOUT_SOURCE_CHANGES'),(NATIVE,'PASS_ADDITIONAL_FROZEN_RSTATIX_VERIFIER_REFUSALS_WITHOUT_SOURCE_CHANGES')]:
  data=root/folder;v=json.loads((data/'VALIDATION.json').read_bytes());boundary=json.loads((data/'VERIFIER_BOUNDARIES.json').read_bytes())
  if boundary['status']!=status or boundary['controls']!=4 or boundary['base_validation']!=pin(data/'VALIDATION.json') or boundary['base_scientific_archive']!=v['archive']:raise ValueError('Frozen-verifier refusal baseline differs')
  if pin(data/'verifier-boundary-controls.zip')!=boundary['additional_archive']:raise ValueError('Frozen-verifier complete refusal archive differs')
  with zipfile.ZipFile(data/'verifier-boundary-controls.zip') as z:
   if len(z.namelist())!=len(set(z.namelist())) or set(z.namelist())!=set(boundary['additional_files']):raise ValueError('Incomplete frozen-verifier refusal members')
   for n,wanted in boundary['additional_files'].items():
    if rawpin(z.read(n))!=wanted:raise ValueError('Frozen-verifier refusal member differs: '+n)
  if len(boundary['calls'])!=4 or any(c['exit_code']!=c['expected_exit'] for c in boundary['calls']):raise ValueError('Frozen-verifier refusal exit differs')
  auxiliaries.append(dict(component=folder,complete_auxiliary_members=len(boundary['additional_files']),frozen_verifier_refusals=4))
 return dict(original_workflow=result,auxiliary_verifier_controls=auxiliaries,native_or_original_analysis_repeated=False,
  scientific_sources_changed=False,scientific_verifiers_changed=False,scientific_sources_overridden=False)
