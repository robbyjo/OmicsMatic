#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Verify complete frozen original velociraptor workflow evidence without fitting a model."""
from pathlib import Path,PurePosixPath
from contextlib import ExitStack
import argparse,hashlib,io,json,sys,tarfile,traceback,zipfile
sys.dont_write_bytecode=True
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'tools/literature/velociraptor120'))
from Runtime import load,pin,save
from RValues import compare_trees
DATA=ROOT/'docs/literature/velociraptor120-workflow-development'
ACTION=('Use the unchanged original1.20.0 Linux/WSL workflow and exact permitted providers; '
 'read VELOCIRAPTOR120_WORKFLOWS.md. SKIP3 skips dependent analysis for unavailable or '
 'forbidden prerequisites. FAIL2 admits no complete result. Preserve every original '
 'input, full RDS, value tree, graph, raw console, condition and partial file. Resolve '
 'the named cause and retry NEW paths; never repair identifiers/nonfinite values or '
 'change saved hashes. Original R native stack diagnostics remain unresolved and '
 'raw-console totals unavailable. Selected versions and synthetic parameters are '
 'not recovered author settings, convergence or paper reproduction. This verifier '
 'installs nothing and fits no model; the full250-paper goal remains open.')

def identity(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())

def portable(name):
 p=PurePosixPath(name)
 if not name or p.is_absolute() or '..' in p.parts or '\\' in name or p.as_posix()!=name:raise ValueError('Unsafe evidence member: '+name)
 return p

def strict(row):
 if row['status']!='PASS_COMPLETE_ORIGINAL_R_VALUES_STRICT' or not row['complete_value_comparison'] or not row['require_numeric_bit_equality'] or not row['require_NA_and_NaN_payload_equality'] or not row['attribute_order_compared'] or row['environment_and_pointer_identity_ignored'] or row['opaque_RDS_file_byte_parity_claimed']:raise ValueError('Retained complete strict R-value equality differs')

