#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Verify complete original CPU evidence, every stage value, refusals and retained failures without fitting models."""
from pathlib import Path,PurePosixPath
import argparse,base64,hashlib,json,re,sys,traceback,uuid,zipfile
sys.dont_write_bytecode=True
HERE=Path(__file__).resolve().parent;ROOT=HERE.parents[2];DATA='docs/literature/cell2location013-development'
sys.path.insert(0,str(HERE))
from EvidenceStore import EvidenceStore,pin,rawpin
ACTION='SKIP3 skips dependent verification/analysis when exact permitted sources or complete evidence are unavailable. Supply the named matching prerequisites and retry NEW paths. FAIL2 admits no complete result after an integrity, complete-population or scientific-value mismatch. Preserve every source, original object, partial file, condition, raw console and retained failed attempt; do not change hashes or drop cases to obtain a pass. This frozen gate executes no original analysis or installation and establishes no independent statistical validity, convergence, author-environment identity or paper reproduction. Read CELL2LOCATION013_DEVELOPMENT.md and the current catalog for coverage.'
def load(p):return json.loads(p.read_bytes())
def require_sources(root,expected):
 for name,wanted in expected.items():
  p=root/name
  if p.is_symlink() or pin(p)!=wanted:raise ValueError('Qualified cell2location source/evidence differs: '+name)

def encoded(v):return json.dumps(v,ensure_ascii=False,separators=(',',':'))

def semantic_key(key,index):
 result=[];i=0
 while i<len(key):
  prefix=key[:i];node=index.get(encoded(prefix))
  if node and node['kind'] in ['builtins.dict','collections.OrderedDict'] and key[i]=='item' and i+1<len(key):
   literal=index[encoded(prefix+['keys',key[i+1]])]
   if literal['kind'] not in ['str','int','bool']:raise ValueError('Unsupported dictionary key type; compare complete raw graph rather than omit keys')
   result.append(literal['value']);i+=2
  else:result.append(key[i]);i+=1
 return result

def metadata_exception(key,left,right,left_root,right_root):
 if len(key)>=2 and key[-2]=='uns' and key[-1] in ['_scvi_uuid','_scvi_manager_uuid']:
  assert left['kind']==right['kind']=='str'
  for row in [left,right]:assert str(uuid.UUID(row['value']))==row['value']
  return 'Original scvi-generated manager/object UUID; both untouched raw values retained.'
 if key[:3]==['colocation','complete-original-return',0] and len(key)==6 and key[3] in ['n_fact2','n_fact3'] and key[-2:]==['export_args','path']:
  assert left['kind']==right['kind']=='str' and left['value']==str(left_root/'colocation') and right['value']==str(right_root/'colocation')
  return 'Only the exact caller-selected output directory; both raw original paths retained.'
 if key[:3]==['colocation','complete-original-return',0] and len(key)==5 and key[3] in ['n_fact2','n_fact3'] and key[-1]=='run_time':
  for row in [left,right]:assert row['kind']=='str' and re.fullmatch(r'[0-9]+(?:\.[0-9]+)? min',row['value'])
  return 'Original elapsed wall time; both raw timing strings retained.'
 return None

