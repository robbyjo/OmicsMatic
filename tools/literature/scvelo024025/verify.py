#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Verify complete original scVelo state and retained failures without installation or fitting."""
from pathlib import Path
import argparse,json,math,sys,traceback,zipfile,xml.etree.ElementTree as ET
sys.dont_write_bytecode=True
HERE=Path(__file__).resolve().parent;ROOT=HERE.parents[2];DATA='docs/literature/scvelo024025-development'
from EvidenceStore import EvidenceStore,pin,rawpin,decode
from compare_complete import compare,canonical,member
ACTION='SKIP3 skips dependent verification/analysis when exact permitted prerequisites or complete original evidence are unavailable. Supply the named requirements and retry NEW paths. FAIL2 admits no complete result after integrity, population or scientific-value mismatch. Preserve every available original object, partial output, warning/error, raw report and failed attempt. Unknown counts are unavailable, never zero. Do not repair NaN/Inf, rename identifiers, change saved hashes or drop failed cases. Four historical SVGs were overwritten and remain irrecoverable; new figures are not recovered old bytes. Original temporary directories were unavailable at archival review: use the verified saved-bundle native-temporary copies, not historical /tmp paths. Printed0.24 and the R velociraptor interface remain separate unqualified requirements. This gate installs nothing, fits no model and establishes no convergence, author-environment identity, independent statistical validity or paper reproduction. Read SCVELO024025_DEVELOPMENT.md and the current catalog; the full250-paper goal remains open.'

def encoded(value):return (json.dumps(value,indent=2,ensure_ascii=False,allow_nan=False)+'\n').encode('utf8')
def load(path):return decode(path.read_bytes())
def require_sources(root,expected):
 for name,wanted in expected.items():
  path=root/name
  if path.is_symlink() or pin(path)!=wanted:raise ValueError('Qualified original scVelo source/evidence differs: '+name)

def retained_pin(store,name):return {k:store.index['files'][name][k] for k in ['bytes','sha256']}

def supervisor(store,prefix,version):
 state=store.json(prefix+'/analysis-status.json');manifest=store.json(prefix+'/MANIFEST.json');native=store.json(prefix+'/native-temporary-inventory.json')
 if state['result_admitted'] is not True or state['exit_code']!=0 or state['original_version']!=version or not state['condition_counts_complete'] or not state['native_temporary_copy_complete']:raise ValueError('Complete original supervised result/conditions/temporary retention not admitted')
 if state['condition_scope']!='trusted_script_and_original_runtime_delivered_conditions':raise ValueError('Delivered-condition scope differs')
 for name,wanted in manifest['files'].items():
  if retained_pin(store,prefix+'/'+member(name))!=wanted:raise ValueError('Complete original controller manifest differs: '+name)
 if canonical(native)!=canonical(manifest['native_temporary']) or native['original_root_retained'] is not True:raise ValueError('Complete native temporary manifest differs')
 for name,wanted in native['files'].items():
  if wanted['kind']=='file' and retained_pin(store,prefix+'/native-temporary/'+member(name))!={k:wanted[k] for k in ['bytes','sha256']}:raise ValueError('Complete native temporary file bytes differ')
 report=store.json(prefix+'/diagnostics/worker-status.json');conditions=store.json(prefix+'/diagnostics/conditions.json')
 for state_key,worker_key,kind in [('original_warnings','warning_count','warning'),('original_log_conditions','log_count','log'),('original_errors','error_count','error')]:
  n=state[state_key]
  if type(n)!=int or n<0 or report[worker_key]!=n or sum(c['kind']==kind for c in conditions)!=n:raise ValueError('Complete delivered-condition count differs')
 return dict(delivered_warnings=state['original_warnings'],delivered_log_conditions=state['original_log_conditions'],delivered_errors=state['original_errors'],warning_review_required=state['warning_review_required'])

