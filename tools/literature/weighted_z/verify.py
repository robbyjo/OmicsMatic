#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Audit native weighted-Z source, complete references and retained qualification evidence."""
import argparse,csv,hashlib,io,json,math,sys,traceback,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[3]
EVIDENCE=ROOT/'docs/literature/weighted-z-development'
ACTION=('SKIP3: dependent audit/analysis skipped. Supply the named permitted source, '
 'evidence or Java 17+ runtime and retry into NEW output. FAIL2: no complete result '
 'or qualification admitted. Retain inputs, partials and diagnostics, correct the '
 'named cause, and retry NEW output. Never drop invalid cohorts, infer an effect '
 'direction, replace zero P with a floor, or repair NaN/Inf to force admission. '
 'Read WARNINGS.md. This native independent-cohort component is not a complete '
 'METAL workflow; overlap, allele alignment and effective N are caller responsibilities.')

def require(ok,why):
 if not ok:raise ValueError(why)

def pin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())

def filepin(path):
 digest=hashlib.sha256()
 with path.open('rb') as stream:
  for block in iter(lambda:stream.read(1048576),b''):digest.update(block)
 return dict(bytes=path.stat().st_size,sha256=digest.hexdigest())

def table(raw):return list(csv.DictReader(io.StringIO(raw.decode('utf-8')),delimiter='\t'))

def model(raw):return {r['field']:r['value'] for r in table(raw)}

def close(observed,expected,tolerance,label):
 require(math.isfinite(observed) and math.isfinite(expected),label+': nonfinite comparison')
 require(abs(observed-expected)<=tolerance,label+': numerical difference exceeds declared precision')

def four_sig(value):return .5*10**(math.floor(math.log10(abs(value)))-3) if value else 1e-14

