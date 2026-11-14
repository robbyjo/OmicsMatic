#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Verify complete retained original-script controls, current sources and unchanged native/provider science."""
from pathlib import Path
import argparse,hashlib,json,sys,traceback,zipfile
HERE=Path(__file__).resolve().parent;ROOT=HERE.parents[2];DATA=ROOT/'docs/literature/rstatix072-workflow-development'
sys.path.insert(0,str(HERE.parent));sys.path.insert(0,str(HERE))
from rstatix072_workflow.qualification import qualification
ACTION='Required action: preserve complete original source, both full original RDS objects, every result file, condition/error/warning, failed attempt and predecessor metadata. Missing permitted source/runtime/provider/artifact/download/install/execution SKIP3 skips dependent work. Supply only permitted prerequisites and retry NEW paths. FAIL2 admits no qualification; retain diagnostics and resolve the named contradiction without changing original algorithms or shrinking populations. This gate checks saved complete controls and live scientific bytes; it performs no new fit/analysis or installation. The original supervised script requires explicit trusted-code/execution policies and complete declared outputs. Unknown paper settings, literal wilcoxon_test spelling, historical R/dependency equivalence and whole-paper reproduction remain unresolved. The full250-paper objective remains open.'
def pin(p):
 h=hashlib.sha256();size=0
 with p.open('rb') as f:
  for raw in iter(lambda:f.read(1048576),b''):h.update(raw);size+=len(raw)
 return dict(bytes=size,sha256=h.hexdigest())