def scientific_populations(store):
 collections={}
 for name in ['scvelo-adapter-examples-2','scvelo-public-examples-1']:
  cases=[]
  for edition,version in [('024','0.2.4'),('025','0.2.5')]:
   for mode in ['stochastic','dynamical']:
    case=edition+'-'+mode;reference=store.path('scvelo-original-candidates-3/'+case+'/analysis');prefix=name+'/'+case+'/example/analysis'
    result=compare(reference,store.path(prefix+'/results/workflow'),version,mode,supervised=True);result['conditions']=supervisor(store,prefix,version);cases.append(result)
  collections[name]=cases
 public=collections['scvelo-public-examples-1']
 if tuple(sum(c[key] for c in public) for key in ['complete_stages','stage_snapshots','graph_records','arrays','elements','complete_result_files','complete_current_figures'])!=(40,160,43246,1628,1863018,1740,8):raise ValueError('Complete public scientific population differs')
 larger=[]
 for edition,version in [('024','0.2.4'),('025','0.2.5')]:
  base='scvelo-larger-stochastic-1/'+edition;left=base+'/original';right=base+'/analysis/results/workflow';graphs=[]
  for prefix in [left,right]:
   graph=store.json(prefix+'/complete-stages/COMPLETE-STAGES.json');result=store.json(prefix+'/COMPLETED.json');files={n[len(prefix)+1:] for n in store.index['files'] if n.startswith(prefix+'/')};arrays=[v for v in graph['records'] if v['kind']=='array']
   if result['version']!=version or result['status']!='COMPLETE_ORIGINAL_LARGER_STOCHASTIC_PARAMETER_CONTROL' or result['requested_parameters']!=dict(min_shared_counts=30,n_top_genes=2000,n_neighbors=50,n_pcs=30) or not result['threshold_keyword_is_explicit_caller_choice'] or not result['input_data_synthetic'] or result['paper_reproduced'] or result['convergence_established']:raise ValueError('Larger original population/settings scope differs')
   if len(result['stages'])!=7 or [(s['cells'],s['genes']) for s in result['stages']]!=[(96,2100)]+[(96,2000)]*6 or len(result['input_genes'])!=2100 or len(result['retained_genes'])!=2000 or len(files)!=266:raise ValueError('Larger complete original raw/selected population differs')
   if graph['schema_version']!=2 or graph['snapshot_scope']!='independent-top-level-stage-values' or graph['opaque_values'] or graph['nonfinite_arrays'] or graph['nonfinite_values_repaired'] or (len(graph['snapshot_roots']),len(graph['records']),len(arrays),sum(math.prod(v['shape']) for v in arrays))!=(21,20705,248,11651009):raise ValueError('Larger complete original stage graph differs')
   if {v['file'] for v in arrays}!={n[len('complete-stages/'):] for n in files if n.startswith('complete-stages/arrays/')}:raise ValueError('Incomplete larger original numeric array namespace')
   figures=[n for n in files if n.startswith('plots/')]
   if len(figures)!=1:raise ValueError('A larger original figure is missing or extra')
   xml=ET.fromstring(store.read(prefix+'/'+figures[0]))
   if xml.tag!='{http://www.w3.org/2000/svg}svg' or not xml.findall('.//{http://www.w3.org/2000/svg}path'):raise ValueError('A complete larger original SVG is unavailable')
   graphs.append((graph,result,files,arrays))
  if canonical(graphs[0][0])!=canonical(graphs[1][0]) or canonical(graphs[0][1])!=canonical(graphs[1][1]) or graphs[0][2]!=graphs[1][2]:raise ValueError('Complete larger original typed scientific state differs')
  for row in graphs[0][3]:
   if retained_pin(store,left+'/complete-stages/'+row['file'])!=retained_pin(store,right+'/complete-stages/'+row['file']):raise ValueError('Larger complete original array bytes/dtype/NaN payload differ')
  raw=retained_pin(store,'scvelo-larger-stochastic-1/counts.h5ad')
  if any(retained_pin(store,prefix+'/original-input.h5ad')!=raw for prefix in [left,right]):raise ValueError('Complete larger original raw input differs')
  conditions=supervisor(store,base+'/analysis',version);larger.append(dict(version=version,stages=7,stage_snapshots=21,graph_records=20705,arrays=248,elements=11651009,result_files=266,figures=1,numeric_tolerance=0,conditions=conditions))
 metadata=store.json('scvelo-larger-stochastic-1/ORIGINAL_PARAMETER_METADATA.json')
 if metadata!=[dict(edition=e,shape=[96,2000],neighbors=50,PCs=30,PCA_shape=[96,30]) for e in ['024','025']]:raise ValueError('Independently captured original parameter metadata differs')
 return dict(development_adapter_cases=collections['scvelo-adapter-examples-2'],public_installer_cases=public,larger_cases=larger,unique_qualified_scientific_cases=6,complete_stages=54,stage_snapshots=202,graph_records=84656,arrays=2124,elements=25165036,result_files=2272,figures=10,numeric_tolerance=0,opaque_pickle_hdf5_svg_byte_parity_claimed=False,convergence_established=False,paper_reproduced=False)

