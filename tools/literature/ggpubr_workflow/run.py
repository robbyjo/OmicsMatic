#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Supervise separate unchanged ggpubr0.4.0/0.6.0 trusted-script workflows and complete plot/statistic outputs."""
from pathlib import Path
import argparse,hashlib,json,os,shutil,sys,time
sys.dont_write_bytecode=True
HERE=Path(__file__).resolve().parent;ROOT=HERE.parents[2];SOURCE=HERE.parent/'ggpubr'
ACTION='Required action: select literal ggpubr0.4.0 or0.6.0 and the matching complete private Linux/WSL R4.3.3/ggplot2 3.4.3/rstatix0.7.2 provider. Supply the exact trusted UTF-8 R script, provenance, complete population/inputs/results and explicit ALLOW execution/trusted-code policies. Install/download only permitted prerequisites using the provider guide. SKIP3 skips dependent analysis when runtime, source, input, literal export/package, installation or execution is unavailable/forbidden. FAIL2 admits no complete result; preserve full original objects, plots, layer data, warnings/errors, raw logs, manifests and every partial result, correct the named cause and retry NEW paths. Review every warning. Known original ggplot Computation failed in warnings refuse admission even when rendering continues; silent empty layers and scientific eligibility require explicit caller checks. Do not drop failed plots, repair names, substitute editions, infer paper settings or claim author-runtime/biological equivalence. Trusted R code runs with account permissions; this is not an OS sandbox or total CPU/RSS containment guarantee. Only the owned process group is supervised, excluding child-created separate sessions.'

