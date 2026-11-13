#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Restore complete original Rphenograph controls and rebuild/replay Java; execute no R code."""
from pathlib import Path
import argparse,hashlib,json,os,shutil,subprocess,sys,traceback,zipfile
ROOT=Path(__file__).resolve().parents[3];DATA=ROOT/'docs/literature/rphenograph099-workflow-development'
ACTION='Required action: provide permitted JDK21/25 and NEW output. Missing compiler/evidence prerequisites SKIP3 skip dependent checks. Retain FAIL2 diagnostics and resolve complete source/archive/numeric contradictions without altering original controls. This replay restores all863 original C++ cases, all five full workflow controls and every saved original model/state/component. It rebuilds Java and audits all four returned component populations, but performs no original fitting or RDS loading and authenticates no producer. P040 Rphenograph0.1.1 remains unresolved; no paper or new catalog badge is claimed.'
def pin(p):
 h=hashlib.sha256();n=0
 with p.open('rb') as f:
  for b in iter(lambda:f.read(1048576),b''):h.update(b);n+=len(b)
 return dict(bytes=n,sha256=h.hexdigest())
def save(p,v):p.write_text(json.dumps(v,indent=2)+'\n',encoding='utf8',newline='\n')
def complete_originals(evidence):
 reference=evidence/'reference';bank=evidence/'bank';models=evidence/'models'
 c=json.loads((reference/'COEFFICIENTS.json').read_bytes())
 if c['status']!='PASS_COMPLETE_ORIGINAL_RPHENOGRAPH099_COEFFICIENT_REFERENCES' or len(c['cases'])!=863 or c['complete_output_rows']!=105921 or c['original_source_patched'] or c['paper_package_identity_recovered']:raise ValueError('Incomplete/unsupported original coefficient population')
 b=json.loads((bank/'COMPLETED.json').read_bytes())
 if b['status']!='PASS_FULL_ORIGINAL_RPHENOGRAPH099_ADAPTER_EXIT_CONTROLS' or set(b['cases'])!={'iris','protein40','square','k1','duplicate'} or any(v['exit_code']!=v['expected_exit'] for v in b['cases'].values()):raise ValueError('Incomplete actual original workflow population')
 result=json.loads((models/'RESULT.json').read_bytes());returned={'iris','protein40','square','duplicate'}
 if set(result)!=returned:raise ValueError('Incomplete independent returned-model population')
 for name in returned:
  row=result[name]
  if not row['complete_public_graph_and_community_identical'] or set(row['complete_components_identical'])!={'neighbors_full','neighbors_used','raw_coefficients'} or not all(row['complete_components_identical'].values()) or row['complete_community_fields']!=['membership','memberships','modularity','names','vcount','algorithm']:raise ValueError('Incomplete independent original model/component identity: '+name)
  if pin(models/(name+'-adapter-state.rds'))!=pin(models/(name+'-reference-state.rds')):raise ValueError('Complete independent public graph/community state differs: '+name)
  for path in [bank/name/'original-model.rds',bank/name/'original-state.rds',bank/name/'complete-components.rds',reference/name/'original-model.rds',reference/name/'complete-components.rds']:
   if path.stat().st_size<1:raise ValueError('Missing complete original retained object')
  status=json.loads((bank/name/'analysis-status.json').read_bytes())
  if status['result_admitted']!=(name!='square') or status['exit_code']!=(2 if name=='square' else 0):raise ValueError('Incorrect original clustering admission: '+name)
 failed=json.loads((bank/'k1/analysis-status.json').read_bytes())
 if failed['exit_code']!=2 or failed['result_admitted'] or 'Not a matrix.' not in (bank/'k1/worker-error.txt').read_text(encoding='utf8'):raise ValueError('Original k1 failure was lost or repaired')
 square=json.loads((bank/'square/native-audit.json').read_bytes())
 if square['reportable_clustering'] or square['graph_vertices']!=0 or square['unassigned_input_cells']!=4 or square['original_modularity_token']!='NaN':raise ValueError('Original empty graph/NaN was repaired or admitted')

