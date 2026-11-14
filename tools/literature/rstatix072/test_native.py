#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Compare every native Levene case with complete saved original R results; retain all refusals."""
from pathlib import Path
import argparse,hashlib,json,os,subprocess,time,traceback
HERE=Path(__file__).resolve().parent
ACTION='Required action: preserve complete input/reference/native populations and all returned original objects/errors/warnings. Missing permitted runtime, artifact or reference SKIP3 skips dependent checks. FAIL2 admits no qualification; retain diagnostics, resolve the discrepancy and retry NEW paths. Degenerate native cases are explicit refusals, not coerced probabilities. Do not drop failed cases or claim native support for all rstatix functions or general car/R formulas.'
def save(path,value):path.write_text(json.dumps(value,indent=2)+'\n',encoding='utf8',newline='\n')
def main():
 p=argparse.ArgumentParser(description=__doc__,epilog=ACTION);p.add_argument('--java-bin',type=Path,required=True);p.add_argument('--jar',type=Path,required=True);p.add_argument('--fixtures',type=Path,required=True);p.add_argument('--reference',type=Path,required=True);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve();owned=False;calls=[]
 try:
  for protected in [HERE,a.java_bin.resolve(),a.jar.resolve(),a.fixtures.resolve(),a.reference.resolve()]:
   if out.is_relative_to(protected) or protected.is_relative_to(out):raise ValueError('Control output overlaps protected source/input/runtime')
  out.mkdir(parents=True,exist_ok=False);owned=True;suffix='.exe' if os.name=='nt' else '';java=a.java_bin/('java'+suffix);javac=a.java_bin/('javac'+suffix)
  env={k:v for k,v in os.environ.items() if k.upper() not in ['JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS','_JAVA_OPTIONS','CLASSPATH']}
  def run(label,command,expected):
   command=list(map(str,command));start=time.time();r=subprocess.run(command,cwd=out,env=env,stdin=subprocess.DEVNULL,capture_output=True,timeout=180);(out/(label+'.stdout')).write_bytes(r.stdout);(out/(label+'.stderr')).write_bytes(r.stderr);row=dict(label=label,command=command,exit_code=r.returncode,expected_exit=expected,elapsed_seconds=time.time()-start);calls.append(row);save(out/(label+'.process.json'),row)
   if r.returncode!=expected:raise ValueError('Unexpected actual control exit: '+label)
  def cli(label,request,expected=2):run(label,[java,'-jar',a.jar,'--request',request,'--out',out/label],expected)
  cli('mixed-complete',a.fixtures/'request.json',2);cli('positive-complete',a.fixtures/'positive-request.json',0)
  rows=json.loads((out/'mixed-complete/cases.json').read_bytes());positive=json.loads((out/'positive-complete/cases.json').read_bytes());expected=json.loads((a.fixtures/'EXPECTATIONS.json').read_bytes());request=json.loads((a.fixtures/'request.json').read_bytes())['cases'];original=json.loads((a.reference/'complete-original-values.json').read_bytes())
  if len(rows)!=206 or len(positive)!=196 or len(original)!=206 or [r['id'] for r in rows]!=[r['id'] for r in original] or [r['id'] for r in rows]!=[r['id'] for r in request]:raise ValueError('Incomplete or changed case identities/order')
  comparisons=[];refusals=[];scalars=0
  for actual,reference,input_row in zip(rows,original,request,strict=True):
   case=actual['id']
   if expected[case]=='refuse':
    if actual['status']!='FAIL2_NATIVE_LEVENE_CASE_UNADMITTED':raise ValueError('Invalid case admitted: '+case)
    refusals.append(dict(id=case,native=actual,original=reference));continue
   if actual['status']!='COMPUTED_NATIVE_LEVENE' or reference['status']!='ORIGINAL_RETURNED':raise ValueError('Expected full native/original result unavailable: '+case)
   differences={}
   for key in ['df1','df2','statistic','p']:
    wanted=reference['result'][key];value=actual[key]
    # jsonlite auto_unbox still retains one-element columns in some versions.
    if isinstance(wanted,list):wanted=wanted[0]
    difference=abs(value-wanted);tolerance=2e-9*max(1,abs(wanted)) if key=='statistic' else (2e-10*max(1e-300,abs(wanted))+1e-14 if key=='p' else 0)
    if difference>tolerance:raise ValueError('Full original scalar differs: '+case+'/'+key+': '+str(value)+' vs '+str(wanted))
    differences[key]=difference;scalars+=1
   omitted=[i for i,(x,g) in enumerate(zip(input_row['values'],input_row['groups'],strict=True)) if x=='NaN' or g is None]
   if actual['omitted_rows']!=omitted or actual['kept_rows']!=[i for i in range(len(input_row['values'])) if i not in omitted]:raise ValueError('Literal row identities differ')
   comparisons.append(dict(id=case,absolute_scalar_differences=differences))
  if positive!=[r for r in rows if expected[r['id']]=='pass']:raise ValueError('Separately declared positive population differs')
  for label,admitted in [('mixed-complete',False),('positive-complete',True)]:
   folder=out/label;status=json.loads((folder/'analysis-status.json').read_bytes());manifest=json.loads((folder/'MANIFEST.json').read_bytes())
   if status['result_admitted']!=admitted:raise ValueError('Partial batch admission differs')
   if set(manifest['files'])!={'request.json','cases.json','WARNINGS.md'} or set(manifest['excluded'])!={'MANIFEST.json','analysis-status.json'}:raise ValueError('Incomplete manifest coverage')
   for name,wanted in manifest['files'].items():
    raw=(folder/name).read_bytes()
    if dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())!=wanted:raise ValueError('Complete native result manifest differs')
  invalid=dict(schema_version=1,cases=[dict(id='invalid-α',values=[1,'NaN',2,4,8,16],groups=['a']*3+['b']*3,center='MEDIAN',missing_policy='FAIL')]);save(out/'missing-policy-input.json',invalid);cli('missing-policy',out/'missing-policy-input.json')
  invalid['cases'][0]['extra']='unsupported';save(out/'unknown-field-input.json',invalid);cli('unknown-field',out/'unknown-field-input.json')
  (out/'invalid-utf8.json').write_bytes(b'{"schema_version":1,"cases":[]}' + bytes([0xff]));cli('invalid-utf8',out/'invalid-utf8.json')
  save(out/'empty.json',dict(schema_version=1,cases=[]));cli('empty-population',out/'empty.json')
  cli('missing-input',out/'absent.json',3)
  before=(out/'positive-complete/analysis-status.json').read_bytes();run('existing-output-refusal',[java,'-jar',a.jar,'--request',a.fixtures/'positive-request.json','--out',out/'positive-complete'],2)
  if (out/'positive-complete/analysis-status.json').read_bytes()!=before:raise ValueError('Existing scientific output overwritten')
  classes=out/'api-classes';classes.mkdir();run('compile-api',[javac,'--release','17','-encoding','UTF-8','-Xlint:all','-Werror','-proc:none','-cp',a.jar,'-d',classes,HERE/'NativeApiControl.java'],0)
  run('api-controls',[java,'-cp',str(classes)+os.pathsep+str(a.jar),'NativeApiControl'],0)
  if 'checks=29' not in (out/'api-controls.stdout').read_text():raise ValueError('Incomplete public API checks')
  save(out/'COMPLETE_COMPARISONS.json',comparisons);save(out/'ORIGINAL_AND_NATIVE_REFUSALS.json',refusals)
  result=dict(status='PASS_NATIVE_LEVENE_FULL_ORIGINAL_SCALARS_IDENTITIES_AND_REFUSALS',complete_cases=206,complete_computed_cases=196,original_scalar_comparisons=scalars,native_refusals=10,public_API_checks=29,CLI_refusals=6,calls=calls,
   original_R_version='4.5.3',original_rstatix_version='0.7.2',paper_reproduced=False,new_tool_credit=0,whole_goal_complete=False)
  save(out/'COMPLETED.json',result);print(result['status'],flush=True);return 0
 except Exception as error:
  code=3 if isinstance(error,OSError) else 2;result=dict(status='SKIP3_LEVENE_CHECK_PREREQUISITE_UNAVAILABLE' if code==3 else 'FAIL2_NATIVE_LEVENE_CONTROLS',cause=repr(error),traceback=traceback.format_exc(),calls=calls,required_action=ACTION)
  if owned:save(out/'FAILURE.json',result);(out/'WARNINGS_FAILURE.md').write_text(result['status']+'\n'+repr(error)+'\n'+ACTION+'\n',encoding='utf8',newline='\n')
  print(result['status'],repr(error),'\n'+ACTION,flush=True);return code
if __name__=='__main__':raise SystemExit(main())