def stage_comparison(store,left,right,comparison):
 docs=[store.json(root+'/COMPLETE-NUMERICAL-INDEX.json') for root in [left,right]]
 for root,doc in zip([left,right],docs):
  if doc['schema_version']!=2 or doc['snapshot_scope']!='independent-top-level-stage-values' or doc['opaque_values'] or doc['nonfinite_values_repaired'] or len(doc['snapshot_roots'])!=17 or len(doc['records'])!=4658:raise ValueError('Complete independently captured17-stage original graph required')
  arrays=[r for r in doc['records'] if r['kind']=='array']
  actual={n[len(root)+1:] for n in store.index['files'] if n.startswith(root+'/arrays/')}
  if len(arrays)!=647 or {r['file'] for r in arrays}!=actual:raise ValueError('Incomplete independently recorded original array population')
  if sum(r['kind']=='sklearn.decomposition._nmf.NMF' for r in doc['records'])!=4:raise ValueError('A complete original NMF model/restart was omitted')
  if any(r['kind']=='reference' and r['key'][:2]!=r['target'][:2] for r in doc['records']):raise ValueError('An independently required stage was replaced by an earlier snapshot reference')
 if docs[0]['snapshot_roots']!=docs[1]['snapshot_roots'] or docs[0]['nonfinite_arrays']!=docs[1]['nonfinite_arrays']:raise ValueError('Complete stage/nonfinite declarations differ')
 indices=[{encoded(r['key']):r for r in d['records']} for d in docs]
 if len(indices[0])!=4658 or set(indices[0])!=set(indices[1]):raise ValueError('Duplicate/missing complete graph keys')
 base='/mnt/d/projects/JLinAlg/build/literature-review/monocle-runtime/'
 exceptions=[];arrays=[]
 for row in docs[0]['records']:
  other=indices[1][encoded(row['key'])];key=semantic_key(row['key'],indices[0])
  if key!=semantic_key(other['key'],indices[1]):raise ValueError('Literal original graph key differs')
  reason=metadata_exception(key,row,other,PurePosixPath(base+left),PurePosixPath(base+right))
  if reason:exceptions.append(dict(key=key,left=row,right=other,reason=reason));continue
  if row!=other:raise ValueError('A complete original scientific graph value differs: '+repr(key))
  if row['kind']=='array':
   l=store.index['files'][left+'/'+row['file']];r=store.index['files'][right+'/'+other['file']]
   if {k:l[k] for k in ['bytes','sha256']}!={k:r[k] for k in ['bytes','sha256']}:raise ValueError('Complete original array bytes differ: '+repr(key))
   arrays.append((row,l,r))
 result=store.json(comparison+'/COMPLETED.json')
 if (result['graph_records'],result['array_records'],result['array_elements'])!=(4658,647,65212) or result['metadata_exceptions']!=exceptions or len(result['arrays'])!=647:raise ValueError('Retained complete numerical comparison population differs')
 if result['opaque_objects_omitted'] or result['nonfinite_values_repaired'] or result['pdf_byte_parity_claimed'] or result['opaque_pickle_byte_parity_claimed']:raise ValueError('Unsupported scientific/opaque-byte parity claim')
 for actual,(record,l,r) in zip(result['arrays'],arrays):
  if actual['file']!=record['file'] or actual['left']!={k:l[k] for k in ['bytes','sha256']} or actual['right']!={k:r[k] for k in ['bytes','sha256']} or not actual['equal_at_tolerance_zero']:raise ValueError('Complete comparator-to-original array binding differs')
 if store.read(left+'/input-identities.json')!=store.read(right+'/input-identities.json') or store.read(left+'/parameters.json')!=store.read(right+'/parameters.json'):raise ValueError('Literal original inputs/settings differ')
 return dict(complete_stage_snapshots=17,complete_graph_records=4658,complete_original_arrays=647,array_elements=65212,generated_metadata_exceptions=len(exceptions),original_array_bytes_equal=True)

