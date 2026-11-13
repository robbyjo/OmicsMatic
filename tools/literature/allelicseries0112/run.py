#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Execute any of the16 unchanged original exports with retained typed RDS arguments and complete returns."""
from pathlib import Path
import argparse,hashlib,json,os,shutil,subprocess,sys,time,traceback
HERE=Path(__file__).resolve().parent
ACTION='Required action: use a ready unchanged provider, complete named argument-list RDS and explicit --allow-analysis. Supply --seed for reproducible stochastic calls. Missing/forbidden prerequisites SKIP3 skips dependent analysis. Original errors, nonfinite values and low-level no-positive-SE/unproven scalar-zero returns admit no analysis. Inspect RETURN_ADMISSION_REASON.txt, original-error.txt, original-warnings.txt, complete-original-return.rds and raw logs; correct named causes and retry NEW paths. Preserve original values and failures. No variant/allele alignment, category repair or component imputation is performed. The actual archive is0.1.1.2; paper1.1.2 equivalence and biological reproduction remain unproven.'

def pin(path):
 h=hashlib.sha256();n=0
 with path.open('rb') as f:
  for b in iter(lambda:f.read(1048576),b''):h.update(b);n+=len(b)
 return dict(bytes=n,sha256=h.hexdigest())
def save(path,value):path.write_text(json.dumps(value,indent=2)+'\n',encoding='utf8',newline='\n')

def provider(path):
 ready=json.loads((path/'READY.json').read_bytes());profile=json.loads((HERE/'source-profile.json').read_bytes())
 if ready['status']!='READY_PRIVATE_ORIGINAL_ALLELICSERIES0112_NOT_ANALYSIS' or ready['version']!='0.1.1.2' or ready['source_modified'] or ready['paper_build_equivalence_proven']:raise ValueError('Provider scope/readiness differs')
 if ready['source_profile']!=pin(HERE/'source-profile.json'):raise ValueError('Original source profile differs')
 for name,wanted in ready['source_pins'].items():
  if pin(HERE/name)!=wanted:raise ValueError('Provider helper changed; qualify a fresh provider: '+name)
 library=(path/'library').resolve();prefix=Path(ready['r_prefix']).resolve();base=prefix/'lib/R/library';rscript=prefix/'bin/Rscript'
 if Path(ready['library']).resolve()!=library or Path(ready['base_library']).resolve()!=base or Path(ready['rscript']).resolve()!=rscript:raise ValueError('Provider runtime/library locations differ')
 actual={p.relative_to(library).as_posix():p for p in library.rglob('*') if p.is_file() or p.is_symlink()}
 if set(actual)!=set(ready['installed_library']):raise ValueError('Complete installed provider population differs')
 for name,wanted in ready['installed_library'].items():
  if actual[name].is_symlink() or pin(actual[name])!=wanted:raise ValueError('Installed original provider differs: '+name)
 for name,wanted in ready['base_metadata'].items():
  if pin(Path(name))!=wanted:raise OSError('Required base-runtime metadata differs: '+name)
 return ready,profile,library,prefix,base,rscript

