#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Verify the sealed original-rstatix revision2 population, failure diagnostics and unchanged predecessor evidence."""
from pathlib import Path,PurePosixPath
import argparse,ast,hashlib,importlib.util,json,os,sys,traceback,zipfile
ROOT=Path(__file__).resolve().parents[3]
DATA=ROOT/'docs/literature/rstatix072-workflow-revision2'
ACTION='Required action: retain every failed attempt and complete original object, raw malformed report, warning/error, partial output and exact source/evidence byte. SKIP3 skips dependent verification/analysis when exact permitted prerequisites are unavailable. Supply only permitted matching sources/provider/input and retry NEW paths. FAIL2 admits no complete result; preserve the named failure and all diagnostics, correct its cause and retry NEW paths. Incomplete condition totals are unavailable, never zero. This verifies original-runtime integration, not independent statistical validity, author-runtime equivalence, paper reproduction or whole-goal completion. Read RSTATIX072_WORKFLOW_REVISION2.md.'

def pin(path):
 h=hashlib.sha256();n=0
 with path.open('rb') as f:
  for block in iter(lambda:f.read(1048576),b''):h.update(block);n+=len(block)
 return dict(bytes=n,sha256=h.hexdigest())

def rawpin(value):return dict(bytes=len(value),sha256=hashlib.sha256(value).hexdigest())
def load(path):return json.loads(path.read_bytes())

def require_sources(root,expected):
 for name,wanted in expected.items():
  path=root/name
  if path.is_symlink() or pin(path)!=wanted:raise ValueError('Qualified source differs: '+name)

def previous(root,data):
 sys.path.insert(0,str(root/'tools/literature'))
 from ggpubr_catalog.bridge import metadata_view
 from ggpubr_catalog.verify import verify
 with zipfile.ZipFile(data/'catalog-at-qualification.zip') as archive:
  rows=archive.namelist()
  from ggpubr_catalog.model import META
  if len(rows)!=8 or set(rows)!=set(META):raise ValueError('Only exact eight predecessor catalog metadata paths may be replayed')
  before={n:archive.read(n) for n in META}
 # Every scientific verifier/source/input/evidence file remains live and exact.
 result=verify(root=metadata_view(root,before),rescan=False)
 if result['status']!='PASS_ORIGINAL_GGPUBR_CATALOG_ADMISSION_AND_NINE_SOURCE_ROLES':raise ValueError('Predecessor cumulative admission is not qualified')
 return result

