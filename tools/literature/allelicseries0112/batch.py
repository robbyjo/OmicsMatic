#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Execute all named gene requests in one unchanged original R namespace; never drop failed entries."""
from pathlib import Path
import argparse,csv,json,os,shutil,subprocess,sys,time,traceback
sys.dont_write_bytecode=True
from run import provider,pin,save
HERE=Path(__file__).resolve().parent
ACTION='Required action: provide a ready unchanged original provider, a named-list batch RDS and explicit --allow-analysis. Each literal identifier must be unique/nonmissing/nonempty; no identifier repair or dropped entry is allowed. A global seed is set once, so stochastic results depend on request order. Missing/forbidden prerequisites SKIP3 skips dependent analysis. Errors, nonfinite values and no-positive-SE/unproven scalar-zero returns prevent complete-batch admission. All completed entries and original returns remain retained; inspect BATCH_INDEX.tsv, BATCH_WARNINGS.md, entry conditions and raw logs, correct named causes and retry a NEW batch. Never relabel a partial batch as complete. The actual package is0.1.1.2; paper1.1.2 equivalence and biological reproduction are unproven.'

def main():
 p=argparse.ArgumentParser(description=__doc__,epilog=ACTION);p.add_argument('--provider',required=True,type=Path);p.add_argument('--function',required=True);p.add_argument('--arguments-rds',required=True,type=Path);p.add_argument('--seed',type=int);p.add_argument('--maximum-calls',type=int,default=100000);p.add_argument('--allow-analysis',action='store_true');p.add_argument('--out',required=True,type=Path);a=p.parse_args();out=a.out.absolute();owned=False;calls=[];details={}
 try:
  if out!=out.resolve():raise ValueError('Canonical NEW output required')
  for protected in [HERE,a.provider.resolve(),a.arguments_rds.resolve()]:
   if out.is_relative_to(protected) or protected.is_relative_to(out):raise ValueError('Output overlaps protected source/provider/input')
  out.mkdir(parents=True,exist_ok=False);owned=True
  if not a.allow_analysis:raise OSError('Analysis permission absent; batch execution skipped')
  if sys.platform!='linux':raise OSError('Use the qualified Linux/WSL R4.5.3 original profile')
  if not 1<=a.maximum_calls<=1000000:raise ValueError('maximum-calls must be1..1000000; no prefix is admitted')
  if a.seed is not None and not 0<=a.seed<=2147483647:raise ValueError('Declare an R integer seed in0..2147483647')
  ready,profile,library,prefix,base,rscript=provider(a.provider.resolve())
  if out.is_relative_to(prefix) or prefix.is_relative_to(out):raise ValueError('Output overlaps protected runtime')
  if a.function not in profile['function_exports']:raise ValueError('Select one of the16 original exports: '+', '.join(profile['function_exports']))
  helpers={n:pin(HERE/n) for n in ['batch.py','run.py','OriginalBatch.R','OriginalProtocol.R','source-profile.json']};input_pin=pin(a.arguments_rds)
  shutil.copyfile(a.arguments_rds,out/'batch-arguments.rds');shutil.copyfile(HERE/'source-profile.json',out/'source-profile.json')
  save(out/'REQUEST.json',dict(function=a.function,seed=a.seed,seed_policy='One original RNG stream, seeded once before the first call; input order preserved.',maximum_calls=a.maximum_calls,input_rds=input_pin,provider_ready=pin(a.provider/'READY.json'),source_pins=helpers,reported_paper_version='1.1.2',actual_package_version='0.1.1.2',paper_build_equivalence_proven=False))
  env={k:v for k,v in os.environ.items() if k not in ['R_LIBS','R_LIBS_USER','R_LIBS_SITE','R_DEFAULT_PACKAGES','R_PROFILE_USER','R_ENVIRON_USER']}
  env.update(PATH=str(prefix/'bin')+':/usr/bin:/bin',R_HOME=str(prefix/'lib/R'),LD_LIBRARY_PATH=str(prefix/'lib'),R_PROFILE_USER='/dev/null',R_ENVIRON_USER='/dev/null',R_LIBS=str(library)+':'+str(base),R_LIBS_USER=str(library),R_LIBS_SITE='',LC_ALL='C.UTF-8',OMP_NUM_THREADS='1',OPENBLAS_NUM_THREADS='1',MKL_NUM_THREADS='1')
  command=list(map(str,[rscript,'--vanilla',HERE/'OriginalBatch.R',library,base,a.function,out/'batch-arguments.rds',out,'MISSING' if a.seed is None else a.seed,a.maximum_calls,HERE/'OriginalProtocol.R']));start=time.time()
  with (out/'original.stdout').open('xb') as so,(out/'original.stderr').open('xb') as se:
   proc=subprocess.Popen(command,env=env,cwd=out,stdin=subprocess.DEVNULL,stdout=so,stderr=se);save(out/'LIVE.json',dict(pid=proc.pid,command=command,started_unix=start));code=proc.wait()
  row=dict(command=command,exit_code=code,elapsed_seconds=time.time()-start);calls.append(row);save(out/'original.process.json',row)
  provider(a.provider.resolve())
  if any(pin(HERE/n)!=wanted for n,wanted in helpers.items()) or pin(a.arguments_rds)!=input_pin:raise ValueError('Batch source, protected provider or input changed during execution')
  if not (out/'BATCH_STATUS.txt').is_file():raise ValueError('Original process exited '+str(code)+' without a complete batch record; inspect raw logs and retain all partial entries')
  status=(out/'BATCH_STATUS.txt').read_text().splitlines()
  if len(status)!=7:raise ValueError('Complete batch record differs')
  expected,accepted,failed,warned,warnings,messages=map(int,status[1:])
  csv.field_size_limit(sys.maxsize)
  with (out/'BATCH_INDEX.tsv').open(encoding='utf8',newline='') as f:entries=list(csv.DictReader(f,delimiter='\t'))
  if not 1<=expected<=a.maximum_calls or len(entries)!=expected or accepted+failed!=expected:raise ValueError('Complete batch population differs')
  ids=[r['identifier'] for r in entries]
  if len(set(ids))!=expected or any(not s for s in ids):raise ValueError('Literal identifier population differs')
  if {p.name for p in (out/'entries').iterdir()}!={f'{i:09d}' for i in range(1,expected+1)}:raise ValueError('Complete ordinal entry population differs')
  for i,r in enumerate(entries,1):
   if int(r['ordinal'])!=i or r['directory']!=f'entries/{i:09d}':raise ValueError('Entry order or protected ordinal path differs')
   s=(out/r['directory']/'RETURN_STATUS.txt').read_text().splitlines()
   if len(s)!=4 or s[0]!=r['status'] or int(s[2])!=int(r['warnings']) or int(s[3])!=int(r['messages']):raise ValueError('Entry return/condition record differs')
   if (r['analysis_admitted']=='TRUE')!=(r['status']=='ORIGINAL_RETURN_CAPTURED'):raise ValueError('Entry admission differs')
   if not (out/r['directory']/'complete-original-return.rds').is_file():raise ValueError('Complete original entry object missing')
   required='original-error.txt' if r['status']=='ORIGINAL_ERROR_NO_RESULT' else 'original-value.rds'
   if not (out/r['directory']/required).is_file():raise ValueError('Original returned value/error missing')
  if sum(r['analysis_admitted']=='TRUE' for r in entries)!=accepted or sum(int(r['warnings']) for r in entries)!=warnings or sum(int(r['messages']) for r in entries)!=messages or sum(int(r['warnings'])>0 for r in entries)!=warned:raise ValueError('Complete condition/admission counts differ')
  details=dict(requested_entries=expected,entries_with_admitted_returns=accepted,unadmitted_entries=failed,entries_with_original_warnings=warned,original_warning_count=warnings,original_message_count=messages,one_original_R_process=True)
  if warnings:print('WARNING: '+str(warned)+'/'+str(expected)+' entries emitted '+str(warnings)+' original warnings. Required action: read BATCH_WARNINGS.md and justify or correct each named input/model choice before using those returns.',flush=True)
  if code or failed or status[0]!='ORIGINAL_BATCH_ALL_RETURNS_CAPTURED':raise ValueError('Complete batch contains unadmitted entries; retain every row and follow BATCH_WARNINGS.md, no complete-batch result admitted')
  result=dict(status='PASS_COMPLETE_ORIGINAL_ALLELICSERIES0112_BATCH',**details,analysis_admitted=True,warning_review_required=warnings>0,source_modified=False,paper_reproduced=False,paper_build_equivalence_proven=False,new_tool_credit=0,whole_goal_complete=False,calls=calls)
  save(out/'COMPLETED.json',result);(out/'WARNINGS.md').write_text(('WARNING: Original warnings require review; see BATCH_WARNINGS.md.\n' if warnings else '')+ACTION+'\n',encoding='utf8',newline='\n')
  save(out/'MANIFEST.json',dict(files={p.relative_to(out).as_posix():pin(p) for p in sorted(out.rglob('*')) if p.is_file()}));print(result['status'],'entries='+str(expected),flush=True);return 0
 except Exception as e:
  code=3 if isinstance(e,OSError) else 2;value=dict(status='SKIP3_ORIGINAL_BATCH_PREREQUISITE_UNAVAILABLE' if code==3 else 'FAIL2_ORIGINAL_BATCH_NO_COMPLETE_ADMISSION',cause=repr(e),traceback=traceback.format_exc(),calls=calls,**details,analysis_admitted=False,required_action=ACTION)
  detail=(out/'BATCH_INPUT_FAILURE.txt').read_text(encoding='utf8') if owned and (out/'BATCH_INPUT_FAILURE.txt').is_file() else ''
  if owned:
   value['original_input_failure_detail']=detail;save(out/'FAILURE.json',value);(out/'WARNINGS_FAILURE.md').write_text(value['status']+'\n'+repr(e)+'\n'+detail+'\n'+ACTION+'\n',encoding='utf8',newline='\n')
  if detail:print(detail,flush=True)
  print(value['status'],repr(e),'\n'+ACTION,flush=True);return code
if __name__=='__main__':raise SystemExit(main())
