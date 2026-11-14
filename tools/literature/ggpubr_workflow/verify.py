#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Verify complete retained original ggpubr controls, source roles and current source bytes; execute no new R analysis."""
from pathlib import Path
import argparse,hashlib,io,json,sys,tarfile,traceback,zipfile
HERE=Path(__file__).resolve().parent;ROOT=HERE.parents[2];DATA=ROOT/'docs/literature/ggpubr-workflow-development'
sys.path.insert(0,str(HERE.parent))
from ggpubr_workflow.source import scan,validate
ACTION='Required action: retain the complete evidence archive and inventories, exact original sources, all earlier failures, primary XML identities and predecessor catalog metadata. Missing/forbidden source, evidence or runtime SKIP3 skips dependent verification/analysis; restore permitted prerequisites and retry NEW paths. FAIL2 admits no qualification: preserve raw diagnostics and correct the named contradiction without changing original algorithms, dropping failed cases or refreshing hashes merely to force success. This command checks retained evidence and current source bytes; it does not install a runtime or refit plots/statistics. Read GGPUBR_WORKFLOW_DEVELOPMENT.md for actual execution, every warning, caller eligibility checks, persistent Linux providers and recovery. The full250-paper objective remains open.'

def rawpin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
def pin(path):return rawpin(path.read_bytes())
def archive_bytes(data,proof):
 parts=proof.get('archive_parts',{})
 if parts:
  if list(parts)!=['controls.zip.part'+str(i).zfill(3) for i in range(1,len(parts)+1)]:raise ValueError('Incomplete or reordered archive parts')
  pieces=[]
  for name,wanted in parts.items():
   raw=(data/name).read_bytes()
   if rawpin(raw)!=wanted:raise ValueError('Evidence archive part differs: '+name)
   pieces.append(raw)
  raw=b''.join(pieces)
 else:raw=(data/'controls.zip').read_bytes()
 if rawpin(raw)!=proof['archive']:raise ValueError('Complete retained evidence archive differs')
 return raw