def protocol_population(store):
 root='cell2location-workflow-validation-2';proof=store.json(root+'/COMPLETED.json')
 if (proof['linux_controls'],proof['windows_refusals'],proof['monitor_controls'])!=(41,1,7):raise ValueError('Incomplete workflow control populations')
 protocols=store.json(root+'/protocols-linux/COMPLETED.json')
 skipped={'execution-forbidden','trusted-code-forbidden','missing-provider','missing-script','missing-input','literal-missing-symbol','missing-required-distribution','wrong-required-version'}
 passed={'warnings-visible','complete-positive'}
 failed={'warning-as-error','original-package-error','explicit-original-error','zero-exit-early-quit','missing-marker','wrong-marker','missing-result','extra-result','changed-captured-input','extra-captured-input','unexpected-root-output','workspace-symlink','truncated-result','wall-time-cap','file-byte-cap','workspace-byte-cap','workspace-file-cap','workspace-file-cap-after-readiness','invalid-script-UTF8','wrong-edition','existing-output-refusal','truncated-worker-status','negative-count-worker-status','boolean-count-worker-status','fractional-count-worker-status','missing-count-worker-status','duplicate-count-worker-status','nonfinite-count-worker-status','nonobject-worker-status','warning-report-path-blocked','status-report-path-blocked'}
 cases={c['id']:c for c in protocols['cases']}
 if len(cases)!=41 or set(cases)!=skipped|passed|failed:raise ValueError('A full original policy/recovery control was omitted or replaced')
 for name,case in cases.items():
  wanted=0 if name in passed else 3 if name in skipped else 2
  if case['exit_code']!=wanted or case['result_admitted']!=(wanted==0):raise ValueError('Unexpected original control exit/admission: '+name)
  prefix=root+'/protocols-linux/'+name
  if name in ['existing-output-refusal','wrong-edition']:
   if prefix+'/analysis-status.json' in store.index['files']:raise ValueError('Pre-ownership refusal created an unexpected result')
   continue
  if name=='status-report-path-blocked':
   if prefix+'/analysis-status.json' in store.index['files'] or prefix+'/analysis-status.json' not in store.index['directories']:raise ValueError('Blocked status identity differs')
   if b'FAIL2' not in store.read(prefix+'/WARNINGS.md'):raise ValueError('Blocked-status recovery warning missing')
   continue
  state=store.json(prefix+'/analysis-status.json')
  if state['exit_code']!=wanted or state['result_admitted']!=(wanted==0):raise ValueError('Original complete control status differs: '+name)
  if name.endswith('worker-status') or name=='zero-exit-early-quit':
   if state['condition_counts_complete'] or not state['warning_review_required'] or any(state[k] is not None for k in ['original_warnings','original_log_conditions','original_errors']):raise ValueError('Unknown condition totals were made to look known')
   if b'unavailable' not in store.read(prefix+'/WARNINGS.md') or prefix+'/WORKER_DIAGNOSTIC_ERROR.txt' not in store.index['files']:raise ValueError('Interrupted-report recovery evidence missing')
   if store.json(prefix+'/process-exit.json')['exit_code']!=0:raise ValueError('The zero-exit malformed-report control was changed')
  if name=='warning-report-path-blocked' and (prefix+'/WARNINGS.md' not in store.index['directories'] or not state['warning_review_required']):raise ValueError('Blocked warning path was not prominently refused')
  manifest=store.json(prefix+'/MANIFEST.json')
  for n,w in manifest['files'].items():
   logical=prefix+'/'+n
   if w.get('kind')=='symlink':
    if store.index['links'].get(logical,{}).get('target')!=w['target']:raise ValueError('Retained original link identity differs')
   elif {k:store.index['files'][logical][k] for k in ['bytes','sha256']}!=w:raise ValueError('Complete original final manifest differs: '+logical)
 if not cases['workspace-file-cap-after-readiness']['analysis_started']:raise ValueError('Post-readiness entry-cap control never reached analysis')
 for platform,count in [('linux',4),('windows',3)]:
  if store.json(root+'/monitor-'+platform+'/COMPLETED.json')['controls']!=count:raise ValueError('Incomplete monitor race/identity controls')
 if store.json(root+'/protocols-windows/COMPLETED.json')['controls']!=1:raise ValueError('Windows original-execution refusal missing')
 return dict(linux_workflow_controls=41,windows_platform_refusals=1,monitor_controls=7,malformed_worker_reports=8,blocked_recording_paths=2,complete_previous_case_population_preserved=True)