def verify_archive(v):
 path=EVIDENCE/'checks.zip';require(filepin(path)==v['archive'],'Qualification archive changed')
 with zipfile.ZipFile(path) as z:
  names=z.namelist();entries=json.loads(z.read('ENTRY-MANIFEST.json'))['payloads']
  require(len(names)==len(set(names)) and set(names)==set(entries)|{'ENTRY-MANIFEST.json'},'Archive inventory differs')
  require(len(entries)==v['indexed_payloads'],'Archive population differs')
  for name,value in entries.items():require(pin(z.read(name))==value,'Archived payload changed: '+name)
  p=json.loads(z.read('portable-2/COMPLETED.json'))
  require(p['junit']==dict(windows=dict(tests=7,errors=0,failures=0,skipped=0),linux=dict(tests=7,errors=0,failures=0,skipped=0)),'Packaged JUnit population differs')
  compatibility=json.loads(z.read('portable-2/JAR-BASELINE-COMPATIBILITY.json'))
  require(not compatibility['changed_members'] and not compatibility['removed_members'],'Pre-existing JAR members differ')
  require(compatibility['shared_members']>0 and compatibility['added_members'] and all(n.startswith('org/jlinalg/meta/SampleSizeWeightedMetaAnalysis') for n in compatibility['added_members']),'Unexpected packaged class additions')
  for name in v['source_guards']:
   if name.startswith('src/') or name.endswith('Probe.java') or name.startswith('examples/'):
    saved='portable-2/sources/'+name
    require(pin(z.read(saved))==v['source_guards'][name],'Packaged qualification source differs: '+name)
  with zipfile.ZipFile(ROOT/'src/test/resources/org/jlinalg/meta/sample-size-weighted-z/reference-fixtures.zip') as fixture:
   cases=table(fixture.read('cases.tsv'));expected=table(fixture.read('expected.tsv'))
  require(len(cases)==70 and len({c['id'] for c in cases})==70,'Independent reference population differs')
  finite=fields=refused=raw_refusal=0
  for c in cases:
   wanted=[r for r in expected if r['case']==c['id']]
   require(wanted,'Reference model has no fields: '+c['id'])
   suffix='.refusal.txt' if c['expected']=='refuse-unrepresentable' else '.tsv'
   name=c['id']+suffix;win=z.read('portable-2/windows-models/'+name);lin=z.read('portable-2/linux-models/'+name)
   require(win==lin,'Complete packaged model differs by platform: '+name)
   if suffix=='.refusal.txt':
    require(b'NO_COMPLETE_MODEL\njava.lang.ArithmeticException\n' in win,'Unrepresentable case admitted or wrong error')
    require(any(r['value'] in ['Inf','-Inf'] for r in wanted),'Raw R unrepresentable diagnostics lost')
    refused+=1;raw_refusal+=len(wanted);continue
   actual=model(win)
   for e in wanted:
    key=e['field'];require(key in actual,'Missing reference field: '+key);observed=actual[key];wanted_value=e['value'];fields+=1
    if wanted_value=='NA':require(observed=='NA','Undefined inference was manufactured');continue
    if observed in ['true','false']:require(observed==('true' if wanted_value=='1' else 'false'),'Underflow indicator differs');continue
    value=float(wanted_value);tolerance=2e-13 if value==0 else max(4*math.ulp(value),5e-11*abs(value))
    if c['id'] in ['homogeneous','underflow'] and (key=='heterogeneityQ' or key.startswith('heterogeneityContributions.')):tolerance=max(tolerance,2e-25)
    close(float(observed),value,tolerance,c['id']+' '+key)
   finite+=1
  require((finite,fields,refused,raw_refusal)==(69,1844,1,20),'Reference comparison counts differ')
  require(z.read('portable-2/windows-models/COMPLETED.properties')==z.read('portable-2/linux-models/COMPLETED.properties'),'Platform completion populations differ')
  require(z.read('portable-2/windows-example.stdout').replace(b'\r\n',b'\n')==z.read('portable-2/linux-example.stdout').replace(b'\r\n',b'\n'),'SDK example differs')
  intake=json.loads(z.read('original/CONTROL-INTAKE.json'));ordinary=intake['ordinary_models'];require(len(ordinary)==62 and intake['all_native_fixture_models_accounted_for']==70,'Original comparison population differs')
  comparisons=single=0
  for mode in ['ordinary-p-output','ordinary-log-output']:
   rows=table(z.read('original/'+mode+'/result1.tbl'));require(len(rows)==62 and {r['MarkerName'] for r in rows}==set(ordinary),'Original output markers differ')
   for r in rows:
    case=r['MarkerName'];n=model(z.read('portable-2/windows-models/'+case+'.tsv'));require((r['Allele1'],r['Allele2'])==('a','c'),'Unexpected source output orientation')
    for field,key,tolerance in [('Weight','totalWeight',.005000001),('Zscore','z',.000500001),('HetChiSq','heterogeneityQ',.000500001),('HetDf','heterogeneityDegreesOfFreedom',0)]:
     close(float(r[field]),float(n[key]),tolerance,case+' '+field);comparisons+=1
    df=int(n['heterogeneityDegreesOfFreedom']);close(float(r['HetISq']),float(n['iSquaredPercent']) if df else 0,.050000001,case+' I2');comparisons+=1
    log=mode=='ordinary-log-output';key='log(P)' if log else 'P-value';value=float(n['logPValue'])/math.log(10) if log else float(n['pValue']);tolerance=.005000001 if log else four_sig(value)+1e-12*abs(value);close(float(r[key]),value,tolerance,case+' '+key);comparisons+=1
    key='logHetP' if log else 'HetPVal'
    if df:
     value=float(n['heterogeneityLogPValue'])/math.log(10) if log else float(n['heterogeneityPValue']);tolerance=four_sig(value)+(1e-10 if log else 1e-11*abs(value));close(float(r[key]),value,tolerance,case+' '+key);comparisons+=1
    else:require(r[key]==('0' if log else '1'),'Single-cohort source convention changed');single+=1
  require(comparisons==844 and single==24,'Original field comparison population differs')
  boundary=json.loads(z.read('original/BOUNDARY-ADMISSION.json'));require(boundary['status']=='FAIL2_ORIGINAL_BOUNDARY_RUN_NO_COMPLETE_RESULT' and boundary['original_exit_code']==0,'Failed source run was relabelled as a complete result')
  bad={r['MarkerName']:r for r in table(z.read('original/boundary/result1.tbl'))};require(len(bad)==6 and 'P_ZERO' not in bad and 'P_NEGATIVE' not in bad,'Source boundary omissions differ')
  require(any(not math.isfinite(float(bad['P_NAN'][k])) for k in ['Zscore','log(P)','HetChiSq']),'Source nonfinite failure evidence lost')
  tiny=table(z.read('tiny-q/expected.tsv'));require(len(tiny)==10,'Independent tiny-Q reference population differs')
  for e in tiny:
   name=e['case']+'.tsv';win=z.read('tiny-q/windows-models/'+name);require(win==z.read('tiny-q/linux-models/'+name),'Tiny-Q platform difference');n=model(win);value=float(e['value']);close(float(n[e['field']]),value,max(1e-18,5e-11*abs(value)),e['case']+' '+e['field'])
  source={r['MarkerName']:r for r in table(z.read('tiny-q/result1.tbl'))};small=model(z.read('tiny-q/windows-models/SMALL_Q.tsv'));require(source['SMALL_Q']['HetPVal']=='1' and float(small['heterogeneityPValue'])<.9999,'Tiny-Q source convention difference was erased')
 return dict(indexed_payloads=len(entries),finite_R_models=finite,finite_R_fields=fields,retained_unrepresentable_models=refused,retained_unrepresentable_R_fields=raw_refusal,tiny_Q_R_models=2,tiny_Q_R_fields=10,original_models_each_output=62,original_numeric_fields=comparisons,single_cohort_convention_records=single,original_nonfinite_failure_retained=True)