def archive_evidence(path,expected,index,baseline_bytes):
 if path.is_symlink() or pin(path)!=expected:raise ValueError('Complete retained evidence ZIP differs')
 with zipfile.ZipFile(path) as archive:
  names=archive.namelist()
  if len(names)!=len(set(names)) or set(names)!=set(index):raise ValueError('Complete archive population differs')
  for name in names:
   p=PurePosixPath(name)
   if p.is_absolute() or '..' in p.parts or '\\' in name or p.as_posix()!=name:raise ValueError('Unsafe retained archive name')
   h=hashlib.sha256();size=0
   with archive.open(name) as stream:
    for block in iter(lambda:stream.read(1048576),b''):h.update(block);size+=len(block)
   if dict(bytes=size,sha256=h.hexdigest())!=index[name]:raise ValueError('Exact retained archive member differs: '+name)
  def j(name):return json.loads(archive.read(name))
  completed=j('validation-3/COMPLETED.json')
  if completed['status']!='PASS_ORIGINAL_RSTATIX072_REVISION2_COMPLETE_POPULATION_AND_DIAGNOSTIC_RECOVERY' or completed['new_tool_credit']!=0 or completed['whole_goal_complete']:raise ValueError('Unsupported qualification scope')
  for n in ['COMPARISON-v2.json','COMPARISON-predecessor-v2.json']:
   c=j('validation-3/'+n)
   if (c['complete_calls'],c['complete_returned_rows'],c['complete_returned_cells'])!=(248,444,5664) or len(c['records'])!=248:raise ValueError('Incomplete original scientific population')
   if c['opaque_RDS_byte_parity_claimed'] or c['formula_execution_environment_pointers_compared'] or c['original_returned_objects_modified']:raise ValueError('Unsupported original-object parity claim')
   if any(not r['complete_original_object_equal'] or not r['caller_rng_states_equal'] for r in c['records']):raise ValueError('Incomplete original object/condition/RNG comparison')
  request=j('validation-3/request-v2.json');expected_results=request['expected_outputs']
  if len(expected_results)!=250:raise ValueError('Complete250-file original result declaration required')
  for folder in ['analysis-v2','analysis-predecessor']:
   prefix='validation-3/'+folder+'/results/'
   if {n[len(prefix):] for n in names if n.startswith(prefix)}!=set(expected_results):raise ValueError('Missing/extra complete scientific result file')
   if not j('validation-3/'+folder+'/analysis-status.json')['result_admitted']:raise ValueError('Complete original run not admitted')
  protocols=j('validation-3/protocols-linux/COMPLETED.json')
  windows=j('validation-3/protocols-windows/COMPLETED.json')
  if protocols['controls']!=41 or windows['controls']!=1 or protocols['new_malformed_worker_status_controls']!=8 or protocols['new_recording_failure_controls']!=2:raise ValueError('Incomplete expanded workflow controls')
  if archive.read('baseline-controls/COMPLETED.json')!=baseline_bytes:raise ValueError('Exact frozen predecessor31-control population differs')
  baseline=j('baseline-controls/COMPLETED.json')
  old_ids={r['id']:r['expected_exit'] for r in baseline['cases']}
  new_ids={r['id']:r['expected_exit'] for r in protocols['cases']}
  if len(old_ids)!=31 or len(new_ids)!=41 or any(new_ids.get(n)!=code for n,code in old_ids.items()):raise ValueError('A previous original workflow case was dropped or changed')
  for case in protocols['cases']:
   base='validation-3/protocols-linux/'+case['id']+'/'
   process=j('validation-3/protocols-linux/'+case['id']+'.process.json')
   if process['exit_code']!=case['expected_exit'] or process['expected_exit']!=case['expected_exit']:raise ValueError('Control process did not return the exact declared refusal/completion')
   if case['id'] in ['existing-output-refusal','wrong-edition']:
    if base+'analysis-status.json' in names or base+'process-live.json' in names or case['expected_exit']!=2:raise ValueError('Pre-ownership refusal created an unexpected analysis')
    continue
   if case['id']=='status-report-path-blocked':
    if base+'analysis-status.json' in names or b'Could not write analysis-status.json' not in archive.read('validation-3/protocols-linux/status-report-path-blocked.stdout'):raise ValueError('Blocked status-recording control differs')
    continue
   state=j(base+'analysis-status.json')
   if state['exit_code']!=case['expected_exit'] or state['result_admitted']!=(case['expected_exit']==0):raise ValueError('Unexpected control admission')
   if case['id'].endswith('worker-status') or case['id']=='zero-exit-early-quit':
    if state['condition_counts_complete'] or not state['warning_review_required'] or any(state[k] is not None for k in ['original_warnings','original_messages','original_errors']):raise ValueError('Unknown original condition totals were treated as known')
    if b'unavailable' not in archive.read(base+'WARNINGS.md') or base+'WORKER_DIAGNOSTIC_ERROR.txt' not in names or base+'MANIFEST.json' not in names:raise ValueError('Incomplete crash-warning/inventory recovery')
   if case['id']=='warning-report-path-blocked' and (not state['manifest_complete'] or b'Could not write WARNINGS.md' not in archive.read('validation-3/protocols-linux/warning-report-path-blocked.stdout')):raise ValueError('Blocked warning-recording control differs')
  before=j('preflight/REPRODUCED.json')
  if before['warnings_present'] or before['manifest_present'] or before['analysis_status']['result_admitted']:raise ValueError('Historical diagnostic bug reproduction changed')
  first=j('validation-1/RESULTS.json')
  if not first['errors'] or not any('complete_original_calls' in e for e in first['errors']):raise ValueError('First failed orchestration evidence omitted')
  for platform,count in [('windows',3),('linux',4)]:
   if j('monitor-controls/'+platform+'/COMPLETED.json')['controls']!=count:raise ValueError('Incomplete monitor race/refusal controls')
  if not j('validation-3/example/analysis/analysis-status.json')['result_admitted']:raise ValueError('Complete public example not admitted')
  return dict(complete_archive_files=len(names),complete_original_calls=248,complete_original_result_files=250,complete_returned_rows=444,complete_returned_cells=5664,linux_workflow_controls=41,windows_platform_refusals=1,malformed_worker_status_controls=8,blocked_recording_controls=2,monitor_controls_windows=3,monitor_controls_linux=4,complete_public_examples=1,failed_attempts_retained=True)