def archive_evidence(archive,index,expected_archive,expected_index):
 if rawpin((json.dumps(index,indent=2,ensure_ascii=False)+'\n').encode('utf8'))!=expected_index:raise ValueError('Exact complete lossless logical evidence index differs')
 store=EvidenceStore(archive,index);result=store.verify(expected_archive)
 left='cell2location-original-run-7/direct-original';right='cell2location-original-run-8/direct-original'
 repeat=stage_comparison(store,left,right,'cell2location-stage-snapshot-validation-1/complete-independent-stages')
 public=stage_comparison(store,left,'cell2location-final-example-1/example/analysis/results/workflow','cell2location-final-example-1/comparison')
 workflows=protocol_population(store)
 setup=store.json('cell2location-public-install-1/setup/COMPLETED.json')
 if setup['status']!='INSTALLED_AND_CAPTURED_PRIVATE_ORIGINAL_CELL2LOCATION013_CPU_NOT_ANALYSIS' or setup['global_python_links_created'] or setup['existing_runtime_modified'] or setup['analysis_executed']:raise ValueError('Permitted original setup scope differs')
 ready=store.json('cell2location-public-install-1/setup/provider/READY.json')
 if (len(ready['original_package_files']),len(ready['complete_source_declarations']),len(ready['installed_distribution_records']),len(ready['import_visible_distribution_records']))!=(44,249,103,119):raise ValueError('Complete original source/dependency visibility population differs')
 if not any('--no-bin' in r['command'] for r in setup['processes'] if r['label']=='standalone-python'):raise ValueError('Private setup did not explicitly preserve global Python links')
 for platform,count in [('linux',7),('windows',3)]:
  if store.json('cell2location-install-controls-1/'+platform+'/COMPLETED.json')['controls']!=count:raise ValueError('Incomplete setup permission/identity refusals')
 final=store.json('cell2location-final-example-1/COMPLETED.json');status=store.json('cell2location-final-example-1/example/analysis/analysis-status.json')
 outputs=store.json('cell2location-final-example-1/example/analysis/result-files.json')
 actual={n[len('cell2location-final-example-1/example/analysis/results/'):] for n in store.index['files'] if n.startswith('cell2location-final-example-1/example/analysis/results/')}
 if set(outputs)!=actual or len(outputs)!=684 or not status['result_admitted'] or not status['condition_counts_complete'] or not status['warning_review_required']:raise ValueError('Incomplete final original result population or warning review')
 for n,w in outputs.items():
  if {k:store.index['files']['cell2location-final-example-1/example/analysis/results/'+n][k] for k in ['bytes','sha256']}!=w:raise ValueError('Complete final original result binding differs')
 if (final['original_result_files'],final['complete_original_arrays'],final['array_elements'])!=(684,647,65212):raise ValueError('Final full scientific population differs')
 if (status['original_warnings'],status['original_log_conditions'],status['original_errors'])!=(17,5,0):raise ValueError('Complete original warning/log/error populations differ')
 conditions=store.json('cell2location-final-example-1/example/analysis/diagnostics/conditions.json')
 if (sum(r['kind']=='warning' for r in conditions),sum(r['kind']=='log' for r in conditions))!=(17,5):raise ValueError('A complete original condition was omitted')
 portable=store.json('cell2location-portable-qualification-2/controls/COMPLETED.json')
 if not portable['independent_mutable_stage_snapshots'] or portable['complete_original_sklearn_models']!=4 or portable['opaque_values']:raise ValueError('Independent-stage retention control missing')
 legacy=store.json('cell2location-snapshot-audit-1/REPRODUCED.json')
 if legacy['status']!='REPRODUCED_CROSS_STAGE_MUTABLE_SNAPSHOT_NOT_RECORDED' or legacy['cross_root_references_in_original_graph']!=55:raise ValueError('Old shared-snapshot limitation was lost')
 refused=store.json('cell2location-stage-refusal-controls-1/controls/COMPLETED.json')
 if refused['controls']!=11 or len([r for r in refused['cases'] if r['expected_exit']!=0])!=10:raise ValueError('Incomplete independent-stage comparator refusals')
 old_log=store.json('cell2location-logging-qualification-1/historical-prototype/OBSERVATION.json');new_log=store.json('cell2location-logging-qualification-1/corrected/OBSERVATION.json')
 if old_log['message_in_original_console'] or not new_log['message_in_original_console']:raise ValueError('Original default-console omission/correction evidence missing')
 for path in ['cell2location-original-run-2/direct-original.stderr','cell2location-runtime-capture-1/provider/FAILURE.json','cell2location-runtime-capture-2/provider/FAILURE.json','cell2location-workflow-validation-1/PROCESSES.json','cell2location-evidence-pack-1/INTERRUPTED_BEFORE_FREEZE.json']:
  if path not in store.index['files']:raise ValueError('A retained failed attempt is missing: '+path)
 return dict(**result,independent_stage_repeatability=repeat,public_original_comparison=public,workflow_controls=workflows,original_result_files=684,original_warnings=17,original_log_conditions=5,setup_controls_linux=7,setup_controls_windows=3,comparator_refusals=10,mutable_stage_control=True,old_snapshot_scope_retained=True,original_console_logging_restored=True,failed_attempts_retained=True,new_tool_credit=0,whole_goal_complete=False)

