#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Replay a frozen two-mode development checkpoint; installs nothing and fits no model."""
from pathlib import Path,PurePosixPath
import argparse,hashlib,io,tarfile,traceback,zipfile
from contextlib import ExitStack
from Runtime import HERE,ACTION,load,pin,save
from RValues import compare_trees

def identity(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())

def main():
 parser=argparse.ArgumentParser(description=__doc__,epilog=ACTION);parser.add_argument('--checkpoint',type=Path,required=True);parser.add_argument('--out',type=Path,required=True);args=parser.parse_args();out=args.out.absolute();owned=False
 try:
  checkpoint=args.checkpoint.absolute()
  if out!=out.resolve() or checkpoint!=checkpoint.resolve():raise ValueError('Canonical NEW output/checkpoint paths required')
  for protected in [HERE,checkpoint]:
   if out.is_relative_to(protected) or protected.is_relative_to(out):raise ValueError('Verification output overlaps protected source/checkpoint')
  out.mkdir(exist_ok=False);owned=True;proof=load(checkpoint/'COMPLETED.json')
  if proof['status']!='PASS_DEVELOPMENT_CHECKPOINT_TWO_COMPLETE_R_MODE_REFERENCES_AND_14_CONTROLS' or proof['new_family_credit'] or proof['whole_R_wrapper_qualified'] or proof['whole_goal_complete']:raise ValueError('Expected exact development scope; no broader admission')
  if proof['original_R_native_stack_cause_resolved'] or proof['raw_console_diagnostic_counts_complete'] or proof['IPC_live_streams_or_terminated_process_memory_recovered']:raise ValueError('Original diagnostic/IPC limitations were removed')
  for name,wanted in proof['engine_sources'].items():
   if pin(HERE/name)!=wanted:raise ValueError('Executed engine source differs: '+name)
  if pin(checkpoint/'MEMBERS.json')!=proof['members']:raise ValueError('Complete original/candidate member index differs')
  members=load(checkpoint/'MEMBERS.json')['members']
  if len(members)!=proof['archived_members']:raise ValueError('Complete archived population differs')
  with ExitStack() as stack:
   archives={};observed={}
   for part in proof['parts']:
    name=part['path']
    if Path(name).name!=name or pin(checkpoint/name)!={key:part[key] for key in ['bytes','sha256']}:raise ValueError('Evidence volume differs: '+name)
    archive=stack.enter_context(zipfile.ZipFile(checkpoint/name));archives[name]=archive
    for member in archive.infolist():
     path=PurePosixPath(member.filename)
     if member.is_dir() or member.filename in observed or path.is_absolute() or any(p in ['.','..'] for p in path.parts) or '\\' in member.filename:raise ValueError('Unexpected duplicate/unsafe original evidence member')
     observed[member.filename]=name
   if set(observed)!=set(members):raise ValueError('Missing/extra complete archive member population')
   def raw(name):return archives[members[name]['archive']].read(name)
   for name,wanted in members.items():
    if observed[name]!=wanted['archive'] or identity(raw(name))!={key:wanted[key] for key in ['bytes','sha256']}:raise ValueError('Original/candidate evidence changed: '+name)
   profile=load(HERE/'source-profile.json')
   wheel_name=next(name for name in members if name.startswith('velociraptor-python-source-audit-1/') and name.endswith('/'+profile['scvelo_wheel']['filename']))
   if identity(raw(wheel_name))!=profile['scvelo_wheel']['identity']:raise ValueError('Original scvelo wheel differs')
   with zipfile.ZipFile(io.BytesIO(raw(wheel_name))) as wheel:
    original={name.removeprefix('scvelo/'):identity(wheel.read(name)) for name in wheel.namelist() if name.startswith('scvelo/') and not name.endswith('/')}
    if original!=profile['scvelo_source_files'] or len(original)!=63:raise ValueError('Complete original scvelo source population differs')
   rarchive='velociraptor-source-intake-5/velociraptor_1.20.0.tar.gz'
   if identity(raw(rarchive))!=profile['R_archive']:raise ValueError('Original R source archive differs')
   with tarfile.open(fileobj=io.BytesIO(raw(rarchive))) as tar:
    originals={entry.name.removeprefix('velociraptor/'):identity(tar.extractfile(entry).read()) for entry in tar.getmembers() if entry.isfile()}
    if originals!=profile['R_source_files'] or len(originals)!=46:raise ValueError('Complete original R release source population differs')
   comparisons=[]
   for mode in ['stochastic','dynamical']:
    extracted=[]
    for label,prefix in [('original','velociraptor-original-scipy1163-1/'+mode+'/original-results/complete-value-tree/'),('adapter','velociraptor-adapter-prototype-5/'+mode+'-adapter/results/result-value-tree/')]:
     directory=out/(mode+'-'+label);directory.mkdir();population=[name for name in members if name.startswith(prefix)]
     if not population:raise ValueError('Complete original/candidate value tree missing')
     for name in population:
      relative=PurePosixPath(name.removeprefix(prefix));dest=directory.joinpath(*relative.parts)
      if not dest.resolve().is_relative_to(directory.resolve()):raise ValueError('Invalid original tree destination')
      dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes(raw(name))
     extracted.append(directory)
    result=compare_trees(*extracted);result['mode']=mode;comparisons.append(result)
    # These are the actual separate R-process strict comparisons, not a fresh R
    # execution or a claim about opaque RDS byte/environment identity.
    import json
    for suffix in ['strict-comparison.json','strict-parameter-comparison.json']:
     strict=json.loads(raw('velociraptor-adapter-prototype-5/'+mode+'-'+suffix))
     if strict['status']!='PASS_COMPLETE_ORIGINAL_R_VALUES_STRICT' or not strict['complete_value_comparison'] or not strict['require_numeric_bit_equality'] or not strict['require_NA_and_NaN_payload_equality'] or strict['opaque_RDS_file_byte_parity_claimed']:raise ValueError('Retained strict complete original R comparison differs')
   if sum(row['nodes'] for row in comparisons)!=941 or sum(row['atomic_payloads'] for row in comparisons)!=666 or sum(row['atomic_payload_bytes'] for row in comparisons)!=668143:raise ValueError('Complete original-versus-adapter value population differs')
   import json
   protocol=json.loads(raw('velociraptor-adapter-protocol-3/COMPLETED.json'));limits=json.loads(raw('velociraptor-adapter-capacity-1/COMPLETED.json'))
   if protocol['status']!='PASS_ORIGINAL_VELOCIRAPTOR_PROTOCOL_CONTROLS' or protocol['case_count']!=12 or len(protocol['cases'])!=12 or limits['status']!='PASS_TWO_ACTUAL_ORIGINAL_R_CAPACITY_REFUSALS' or len(limits['cases'])!=2:raise ValueError('Complete current protocol/capacity control population differs')
   for stage in [protocol,limits]:
    for name,wanted in proof['engine_sources'].items():
     if stage['source_pins'][name]!=wanted:raise ValueError('Actual current control executed engine differs')
   for case in limits['cases']:
    status=case['status']
    if case['observed_exit']!=2 or status['result_admitted'] or not status['warning_review_required']:raise ValueError('Capacity failure admitted a partial result or removed review')
    if case['case']=='wall-time' and (status['R_handler_condition_counts_complete'] or any(status[k] is not None for k in ['R_handler_warnings','R_handler_messages','R_handler_errors'])):raise ValueError('Interrupted unknown conditions were zeroed')
  result=dict(status='PASS_FROZEN_DEVELOPMENT_CHECKPOINT_TWO_FULL_R_VALUE_TREES_AND_14_RETAINED_CONTROLS',complete_compared_modes=comparisons,all_archive_members_verified=len(members),archive_volumes=len(proof['parts']),retained_protocol_controls=12,retained_actual_capacity_controls=2,verifier_source=pin(Path(__file__)),R_or_Python_backend_installed=False,models_fit=False,original_R_diagnostics_unresolved=True,raw_console_diagnostic_counts_complete=False,whole_R_wrapper_qualified=False,new_family_credit=0,whole_goal_complete=False)
  save(out/'COMPLETED.json',result);(out/'WARNINGS.md').write_text('# Development checkpoint replay; no study or catalog admission\n\n'+ACTION+'\n',encoding='utf-8',newline='\n');print(result['status'],flush=True);return 0
 except BaseException as error:
  result=dict(status='FAIL2_FROZEN_DEVELOPMENT_CHECKPOINT_UNADMITTED',cause=repr(error),traceback=traceback.format_exc(),result_admitted=False,required_action=ACTION)
  if owned:save(out/'FAILURE.json',result)
  print(result['status'],repr(error),ACTION,flush=True);return 2
if __name__=='__main__':raise SystemExit(main())