def evidence(root=ROOT):
 data=root/'docs/literature/rstatix072-workflow-revision2';proof=load(data/'VALIDATION.json')
 if proof['status']!='PASS_ORIGINAL_RSTATIX072_REVISION2_FROZEN_COMPLETE_EVIDENCE' or proof['new_tool_credit']!=0 or proof['whole_goal_complete']:raise ValueError('Unsupported revision2 proof/scope')
 require_sources(root,proof['sources'])
 for name,wanted in proof['evidence_pins'].items():
  if pin(data/name)!=wanted:raise ValueError('Evidence binding differs: '+name)
 profile=load(root/'tools/literature/rstatix072_workflow_v2/supervisor-profile.json')
 if profile['generic_helper_functions_unchanged'] or profile['unchanged_helper_functions']!=13:raise ValueError('Monitor revision must be declared explicitly')
 worker='tools/literature/rstatix072_workflow_v2/OriginalWorker.R'
 if pin(root/worker)!=profile['unchanged_original_sources'][profile['original_worker']]:raise ValueError('Original R worker changed')
 old=ast.parse((root/'tools/literature/rstatix072_workflow/run.py').read_text());new=ast.parse((root/'tools/literature/rstatix072_workflow_v2/run.py').read_text())
 functions=lambda tree:{f.name:ast.dump(f,include_attributes=False) for f in tree.body if isinstance(f,ast.FunctionDef)}
 original,current=functions(old),functions(new)
 if any(current[k]!=v for k,v in original.items() if k not in ['main','tree']):raise ValueError('Unrelated original helper changed')
 with zipfile.ZipFile(root/'docs/literature/rstatix072-workflow-development/controls.zip') as archive:baseline=archive.read('protocols/linux/COMPLETED.json')
 index=load(data/'MEMBERS.json');result=archive_evidence(data/'controls.zip',proof['archive'],index,baseline)
 prior=previous(root,data)
 return dict(status=proof['status'],**result,predecessor=prior['status'],original_worker_and_statistical_sources_unchanged=True,scientific_sources_overridden=False,preserved_catalog_inputs=8,new_original_analysis_executed=False,new_tool_credit=0,whole_goal_complete=False)

def main():
 p=argparse.ArgumentParser(description=__doc__,epilog=ACTION);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve();owned=False
 try:
  if not out.is_relative_to(ROOT/'build/literature-review/monocle-runtime'):raise ValueError('Use a NEW monocle-runtime output')
  out.mkdir(parents=True,exist_ok=False);owned=True;result=evidence();(out/'COMPLETED.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf8');(out/'WARNINGS.md').write_text(ACTION+'\n',encoding='utf8');print(result['status'],flush=True);return 0
 except Exception as error:
  code=3 if isinstance(error,OSError) else 2;result=dict(status='SKIP3_REVISION2_PREREQUISITE_UNAVAILABLE' if code==3 else 'FAIL2_REVISION2_EVIDENCE_UNADMITTED',cause=repr(error),traceback=traceback.format_exc(),required_action=ACTION)
  if owned:(out/'FAILURE.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf8');(out/'WARNINGS_FAILURE.md').write_text(result['status']+'\n'+repr(error)+'\n'+ACTION+'\n',encoding='utf8')
  print(result['status'],repr(error),'\n'+ACTION,flush=True);return code
if __name__=='__main__':raise SystemExit(main())