def main():
 p=argparse.ArgumentParser(description=__doc__,epilog=ACTION);p.add_argument('--out',required=True,type=Path);p.add_argument('--jar',type=Path,help='Optionally hash the exact qualified development JAR; not an analysis launch');a=p.parse_args();out=None
 try:
  if sys.version_info<(3,9):raise FileNotFoundError('Developer audit needs Python 3.9+; native analysis needs Java 17+')
  target=a.out.absolute();require(target.resolve().is_relative_to(ROOT/'build'),'Choose NEW workspace build audit output');target.mkdir(parents=True,exist_ok=False);out=target
  v=json.loads((EVIDENCE/'VALIDATION.json').read_bytes());require(v['status']=='PASS_NATIVE_SAMPLE_SIZE_WEIGHTED_Z_COMPONENT','Missing native qualification')
  for name,value in v['source_guards'].items():require(filepin(ROOT/name)==value,'Qualified source/fixture changed: '+name)
  require(v['new_family_credit']==0 and v['whole_METAL_tool_implemented'] is False and v['whole_goal_complete'] is False,'Native component scope was broadened')
  result=verify_archive(v)
  if a.jar:require(filepin(a.jar)==v['jar'],'JAR does not match this qualification; build/qualify changed source separately')
  result.update(status=v['status'],current_jar_hashed=bool(a.jar),scientific_runtime_reexecuted=False,junit_reexecuted=False,new_family_credit=0,whole_goal_complete=False)
  (out/'COMPLETED.json').write_bytes((json.dumps(result,indent=2)+'\n').encode());print(json.dumps(result),flush=True);return 0
 except Exception as e:
  code=3 if isinstance(e,(FileNotFoundError,PermissionError)) else 2;result=dict(status='SKIP3_DEPENDENT_AUDIT_SKIPPED' if code==3 else 'FAIL2_NO_QUALIFICATION_ADMITTED',error=repr(e),detail=traceback.format_exc(),required_action=ACTION)
  if out is not None:(out/'FAILURE.json').write_bytes((json.dumps(result,indent=2)+'\n').encode())
  print(result['status']+': '+repr(e)+'\n'+ACTION,file=sys.stderr);return code
if __name__=='__main__':raise SystemExit(main())
