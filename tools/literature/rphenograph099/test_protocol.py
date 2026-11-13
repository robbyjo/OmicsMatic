#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Test complete saved components, refusals and original failure retention in the packaged adapter."""
from pathlib import Path
import argparse,copy,json,os,shutil,subprocess,sys,traceback
ACTION='Required action: supply permitted Java, the standalone adapter, the complete five-case original adapter bank and NEW output. Retain diagnostics; resolve FAIL2 complete source/input/component contradictions without changing original controls. Optional --runtime-request on Linux exercises invalid-input refusals against an existing exact provider; no original process is launched. Portable checks refit no original model, reload no RDS, authenticate no producer and never recover paper version0.1.1.'
def save(p,v):p.write_text(json.dumps(v,indent=2)+'\n',encoding='utf8',newline='\n')
def main():
 p=argparse.ArgumentParser(description=__doc__,epilog=ACTION);p.add_argument('--java',required=True,type=Path);p.add_argument('--jar',required=True,type=Path);p.add_argument('--reference',required=True,type=Path);p.add_argument('--out',required=True,type=Path);p.add_argument('--runtime-request',type=Path);a=p.parse_args();out=a.out.resolve();owned=False;calls=[]
 try:
  out.mkdir(parents=True,exist_ok=False);owned=True;data=out/'donors';data.mkdir();requests=out/'requests';requests.mkdir();env={k:v for k,v in os.environ.items() if k.upper() not in ['JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS','_JAVA_OPTIONS']}
  def run(label,args,expected=0,needle=None):
   command=list(map(str,[a.java.resolve(),'-Xmx2g','-jar',a.jar.resolve(),*args]));r=subprocess.run(command,env=env,stdin=subprocess.DEVNULL,capture_output=True,timeout=240);(out/(label+'.stdout')).write_bytes(r.stdout);(out/(label+'.stderr')).write_bytes(r.stderr);calls.append(dict(label=label,command=command,exit_code=r.returncode,expected_exit=expected))
   if r.returncode!=expected or (needle and needle.encode() not in r.stdout+r.stderr):raise ValueError('Packaged complete/refusal control differs: '+label)
   if b'Required action:' not in r.stdout+r.stderr:raise ValueError('Recovery guidance absent: '+label)
   target=out/label
   if (target/'analysis-status.json').exists():
    status=json.loads((target/'analysis-status.json').read_bytes())
    if status['result_admitted'] or status['exit_code']!=expected:raise ValueError('Portable/refusal control admitted original clustering: '+label)
   if (target/'process').exists():raise ValueError('Portable/refusal control launched an original process: '+label)
  bank=json.loads((a.reference/'COMPLETED.json').read_bytes())
  if bank['status']!='PASS_FULL_ORIGINAL_RPHENOGRAPH099_ADAPTER_EXIT_CONTROLS' or set(bank['cases'])!={'iris','protein40','square','k1','duplicate'} or any(v['exit_code']!=v['expected_exit'] for v in bank['cases'].values()):raise ValueError('Incomplete actual original five-case bank')
  for name in ['square','k1']:
   status=json.loads((a.reference/name/'analysis-status.json').read_bytes())
   if status['exit_code']!=2 or status['result_admitted']:raise ValueError('Original unreportable/failure case was admitted')
  if 'Not a matrix.' not in (a.reference/'k1/worker-error.txt').read_text(encoding='utf8'):raise ValueError('Original k1 dimension-drop error was replaced')
  if not (a.reference/'square/original-model.rds').is_file():raise ValueError('Original empty graph model was discarded')
  run('help',['--help'])
  for name in ['iris','protein40','square','duplicate']:
   run('original-'+name,['--audit-result',(a.reference/name/'worker-result.json').resolve(),'--out',out/('original-'+name)])
   audit=json.loads((out/('original-'+name)/'native-audit.json').read_bytes())
   if audit['reportable_clustering']!=(name!='square') or audit['original_analysis_admitted'] or audit['nearest_neighbor_search_repeated'] or audit['Louvain_clustering_repeated']:raise ValueError('Incorrect original reportability/native scope')
  donor=a.reference/'protein40';files=['worker-result.json','neighbors-full.tsv','coefficients-raw.tsv','graph-vertices.tsv','graph-edges.tsv','cell-assignments.tsv']
  def bad(label,mutate):
   folder=data/label;folder.mkdir()
   for name in files:shutil.copyfile(donor/name,folder/name)
   mutate(folder);run(label,['--audit-result',folder/'worker-result.json','--out',out/label],2,'FAIL2')
  def field(folder,file,row,column,value):
   path=folder/file;lines=path.read_text(encoding='utf8').splitlines();fields=lines[row].split('\t');fields[column]=value;lines[row]='\t'.join(fields);path.write_text('\n'.join(lines)+'\n',encoding='utf8',newline='\n')
  bad('wrong-positive-coefficient',lambda f:field(f,'coefficients-raw.tsv',1,2,'0.1234567'))
  bad('wrong-final-zero-padding',lambda f:field(f,'coefficients-raw.tsv',4800,2,'0.5'))
  bad('wrong-neighbor',lambda f:field(f,'neighbors-full.tsv',1,2,'999999'))
  bad('wrong-literal-cell',lambda f:field(f,'neighbors-full.tsv',1,0,'invented cell'))
  bad('wrong-edge-weight',lambda f:field(f,'graph-edges.tsv',1,2,'999999'))
  bad('wrong-cell-assignment',lambda f:field(f,'cell-assignments.tsv',120,2,'999999'))
  bad('nonfinite-coefficient',lambda f:field(f,'coefficients-raw.tsv',1,2,'NaN'))
  def truncate(f):
   p=f/'neighbors-full.tsv';p.write_bytes(b'\n'.join(p.read_bytes().splitlines()[:-1])+b'\n')
  bad('missing-final-cell',truncate)
  def extra(f):
   p=f/'graph-edges.tsv';raw=p.read_bytes();p.write_bytes(raw+raw.splitlines()[1]+b'\n')
  bad('excess-edge',extra)
  def swapped(f):
   p=f/'graph-vertices.tsv';lines=p.read_text(encoding='utf8').splitlines();lines[1],lines[2]=lines[2],lines[1];p.write_text('\n'.join(lines)+'\n',encoding='utf8',newline='\n')
  bad('reordered-vertices',swapped)
  def metadata(f,key,value):
   m=json.loads((f/'worker-result.json').read_bytes());m[key]=value;save(f/'worker-result.json',m)
  for label,key,value in [('false-roundtrip','complete_model_roundtrip',False),('false-paper-identity','paper_package_identity_recovered',True),('wrong-edition','version','0.1.1'),('false-reportability','reportable_clustering',False),('wrong-graph-count','graph_edges',0)]:bad(label,lambda f,key=key,value=value:metadata(f,key,value))
  run('native-capacity',['--audit-result',donor/'worker-result.json','--out',out/'native-capacity','--maximum-native-operations','1'],2,'capacity')
  run('table-byte-capacity',['--audit-result',donor/'worker-result.json','--out',out/'table-byte-capacity','--maximum-table-bytes','1'],2)
  request=json.loads((a.reference/'requests/iris.json').read_bytes())
  def request_case(label,mutate,expected,needle=None):
   m=copy.deepcopy(request);m['out']=str(out/label);mutate(m);path=requests/(label+'.json');save(path,m);run(label,['--request',path],expected,needle)
  request_case('forbidden',lambda m:m.update(execution_policy='FORBID'),3,'FORBID')
  request_case('unsupported-paper-edition',lambda m:m.update(version='0.1.1'),2,'unresolved')
  request_case('unknown-drop-field',lambda m:m.update(silent_drop_cells=True),2,'Unknown field')
  request_case('unknown-policy',lambda m:m.update(execution_policy='BYPASS'),2,'execution_policy')
  if sys.platform!='linux':request_case('unsupported-native-host',lambda m:None,3,'Linux Java')
  if a.runtime_request:
   if sys.platform!='linux':raise OSError('Actual provider input refusals require Linux Java/Python')
   runtime=json.loads(a.runtime_request.read_bytes())
   def input_case(label,mutate):
    folder=data/label;folder.mkdir();m=copy.deepcopy(runtime);shutil.copyfile(Path(m['matrix']),folder/'matrix.tsv');m['matrix']=str(folder/'matrix.tsv');m['out']=str(out/label);mutate(m,folder);path=requests/(label+'.json');save(path,m);run(label,['--request',path],2)
   input_case('input-nonfinite',lambda m,f:field(f,'matrix.tsv',150,1,'NaN'))
   input_case('input-duplicate-cell',lambda m,f:field(f,'matrix.tsv',150,0,'literal cell 1'))
   input_case('input-duplicate-feature',lambda m,f:field(f,'matrix.tsv',0,2,'feature 1'))
   input_case('input-entry-capacity',lambda m,f:m.update(maximum_entries=1))
   input_case('input-native-capacity',lambda m,f:m.update(maximum_native_operations=1))
   input_case('input-invalid-k',lambda m,f:m.update(k=149))
  save(out/'COMPLETED.json',dict(status='PASS_COMPLETE_SAVED_RPHENOGRAPH099_COMPONENTS_AND_REFUSALS',calls=calls,packaged_checks=len(calls),complete_original_component_cases=4,original_k1_failure_retained=True,original_empty_graph_retained_unadmitted=True,original_R_execution_repeated=False,RDS_reloaded=False,producer_authenticated=False,paper_package_identity_recovered=False,whole_goal_complete=False));(out/'WARNINGS.md').write_text(ACTION+'\n',encoding='utf8',newline='\n');print('PASS_COMPLETE_SAVED_RPHENOGRAPH099_COMPONENTS_AND_REFUSALS',len(calls),flush=True);return 0
 except Exception as e:
  code=3 if isinstance(e,OSError) else 2
  if owned:save(out/'FAILURE.json',dict(status='SKIP3_PROTOCOL_PREREQUISITE_UNAVAILABLE' if code==3 else 'FAIL2_RPHENOGRAPH099_PROTOCOL',cause=repr(e),traceback=traceback.format_exc(),calls=calls,required_action=ACTION));(out/'WARNINGS_FAILURE.md').write_text(repr(e)+'\n'+ACTION+'\n',encoding='utf8',newline='\n')
  print('SKIP3' if code==3 else 'FAIL2',repr(e),'\n'+ACTION,flush=True);return code
if __name__=='__main__':raise SystemExit(main())