def evidence(rescan=False):
 proof=json.loads((DATA/'VALIDATION.json').read_bytes())
 if proof['status']!='PASS_ORIGINAL_GGPUBR040_AND060_COMPLETE_WORKFLOW_DEVELOPMENT' or proof['new_tool_credit']!=0 or proof['whole_goal_complete'] or proof['paper_reproduced'] or proof['original_scientific_source_modified']:raise ValueError('Unsupported qualification or premature whole-goal/paper credit')
 for name,wanted in proof['sources'].items():
  if pin(ROOT/name)!=wanted:raise ValueError('Current qualified source differs: '+name)
 artifacts=json.loads((DATA/'ARTIFACTS.json').read_bytes())
 expected_artifacts={'tools/literature/ggpubr/provider.py','tools/literature/ggpubr_workflow/run.py','tools/literature/ggpubr_workflow/OriginalWorker.R','tools/literature/ggpubr_workflow/make_example.py'}
 if set(artifacts)!=expected_artifacts:raise ValueError('Incomplete original source-entrypoint artifact population')
 for name,wanted in artifacts.items():
  if pin(ROOT/name)!={k:wanted[k] for k in ['bytes','sha256']}:raise ValueError('Qualified source entry point differs: '+name)
 for name,wanted in proof['main_catalog_files'].items():
  if pin(ROOT/name)!=wanted:raise ValueError('Development checkpoint catalog baseline differs: '+name)
 if pin(DATA/'catalog-before.zip')!=proof['catalog_before']:raise ValueError('Exact predecessor catalog archive differs')
 with zipfile.ZipFile(DATA/'catalog-before.zip') as z:
  if len(z.namelist())!=8 or set(z.namelist())!=set(proof['main_catalog_files']):raise ValueError('Only the exact eight predecessor metadata paths are allowed')
  for name,wanted in proof['main_catalog_files'].items():
   if rawpin(z.read(name))!=wanted:raise ValueError('Predecessor catalog member differs: '+name)
 if pin(DATA/'CONTROLS_LAYOUT.json')!=proof['controls_layout']:raise ValueError('Complete evidence inventory differs')
 layout=json.loads((DATA/'CONTROLS_LAYOUT.json').read_bytes());files=layout['files'];links=layout['retained_link_identities']
 with zipfile.ZipFile(io.BytesIO(archive_bytes(DATA,proof))) as z:
  names=z.namelist()
  if len(names)!=len(set(names)) or set(names)!=set(files):raise ValueError('Incomplete or duplicate retained scientific evidence population')
  for name,wanted in files.items():
   if name.startswith('/') or '\\' in name or any(p in ['', '.', '..'] for p in name.split('/')):raise ValueError('Unsafe retained archive member')
   if rawpin(z.read(name))!=wanted:raise ValueError('Retained scientific member differs: '+name)
  def js(name):return json.loads(z.read(name))
  def complete_manifest(prefix):
   manifest=js(prefix+'MANIFEST.json');present={n[len(prefix):] for n in names if n.startswith(prefix)}
   link_names={n[len(prefix):] for n in links if n.startswith(prefix)}
   if set(manifest['excluded_final_records'])!={'MANIFEST.json','analysis-status.json'} or set(manifest['files'])!=(present|link_names)-{'MANIFEST.json','analysis-status.json'}:raise ValueError('Incomplete original run manifest: '+prefix)
   for name,wanted in manifest['files'].items():
    if wanted.get('kind')=='symlink':
     if links.get(prefix+name)!=wanted:raise ValueError('Retained forbidden-link identity differs')
    elif rawpin(z.read(prefix+name))!=wanted:raise ValueError('Original manifest member differs: '+prefix+name)
  for name,wanted in proof['sources'].items():
   if rawpin(z.read('current-source/'+name))!=wanted:raise ValueError('Current source snapshot differs: '+name)
  profile=json.loads((ROOT/'tools/literature/ggpubr/source-profiles.json').read_bytes())
  for spec in profile['profiles'].values():
   raw=z.read('original-source/'+spec['archive_name'])
   if rawpin(raw)!=spec['archive']:raise ValueError('Complete original upstream archive differs')
   with tarfile.open(fileobj=io.BytesIO(raw)) as t:
    members=[m for m in t.getmembers() if not m.isdir()]
    if len(members)!=len(spec['source_files']) or {m.name for m in members}!=set(spec['source_files']):raise ValueError('Incomplete original source members')
    for m in members:
     if not m.isfile() or rawpin(t.extractfile(m).read())!=spec['source_files'][m.name]:raise ValueError('Original source archive member differs')
  fixture=js('complete/current/fixtures/fixture-manifest.json')
  if (fixture['case_count'],fixture['plot_count'],fixture['table_count'])!=(79,54,25):raise ValueError('Incomplete predeclared original case matrix')
  for version in ['0.4.0','0.6.0']:
   prefix='complete/current/actual-'+version+'/'
   state=js(prefix+'analysis-status.json');worker=js(prefix+'diagnostics/worker-status.json')
   if not state['result_admitted'] or state['exit_code']!=0 or not state['condition_counts_complete'] or worker['version']!=version or worker['R_version']!='4.3.3' or worker['error_count'] or worker['failed_layer_warning_count']:raise ValueError('Original complete population was unadmitted')
   complete_manifest(prefix)
   results=js(prefix+'result-files.json');request=js('complete/current/request-'+version+'.json')
   if len(results)!=294 or set(results)!=set(request['expected_outputs']):raise ValueError('Incomplete original result file population')
   comparison=js('complete/current/COMPARISON-'+version+'.json')
   if (comparison['complete_cases'],comparison['complete_plots'],comparison['complete_tables'],comparison['complete_table_cells'],comparison['complete_layer_cells'])!=(79,54,25,416,171084):raise ValueError('Incomplete original numerical/layer population')
   if set(comparison['records'])!=set(fixture['cases']) or any(not r['complete_portable_object_equal'] or not r['caller_rng_states_equal'] for r in comparison['records'].values()):raise ValueError('Original complete objects, conditions or random states differed')
   portable=js('complete/current/COMPLETE_PORTABLE_FILES-'+version+'.json')
   if len(portable)!=134 or {r['path'] for r in portable}!={n for n in results if n.endswith(('.png','.svg','.tsv'))}:raise ValueError('Incomplete portable SVG/PNG/table population')
   for row in portable:
    wanted={k:row[k] for k in ['bytes','sha256']}
    if rawpin(z.read(prefix+'results/'+row['path']))!=wanted or rawpin(z.read('complete/current/direct-'+version+'/'+row['path']))!=wanted:raise ValueError('Complete direct-original portable output differs')
   ready=js('setup/persistent-provider-'+version+'/READY.json')
   expected={'ggplot2':(882,115,536),'rstatix':(339,0,126),'ggpubr':(281,12,109) if version=='0.4.0' else (329,15,122)}
   for package,counts in expected.items():
    check=ready['namespace_checks'][package]
    if (len(check['direct_source_functions']),len(check['source_ggproto_objects']),len(check['exports']))!=counts:raise ValueError('Incomplete original namespace function/prototype/export readiness')
   base='protocols/current/protocols-linux-'+version+'/'
   controls=js(base+'COMPLETED.json')
   if controls['controls']!=36 or len(controls['direct_original_calls'])!=1 or controls['direct_original_calls'][0]['exit_code']!=0 or any(r['exit_code']!=r['expected_exit'] for r in controls['calls']):raise ValueError('Incomplete original protocol population/exit behavior')
   for case in controls['cases']:
    folder=base+case['id']+'/'
    if folder+'MANIFEST.json' in files:complete_manifest(folder)
   failed=js(base+'original-statistical-layer-failure/analysis-status.json');original=js(base+'direct-original-statistical-layer-failure/direct-status.json')
   if failed['result_admitted'] or failed['exit_code']!=2 or failed['failed_layer_warnings']<1 or failed['original_errors']!=0 or original['warning_count']<1:raise ValueError('Original warning-only statistical failure was admitted or erased')
   empty=js(base+'caller-rejects-empty-statistical-layer/analysis-status.json')
   if empty['result_admitted'] or empty['original_errors']!=1 or empty['failed_layer_warnings']!=0 or z.read(base+'caller-rejects-empty-statistical-layer/results/result.txt')!=b'0\n':raise ValueError('Caller empty-layer eligibility refusal differs')
   declared=js(base+'explicit-declared-empty-layer/analysis-status.json')
   if not declared['result_admitted'] or declared['failed_layer_warnings']!=0 or z.read(base+'explicit-declared-empty-layer/results/result.txt')!=b'0\n':raise ValueError('Explicit empty-layer protocol boundary was changed')
   partial=js(base+'partial-worker-status-diagnostics/analysis-status.json')
   if partial['result_admitted'] or partial['condition_counts_complete'] or partial['original_warnings'] is not None or partial['original_errors'] is not None or not partial['warning_review_required'] or z.read(base+'partial-worker-status-diagnostics/diagnostics/worker-status.json')!=b'{"status":':raise ValueError('Truncated original diagnostics were repaired, hidden or reported as zero')
   if b'unavailable' not in z.read(base+'partial-worker-status-diagnostics/WARNINGS.md'):raise ValueError('Incomplete diagnostics recovery guidance omitted')
   for case in ['wall-time-cap','file-byte-cap','workspace-byte-cap','workspace-file-cap']:
    if not js(base+case+'/process-exit.json')['owned_process_group_terminated'] or js(base+case+'/analysis-status.json')['result_admitted']:raise ValueError('Owned worker capacity failure was admitted')
   for family,count in [('protocols-windows',1),('setup-linux',8),('setup-windows',1)]:
    record=js('protocols/current/'+family+'-'+version+'/COMPLETED.json')
    if record['controls']!=count or any(r['exit_code']!=r['expected_exit'] for r in record['calls']):raise ValueError('Missing platform/setup control population')
   example='protocols/current/example-'+version+'/analysis/'
   complete_manifest(example)
   if not js(example+'analysis-status.json')['result_admitted'] or len(js(example+'result-files.json'))!=10:raise ValueError('Complete public example was not admitted')
  if js('failed/initial-grouping/HARNESS_FAILURE.json')['status']!='FAILED_SYNTHETIC_CALLER_GROUPING_ASSERTION_NOT_ORIGINAL_PACKAGE_FAILURE':raise ValueError('Earlier grouping-call failure history omitted')
  if js('failed/undeclared-device/HARNESS_FAILURE.json')['status']!='FAILED_UNDECLARED_ORIGINAL_DEFAULT_GRAPHICS_DEVICE_OUTPUT_NOT_SCIENTIFIC_SOURCE_FAILURE':raise ValueError('Earlier undeclared-device failure history omitted')
  if js('failed/diagnostic-finalization/ADAPTER_FAILURE.json')['status']!='FAIL2_ADAPTER_FINALIZATION_BUG_EXPOSED_BY_SYMLINK_REFUSAL':raise ValueError('Earlier adapter diagnostic bug history omitted')
 for line in (DATA/'SHA256SUMS').read_text().splitlines():
  if line.strip():
   sha,name=line.split(maxsplit=1)
   if pin(DATA/name.lstrip('*'))['sha256']!=sha:raise ValueError('Checkpoint root record differs: '+name)
 papers=json.loads((ROOT/'docs/literature/omics-2023-2026/papers.json').read_bytes());intake=json.loads((ROOT/'tools/literature/ggpubr/paper-use-intake.json').read_bytes());census=json.loads((DATA/'CENSUS.json').read_bytes())
 roles=validate(census,papers,intake)
 if rescan and scan(ROOT,papers,intake)!=census:raise ValueError('Fresh250-source census differs')
 return dict(status='PASS_COMPLETE_RETAINED_ORIGINAL_GGPUBR040_AND060_WORKFLOWS_AND_CURRENT_SOURCES',complete_archive_files=len(files),retained_links=len(links),
  complete_cases_per_edition=79,complete_plots_per_edition=54,complete_tables_per_edition=25,complete_result_files_per_edition=294,
  complete_table_cells_per_edition=416,complete_layer_cells_per_edition=171084,complete_portable_file_comparisons_per_edition=134,
  public_protocol_controls_linux=72,public_platform_refusals_windows=2,setup_controls_linux=16,setup_platform_refusals_windows=2,public_examples=2,
  primary_source_scope=roles,primary_sources_rescanned=rescan,original_scientific_source_modified=False,new_original_analysis_executed=False,new_tool_credit=0,whole_goal_complete=False)

