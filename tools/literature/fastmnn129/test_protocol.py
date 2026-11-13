#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Replay complete saved FastMNN models and test actual packaged refusals; fit no new models."""
from pathlib import Path
import argparse,copy,json,os,shutil,subprocess,sys,traceback
ACTION='Required action: retain diagnostics; provide permitted Java21/25, the standalone adapter, complete saved result directories and NEW output. Auditing requires all sibling factor tables and ALL_VALUES reconstruction tables. It refits no R model, reloads no RDS and authenticates no producer. Missing/forbidden prerequisites SKIP3 dependent checks; resolve FAIL2 input/source/numeric contradictions without altering original references.'
def save(p,v):p.write_text(json.dumps(v,indent=2)+'\n',encoding='utf8',newline='\n')
def main():
 p=argparse.ArgumentParser(description=__doc__,epilog=ACTION);p.add_argument('--java',required=True,type=Path);p.add_argument('--jar',required=True,type=Path);p.add_argument('--reference',required=True,type=Path);p.add_argument('--factors',required=True,type=Path);p.add_argument('--request',required=True,type=Path);p.add_argument('--legacy-result',type=Path);p.add_argument('--runtime-request',type=Path);p.add_argument('--out',required=True,type=Path);a=p.parse_args();out=a.out.resolve();owned=False;calls=[]
 try:
  out.mkdir(parents=True,exist_ok=False);owned=True;(out/'requests').mkdir();env={k:v for k,v in os.environ.items() if k.upper() not in ['JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS','_JAVA_OPTIONS']}
  def run(label,args,expected=0,needle=None):
   command=list(map(str,[a.java.resolve(),'-Xmx2g','-jar',a.jar.resolve(),*args]));r=subprocess.run(command,env=env,stdin=subprocess.DEVNULL,capture_output=True,timeout=300);(out/(label+'.stdout')).write_bytes(r.stdout);(out/(label+'.stderr')).write_bytes(r.stderr);row=dict(label=label,command=command,exit_code=r.returncode,expected_exit=expected);calls.append(row);save(out/(label+'.process.json'),row)
   if r.returncode!=expected or (needle and needle.encode() not in r.stdout+r.stderr):raise ValueError('Packaged control differs: '+label)
   if b'Required action:' not in r.stdout+r.stderr:raise ValueError('Recovery instructions absent: '+label)
   target=out/label
   if (target/'analysis-status.json').is_file():
    status=json.loads((target/'analysis-status.json').read_bytes())
    if status['result_admitted'] or status['exit_code']!=expected:raise ValueError('Portable/refusal control admitted original analysis: '+label)
   if (target/'process').exists():raise ValueError('Portable/refusal control launched an original process: '+label)
  run('help',['--help'])
  bank=json.loads((a.reference/'COMPLETED.json').read_bytes())
  if bank['status']!='PASS_NINETEEN_COMPLETE_ORIGINAL_FASTMNN129_STREAM_CONTROLS' or len(bank['cases'])!=19 or bank['failures']:raise ValueError('Incomplete qualified19-case reference bank')
  complete=[]
  for name,row in sorted(bank['cases'].items()):
   if row['status']!='PASS_COMPLETE_ORIGINAL_FASTMNN129_STREAMED_ALL_VALUES_PARITY':continue
   original=a.reference/name/'adapter/worker-result.json';label='original-'+name;run(label,['--audit-result',original.resolve(),'--out',out/label]);audit=json.loads((out/label/'native-audit.json').read_bytes())
   if audit['reconstruction_audit']!='ALL_VALUES' or audit['complete_cells']!=row['complete_cells'] or audit['complete_features']!=row['complete_features'] or audit['numeric_reconstruction_values_checked']!=row['complete_cells']*row['complete_features']:raise ValueError('Incomplete original reconstructed population')
   complete.append(original)
  if len(complete)!=17:raise ValueError('Require all17 complete successful original forward controls')
  run('original-factors',['--audit-result',a.factors.resolve(),'--out',out/'original-factors']);factor=json.loads((out/'original-factors/native-audit.json').read_bytes())
  if factor['reconstruction_audit']!='FACTORS' or factor['numeric_reconstruction_values_checked']!=0 or factor['maximum_absolute_difference'] is not None or not factor['complete_original_low_rank_seed_factors_equal']:raise ValueError('Lazy-factor mode falsely claimed numeric reconstruction')
  if a.legacy_result:run('legacy-original',['--audit-result',a.legacy_result.resolve(),'--out',out/'legacy-original'])
  base=complete[0];source=json.loads(base.read_bytes());files=['corrected.tsv','rotation.tsv','assay-components.tsv','assay-rotation.tsv','reconstructed-cells.tsv']
  def bad(label,mutate):
   folder=out/'requests'/label;folder.mkdir();save(folder/'worker-result.json',source)
   for name in files:shutil.copyfile(base.parent/name,folder/name)
   mutate(folder);run(label,['--audit-result',folder/'worker-result.json','--out',out/label],2,'FAIL2')
  def numeric(folder,name,value):
   path=folder/name;lines=path.read_text(encoding='utf8').splitlines();fields=lines[1].split('\t');fields[1]=value;lines[1]='\t'.join(fields);path.write_text('\n'.join(lines)+'\n',encoding='utf8',newline='\n')
  for name,file in [('wrong-coordinate','corrected.tsv'),('wrong-rotation','rotation.tsv'),('wrong-seed','assay-components.tsv'),('wrong-reconstruction','reconstructed-cells.tsv')]:bad(name,lambda folder,file=file:numeric(folder,file,'999999'))
  bad('nonfinite-original',lambda folder:numeric(folder,'rotation.tsv','NaN'))
  def truncate(folder):
   path=folder/'reconstructed-cells.tsv';path.write_bytes(b'\n'.join(path.read_bytes().splitlines()[:-1])+b'\n')
  bad('truncated-population',truncate)
  def extra(folder):
   path=folder/'assay-rotation.tsv';raw=path.read_bytes();path.write_bytes(raw+raw.splitlines()[1]+b'\n')
  bad('excess-population',extra)
  def swapped(folder):
   path=folder/'corrected.tsv';lines=path.read_text(encoding='utf8').splitlines();lines[1],lines[2]=lines[2],lines[1];path.write_text('\n'.join(lines)+'\n',encoding='utf8',newline='\n')
  bad('reordered-cells',swapped)
  def metadata(folder,key,value):
   m=copy.deepcopy(source);m[key]=value;save(folder/'worker-result.json',m)
  for label,key,value in [('false-roundtrip','complete_required_model_roundtrip',False),('false-paper-patch','paper_patch_recovered',True),('wrong-model-class','original_seed_class','OtherMatrix'),('unknown-materialization','reconstruction_audit','FIRST100')]:bad(label,lambda folder,key=key,value=value:metadata(folder,key,value))
  run('native-budget',['--audit-result',base.resolve(),'--out',out/'native-budget','--maximum-native-operations','1'],2,'capacity')
  run('table-byte-budget',['--audit-result',base.resolve(),'--out',out/'table-byte-budget','--maximum-table-bytes','1'],2)
  request=json.loads(a.request.read_bytes())
  def request_case(label,mutate,expected,needle=None):
   m=copy.deepcopy(request);m['out']=str(out/label);mutate(m);path=out/'requests'/(label+'.json');save(path,m);run(label,['--request',path],expected,needle)
  request_case('forbidden',lambda m:m.update(execution_policy='FORBID'),3,'FORBID')
  request_case('deferred-null-forbidden',lambda m:(m.update(execution_policy='FORBID'),m['parameters'].update(deferred=None)),3,'FORBID')
  request_case('wrong-version',lambda m:m.update(version='1.20.0'),2,'version')
  request_case('raw-count-units',lambda m:m.update(input_units='raw-integer-counts'),2,'prepared')
  request_case('unknown-drop-field',lambda m:m.update(silent_drop_cells=True),2,'Unknown field')
  request_case('unknown-policy',lambda m:m.update(execution_policy='BYPASS'),2,'execution_policy')
  request_case('bad-named-weights',lambda m:m['parameters'].update(weights={'batch_weights':{'absent':1}}),2,'Named weights')
  request_case('duplicate-weight-tree',lambda m:m['parameters'].update(weights={'tree':[[1,1],3]}),2,'every one-based batch')
  if sys.platform!='linux':request_case('unsupported-native-host',lambda m:m.update(execution_policy='ALLOW'),3,'Linux Java')
  if a.runtime_request:
   if sys.platform!='linux':raise OSError('Actual Linux input refusals require Linux Python/Java')
   runtime=json.loads(a.runtime_request.read_bytes());data=out/'input-refusals';data.mkdir();matrix=Path(runtime['matrix']);cells=Path(runtime['cells']);features=Path(runtime['features'])
   if runtime['format']!='TSV':raise ValueError('Runtime refusal donor must be an actual qualified TSV request')
   def input_case(label,mutate):
    folder=data/label;folder.mkdir();m=copy.deepcopy(runtime)
    for key,path in [('matrix',matrix),('cells',cells),('features',features)]:target=folder/(key+'.tsv');shutil.copyfile(path,target);m[key]=str(target)
    m['out']=str(out/label);mutate(m,folder);path=out/'requests'/(label+'.json');save(path,m);run(label,['--request',path],2)
   input_case('input-nonfinite',lambda m,folder:numeric(folder,'matrix.tsv','NaN'))
   def duplicate_feature(m,folder):
    path=Path(m['features']);lines=path.read_text(encoding='utf8').splitlines();f=lines[2].split('\t');f[0]=lines[1].split('\t')[0];lines[2]='\t'.join(f);path.write_text('\n'.join(lines)+'\n',encoding='utf8',newline='\n')
   input_case('duplicate-feature-id',duplicate_feature)
   input_case('unknown-restriction-cell',lambda m,folder:m['parameters'].update(restrict_cells=['absent literal cell']))
   input_case('entry-budget',lambda m,folder:m.update(maximum_entries=1))
   def duplicate_coordinate(m,folder):
    fcount=len(Path(m['features']).read_text(encoding='utf8').splitlines())-1;ccount=len(Path(m['cells']).read_text(encoding='utf8').splitlines())-1;path=folder/'duplicate.mtx';path.write_text('%%MatrixMarket matrix coordinate real general\n'+str(fcount)+' '+str(ccount)+' 2\n1 1 1\n1 1 2\n',encoding='ascii');m.update(matrix=str(path),format='MATRIX_MARKET')
   input_case('duplicate-matrix-coordinate',duplicate_coordinate)
  save(out/'COMPLETED.json',dict(status='PASS_COMPLETE_SAVED_FASTMNN129_MATRICES_AND_ADVERSARIAL_CONTROLS',calls=calls,complete_successful_original_cases=17,complete_lazy_factor_cases=1,legacy_original_checked=bool(a.legacy_result),packaged_checks=len(calls),original_fitting_repeated=False,RDS_reloaded=False,producer_authenticated=False,new_tool_credit=0,whole_goal_complete=False));(out/'WARNINGS.md').write_text(ACTION+'\n',encoding='utf8',newline='\n');print('PASS_COMPLETE_SAVED_FASTMNN129_MATRICES_AND_ADVERSARIAL_CONTROLS',len(calls),flush=True);return 0
 except Exception as e:
  code=3 if isinstance(e,OSError) else 2;value=dict(status='SKIP3_PROTOCOL_PREREQUISITE_UNAVAILABLE' if code==3 else 'FAIL2_FASTMNN_PROTOCOL',cause=repr(e),traceback=traceback.format_exc(),calls=calls,required_action=ACTION)
  if owned:save(out/'FAILURE.json',value);(out/'WARNINGS_FAILURE.md').write_text(repr(e)+'\n'+ACTION+'\n',encoding='utf8',newline='\n')
  print(value['status'],repr(e),'\n'+ACTION,flush=True);return code
if __name__=='__main__':raise SystemExit(main())