def rawpin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
def save(p,value):p.write_text(json.dumps(value,indent=2)+'\n',encoding='utf8',newline='\n')
def evidence():
 proof=json.loads((DATA/'VALIDATION.json').read_bytes())
 if proof['status']!='PASS_ORIGINAL_RSTATIX072_COMPLETE_SCRIPT_WORKFLOW_DEVELOPMENT' or proof['new_tool_credit']!=0 or proof['whole_goal_complete'] or proof['original_scientific_source_modified']:
  raise ValueError('Unsupported original workflow development/scope')
 for key in ['sources','qualified_component_records']:
  for n,wanted in proof[key].items():
   if pin(ROOT/n)!=wanted:raise ValueError('Current qualified source/evidence differs: '+n)
 for key,name in [('archive','controls.zip'),('controls_layout','CONTROLS_LAYOUT.json'),('catalog_before','catalog-before.zip')]:
  if pin(DATA/name)!=proof[key]:raise ValueError('Complete retained evidence differs: '+name)
 files=json.loads((DATA/'CONTROLS_LAYOUT.json').read_bytes())['files']
 with zipfile.ZipFile(DATA/'controls.zip') as archive:
  names=archive.namelist()
  if len(names)!=len(set(names)) or set(names)!=set(files):raise ValueError('Incomplete/duplicate scientific control population')
  for n,wanted in files.items():
   if n.startswith('/') or '\\' in n or any(s in ['', '.', '..'] for s in n.split('/')):raise ValueError('Unsafe scientific evidence member')
   if rawpin(archive.read(n))!=wanted:raise ValueError('Complete scientific evidence member differs: '+n)
  def js(n):return json.loads(archive.read(n))
  for n,wanted in proof['sources'].items():
   if rawpin(archive.read('current-source/'+n))!=wanted:raise ValueError('Current source snapshot differs')
  comparison=js('validation/COMPLETE_COMPARISON.json');records=comparison['records']
  if comparison['status']!='PASS_COMPLETE_ORIGINAL_RSTATIX_RETURNED_OBJECTS_ATTRIBUTES_ARGUMENTS_TABLES_AND_CONDITIONS' or (comparison['complete_calls'],comparison['complete_returned_rows'],comparison['complete_returned_cells'])!=(248,444,5664) or len(records)!=248:
   raise ValueError('Incomplete full original object/attribute/argument/condition comparisons')
  fixture=js('fixtures/fixture-manifest.json')
  if fixture['complete_cases']!=248 or [r['id'] for r in records]!=fixture['ids']:raise ValueError('Original literal case identities/order differ')
  if any(not r['complete_original_object_equal'] or not r['caller_rng_states_equal'] for r in records):raise ValueError('Original returned object/RNG comparison failed')
  expected={'complete-index.tsv','complete-original-results.rds'}|{f'{i:04d}.tsv' for i in range(1,249)}
  for prefix in ['original/direct-reference/','public/complete/results/']:
   actual={n[len(prefix):] for n in names if n.startswith(prefix)}
   if prefix.startswith('original'):actual.discard('direct-session-info.txt')
   if actual!=expected:raise ValueError('Incomplete250-file original scientific result population')
  for n in expected-{'complete-original-results.rds'}:
   if archive.read('original/direct-reference/'+n)!=archive.read('public/complete/results/'+n):raise ValueError('Complete portable original result differs: '+n)
  if not archive.read('original/direct-reference/complete-original-results.rds') or not archive.read('public/complete/results/complete-original-results.rds'):
   raise ValueError('Complete original RDS objects are missing')
  state=js('public/complete/analysis-status.json');worker=js('public/complete/diagnostics/worker-status.json')
  if not state['result_admitted'] or state['exit_code']!=0 or worker['source_functions']!=339 or worker['original_version']!='0.7.2' or worker['R_version']!='4.5.3' or worker['error_count']!=0:
   raise ValueError('Complete original scientific control was unadmitted')
  for prefix in ['public/complete/','public/example/analysis/']:
   manifest=js(prefix+'MANIFEST.json');members={n[len(prefix):] for n in names if n.startswith(prefix)}
   if set(manifest['files'])!=members-{'MANIFEST.json','analysis-status.json'} or set(manifest['excluded_final_records'])!={'MANIFEST.json','analysis-status.json'}:
    raise ValueError('Incomplete original workflow manifest')
   for n,wanted in manifest['files'].items():
    if rawpin(archive.read(prefix+n))!=wanted:raise ValueError('Original workflow manifest differs: '+n)
   if not js(prefix+'analysis-status.json')['result_admitted']:raise ValueError('Public complete example was unadmitted')
  linux=js('protocols/linux/COMPLETED.json');windows=js('protocols/windows/COMPLETED.json')
  if linux['controls']!=31 or windows['controls']!=1:raise ValueError('Incomplete public policy/protocol population')
  for result in [linux,windows]:
   if any(r['exit_code']!=r['expected_exit'] for r in result['calls']):raise ValueError('Original protocol exit differs')
  for name in ['execution-forbidden','trusted-code-forbidden','literal-missing-export','missing-provider','missing-script','missing-input','changed-provider-source','invalid-script-utf8']:
   if 'protocols/linux/'+name+'/process-live.json' in files:raise ValueError('A prerequisite/source refusal launched original analysis')
  for name in ['missing-required-package','wrong-required-package-version']:
   if 'protocols/linux/'+name+'/diagnostics/analysis-started.json' in files:raise ValueError('A missing required dependency launched caller analysis')
  warning=js('protocols/linux/warnings-visible/analysis-status.json');error=js('protocols/linux/warning-as-error/analysis-status.json')
  if warning['original_warnings']!=1 or warning['original_messages']!=1 or not warning['warning_review_required'] or not warning['result_admitted']:
   raise ValueError('Original warning/message visibility differs')
  if error['original_warnings']!=1 or error['original_errors']!=1 or error['result_admitted']:raise ValueError('Original warn=2 semantics changed')
  zero='protocols/linux/zero-exit-early-quit/'
  if js(zero+'process-exit.json')['exit_code']!=0 or js(zero+'analysis-status.json')['result_admitted'] or archive.read(zero+'results/result.txt')!='partial exact α\n'.encode('utf8'):
   raise ValueError('Early zero-exit partial result was admitted/modified')
  for name in ['wall-time-cap','file-byte-cap','workspace-byte-cap','workspace-file-cap']:
   prefix='protocols/linux/'+name+'/'
   if js(prefix+'analysis-status.json')['result_admitted'] or not js(prefix+'process-exit.json')['owned_process_group_terminated']:raise ValueError('Owned capacity/worker refusal differs')
  for name in ['original-package-error','explicit-original-error']:
   if not archive.read('protocols/linux/'+name+'/diagnostics/original-error.rds'):raise ValueError('Complete original error object omitted')
  if js('failed-protocol-harness/HARNESS_FAILURE.json')['status']!='FAILED_HARNESS_ASSERTION_NOT_ORIGINAL_WORKFLOW_FAILURE':raise ValueError('Failed harness history omitted/relabelled')
 for line in (DATA/'SHA256SUMS').read_text().splitlines():
  if line.strip():
   sha,n=line.split(maxsplit=1)
   if pin(DATA/n.lstrip('*'))['sha256']!=sha:raise ValueError('Checkpoint record differs: '+n)
 components=qualification(ROOT,DATA)
 return dict(status='PASS_COMPLETE_RETAINED_ORIGINAL_RSTATIX072_WORKFLOW_AND_LIVE_SCIENTIFIC_SOURCES',complete_archive_files=len(files),complete_original_calls=248,
  complete_returned_rows=444,complete_returned_cells=5664,complete_result_files=250,public_protocol_controls_linux=31,public_platform_refusals_windows=1,
  original_namespace_functions=339,original_exports=126,components=components,original_scientific_source_modified=False,
  native_or_original_analysis_repeated=False,new_tool_credit=0,whole_goal_complete=False)
def main():
 p=argparse.ArgumentParser(description=__doc__,epilog=ACTION);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve();owned=False
 try:
  if not out.is_relative_to(ROOT/'build/literature-review/monocle-runtime'):raise ValueError('Use NEW monocle-runtime output')
  out.mkdir(parents=True,exist_ok=False);owned=True;result=evidence();save(out/'COMPLETED.json',result);(out/'WARNINGS.md').write_text(ACTION+'\n',encoding='utf8',newline='\n');print(result['status'],flush=True);return 0
 except Exception as error:
  code=3 if isinstance(error,OSError) else 2;result=dict(status='SKIP3_RSTATIX_WORKFLOW_VERIFICATION_PREREQUISITE_UNAVAILABLE' if code==3 else 'FAIL2_RSTATIX_WORKFLOW_VERIFICATION',cause=repr(error),traceback=traceback.format_exc(),required_action=ACTION)
  if owned:save(out/'FAILURE.json',result);(out/'WARNINGS_FAILURE.md').write_text(result['status']+'\n'+repr(error)+'\n'+ACTION+'\n',encoding='utf8',newline='\n')
  print(result['status'],repr(error),'\n'+ACTION,flush=True);return code
if __name__=='__main__':raise SystemExit(main())