def main():
 p=argparse.ArgumentParser(description=__doc__,epilog=ACTION);p.add_argument('--out',type=Path,required=True);p.add_argument('--rescan-sources',action='store_true');a=p.parse_args();out=a.out.resolve();owned=False
 try:
  if not out.is_relative_to(ROOT/'build/literature-review/monocle-runtime'):raise ValueError('Use NEW monocle-runtime output')
  out.mkdir(parents=True,exist_ok=False);owned=True;result=evidence(a.rescan_sources);(out/'COMPLETED.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf8');(out/'WARNINGS.md').write_text(ACTION+'\n',encoding='utf8');print(result['status'],flush=True);return 0
 except Exception as error:
  code=3 if isinstance(error,OSError) else 2;result=dict(status='SKIP3_GGPUBR_VERIFICATION_PREREQUISITE_UNAVAILABLE' if code==3 else 'FAIL2_GGPUBR_WORKFLOW_VERIFICATION',cause=repr(error),traceback=traceback.format_exc(),required_action=ACTION)
  if owned:(out/'FAILURE.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf8');(out/'WARNINGS_FAILURE.md').write_text(result['status']+'\n'+repr(error)+'\n'+ACTION+'\n',encoding='utf8')
  print(result['status'],repr(error),'\n'+ACTION,flush=True);return code
if __name__=='__main__':raise SystemExit(main())
