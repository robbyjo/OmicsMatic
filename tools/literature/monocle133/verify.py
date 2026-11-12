#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Replay complete saved original-1.3.3 graph ordering comparisons; repeats no R graph fitting."""
from pathlib import Path
import argparse,hashlib,json,os,shutil,subprocess,traceback,zipfile
ROOT=Path(__file__).resolve().parents[3];DATA=ROOT/'docs/literature/monocle133-graph-development'
ACTION='Required action: retain diagnostics; supply a permitted JDK21/25 and NEW output. If compiler/runtime/install is unavailable or forbidden, SKIP3 skips the dependent replay. Resolve FAIL2 source/archive/numeric/identity contradictions before retrying. This portable check compares all supplied cell/graph distances and root selections with saved original controls; it neither repeats original graph learning nor authenticates producers or reloads RDS. The separate Linux R4.3.3/Monocle3 1.3.3 runtime is required for original graph analysis.'
def pin(p):
 h=hashlib.sha256();n=0
 with p.open('rb') as f:
  for b in iter(lambda:f.read(1048576),b''):h.update(b);n+=len(b)
 return dict(bytes=n,sha256=h.hexdigest())
def save(p,v):p.write_text(json.dumps(v,indent=2)+'\n',encoding='utf8',newline='\n')
def main():
 p=argparse.ArgumentParser(description=__doc__,epilog=ACTION);p.add_argument('--java-bin',required=True,type=Path);p.add_argument('--out',required=True,type=Path);a=p.parse_args();out=a.out.absolute();owned=False;calls=[]
 try:
  if out!=out.resolve():raise ValueError('Canonical NEW output required')
  out.mkdir(parents=True,exist_ok=False);owned=True;v=json.loads((DATA/'VALIDATION.json').read_bytes())
  for n,value in v['sources'].items():
   if pin(ROOT/n)!=value:raise ValueError('Qualified source differs: '+n)
  if pin(DATA/'controls.zip')!=v['archive']:raise ValueError('Complete controls archive differs')
  evidence=out/'evidence';evidence.mkdir();objects=out/'objects';objects.mkdir()
  with zipfile.ZipFile(DATA/'controls.zip') as z:
   members=z.infolist()
   if len({m.filename for m in members})!=len(members) or len(members)>10000 or sum(m.file_size for m in members)>268435456:raise ValueError('Archive population/capacity differs')
   if z.getinfo('LAYOUT.json').file_size>4194304:raise ValueError('Object layout exceeds complete byte capacity')
   layout=json.loads(z.read('LAYOUT.json'))
   if set(layout)!=set(['schema','files']) or layout['schema']!='MONOCLE133_COMPLETE_GRAPH_OBJECTS_V1' or layout['files']!=v['archive_members']:raise ValueError('Complete logical object layout differs')
   files=layout['files'];expected={}
   if len(files)>10000 or sum(r['bytes'] for r in files.values())>268435456:raise ValueError('Restored complete population exceeds capacity')
   for name,row in files.items():
    sha=row['sha256'];size=row['bytes']
    if len(sha)!=64 or any(c not in '0123456789abcdef' for c in sha) or type(size) is not int or not 0<=size<=16777216:raise ValueError('Invalid complete object fingerprint')
    if sha in expected and expected[sha]!=size:raise ValueError('Object identity has conflicting sizes')
    expected[sha]=size
   if set(z.namelist())!={'LAYOUT.json',*('objects/'+sha for sha in expected)}:raise ValueError('Missing/extra complete object members')
   for sha,size in expected.items():
    m=z.getinfo('objects/'+sha)
    if m.is_dir() or m.file_size!=size or (m.external_attr>>16)&0o170000==0o120000:raise ValueError('Unsafe complete object')
    with z.open(m) as f:raw=f.read(16777217)
    if len(raw)!=size or hashlib.sha256(raw).hexdigest()!=sha:raise ValueError('Complete object content differs')
    (objects/sha).write_bytes(raw)
   for name,row in files.items():
    if '\\' in name or name.startswith('/') or any(x in ['','.','..'] for x in name.split('/')) or any(ord(c)<32 or ord(c)==127 for c in name):raise ValueError('Unsafe restored logical member')
    target=(evidence/name).resolve()
    if not target.is_relative_to(evidence):raise ValueError('Archive escapes owned root')
    target.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(objects/row['sha256'],target)
  def run(label,command,expected=0):
   command=list(map(str,command));r=subprocess.run(command,cwd=ROOT,stdin=subprocess.DEVNULL,capture_output=True,timeout=240);(out/(label+'.stdout')).write_bytes(r.stdout);(out/(label+'.stderr')).write_bytes(r.stderr);row=dict(label=label,command=command,exit_code=r.returncode,expected_exit=expected);calls.append(row);save(out/(label+'.process.json'),row)
   if r.returncode!=expected:raise ValueError('Replay failed: '+label)
  import sys
  run('build',[sys.executable,ROOT/'tools/literature/monocle133/build.py','--java-bin',a.java_bin,'--out',out/'build']);java=a.java_bin/('java.exe' if os.name=='nt' else 'java');jar=out/'build/monocle133-graph-development.jar'
  run('packaged-controls',[sys.executable,ROOT/'tools/literature/monocle133/test_graph.py','--java-bin',a.java_bin,'--jar',jar,'--reference',evidence/'reference','--out',out/'packaged-controls'])
  tested=json.loads((out/'packaged-controls/COMPLETED.json').read_bytes())
  if (tested['original_graphs'],tested['specific_graph_refusals'],tested['schema_skip_controls'])!=(6,10,8):raise ValueError('Incomplete packaged control populations')
  result=dict(status='PASS_SIX_COMPLETE_SAVED_MONOCLE133_NATIVE_GRAPH_ORDERING_COMPARISONS',calls=calls,complete_graphs=6,cells_compared=sum(v['cells_per_case']),graph_vertices_compared=sum(v['vertices_per_case']),original_fitting_repeated=False,RDS_reloaded=False,producer_authenticated=False,new_tool_credit=0,whole_goal_complete=False)
  save(out/'COMPLETED.json',result);(out/'WARNINGS.md').write_text(ACTION+'\n',encoding='utf8',newline='\n');print(result['status'],flush=True);return 0
 except Exception as e:
  code=3 if isinstance(e,OSError) else 2
  if owned:save(out/'FAILURE.json',dict(status='SKIP3_REPLAY_UNAVAILABLE' if code==3 else 'FAIL2_MONOCLE133_SAVED_GRAPH_COMPARISON',cause=repr(e),traceback=traceback.format_exc(),calls=calls,required_action=ACTION));(out/'WARNINGS_FAILURE.md').write_text(repr(e)+'\n'+ACTION+'\n',encoding='utf8',newline='\n')
  print('SKIP3' if code==3 else 'FAIL2',repr(e),'\n'+ACTION,flush=True);return code
if __name__=='__main__':raise SystemExit(main())