def evidence(root=ROOT):
 data=root/DATA;proof=load(data/'VALIDATION.json')
 if proof['status']!='PASS_ORIGINAL_CELL2LOCATION013_COMPLETE_INDEPENDENT_STAGE_WORKFLOW_EVIDENCE' or proof['new_tool_credit']!=0 or proof['whole_goal_complete']:raise ValueError('Unsupported original workflow qualification scope')
 require_sources(root,proof['sources'])
 if pin(data/'INDEX.json')!=proof['index']:raise ValueError('Exact retained logical file/link/directory identity map differs')
 result=archive_evidence(data/'objects.zip',load(data/'INDEX.json'),proof['archive'],proof['index'])
 if result!=proof['qualification']:raise ValueError('Complete frozen original qualification differs')
 auxiliary=auxiliary_evidence(root,data,proof)
 store=EvidenceStore(data/'objects.zip',load(data/'INDEX.json'));census=store.json('cell2location-paper-readings-2/CENSUS.json');readings=store.json('cell2location-paper-readings-2/READINGS.json');papers=load(root/'docs/literature/omics-2023-2026/papers.json')
 if len(census)!=250 or len(papers)!=250 or len(readings['readings'])!=6 or sum(len(r['named_anchors']) for r in census)!=9:raise ValueError('Incomplete primary-source census/readings')
 for paper,row in zip(papers,census):
  if any(paper[k]!=row[k] for k in ['paper_id','pmcid','doi','xml_sha256']):raise ValueError('Fixed250 primary-source identities differ')
 if (root/'tools/literature/cell2location013/paper-use-intake.json').read_bytes()!=store.read('cell2location-paper-readings-2/READINGS.json'):raise ValueError('Six qualified source readings changed')
 return dict(status=proof['status'],**result,auxiliary=auxiliary,primary_sources=250,individually_read_paper_uses=6,named_primary_anchors=9,original_statistical_algorithms_modified=False,original_analysis_executed=False,paper_reproduced=False,whole_tool_qualified=False)