def protocol_population(store):
 skipped=set('execution-forbidden trusted-code-forbidden missing-provider missing-script missing-input missing-required-distribution literal-missing-symbol wrong-required-version wrong-edition supported-provider-edition-mismatch'.split())
 passed=set('warnings-visible complete-positive fork-manager-round-trip spawn-manager-round-trip native-temporary-bytes-retained ignored-warning-filter-preserved'.split())
 failed=set('warning-as-error original-package-error zero-exit-early-quit explicit-original-error missing-marker wrong-marker extra-result missing-result changed-captured-input extra-captured-input workspace-symlink unexpected-root-output wall-time-cap truncated-result file-byte-cap workspace-byte-cap workspace-file-cap invalid-script-UTF8 workspace-file-cap-after-readiness existing-output-refusal truncated-worker-status negative-count-worker-status boolean-count-worker-status fractional-count-worker-status missing-count-worker-status duplicate-count-worker-status nonfinite-count-worker-status nonobject-worker-status warning-report-path-blocked status-report-path-blocked native-temporary-file-cap unsupported-mode combined-two-root-byte-cap native-temporary-symlink-refused'.split())
 if len(skipped|passed|failed)!=50:raise ValueError('Incomplete declared control population')
 for edition,version in [('024','0.2.4'),('025','0.2.5')]:
  root='scvelo-workflow-validation-2/'+edition+'/controls';proof=store.json(root+'/COMPLETED.json');cases={c['id']:c for c in proof['cases']}
  if proof['controls']!=50 or len(proof['cases'])!=50 or set(cases)!=skipped|passed|failed or not proof['original_inputs_provider_unchanged']:raise ValueError('A complete original workflow control was omitted or replaced')
  for name,case in cases.items():
   wanted=3 if name in skipped else 0 if name in passed else 2
   if case['exit_code']!=wanted or case['result_admitted']!=(wanted==0):raise ValueError('Original policy/recovery control outcome differs: '+name)
   prefix=root+'/'+name
   if name in ['wrong-edition','existing-output-refusal','unsupported-mode']:continue
   if name=='status-report-path-blocked':
    if prefix+'/analysis-status.json' not in store.index['directories'] or b'FAIL2' not in store.read(prefix+'/WARNINGS.md'):raise ValueError('Blocked-status failure/recovery not retained')
    continue
   state=store.json(prefix+'/analysis-status.json')
   if state['exit_code']!=wanted or state['result_admitted']!=(wanted==0):raise ValueError('Complete original control status differs: '+name)
   if name.endswith('worker-status') or name=='zero-exit-early-quit':
    if state['condition_counts_complete'] or not state['warning_review_required'] or any(state[k] is not None for k in ['original_warnings','original_log_conditions','original_errors']):raise ValueError('Unknown condition totals were made to look known')
    if b'unavailable' not in store.read(prefix+'/WARNINGS.md') or prefix+'/WORKER_DIAGNOSTIC_ERROR.txt' not in store.index['files'] or store.json(prefix+'/process-exit.json')['exit_code']!=0:raise ValueError('Zero-exit malformed/interrupted-report refusal evidence differs')
   if name in passed:supervisor(store,prefix,version)
  visible=store.json(root+'/warnings-visible/diagnostics/conditions.json')
  if not any(c['kind']=='warning' and c['message']=='original warning line1\noriginal warning line2' for c in visible) or not any(c['kind']=='log' and c['message']=='original log α' for c in visible):raise ValueError('Caller-requested visible warnings/logs were lost')
  ignored=store.json(root+'/ignored-warning-filter-preserved/diagnostics/conditions.json')
  if any('explicitly ignored original warning' in c.get('message','') for c in ignored):raise ValueError('Original ignored warning policy was changed')
  if store.read(root+'/native-temporary-bytes-retained/native-temporary/payload.bin')!=b'\0NA\xff':raise ValueError('Complete literal native temporary bytes differ')
 counts={}
 for name,count in [('scvelo-workflow-controls-windows-1',1),('scvelo-monitor-controls-linux-1',4),('scvelo-monitor-controls-windows-1',3),('scvelo-install-controls-linux-1',7),('scvelo-install-controls-windows-1',3),('scvelo-native-temporary-controls-1',10)]:
  if store.json(name+'/COMPLETED.json')['controls']!=count:raise ValueError('A complete setup/monitor/native control population differs')
  counts[name]=count
 return dict(linux_workflow_controls=100,controls_per_edition=50,windows_execution_refusals=1,monitor_controls=7,setup_refusals=10,native_temporary_controls=10,unknown_report_controls=16,blocked_recording_paths=4,complete_case_population_preserved=True)

