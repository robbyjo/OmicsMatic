# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Qualify unchanged complete revision2 evidence, full predecessor gates and lossless negative-input controls."""
import hashlib,importlib.util,json,os,zipfile
from pathlib import Path
from rstatix_revision2_catalog.model import WORKFLOW,pin,rawpin

def qualification(root):
 data=root/WORKFLOW;proof=json.loads((data/'VALIDATION.json').read_bytes());cumulative=json.loads((data/'CUMULATIVE_VERIFICATION.json').read_bytes())
 if cumulative['base_validation']!=pin(data/'VALIDATION.json') or cumulative['source_pins']!=proof['sources']:raise ValueError('Revision2 qualified source closure differs')
 for n,wanted in proof['sources'].items():
  if pin(root/n)!=wanted:raise ValueError('Qualified revision2 source differs: '+n)
 path=Path(os.fspath(root/'tools/literature/rstatix072_workflow_v2/verify.py')).resolve();spec=importlib.util.spec_from_file_location('frozen_original_rstatix_revision2',path);module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
 # The unchanged component already replays exactly its own eight catalog
 # inputs. All source, verifiers, scientific inputs/results and archives stay live.
 result=module.evidence(root)
 if tuple(result[k] for k in ['complete_archive_files','complete_original_calls','complete_original_result_files','complete_returned_rows','complete_returned_cells','linux_workflow_controls','malformed_worker_status_controls','blocked_recording_controls'])!=(5185,248,250,444,5664,41,8,2):raise ValueError('Incomplete revision2 scientific/control population')
 if result!=cumulative['base_qualification']:raise ValueError('Frozen component result differs from retained qualification')
 if pin(data/'verifier-controls.zip')!=cumulative['auxiliary_archive'] or pin(data/'VERIFIER_CONTROL_MEMBERS.json')!=cumulative['auxiliary_members']:raise ValueError('Complete auxiliary evidence differs')
 index=json.loads((data/'VERIFIER_CONTROL_MEMBERS.json').read_bytes())
 with zipfile.ZipFile(data/'verifier-controls.zip') as z:
  if len(z.namelist())!=len(set(z.namelist())) or set(z.namelist())!=set(index) or len(index)!=16:raise ValueError('Incomplete auxiliary archive')
  for n,wanted in index.items():
   if rawpin(z.read(n))!=wanted:raise ValueError('Auxiliary member differs: '+n)
  controls=json.loads(z.read('refusals/COMPLETED.json'));base=z.read('refusals/damaged-evidence.zip');delta=json.loads(z.read('NEGATIVE_INPUT_DELTAS.json'));main_index=json.loads((data/'MEMBERS.json').read_bytes())
  if controls!=cumulative['verifier_controls'] or len(controls['refusal_controls'])!=4 or not controls['original_archive_unchanged']:raise ValueError('Frozen verifier control population differs')
  original=(data/'controls.zip').read_bytes()
  if base!=original+b'isolated deliberate changed evidence byte':raise ValueError('Exact negative ZIP bytes differ')
  if z.read('refusals/changed-source/run.py')!=(root/'tools/literature/rstatix072_workflow_v2/run.py').read_bytes()+b'\n# deliberately changed isolated source\n':raise ValueError('Exact negative source input differs')
  if delta['base_index']!=pin(data/'MEMBERS.json') or delta['incomplete_index']['retained_members']!=len(main_index)-1 or delta['corrupted_index']['retained_members']!=len(main_index):raise ValueError('Lossless negative-index delta differs')
  omitted=dict(main_index);del omitted[delta['incomplete_index']['remove_exact_key']]
  bad={n:dict(v) for n,v in main_index.items()};change=delta['corrupted_index'];bad[change['path']][change['replace_field']]=change['replacement']
  with zipfile.ZipFile(root/'docs/literature/rstatix072-workflow-development/controls.zip') as old:baseline=old.read('protocols/linux/COMPLETED.json')
  for malformed in [omitted,bad]:
   try:module.archive_evidence(data/'controls.zip',proof['archive'],malformed,baseline)
   except ValueError:pass
   else:raise ValueError('Losslessly reconstructed negative index was accepted')
 if pin(data/'RETENTION_AUDIT.json')!=cumulative['retention_audit']:raise ValueError('Retained link/directory audit differs')
 retained=json.loads((data/'RETENTION_AUDIT.json').read_bytes());dirs=json.loads((data/'DIRECTORY_IDENTITIES.json').read_bytes())
 if len(retained['retained_links'])!=2 or len(retained['blocked_empty_directory_identities'])!=4 or any(n not in dirs for n in retained['blocked_empty_directory_identities']):raise ValueError('Missing retained link/blocked directory identity')
 return dict(original_workflow=result,auxiliary_archive_members=16,frozen_verifier_refusals=4,baseline_controls=1,lossless_negative_indexes_reconstructed=2,original_sources_and_verifiers_changed=False,scientific_sources_overridden=False,new_original_analysis_executed=False,new_tool_credit=0)