def auxiliary_evidence(root,data,proof):
 archive=data/'verifier-controls.zip';index_file=data/'VERIFIER_CONTROL_MEMBERS.json'
 if pin(archive)!=proof['auxiliary_archive'] or pin(index_file)!=proof['auxiliary_index']:raise ValueError('Complete verifier-development/refusal evidence differs')
 index=load(index_file)
 with zipfile.ZipFile(archive) as z:
  if len(z.namelist())!=len(set(z.namelist())) or set(z.namelist())!=set(index) or len(index)!=18:raise ValueError('Complete auxiliary member population differs')
  for n,w in index.items():
   if rawpin(z.read(n))!=w:raise ValueError('Complete auxiliary raw member differs: '+n)
  prefix='cell2location-frozen-refusal-validation-1/controls/'
  controls=json.loads(z.read(prefix+'COMPLETED.json'))
  if controls['status']!='PASS_COMPLETE_ORIGINAL_FROZEN_EVIDENCE_AND_FIVE_REFUSALS' or len(controls['refusal_controls'])!=5 or not controls['published_evidence_unchanged']:raise ValueError('Incomplete frozen-verifier refusals')
  delta=json.loads(z.read(prefix+'NEGATIVE_ARCHIVE_DELTA.json'));base=data/'objects.zip'
  if delta['base_archive']!=proof['archive']:raise ValueError('Negative archive delta base differs')
  h=hashlib.sha256();size=0
  with base.open('rb') as f:
   for b in iter(lambda:f.read(1048576),b''):h.update(b);size+=len(b)
  suffix=base64.b64decode(delta['append_exact_bytes_base64'],validate=True);h.update(suffix);size+=len(suffix)
  if dict(bytes=size,sha256=h.hexdigest())!=delta['complete_negative_archive']:raise ValueError('Losslessly reconstructed negative archive bytes differ')
  if z.read(prefix+'changed-source/run.py')!=(root/'tools/literature/cell2location013/run.py').read_bytes()+b'\n# isolated deliberate source mismatch\n':raise ValueError('Exact negative source input differs')
  for n in ['omitted-logical-member.json','omitted-original-link.json']:
   mutated=json.loads(z.read(prefix+n))
   try:archive_evidence(base,mutated,proof['archive'],proof['index'])
   except ValueError:pass
   else:raise ValueError('Altered complete identity map was accepted')
  legacy=json.loads(z.read(prefix+'legacy-stage-graph-input.json'))
  try:stage_comparison(EvidenceStore(base,legacy),'cell2location-original-run-7/direct-original','cell2location-original-run-8/direct-original','cell2location-stage-snapshot-validation-1/complete-independent-stages')
  except ValueError:pass
  else:raise ValueError('A superseded graph was accepted for independent-stage qualification')
  first=json.loads(z.read('cell2location-frozen-qualification-1/FAILURE.json'))
  if first['status']!='FAIL2_COMPONENT_ARCHIVE_QUALIFICATION_UNADMITTED' or 'metadata_exception' not in first['traceback']:raise ValueError('The initial cross-platform verifier failure was not retained')
 return dict(complete_auxiliary_members=18,frozen_verifier_refusals=5,lossless_negative_archive_reconstructed=True,initial_verifier_failure_retained=True)

def main():
 p=argparse.ArgumentParser(description=__doc__,epilog=ACTION);p.add_argument('--out',type=Path,required=True);p.add_argument('--member',help='Materialize one exact retained regular file, preserving link identities separately');a=p.parse_args();out=a.out.resolve();owned=False
 try:
  if not out.is_relative_to(ROOT/'build/literature-review/monocle-runtime'):raise ValueError('Use a NEW monocle-runtime output')
  out.mkdir(parents=True,exist_ok=False);owned=True;result=evidence()
  if a.member:
   data=ROOT/DATA;store=EvidenceStore(data/'objects.zip',load(data/'INDEX.json'));selected=store.materialize(a.member,out/'selected-original-file');result['materialized_member']=a.member;result['materialized_file']=str(selected)
  (out/'COMPLETED.json').write_bytes((json.dumps(result,indent=2,ensure_ascii=False)+'\n').encode('utf8'));(out/'WARNINGS.md').write_bytes((ACTION+'\n').encode('utf8'));print(result['status'],flush=True);return 0
 except BaseException as error:
  code=3 if isinstance(error,OSError) and not isinstance(error,FileExistsError) else 2;status='SKIP3_ORIGINAL_EVIDENCE_UNAVAILABLE' if code==3 else 'FAIL2_ORIGINAL_WORKFLOW_EVIDENCE_UNADMITTED';record=dict(status=status,cause=repr(error),traceback=traceback.format_exc(),required_action=ACTION)
  if owned:
   try:(out/'FAILURE.json').write_bytes((json.dumps(record,indent=2,ensure_ascii=False)+'\n').encode('utf8'))
   except BaseException as recording:print('FAIL2: failure record unavailable',repr(recording),flush=True);code=2
   try:(out/'WARNINGS.md').write_bytes((status+'\n'+repr(error)+'\n'+ACTION+'\n').encode('utf8'))
   except BaseException as recording:print('FAIL2: warning record unavailable',repr(recording),flush=True);code=2
  print(status,repr(error),'\n'+ACTION,flush=True);return code
if __name__=='__main__':raise SystemExit(main())