def runtime_population(store,root):
 profile=load(root/'tools/literature/scvelo024025/source-profile.json');rows=[]
 for edition,version,source_members in [('024','0.2.4',106),('025','0.2.5',402)]:
  selected=next(p for p in profile['profiles'] if p['version']==version)
  for prefix in ['scvelo-runtime-capture-2/'+edition+'/provider','scvelo-public-install-1/'+edition+'/setup/provider']:
   ready=store.json(prefix+'/READY.json')
   if ready['status']!='READY_CAPTURED_PRIVATE_ORIGINAL_SCVELO_CPU_NOT_ANALYSIS' or ready['version']!=version or ready['original_algorithms_patched'] or ready['analysis_executed']:raise ValueError('Unsupported original provider scope')
   if len(ready['source_members'])!=source_members or canonical(ready['original_package_files'])!=canonical(selected['original_package_files']) or len(ready['complete_source_declarations'])!=len(selected['original_declarations']) or len(ready['installed_distribution_records'])!=45 or ready['installed_distribution_records']!=ready['import_visible_distribution_records']:raise ValueError('Complete original namespace/source/dependency population differs')
   if set(ready['runtime'])!={'python',ready['venv']} or not all(ready['runtime'].values()) or not ready['loaded_files'] or not ready['imported_module_origins']:raise ValueError('Complete runtime/module/library identity maps missing')
   for name,wanted in ready['capture_artifacts'].items():
    if retained_pin(store,prefix+'/'+member(name))!=wanted:raise ValueError('Raw captured runtime map/transient data differs')
   for row in ready['transient_data_mappings']:
    if row['classification']!='unlinked-nonexecutable-shared-POSIX-semaphore-data':raise ValueError('Deleted runtime code was accepted as transient data')
   rows.append(dict(provider=prefix,version=version,source_members=source_members,original_package_files=len(ready['original_package_files']),complete_source_declarations=len(ready['complete_source_declarations']),installed_distributions=45,import_visible_distributions=45,runtime_members=sum(len(v) for v in ready['runtime'].values()),loaded_files=len(ready['loaded_files'])))
  setup=store.json('scvelo-public-install-1/'+edition+'/setup/COMPLETED.json')
  if setup['status']!='INSTALLED_AND_CAPTURED_PRIVATE_ORIGINAL_SCVELO_CPU_NOT_ANALYSIS' or setup['version']!=version or any(setup[k] for k in ['global_python_links_created','existing_runtime_modified','original_algorithms_patched','analysis_executed']):raise ValueError('Permitted private original installer scope differs')
  if not any('--no-bin' in x['command'] for x in setup['processes'] if x['label']=='standalone-python'):raise ValueError('Original setup created global Python links')
 return rows

def native_copy_population(store):
 copies=[];roots=store.index['native_original_roots']
 for root in roots:
  for name in root['receipt_files']:
   receipt=store.json(name)
   if receipt['original_root']!=root['original_root']:raise ValueError('Original native temporary ownership receipt differs')
   for relative,row in receipt['files'].items():
    if row['kind']!='file':continue
    logical=name.rsplit('/',1)[0]+'/native-temporary/'+member(relative)
    if retained_pin(store,logical)!={k:row[k] for k in ['bytes','sha256']}:raise ValueError('A complete recorded original temporary byte copy is missing or changed')
    copies.append(dict(receipt=name,file=relative,bytes=row['bytes']))
 if len(roots)!=169 or len(copies)!=13 or sum(x['bytes'] for x in copies)!=268435479:raise ValueError('Complete recorded native temporary/copy population differs')
 return dict(original_roots_observed_at_archive=len(roots),original_roots_available_at_archive=sum(x['available_at_pack'] for x in roots),original_roots_unavailable_at_archive=sum(not x['available_at_pack'] for x in roots),analysis_original_roots_unavailable_at_archive=sum(not x['available_at_pack'] and x['original_root'].startswith('/tmp/omicsmatic-scvelo-') for x in roots),unit_fixture_roots_unavailable_at_archive=sum(not x['available_at_pack'] and x['original_root'].startswith('/tmp/omicsmatic-ipc-control-') for x in roots),verified_recorded_regular_copies=len(copies),verified_recorded_regular_copy_bytes=sum(x['bytes'] for x in copies),original_root_retained_flag_is_historical=True,durable_evidence_is_saved_bundle=True,deleted_original_directories_claimed_recovered=False)