def verify(out):
 proof=load(DATA/'VALIDATION.json')
 if proof['status']!='PASS_SCOPED_ORIGINAL_VELOCIRAPTOR120_WORKFLOW_COMPONENT' or proof['new_family_credit'] or proof['whole_tool_qualified'] or proof['paper_reproduced'] or proof['whole_goal_complete']:raise ValueError('Unsupported qualification scope')
 if proof['original_R_native_stack_cause_resolved'] or proof['raw_console_diagnostic_counts_complete']:raise ValueError('Original diagnostic limitations were removed')
 for name,wanted in proof['sources'].items():
  portable(name)
  if pin(ROOT/name)!=wanted:raise ValueError('Executed source/example changed: '+name)
 for name,key in [('MEMBERS.json','members'),('BLOBS.json','blobs')]:
  if pin(DATA/name)!=proof[key]:raise ValueError('Complete evidence index changed: '+name)
 members=load(DATA/'MEMBERS.json');blobs=load(DATA/'BLOBS.json')['blobs'];files=members['files']
 if len(files)!=proof['logical_regular_files'] or len(blobs)!=proof['unique_regular_payloads']:raise ValueError('Complete logical/payload population differs')
 for name in list(files)+list(members['symlinks'])+members['directories']:portable(name)
 with ExitStack() as stack:
  archives={};observed={}
  for part in proof['parts']:
   name=part['path']
   if Path(name).name!=name or pin(DATA/name)!={k:part[k] for k in ['bytes','sha256']}:raise ValueError('Evidence volume changed: '+name)
   archive=stack.enter_context(zipfile.ZipFile(DATA/name));archives[name]=archive
   for info in archive.infolist():
    portable(info.filename)
    if info.is_dir() or info.filename in observed:raise ValueError('Duplicate or unexpected archive entry')
    observed[info.filename]=name
  if set(observed)!={'blobs/'+digest for digest in blobs}:raise ValueError('Complete blob population differs')
  for digest,row in blobs.items():
   if observed['blobs/'+digest]!=row['archive'] or identity(archives[row['archive']].read('blobs/'+digest))!={k:row[k] for k in ['bytes','sha256']} or digest!=row['sha256']:raise ValueError('Original payload changed: '+digest)
  for name,row in files.items():
   if row['sha256'] not in blobs or {k:row[k] for k in ['bytes','sha256']}!={k:blobs[row['sha256']][k] for k in ['bytes','sha256']}:raise ValueError('Incomplete logical file: '+name)
  def raw(name):
   digest=files[name]['sha256'];return archives[blobs[digest]['archive']].read('blobs/'+digest)
  def document(name):return json.loads(raw(name))
  def extract(prefix,destination):
   population=[name for name in files if name.startswith(prefix+'/')]
   if not population:raise ValueError('Complete original value tree absent: '+prefix)
   for name in population:
    suffix=portable(name.removeprefix(prefix+'/'));target=destination.joinpath(*suffix.parts)
    target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(raw(name))
  profile=load(ROOT/'tools/literature/velociraptor120/source-profile.json')
  rsource=raw(proof['original_R_archive'])
  if identity(rsource)!=profile['R_archive']:raise ValueError('Original R source artifact differs')
  with tarfile.open(fileobj=io.BytesIO(rsource)) as archive:
   original={e.name.removeprefix('velociraptor/'):identity(archive.extractfile(e).read()) for e in archive.getmembers() if e.isfile()}
   if original!=profile['R_source_files'] or len(original)!=46:raise ValueError('Full46-file original R release differs')
  wheel=raw(proof['original_Python_wheel'])
  if identity(wheel)!=profile['scvelo_wheel']['identity']:raise ValueError('Original Python wheel differs')
  with zipfile.ZipFile(io.BytesIO(wheel)) as archive:
   original={n.removeprefix('scvelo/'):identity(archive.read(n)) for n in archive.namelist() if n.startswith('scvelo/') and not n.endswith('/')}
   if original!=profile['scvelo_source_files'] or len(original)!=63:raise ValueError('Full63-file original scvelo package differs')
  comparisons=[]
  if len(proof['numeric_cases'])!=13 or len({r['id'] for r in proof['numeric_cases']})!=13:raise ValueError('Incomplete numeric method/dispatch population')
  for number,case in enumerate(proof['numeric_cases']):
   paths=[]
   for label,key in [('original','original_tree'),('adapter','adapter_tree')]:
    path=out/('n'+str(number))/label;extract(case[key],path);paths.append(path)
   result=compare_trees(*paths)
   if result!=case['comparison']:raise ValueError('Complete original/adapter value-tree comparison differs: '+case['id'])
   for name in case['strict_records']:strict(document(name))
   comparisons.append(result)
  totals={key:sum(row[key] for row in comparisons) for key in ['nodes','atomic_payloads','atomic_payload_bytes']}
  if totals!=dict(nodes=3732,atomic_payloads=2617,atomic_payload_bytes=2457351):raise ValueError('Complete numeric value population differs')
  if len(proof['figure_cases'])!=4 or len(proof['figure_error_cases'])!=2:raise ValueError('Incomplete whole-figure/error population')
  for case in proof['figure_cases']:
   for suffix in ['svg','png']:
    original=raw(case['original']+'/COMPLETE-ORIGINAL-FIGURE.'+suffix)
    if suffix=='png' and not original.startswith(b'\x89PNG\r\n\x1a\n') or suffix=='svg' and b'<svg' not in original:raise ValueError('Incomplete original figure')
    if original!=raw(case['adapter']+'/results/figure.'+suffix):raise ValueError('Complete rendered figure bytes differ')
    for prefix in case['replays']:
     if raw(prefix+'/figure.'+suffix)!=original:raise ValueError('Retained complete graph replay differs')
   for source in [case['original']+'/COMPLETE-ORIGINAL-GRAPH-BEFORE-RENDER.rds',case['adapter']+'/results/original-graph.rds']:
    if not raw(source):raise ValueError('Complete graph RDS missing')
  for case in proof['figure_error_cases']:
   original=document(case['original']+'/FIGURE-STATUS.json');worker=document(case['adapter']+'/diagnostics/worker-status.json');status=document(case['adapter']+'/analysis-status.json')
   if status['exit_code']!=2 or status['result_admitted'] or any(original['error'][key]!=worker['error'][key] for key in ['class','message']):raise ValueError('Original named-coordinate failure was repaired or admitted')
  controls=0
  for group in proof['control_groups']:
   receipt=document(group['receipt'])
   if receipt['status']!=group['status'] or len(receipt['cases'])!=group['count']:raise ValueError('Complete executed control population differs')
   controls+=group['count']
  if controls!=36:raise ValueError('Expected32 Linux/R workflow controls and4 native Windows skips')
  verified_statuses=0
  for name in files:
   if name.endswith('/analysis-status.json') and any(name.startswith(prefix+'/') for prefix in proof['current_analysis_roots']):
    status=document(name);code=status['exit_code']
    if code not in [0,2,3] or status['result_admitted']!=(code==0) or not status['warning_review_required'] or status['raw_console_diagnostic_counts_complete']:raise ValueError('Terminal admission/diagnostic scope differs: '+name)
    if not status['R_handler_condition_counts_complete'] and any(status[k] is not None for k in ['R_handler_warnings','R_handler_messages','R_handler_errors']):raise ValueError('Unknown R conditions were zeroed')
    if not status['status'].startswith({0:'PASS',2:'FAIL2',3:'SKIP3'}[code]):raise ValueError('Failure retained a stale PASS')
    verified_statuses+=1
  for number,row in enumerate(proof['example_cases']):
   status=document(row['analysis']+'/analysis-status.json')
   if status['exit_code'] or not status['result_admitted'] or not status['complete_manifest']:raise ValueError('Incomplete public example admitted')
   for name,wanted in document(row['analysis']+'/MANIFEST.json')['files'].items():
    if 'kind' not in wanted and identity(raw(row['analysis']+'/'+name))!=wanted:raise ValueError('Complete public example manifest differs')
   label=row['id'].removesuffix('-request.json')
   if label in ['stochastic','dynamical']:
    original=out/('example'+str(number))/'original';actual=out/('example'+str(number))/'adapter'
    extract('velociraptor-original-scipy1163-1/'+label+'/original-results/complete-value-tree',original);extract(row['analysis']+'/results/result-value-tree',actual);compare_trees(original,actual)
   elif row['mode']=='FIGURES':
    for suffix in ['svg','png']:
     if raw(row['analysis']+'/results/figure.'+suffix)!=raw('velociraptor-original-figures-2/'+label+'/COMPLETE-ORIGINAL-FIGURE.'+suffix):raise ValueError('Complete public example figure differs')
   elif label!='project-grid':raise ValueError('Unknown public example scope')
  if len(proof['example_cases'])!=6:raise ValueError('All six public examples are required')
  recovery=document('velociraptor-six-examples-recovery-1/COMPLETED.json')
  if recovery['status']!='PASS_SIX_COMPLETE_PUBLIC_EXAMPLES_AND_ORIGINAL_COMPARISONS' or not recovery['prior_failed_harness_preserved']:raise ValueError('Actual example recovery scope differs')
  strict(document('velociraptor-six-examples-recovery-1/strict-project-grid.json'))
 return dict(status=proof['status'],complete_numeric_cases=13,**totals,complete_figure_cases=4,complete_graph_RDS_replays=8,matching_original_figure_error_cases=2,executed_workflow_controls=36,complete_public_examples=6,verified_terminal_analysis_records=verified_statuses,logical_regular_files=len(files),unique_regular_payloads=len(blobs),archive_volumes=len(proof['parts']),original_R_files=46,original_Python_files=63,original_R_native_stack_cause_resolved=False,raw_console_diagnostic_counts_complete=False,backend_installed=False,models_fit=False,new_family_credit=0,whole_tool_qualified=False,paper_reproduced=False,whole_goal_complete=False)