def main():
 parser=argparse.ArgumentParser(description=__doc__,epilog=ACTION);parser.add_argument('--request',type=Path,required=True);a=parser.parse_args();out=None;owned=False;admitted=False;code=2;cause='FAIL2_INVALID_REQUEST';warnings=messages=errors=failed_layers=0
 try:
  # Reuse only the unchanged, already-qualified process/file/JSON helpers.
  supervisor=json.loads((HERE/'supervisor-profile.json').read_bytes());expected_path='tools/literature/rstatix072_workflow/run.py'
  if set(supervisor['sources'])!={expected_path}:raise ValueError('Unexpected process-supervisor source profile')
  for n,wanted in supervisor['sources'].items():
   raw=(ROOT/n).read_bytes()
   if dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())!=wanted:raise ValueError('Qualified process-supervisor source differs')
  sys.path.insert(0,str(HERE.parent))
  from rstatix072_workflow.run import Skipped,pin,save,load,text,member,integer,canonical,regular,same,tree,execute
  request=a.request.absolute();regular(request)
  if request!=request.resolve() or request.stat().st_size>1048576:raise ValueError('Canonical complete request up to1MiB required')
  raw=request.read_bytes();document=load(request);request_pin=pin(request)
  if request_pin!=dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest()):raise ValueError('Complete request changed during capture')
  fields={'schema_version','version','mode','script','provider_directory','inputs','expected_outputs','required_exports','required_packages','expected_return','input_provenance','analysis_population','execution_policy','trusted_code_policy','out','limits'}
  if not isinstance(document,dict) or set(document)!=fields:raise ValueError('Require exactly documented request fields')
  integer(document['schema_version'],1,1);version=document['version']
  if version not in ['0.4.0','0.6.0'] or document['mode']!='SCRIPT':raise ValueError('Explicit supported ggpubr edition and SCRIPT mode required')
  parent=request.parent;out=canonical(document['out'],parent);script=canonical(document['script'],parent);provider=canonical(document['provider_directory'],parent)
  if not isinstance(document['inputs'],dict) or not isinstance(document['expected_outputs'],dict):raise ValueError('Declare complete logical input/output objects')
  inputs={member(n):canonical(v,parent) for n,v in document['inputs'].items()};outputs={member(n):integer(v,0,137438953472) for n,v in document['expected_outputs'].items()}
  for names in [inputs,outputs]:
   if len(names)>4096 or len({n.casefold() for n in names})!=len(names):raise ValueError('Duplicate portable logical filename or excessive population')
   for n in names:
    if any(other.casefold().startswith(n.casefold()+'/') for other in names):raise ValueError('Logical files overlap as directories')
  if not outputs:raise ValueError('Declare every complete result file')
  exports=document['required_exports'];packages=document['required_packages']
  if not isinstance(exports,list) or any(not isinstance(n,str) for n in exports) or len(exports)!=len(set(exports)):raise ValueError('Unique literal export names required')
  if not isinstance(packages,dict):raise ValueError('Explicit required package/version object required')
  for n,v in packages.items():text(n);text(v)
  for n in ['expected_return','input_provenance','analysis_population']:text(document[n])
  limits=document['limits'];caps={'timeout_seconds':(1,604800),'maximum_files':(32,1000000),'maximum_workspace_bytes':(1024,137438953472),'maximum_file_bytes':(1,137438953472)}
  if not isinstance(limits,dict) or set(limits)!=set(caps):raise ValueError('Declare all resource capacities')
  for n,(lower,upper) in caps.items():integer(limits[n],lower,upper)
  for p in [request,script,provider,*inputs.values(),HERE,SOURCE]:
   if out.is_relative_to(p) or p.is_relative_to(out):raise ValueError('Output overlaps protected source/provider/input/request')
  out.mkdir(exist_ok=False);owned=True;(out/'source-request.json').write_bytes(raw)
  for n in ['execution_policy','trusted_code_policy']:
   if document[n] not in ['ALLOW','FORBID']:raise ValueError('Declare ALLOW or FORBID: '+n)
   if document[n]=='FORBID':raise Skipped('Policy FORBID: '+n+'; no original process launched')
  if sys.platform!='linux':raise Skipped('Use the qualified Linux/WSL original provider')
  ready_file=provider/'READY.json';regular(ready_file);ready_pin=pin(ready_file);ready=load(ready_file);profile_file=SOURCE/'source-profiles.json';profile=load(profile_file)
  if ready.get('status')!='READY_PRIVATE_UNCHANGED_ORIGINAL_GGPUBR_NOT_ANALYSIS' or ready.get('version')!=version or ready.get('R_version')!='4.3.3' or ready.get('source_profile')!=pin(profile_file):raise ValueError('Matching original edition/provider identity differs')
  selected=[profile['profiles']['ggplot2-3.4.3'],profile['profiles']['rstatix-0.7.2'],profile['profiles']['ggpubr-'+version]]
  if set(exports)-set(selected[-1]['namespace_exports']):raise Skipped('Required literal original export unavailable; no edition/name substitution')
  protected={request:request_pin,ready_file:ready_pin,profile_file:pin(profile_file),HERE/'supervisor-profile.json':pin(HERE/'supervisor-profile.json')}
  for n,wanted in supervisor['sources'].items():same(ROOT/n,wanted);protected[ROOT/n]=wanted
  for n,wanted in ready['source_pins'].items():same(SOURCE/n,wanted);protected[SOURCE/n]=wanted
  for key in ['dependency_files','font_files']:
   for n,wanted in ready[key].items():same(Path(n),wanted);protected[Path(n)]=wanted
  library=Path(ready['library']);source=Path(ready['source_root']);prefix=Path(ready['r_prefix']);rscript=Path(ready['rscript'])
  if library!=provider/'library' or source!=provider/'source' or rscript!=prefix/'bin/Rscript' or Path(ready['base_library'])!=prefix/'lib/R/library':raise ValueError('Declared provider paths differ')
  expected_source={n:wanted for spec in selected for n,wanted in spec['source_files'].items()}
  for root,expected in [(library,ready['installed_library']),(source,expected_source)]:
   if not root.is_dir():raise Skipped('Complete original provider directory unavailable')
   actual={p.relative_to(root).as_posix():p for p in root.rglob('*') if p.is_file() or p.is_symlink()}
   if set(actual)!=set(expected):raise ValueError('Complete original source/library population differs')
   for n,wanted in expected.items():same(actual[n],wanted);protected[actual[n]]=wanted
  for spec in selected:
   p=provider/'archives'/spec['archive_name'];same(p,spec['archive']);protected[p]=spec['archive']
  same(provider/'readiness.json',ready['readiness']);protected[provider/'readiness.json']=ready['readiness']
  regular(script);regular(rscript)
  if not os.access(rscript,os.X_OK):raise Skipped('Original Rscript is not executable')
  if script.stat().st_size>16777216:raise ValueError('Complete trusted script exceeds16MiB')
  script_raw=script.read_bytes();script_raw.decode('utf8',errors='strict');script_pin=pin(script);protected[script]=script_pin
  for n in ['inputs','results','diagnostics','owned-home','tmp']:(out/n).mkdir()
  (out/'script.R').write_bytes(script_raw);same(out/'script.R',script_pin);protected[out/'script.R']=script_pin
  for n in ['run.py','OriginalWorker.R']:
   protected[HERE/n]=pin(HERE/n)
  shutil.copyfile(HERE/'OriginalWorker.R',out/'OriginalWorker.R');protected[out/'OriginalWorker.R']=pin(HERE/'OriginalWorker.R')
  shutil.copyfile(profile_file,out/'source-profiles.json');protected[out/'source-profiles.json']=pin(profile_file)
  input_pins={}
  for n,p in inputs.items():
   regular(p);wanted=pin(p);target=out/'inputs'/n;target.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(p,target);same(target,wanted);same(p,wanted);protected[p]=wanted;protected[target]=wanted;input_pins[n]=wanted
  save(out/'input-pins.json',input_pins);save(out/'original-provider.json',ready)
  expected_counts={name:dict(functions=len(v['direct_source_functions']),prototypes=len(v['source_ggproto_objects'])) for name,v in ready['namespace_checks'].items()}
  control=dict(version=version,library=str(library),base_library=ready['base_library'],source_root=str(source),source_profile=str(out/'source-profiles.json'),expected_counts=expected_counts,
   required_exports=exports,required_packages=packages,script=str(out/'script.R'),inputs=str(out/'inputs'),results=str(out/'results'),diagnostics=str(out/'diagnostics'),
   expected_return=document['expected_return'],input_provenance=document['input_provenance'],analysis_population=document['analysis_population'])
  save(out/'worker-control.json',control);protected[out/'worker-control.json']=pin(out/'worker-control.json')
  env={k:v for k,v in os.environ.items() if not k.startswith('R_') and k not in ['LD_LIBRARY_PATH','LD_PRELOAD','PYTHONPATH']};env.update(PATH=str(prefix/'bin')+':/usr/bin:/bin',R_HOME=str(prefix/'lib/R'),LD_LIBRARY_PATH=str(prefix/'lib'),R_ENVIRON_USER='/dev/null',R_PROFILE_USER='/dev/null',R_LIBS=str(library)+':'+ready['base_library'],R_LIBS_USER=str(library),R_LIBS_SITE='',HOME=str(out/'owned-home'),TMPDIR=str(out/'tmp'),LC_ALL='C.UTF-8',OMP_NUM_THREADS='1',OPENBLAS_NUM_THREADS='1',MKL_NUM_THREADS='1')
  tree(out,limits['maximum_files'],limits['maximum_workspace_bytes'],limits['maximum_file_bytes'])
  result=execute([rscript,'--vanilla',out/'OriginalWorker.R',out/'worker-control.json'],out,env,limits)
  for p,wanted in protected.items():same(p,wanted)
  status_file=out/'diagnostics/worker-status.json'
  if not status_file.is_file():
   if not (out/'diagnostics/analysis-started.json').is_file():raise Skipped('Original R startup/readiness unavailable; retain full logs')
   raise ValueError('Original caller script ended without complete worker diagnostics')
  status=load(status_file);warnings=status['warning_count'];messages=status['message_count'];errors=status['error_count'];failed_layers=status['failed_layer_warning_count']
  if result==3 and status['phase']=='READINESS' and status['exit_code']==3:raise Skipped('Required original dependency unavailable; retain original error and logs')
  if result!=0 or status['status']!='ORIGINAL_GGPUBR_SCRIPT_COMPLETE' or errors or failed_layers or not status['script_completed_marker']:raise ValueError('Original script failed, a statistical layer failed, or the complete population marker is missing')
  if set(tree(out/'inputs',limits['maximum_files'],limits['maximum_workspace_bytes'],limits['maximum_file_bytes']))!=set(inputs):raise ValueError('Captured input population changed')
  allowed={'source-request.json','script.R','OriginalWorker.R','source-profiles.json','inputs','results','diagnostics','owned-home','tmp','input-pins.json','original-provider.json','worker-control.json','process.stdout','process.stderr','process-live.json','process-exit.json'}
  if {p.name for p in out.iterdir()}-allowed:raise ValueError('Unexpected output outside declared results/owned diagnostics')
  actual=tree(out/'results',limits['maximum_files'],limits['maximum_workspace_bytes'],limits['maximum_file_bytes'],True)
  if set(actual)!=set(outputs):raise ValueError('Missing/extra complete results; no prefix/subset admitted')
  for n,minimum in outputs.items():
   if actual[n]['bytes']<minimum:raise ValueError('Truncated expected result: '+n)
  save(out/'result-files.json',actual);admitted=True;code=0;cause='PASS_COMPLETE_DECLARED_ORIGINAL_GGPUBR_SCRIPT_OUTPUTS'
 except Exception as error:
  unavailable=isinstance(error,OSError) and not isinstance(error,FileExistsError)
  started=owned and (out/'diagnostics/analysis-started.json').is_file()
  code=3 if unavailable and not started else 2;cause=('SKIP3: ' if code==3 else 'FAIL2: ')+repr(error)
 finally:
  if owned:
   try:
    counts_complete=not (out/'process-live.json').is_file()
    status_file=out/'diagnostics/worker-status.json'
    if status_file.is_file():
     try:
      status=load(status_file)
      values=[status[n] for n in ['warning_count','message_count','error_count','failed_layer_warning_count']]
      if any(type(v)!=int or v<0 for v in values):raise ValueError('Complete nonnegative original condition counts required')
      warnings,messages,errors,failed_layers=values;counts_complete=True
     except Exception as diagnostic_error:
      code=2;admitted=False;cause='FAIL2: '+cause+'; unreadable worker status: '+repr(diagnostic_error)
      (out/'WORKER_DIAGNOSTIC_ERROR.txt').write_text(repr(diagnostic_error)+'\nRaw worker-status.json is retained without repair. Condition totals are unavailable; inspect all raw original logs and partial condition objects.\n',encoding='utf8',newline='\n')
    if not counts_complete:warnings=messages=errors=failed_layers=None
    count_text=lambda v:str(v) if v is not None else 'unavailable (worker diagnostics incomplete)'
    (out/'WARNINGS.md').write_text('# '+cause+'\n\nOriginal warnings: '+count_text(warnings)+'; messages: '+count_text(messages)+'; errors: '+count_text(errors)+'; failed statistical-layer warnings: '+count_text(failed_layers)+'. Review every condition, full RDS object and raw process log.\n\n'+ACTION+'\n',encoding='utf8',newline='\n')
    files=tree(out,1000000,137438953472,137438953472,True,retain_link_metadata=not admitted);save(out/'MANIFEST.json',dict(files=files,excluded_final_records=['MANIFEST.json','analysis-status.json']))
    save(out/'analysis-status.json',dict(status=cause,exit_code=code,result_admitted=admitted,version=document['version'],original_warnings=warnings,original_messages=messages,original_errors=errors,failed_layer_warnings=failed_layers,condition_counts_complete=counts_complete,warning_review_required=warnings is None or warnings>0,whole_tool_qualified=False,paper_reproduced=False,whole_goal_complete=False,required_action=ACTION))
   except Exception as recording:
    code=2;admitted=False;cause='FAIL2: Complete status/inventory recording failed: '+repr(recording)
    try:
     (out/'WARNINGS.md').write_text('# '+cause+'\n\nNo complete analysis is admitted. Preserve all raw logs, objects and partial files.\n\n'+ACTION+'\n',encoding='utf8',newline='\n')
     (out/'analysis-status.json').write_text(json.dumps(dict(status=cause,exit_code=2,result_admitted=False,required_action=ACTION),indent=2)+'\n',encoding='utf8')
    except OSError:pass
 print(cause+'\n'+ACTION,flush=True);return code
if __name__=='__main__':raise SystemExit(main())