def main():
 p=argparse.ArgumentParser(description=__doc__,epilog=ACTION);p.add_argument('--java-bin',required=True,type=Path);p.add_argument('--out',required=True,type=Path);a=p.parse_args();out=a.out.absolute();owned=False;calls=[]
 try:
  if out!=out.resolve():raise ValueError('Use canonical NEW output')
  out.mkdir(parents=True,exist_ok=False);owned=True;v=json.loads((DATA/'VALIDATION.json').read_bytes())
  if v['status']!='PASS_ORIGINAL_RPHENOGRAPH099_COMPLETE_CLUSTERING_AND_NATIVE_COEFFICIENTS' or any(v[k] for k in ['paper_package_identity_recovered','original_source_patched','new_tool_credit','whole_tool_qualified','whole_goal_complete']):raise ValueError('Unsupported qualification scope')
  for name,wanted in v['sources'].items():
   if pin(ROOT/name)!=wanted:raise ValueError('Qualified source differs: '+name)
  if pin(DATA/'controls.zip')!=v['archive']:raise ValueError('Complete retained archive integrity differs')
  evidence=out/'evidence';evidence.mkdir();objects=out/'objects';objects.mkdir()
  with zipfile.ZipFile(DATA/'controls.zip') as z:
   members=z.infolist()
   if len({m.filename for m in members})!=len(members) or len(members)>10000 or sum(m.file_size for m in members)>536870912:raise ValueError('Invalid archive capacity/population')
   if z.getinfo('LAYOUT.json').file_size>8388608:raise ValueError('Complete layout exceeds byte capacity')
   layout=json.loads(z.read('LAYOUT.json'))
   if set(layout)!={'schema','files'} or layout['schema']!='RPHENOGRAPH099_COMPLETE_OBJECTS_V1' or layout['files']!=v['archive_members']:raise ValueError('Complete logical archive population differs')
   files=layout['files'];expected={}
   if len(files)>20000 or sum(x['bytes'] for x in files.values())>536870912:raise ValueError('Complete restored population exceeds capacity')
   for name,row in files.items():
    sha=row['sha256'];size=row['bytes']
    if len(sha)!=64 or any(c not in '0123456789abcdef' for c in sha) or type(size)is not int or not 0<=size<=67108864:raise ValueError('Invalid archive object fingerprint')
    if sha in expected and expected[sha]!=size:raise ValueError('Conflicting archive object identity')
    expected[sha]=size
   if set(z.namelist())!={'LAYOUT.json',*('objects/'+sha for sha in expected)}:raise ValueError('Missing/extra archive object')
   for sha,size in expected.items():
    m=z.getinfo('objects/'+sha)
    if m.is_dir() or m.file_size!=size or (m.external_attr>>16)&0o170000==0o120000:raise ValueError('Unsafe archive object')
    with z.open(m) as f:raw=f.read(67108865)
    if len(raw)!=size or hashlib.sha256(raw).hexdigest()!=sha:raise ValueError('Archive object content differs')
    (objects/sha).write_bytes(raw)
   for name,row in files.items():
    if '\\' in name or name.startswith('/') or any(p in ['', '.', '..'] for p in name.split('/')) or any(ord(c)<32 or ord(c)==127 for c in name):raise ValueError('Unsafe restored logical member')
    target=(evidence/name).resolve()
    if not target.is_relative_to(evidence):raise ValueError('Archive member escapes owned output')
    target.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(objects/row['sha256'],target)
  complete_originals(evidence)
  def run(label,args):
   command=list(map(str,args));r=subprocess.run(command,cwd=ROOT,stdin=subprocess.DEVNULL,capture_output=True,timeout=600);(out/(label+'.stdout')).write_bytes(r.stdout);(out/(label+'.stderr')).write_bytes(r.stderr);calls.append(dict(label=label,command=command,exit_code=r.returncode));save(out/(label+'.process.json'),calls[-1])
   if r.returncode==3:raise OSError('Required replay prerequisite unavailable at '+label+'; inspect retained logs')
   if r.returncode:raise ValueError('Complete native saved-control replay failed at '+label+'; inspect retained logs')
  tools=ROOT/'tools/literature/rphenograph099';run('build',[sys.executable,tools/'build.py','--java-bin',a.java_bin.resolve(),'--out',out/'build']);run('coefficients',[sys.executable,tools/'test_coefficients.py','--java-bin',a.java_bin.resolve(),'--reference',evidence/'reference','--out',out/'native-coefficients'])
  java=a.java_bin.resolve()/('java.exe' if os.name=='nt' else 'java');run('packaged-controls',[sys.executable,tools/'test_protocol.py','--java',java,'--jar',out/'build/rphenograph099-workflow-development.jar','--reference',evidence/'bank','--out',out/'packaged-controls'])
  tested=json.loads((out/'packaged-controls/COMPLETED.json').read_bytes());count=27 if os.name=='nt' else 26
  if tested['packaged_checks']!=count or tested['complete_original_component_cases']!=4:raise ValueError('Incomplete packaged control population')
  if any(pin(ROOT/name)!=wanted for name,wanted in v['sources'].items()) or pin(DATA/'controls.zip')!=v['archive']:raise ValueError('Qualified source/archive changed during replay')
  save(out/'COMPLETED.json',dict(status='PASS_COMPLETE_SAVED_RPHENOGRAPH099_MODELS_COEFFICIENTS_AND_REFUSALS',calls=calls,original_coefficient_cases=863,complete_coefficient_rows=105921,complete_returned_original_models=4,original_k1_failure_preserved=True,original_empty_graph_preserved_unadmitted=True,packaged_checks=count,original_R_execution_repeated=False,RDS_reloaded=False,producer_authenticated=False,paper_package_identity_recovered=False,new_tool_credit=0,whole_goal_complete=False));(out/'WARNINGS.md').write_text(ACTION+'\n',encoding='utf8',newline='\n');print('PASS_COMPLETE_SAVED_RPHENOGRAPH099_MODELS_COEFFICIENTS_AND_REFUSALS',flush=True);return 0
 except Exception as e:
  code=3 if isinstance(e,OSError) else 2
  if owned:save(out/'FAILURE.json',dict(status='SKIP3_REPLAY_PREREQUISITE_UNAVAILABLE' if code==3 else 'FAIL2_RPHENOGRAPH099_SAVED_CONTROLS',cause=repr(e),traceback=traceback.format_exc(),calls=calls,required_action=ACTION));(out/'WARNINGS_FAILURE.md').write_text(repr(e)+'\n'+ACTION+'\n',encoding='utf8',newline='\n')
  print('SKIP3' if code==3 else 'FAIL2',repr(e),'\n'+ACTION,flush=True);return code
if __name__=='__main__':raise SystemExit(main())