def main():
 parser=argparse.ArgumentParser(description=__doc__,epilog=ACTION);parser.add_argument('--out',required=True,type=Path);args=parser.parse_args();out=args.out.absolute();owned=False
 try:
  if out!=out.resolve() or not out.is_relative_to(ROOT/'build/literature-review/monocle-runtime'):raise ValueError('Use a canonical NEW monocle-runtime verification output')
  out.mkdir(parents=True,exist_ok=False);owned=True;result=verify(out);save(out/'COMPLETED.json',result);(out/'WARNINGS.md').write_text('# Original namespace warning review remains required\n\n'+ACTION+'\n',encoding='utf-8',newline='\n');print(result['status'],flush=True);return 0
 except BaseException as error:
  code=3 if isinstance(error,OSError) and not isinstance(error,FileExistsError) else 2;status='SKIP3_VELOCIRAPTOR_EVIDENCE_PREREQUISITE_UNAVAILABLE' if code==3 else 'FAIL2_VELOCIRAPTOR_EVIDENCE_UNADMITTED'
  if owned:
   try:save(out/'FAILURE.json',dict(status=status,cause=repr(error),traceback=traceback.format_exc(),required_action=ACTION));(out/'WARNINGS.md').write_text('# '+status+'\n\n'+repr(error)+'\n\n'+ACTION+'\n',encoding='utf-8')
   except OSError as recording:code=2;status='FAIL2_VELOCIRAPTOR_EVIDENCE_RECORDING_UNAVAILABLE';print(status,repr(recording),'Retain the complete console and use a NEW writable output.',flush=True)
  print(status,repr(error),ACTION,flush=True);return code
if __name__=='__main__':raise SystemExit(main())