def archive_evidence(directory,index,archives,expected_index,root=ROOT):
 if rawpin(encoded(index))!=expected_index:raise ValueError('Exact complete logical evidence map differs')
 with EvidenceStore(directory,index) as store:
  retained=store.verify(archives);scientific=scientific_populations(store);protocol=protocol_population(store);runtimes=runtime_population(store,root);native_copies=native_copy_population(store)
  controls=store.json('scvelo-comparator-refusals-1/COMPLETED.json')
  if controls['controls']!=12 or controls['refusal_controls']!=11 or len(controls['cases'])!=12 or any(c['exit_code']!=c['expected_exit'] for c in controls['cases']):raise ValueError('Original complete comparator/refusal population differs')
  reader=store.json('scvelo-store-validation-1/controls/COMPLETED.json')
  if reader['controls']!=7 or reader['refusals']!=6 or not reader['original_files_unchanged']:raise ValueError('Complete original archive-reader replay/refusals missing')
  mapping=store.json('scvelo-mapping-controls-2/COMPLETED.json')
  if (mapping['accepted_controls'],mapping['refusal_controls'])!=(1,11):raise ValueError('Narrow non-executable mapping controls differ')
  if store.json('scvelo-portable-controls-2/COMPLETED.json')['control_groups']!=8:raise ValueError('Independent scientific snapshot/H5AD regression controls missing')
  failure=store.json('scvelo-original-candidates-2/QUALIFICATION_REFUSED.json')
  if failure['status']!='FAIL2_ARTIFACT_CAPTURE_INCOMPLETE_DESPITE_SUCCESSFUL_ORIGINAL_FITS' or failure['earlier_0_2_4_svg_bytes_recoverable'] or failure['result_admitted'] or index['irrecoverable_historical_files']!=4:raise ValueError('Historical irrecoverable plot loss was hidden or repaired')
  for name in ['scvelo-original-candidates-1/COMPLETED.json','scvelo-runtime-capture-1/024/provider/FAILURE.json','scvelo-adapter-examples-1/024-stochastic/example/analysis/analysis-status.json','scvelo-workflow-validation-1/024/controls/HARNESS_ERRORS.json','scvelo-workflow-validation-1/025/controls/HARNESS_ERRORS.json']:
   if name not in index['files']:raise ValueError('A retained original failed attempt is missing: '+name)
  tested=store.json('scvelo-workflow-validation-2/COMPLETED.json')
  if len(tested['cases'])!=2 or any(c['exit_code']!=0 for c in tested['cases']):raise ValueError('Both original workflow suites did not terminate successfully')
  for name,wanted in tested['source_pins'].items():
   if name in ['CANDIDATE_STATE.json','IN_PROGRESS.md']:continue
   if pin(root/'tools/literature/scvelo024025'/name)!=wanted:raise ValueError('An executed runtime, example, comparator or control source changed after validation')
 return dict(**retained,scientific=scientific,workflow_controls=protocol,runtime_providers=runtimes,native_temporary_copies=native_copies,comparator_refusals=11,archive_reader_refusals=6,portable_control_groups=8,mapping_controls=12,available_failed_attempts_retained=True,irrecoverable_historical_SVGs=4,original_algorithms_patched=False,new_tool_credit=0,whole_goal_complete=False,paper_reproduced=False)