def main():
 p=argparse.ArgumentParser(description=__doc__,epilog=ACTION);p.add_argument('--provider',required=True,type=Path);p.add_argument('--function',required=True);p.add_argument('--arguments-rds',required=True,type=Path);p.add_argument('--seed',type=int);p.add_argument('--allow-analysis',action='store_true');p.add_argument('--out',required=True,type=Path);a=p.parse_args();out=a.out.absolute();owned=False;calls=[]
 try:
  if out!=out.resolve():raise ValueError('Canonical NEW output required')
  for protected in [HERE,a.provider.resolve(),a.arguments_rds.resolve()]:
   if out.is_relative_to(protected) or protected.is_relative_to(out):raise ValueError('Output overlaps protected source/provider/input')
  out.mkdir(parents=True,exist_ok=False);owned=True
  if not a.allow_analysis:raise OSError('Analysis permission absent; original execution skipped')
  if sys.platform!='linux':raise OSError('Use the qualified Linux/WSL original R profile')
  ready,profile,library,prefix,base,rscript=provider(a.provider.resolve())
  if out.is_relative_to(prefix) or prefix.is_relative_to(out):raise ValueError('Output overlaps protected R prefix')
  if a.function not in profile['function_exports']:raise ValueError('Select one of the16 original function exports')
  if a.seed is not None and not 0<=a.seed<=2147483647:raise ValueError('Declare an R integer seed in0..2147483647')
  helpers={n:pin(HERE/n) for n in ['run.py','OriginalRun.R','OriginalProtocol.R','source-profile.json']};input_pin=pin(a.arguments_rds);shutil.copyfile(a.arguments_rds,out/'arguments.rds');shutil.copyfile(HERE/'source-profile.json',out/'source-profile.json')
  save(out/'REQUEST.json',dict(function=a.function,seed=a.seed,input_rds=input_pin,provider_ready=pin(a.provider/'READY.json'),provider_version='0.1.1.2',reported_paper_version='1.1.2',paper_build_equivalence_proven=False,runner_sources=helpers))
  env={k:v for k,v in os.environ.items() if k not in ['R_LIBS','R_LIBS_USER','R_LIBS_SITE','R_DEFAULT_PACKAGES','R_PROFILE_USER','R_ENVIRON_USER']}
  env.update(PATH=str(prefix/'bin')+':/usr/bin:/bin',R_HOME=str(prefix/'lib/R'),LD_LIBRARY_PATH=str(prefix/'lib'),R_PROFILE_USER='/dev/null',R_ENVIRON_USER='/dev/null',R_LIBS=str(library)+':'+str(base),R_LIBS_USER=str(library),R_LIBS_SITE='',LC_ALL='C.UTF-8',OMP_NUM_THREADS='1',OPENBLAS_NUM_THREADS='1',MKL_NUM_THREADS='1')
  command=list(map(str,[rscript,'--vanilla',HERE/'OriginalRun.R',library,base,a.function,out/'arguments.rds',out,'MISSING' if a.seed is None else a.seed,HERE/'OriginalProtocol.R']));start=time.time()
  with (out/'original.stdout').open('xb') as so,(out/'original.stderr').open('xb') as se:
   proc=subprocess.Popen(command,env=env,cwd=out,stdin=subprocess.DEVNULL,stdout=so,stderr=se);save(out/'LIVE.json',dict(pid=proc.pid,command=command,started_unix=start));code=proc.wait()
  row=dict(command=command,exit_code=code,elapsed_seconds=time.time()-start);calls.append(row);save(out/'original.process.json',row)
  # A second full inventory check catches runtime/provider changes during execution.
  provider(a.provider.resolve())
  if any(pin(HERE/n)!=wanted for n,wanted in helpers.items()) or pin(a.arguments_rds)!=input_pin:raise ValueError('Runner or protected input changed during original execution')
  if not (out/'RETURN_STATUS.txt').is_file():raise ValueError('Original process exited '+str(code)+' before a complete return record; inspect raw logs, no result admitted')
  status=(out/'RETURN_STATUS.txt').read_text().splitlines()
  if len(status)!=4:raise ValueError('Original return-record structure differs; no result admitted')
  if code or status[0]!='ORIGINAL_RETURN_CAPTURED':raise ValueError('Original error, nonfinite value or low-level scalar-zero return; inspect the complete original packet and admission reason')
  warning_count=int(status[2]);message_count=int(status[3]);notice=''
  if warning_count:
   notice='WARNING: Original function emitted '+str(warning_count)+' warning(s). Required action: review the original warnings below and justify or correct the named input/model choice before using the returned result.\n'+(out/'original-warnings.txt').read_text(encoding='utf8')+'\n'
   print(notice,flush=True)
  result=dict(status='PASS_UNCHANGED_ORIGINAL_ALLELICSERIES0112_RETURN_CAPTURED',function=a.function,calls=calls,original_package_version='0.1.1.2',reported_paper_version='1.1.2',paper_build_equivalence_proven=False,original_return_captured=True,original_warning_count=warning_count,original_message_count=message_count,warning_review_required=warning_count>0,analysis_admitted=True,paper_reproduced=False,source_modified=False,new_tool_credit=0,whole_goal_complete=False)
  save(out/'COMPLETED.json',result);(out/'WARNINGS.md').write_text(notice+ACTION+'\n',encoding='utf8',newline='\n')
  save(out/'MANIFEST.json',dict(files={p.relative_to(out).as_posix():pin(p) for p in sorted(out.rglob('*')) if p.is_file()}));print(result['status'],flush=True);return 0
 except Exception as e:
  code=3 if isinstance(e,OSError) else 2;v=dict(status='SKIP3_ORIGINAL_ALLELICSERIES_ANALYSIS_UNAVAILABLE' if code==3 else 'FAIL2_ORIGINAL_ALLELICSERIES_NO_ANALYSIS_ADMISSION',cause=repr(e),traceback=traceback.format_exc(),calls=calls,analysis_admitted=False,required_action=ACTION)
  detail=''
  if owned and (out/'RETURN_STATUS.txt').is_file():detail+=(out/'RETURN_STATUS.txt').read_text(encoding='utf8')+'\n'
  if owned and (out/'original-error.txt').is_file():detail+=(out/'original-error.txt').read_text(encoding='utf8')+'\n'
  if owned and (out/'RETURN_ADMISSION_REASON.txt').is_file():detail+=(out/'RETURN_ADMISSION_REASON.txt').read_text(encoding='utf8')+'\n'
  if owned:
   v['original_return_or_error_detail']=detail;save(out/'FAILURE.json',v);(out/'WARNINGS_FAILURE.md').write_text(v['status']+'\n'+repr(e)+'\n'+detail+ACTION+'\n',encoding='utf8',newline='\n')
  if detail:print(detail,flush=True)
  print(v['status'],repr(e),'\n'+ACTION,flush=True);return code
if __name__=='__main__':raise SystemExit(main())
