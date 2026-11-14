#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Check complete saved original controls and all source/artifact pins without a new image analysis."""
from pathlib import Path
import argparse,hashlib,json,re,sys,traceback,zipfile
HERE=Path(__file__).resolve().parent;ROOT=HERE.parents[2];DATA=ROOT/'docs/literature/qupath051-workflow-development'
ACTION='Required action: retain original source, full control images/hierarchies/tables, every failure and current scope. This command verifies saved controls, complete inventories and current source/artifact bytes; it does not run a new original analysis or independently refit segmentation. Original execution needs the documented complete private native-platform provider and trusted-script request. Missing/forbidden prerequisites SKIP3 skip dependent analysis; supply them only where permitted and retry NEW paths. FAIL2 admits no qualification. Retain raw diagnostics and resolve the named source/output contradiction without changing original algorithms, shrinking the image population or discarding failed cases. Other editions, author ROIs/stains/models and separate extensions remain unresolved. The full250-paper goal stays open.'
def pin(path):
 sha=hashlib.sha256();size=0
 with path.open('rb') as source:
  for raw in iter(lambda:source.read(1024*1024),b''):sha.update(raw);size+=len(raw)
 return dict(bytes=size,sha256=sha.hexdigest())
def rawpin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
def save(path,value):path.write_text(json.dumps(value,indent=2)+'\n',encoding='utf8',newline='\n')
def evidence(out):
 proof=json.loads((DATA/'VALIDATION.json').read_bytes());profile=json.loads((HERE/'source-profile.json').read_bytes())
 if proof['status']!='PASS_ORIGINAL_QUPATH051_SCRIPT_WORKFLOW_DEVELOPMENT' or proof['new_tool_credit']!=0 or proof['whole_goal_complete'] or proof['original_upstream_scientific_source_modified']:
  raise ValueError('Unsupported original workflow qualification or premature credit')
 if profile['source_commit']!='4e6bfc31904be7ee39c10d457149cb962002e1f2' or profile['version']!='0.5.1' or len(profile['source_members'])!=1097:raise ValueError('Selected complete original source identity differs')
 for name,wanted in proof['sources'].items():
  if pin(ROOT/name)!=wanted:raise ValueError('Qualified source differs: '+name)
 artifacts=json.loads((DATA/'ARTIFACTS.json').read_bytes())
 if len(artifacts)!=3:raise ValueError('Incomplete three-controller artifact population')
 for name,wanted in artifacts.items():
  if pin(ROOT/name)!=wanted:raise ValueError('Published development artifact differs: '+name)
 if pin(DATA/'controls.zip')!=proof['archive'] or pin(DATA/'CONTROLS_LAYOUT.json')!=proof['controls_layout']:raise ValueError('Complete evidence archive/inventory differs')
 layout=json.loads((DATA/'CONTROLS_LAYOUT.json').read_bytes());files=layout['files']
 with zipfile.ZipFile(DATA/'controls.zip') as archive:
  names=archive.namelist()
  if len(names)!=len(set(names)) or set(names)!=set(files):raise ValueError('Incomplete or duplicate saved evidence population')
  for name,wanted in files.items():
   if name.startswith('/') or '\\' in name or any(p in ['', '.', '..'] for p in name.split('/')):raise ValueError('Unsafe saved evidence member')
   if rawpin(archive.read(name))!=wanted:raise ValueError('Saved original evidence member differs: '+name)
  def read(name):return archive.read(name)
  def js(name):return json.loads(read(name))
  for name,wanted in proof['sources'].items():
   if rawpin(read('current-source/'+name))!=wanted:raise ValueError('Complete current-source snapshot differs: '+name)
  if rawpin(read('original-source/'+profile['source_archive']['name']))!={k:profile['source_archive'][k] for k in ['bytes','sha256']}:raise ValueError('Complete original source archive differs')
  summaries={};counts={}
  for platform,scope in proof['platform_controls'].items():
   actual=scope['controls'];reference=scope['reference'];result=js(actual+'/COMPLETED.json')
   if result['status']!='PASS_ORIGINAL_QUPATH051_COMPLETE_PORTABLE_CELL_EXPORTS_AND_PUBLIC_ADMISSION_CONTROLS' or result['complete_image_jobs']!=6 or result['complete_detections']!=72 or result['complete_original_portable_files_compared']!=13 or result['protocol_controls']!=scope['protocol_controls']:
    raise ValueError('Incomplete original/control population: '+platform)
   if len(result['calls'])!=1+scope['protocol_controls'] or any(c['exit_code']!=c['expected_exit'] for c in result['calls']):raise ValueError('Original protocol exit population differs')
   prefix=actual+'/actual/complete-cells';status=js(prefix+'/analysis-status.json')
   if not status['result_admitted'] or status['exit_code']!=0 or status['original_error_headers']!=0:raise ValueError('Complete scientific control was not admitted')
   if js(prefix+'/process-exit.json')['exit_code']!=0:raise ValueError('Original scientific process failed')
   settings=js(reference+'/inputs/settings.json');expected={'complete-summary.json'}|{r['id']+suffix for r in settings['jobs'] for suffix in ['.raw.qpdata','.qpdata','.measurements.tsv','.objects.geojson']}
   for base in [reference+'/results/',prefix+'/results/']:
    if {n[len(base):] for n in names if n.startswith(base)}!=expected:raise ValueError('Incomplete25-file original scientific output population')
   portable=sorted(n for n in expected if not n.endswith('.qpdata'))
   if len(portable)!=13:raise ValueError('Incomplete portable original comparison')
   for name in portable:
    if read(reference+'/results/'+name)!=read(prefix+'/results/'+name):raise ValueError('Complete portable original output differs: '+platform+'/'+name)
   summary=js(prefix+'/results/complete-summary.json')
   if [r['detections'] for r in summary]!=[16,0,8,16,16,16] or sum(r['detections'] for r in summary)!=72:raise ValueError('Synthetic original cell population differs')
   if summary[0]['class_counts']!={'1+':4,'2+':4,'3+':4,'Negative':4}:raise ValueError('Full original intensity-class control differs')
   if 'α'.encode() not in read(prefix+'/results/all-16.measurements.tsv'):raise ValueError('Original literal Unicode object names are missing')
   if scope['protocol_controls']:
    error=actual+'/actual/logged-error-with-zero-exit';warning=actual+'/actual/warning-visible'
    if js(error+'/process-exit.json')['exit_code']!=0 or js(error+'/analysis-status.json')['result_admitted'] or js(error+'/analysis-status.json')['original_error_headers']!=1:raise ValueError('Original zero-exit ERROR was incorrectly admitted')
    if not js(warning+'/analysis-status.json')['warning_review_required'] or js(warning+'/analysis-status.json')['original_warning_headers']!=1:raise ValueError('Original warning population was not retained/prominent')
    for label in ['execution-skip','trusted-code-skip','missing-provider','missing-class']:
     if actual+'/actual/'+label+'/process-live.json' in files:raise ValueError('A skipped dependency launched original analysis')
   summaries[platform]=summary;counts[platform]=dict(images=6,detections=72,complete_portable_exports=13,raw_and_fixture_qpdata_files=12,protocol_controls=scope['protocol_controls'])
  for platform,prefix in proof['setup_controls'].items():
   result=js(prefix+'/COMPLETED.json')
   if result['status']!='PASS_QUPATH051_SETUP_POLICY_CACHE_AND_ARCHIVE_BOUNDARIES' or (result['public_setup_controls'],result['archive_boundary_controls'])!=(6,5):raise ValueError('Incomplete setup refusal controls')
  for platform,prefix in proof['setup_records'].items():
   ready=js(prefix+'/READY.json')
   if ready['status']!='READY_PRIVATE_ORIGINAL_QUPATH051_NOT_ANALYSIS' or ready['version']!='0.5.1' or ready['complete_source_files']!=1097:raise ValueError('Original private installation record differs')
   if ready['source_profile']!=pin(HERE/'source-profile.json'):raise ValueError('Original installed source profile differs')
  census=json.loads((DATA/'CENSUS.json').read_bytes());reviews=json.loads((HERE/'paper-use-intake.json').read_bytes())['reviews']
  if len(census['census'])!=250 or len(census['matches'])!=16 or len(reviews)!=12:raise ValueError('Incomplete primary name/individual review population')
  named={(r['paper_id'],r['paragraph_index'],r['paragraph_sha256'],r['section']) for r in census['matches']}
  supports={(r['paper_id'],a['paragraph_index'],a['paragraph_sha256'],a['section']) for r in reviews for a in r['supporting_evidence']}
  if named!=supports or len(named)!=16:raise ValueError('Individual reviews do not cover every named primary anchor')
 for line in (DATA/'SHA256SUMS').read_text().splitlines():
  if line.strip():
   sha,name=line.split(maxsplit=1)
   if pin(DATA/name.lstrip('*'))['sha256']!=sha:raise ValueError('Checkpoint record differs: '+name)
 return dict(status='PASS_COMPLETE_RETAINED_QUPATH051_ORIGINAL_CONTROLS_AND_CURRENT_SOURCES',complete_archive_files=len(files),
  controllers=counts,source_files=1097,reviewed_papers=12,named_primary_anchors=16,native_runtime_or_cache_redistributed=False,
  opaque_qpdata_byte_parity_claimed=False,new_original_analysis_executed=False,paper_reproduced=False,new_tool_credit=0,whole_goal_complete=False)

def main():
 p=argparse.ArgumentParser(description=__doc__,epilog=ACTION);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve();owned=False
 try:
  if not out.is_relative_to(ROOT/'build/literature-review/monocle-runtime'):raise ValueError('Use NEW monocle-runtime output')
  out.mkdir(parents=True,exist_ok=False);owned=True;result=evidence(out);save(out/'COMPLETED.json',result);(out/'WARNINGS.md').write_text(ACTION+'\n',encoding='utf8',newline='\n');print(result['status'],flush=True);return 0
 except Exception as e:
  code=3 if isinstance(e,OSError) else 2;result=dict(status='SKIP3_QUPATH_VERIFICATION_PREREQUISITE_UNAVAILABLE' if code==3 else 'FAIL2_QUPATH051_RETAINED_VERIFICATION',cause=repr(e),traceback=traceback.format_exc(),required_action=ACTION)
  if owned:save(out/'FAILURE.json',result);(out/'WARNINGS_FAILURE.md').write_text(result['status']+'\n'+repr(e)+'\n'+ACTION+'\n',encoding='utf8',newline='\n')
  print(result['status'],repr(e),'\n'+ACTION,flush=True);return code
if __name__=='__main__':raise SystemExit(main())