def evidence(root=ROOT):
 data=root/DATA;proof=load(data/'VALIDATION.json')
 if proof['status']!='PASS_ORIGINAL_SCVELO024025_COMPLETE_AVAILABLE_ORIGINAL_WORKFLOW_EVIDENCE' or proof['new_tool_credit']!=0 or proof['whole_goal_complete']:raise ValueError('Unsupported original workflow qualification scope')
 require_sources(root,proof['sources'])
 if pin(data/'INDEX.json')!=proof['index']:raise ValueError('Complete retained original logical identity map differs')
 result=archive_evidence(data,load(data/'INDEX.json'),proof['archives'],proof['index'],root)
 if result!=proof['qualification']:raise ValueError('Complete original frozen qualification differs')
 journal=load(data/'RETENTION_JOURNAL_MEMBERS.json')
 with zipfile.ZipFile(data/'retention-journal.zip') as z:
  names=z.namelist()
  if len(names)!=len(set(names)) or set(names)!=set(journal):raise ValueError('Complete additive retention journal population differs')
  for name,wanted in journal.items():
   if rawpin(z.read(name))!=wanted:raise ValueError('Raw original additive retention journal differs: '+name)
  recovered=decode(z.read('evidence-scvelo-pack-recovery-1/COMPLETED.json'));observed=decode(z.read('evidence-scvelo-pack-observation-1/OBSERVED.json'));modes=decode(z.read('evidence-scvelo-pack-recovery-1/MODES.json'))
  if recovered['qualification']!=proof['qualification'] or recovered['original_process_exit_known'] or observed['owned_linux_processes'] or any(v is not None for v in observed['terminal_receipts'].values()):raise ValueError('Stopped-worker history or independent full recovery was rewritten')
  if recovered['source_bytes_checked']!=30983 or modes['entries']!=34192 or modes['links_followed']:raise ValueError('Complete original source recovery identity audit differs')
 papers=load(root/'docs/literature/omics-2023-2026/papers.json');census=load(root/'tools/literature/scvelo024025/source-census.json');wrapper=load(root/'tools/literature/scvelo024025/wrapper-source-census.json')
 if len(papers)!=250 or len(census)!=250 or len(wrapper)!=250 or sum(len(x['named_anchors']) for x in census)!=6 or sum(len(x['named_anchors']) for x in wrapper)!=1:raise ValueError('Complete primary-source census differs')
 for paper,row,other in zip(papers,census,wrapper):
  if any(paper[k]!=x[k] for x in [row,other] for k in ['paper_id','pmcid','doi','xml_sha256']):raise ValueError('Fixed primary-source identity differs')
 return dict(status=proof['status'],**result,primary_sources=250,source_intake_papers=5,named_scvelo_anchors=6,separate_velociraptor_anchors=1,additive_retention_journal_members=len(journal),stopped_packer_history_preserved=True,original_analysis_executed=False)

def main():
 p=argparse.ArgumentParser(description=__doc__,epilog=ACTION);p.add_argument('--out',type=Path,required=True);p.add_argument('--member',help='Materialize one exact retained regular file in NEW output; links are not followed');a=p.parse_args();out=a.out.resolve();owned=False
 try:
  if not out.is_relative_to(ROOT/'build/literature-review/monocle-runtime'):raise ValueError('Use a NEW monocle-runtime output')
  out.mkdir(parents=True,exist_ok=False);owned=True;result=evidence()
  if a.member:
   data=ROOT/DATA
   with EvidenceStore(data,load(data/'INDEX.json')) as store:result['materialized_file']=str(store.materialize(a.member,out/'selected-original-file'))
  (out/'COMPLETED.json').write_bytes(encoded(result));(out/'WARNINGS.md').write_bytes((ACTION+'\n').encode('utf8'));print(result['status'],flush=True);return 0
 except BaseException as error:
  code=3 if isinstance(error,OSError) and not isinstance(error,FileExistsError) else 2;result=dict(status='SKIP3_ORIGINAL_SCVELO_EVIDENCE_UNAVAILABLE' if code==3 else 'FAIL2_ORIGINAL_SCVELO_WORKFLOW_UNADMITTED',cause=repr(error),traceback=traceback.format_exc(),required_action=ACTION)
  if owned:
   for name,raw in [('FAILURE.json',encoded(result)),('WARNINGS.md',(result['status']+'\n'+repr(error)+'\n'+ACTION+'\n').encode('utf8'))]:
    try:(out/name).write_bytes(raw)
    except BaseException as recording:print('FAIL2: unable to record',name,repr(recording),flush=True);code=2
  print(result['status'],repr(error),ACTION,flush=True);return code
if __name__=='__main__':raise SystemExit(main())
