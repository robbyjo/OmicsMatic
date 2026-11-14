#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Replay frozen native-scope R values and all exported comparison fields; no model fitting."""
from pathlib import Path,PurePosixPath
import argparse,csv,hashlib,io,json,math,struct,sys,tarfile,traceback,zipfile
sys.dont_write_bytecode=True
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'tools/literature/velociraptor120'))
from RValues import load_tree
PROOF='docs/literature/doubletfinder206-native-development/VALIDATION.json'

def identity(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
def load(path):return json.loads(path.read_bytes())
def numeric(text):return float('nan') if text in ['NA','NaN'] else float(text)
def equal(a,b):return (math.isnan(a) and math.isnan(b)) or a==b
def label(text):return None if text=='!NA' else bytes.fromhex(text).decode('utf-8')

class Values:
 def __init__(self,path):self.path=path;self.tree=load_tree(path);self.index=self.tree['index'];self.nodes={n['id']:n for n in self.index['nodes']}
 def value(self,identifier):
  node=self.nodes[identifier];kind=node['type'];length=node['length']
  if kind=='NULL':return None
  if kind in ['list','pairlist']:
   values=[self.value(child) for child in node['children']]
   names=self.value(node['element_names'])
   if names is None:return values
   if len(set(names))!=len(names):raise ValueError('Unexpected duplicate comparison field names')
   return dict(zip(names,values,strict=True))
  raw=(self.path/node['payload']['path']).read_bytes()
  if kind in ['integer','double','logical']:
   return list(struct.unpack('<'+str(length)+('d' if kind=='double' else 'i'),raw))
  if kind=='character':
   result=[];stream=io.BytesIO(raw)
   for i in range(length):
    flag=stream.read(1)
    if flag==b'\0':result.append(None);continue
    if flag!=b'\1':raise ValueError('Unknown original character marker')
    encoding=stream.read(struct.unpack('<i',stream.read(4))[0]).decode('ascii')
    value=stream.read(struct.unpack('<i',stream.read(4))[0])
    if encoding not in ['unknown','UTF-8']:raise ValueError('Unexpected fixture string encoding')
    result.append(value.decode('utf-8'))
   if stream.read():raise ValueError('Unexpected character payload suffix')
   return result
  raise ValueError('Unsupported native comparison value kind: '+kind)

def verify(out):
 proof=load(ROOT/PROOF)
 for name,wanted in proof['sources'].items():
  if identity((ROOT/name).read_bytes())!=wanted:raise ValueError('Qualified native source differs: '+name)
 archive=ROOT/proof['artifact']
 if identity(archive.read_bytes())!=proof['archive']:raise ValueError('Frozen original native archive differs')
 extracted=out/'original-fixture';extracted.mkdir()
 with zipfile.ZipFile(archive) as stream:
  names=stream.namelist()
  if len(names)!=len(set(names)):raise ValueError('Duplicate original fixture member')
  members=json.loads(stream.read('MEMBERS.json'))
  if set(names)!=set(members['files'])|{'MEMBERS.json'}:raise ValueError('Omitted/unindexed fixture members')
  for name in names:
   path=PurePosixPath(name)
   if path.is_absolute() or any(p in ['.','..'] for p in path.parts) or '\\' in name:raise ValueError('Unsafe original member')
   raw=stream.read(name)
   if name!='MEMBERS.json' and identity(raw)!=members['files'][name]:raise ValueError('Original fixture payload differs: '+name)
   destination=extracted.joinpath(*path.parts);destination.parent.mkdir(parents=True,exist_ok=True);destination.write_bytes(raw)
 source=load(extracted/'upstream/SOURCE.json');seen={}
 raw=(extracted/'upstream/DoubletFinder-original.tar.gz').read_bytes()
 if identity(raw)!=source['archive']:raise ValueError('Original upstream archive differs')
 with tarfile.open(fileobj=io.BytesIO(raw),mode='r:gz') as stream:
  for member in stream.getmembers():
   if member.isdir():continue
   if not member.isfile():raise ValueError('Unsupported original source member')
   name='/'.join(PurePosixPath(member.name).parts[1:])
   if name in seen:raise ValueError('Duplicate upstream source member')
   seen[name]=identity(stream.extractfile(member).read())
 if seen!=source['complete_original_regular_members'] or len(seen)!=38:raise ValueError('Original complete source census differs')
 cases=list(csv.DictReader((extracted/'references/cases.tsv').open(encoding='utf-8'),delimiter='\t'))
 nodes=payloads=payload_bytes=fields=errors=0
 for case in cases:
  path=extracted/'references'/case['name'];v=Values(path/'complete-native-scope-R-values');value=v.value(v.index['root']);inputs=value['inputs'];result=value['result'];n=int(case['n'])
  nodes+=v.index['node_count'];payloads+=len(v.tree['payloads']);payload_bytes+=sum(x['bytes'] for x in v.tree['payloads'].values())
  if not (path/'complete-original-return-and-conditions.rds').is_file():raise ValueError('Complete original condition RDS missing')
  kind=case['kind']
  if kind=='homotypic':
   values=[label(x) for x in (path/'annotations.tsv').read_text().splitlines()]
   if inputs!={'annotations':values} or len(values)!=n or not equal(result[0],struct.unpack('<d',(path/'value.bin').read_bytes())[0]):raise ValueError('Homotypic original input/result export differs')
   fields+=1
  elif kind=='pk':
   rows=list(csv.reader((path/'sweep.tsv').open(encoding='utf-8'),delimiter='\t'));auc=case['auc']=='1'
   if len(rows)!=n or [label(row[0]) for row in rows]!=inputs['pK']:raise ValueError('Complete sweep labels/order differ')
   for key,column in [('BCreal',1)]+([('auc',2)] if auc else []):
    if not all(equal(numeric(row[column]),x) for row,x in zip(rows,inputs[key],strict=True)):raise ValueError('Complete sweep input numeric export differs')
   if case['status']=='ERROR':
    if result is not None or (path/'table.tsv').exists():raise ValueError('Original error case admitted a table')
    errors+=1;continue
   table=list(csv.reader((path/'table.tsv').open(encoding='utf-8'),delimiter='\t'))
   if list(result)!=table[0] or len(table)-1!=len(result['ParamID']):raise ValueError('Complete original table fields/population differ')
   for i,row in enumerate(table[1:]):
    for column,key in enumerate(table[0]):
     if key=='pK':
      if label(row[column])!=result[key][i]:raise ValueError('Original table label/order differs')
     elif not equal(numeric(row[column]),result[key][i]):raise ValueError('Original table numeric export differs')
     if key not in ['ParamID','pK']:fields+=1
  elif kind=='classify':
   scores=[numeric(x) for x in (path/'scores.tsv').read_text().splitlines()];labels=(path/'classifications.tsv').read_text().splitlines()
   if len(scores)!=n or inputs['nExp']!=[int(case['expected'])] or not all(equal(a,b) for a,b in zip(scores,inputs['scores'],strict=True)) or labels!=result:raise ValueError('Complete original pANN inputs/call vector differ')
   if not (path/'complete-original-Seurat-return.rds').is_file():raise ValueError('Full original Seurat reuse-pANN result missing')
   fields+=n
  else:raise ValueError('Unexpected native comparison kind')
 if (len(cases),errors,fields)!=(388,10,3535):raise ValueError('Complete native comparison population differs')
 return dict(status='PASS_COMPLETE_ORIGINAL_NATIVE_SCOPE_AND_ALL_EXPORTED_FIELDS',cases=388,successful=378,original_errors=10,compared_numeric_or_classification_values=3535,complete_native_scope_R_nodes=nodes,atomic_payloads=payloads,atomic_payload_bytes=payload_bytes,regular_fixture_members=len(names),complete_original_source_members=38,portable_conditions_or_Seurat_objects_claimed=False,complete_original_condition_and_Seurat_RDS_retained=True,models_fit=False,new_family_credit=0,whole_tool_qualified=False,whole_goal_complete=False)

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',required=True,type=Path);args=p.parse_args();out=args.out.resolve();owned=False
 try:
  if not out.is_relative_to(ROOT/'build/literature-review/monocle-runtime'):raise ValueError('Use a NEW monocle-runtime output')
  out.mkdir(exist_ok=False);owned=True;result=verify(out);(out/'COMPLETED.json').write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(result),flush=True);return 0
 except BaseException as e:
  if owned:(out/'FAILURE.json').write_text(json.dumps(dict(status='FAIL2_NATIVE_COMPONENT_UNADMITTED',cause=repr(e),traceback=traceback.format_exc(),required_action='Preserve all evidence, source and full RDS objects. Resolve the named cause and retry NEW paths; do not refresh old hashes or waive differences.'),indent=2)+'\n')
  print('FAIL2_NATIVE_COMPONENT_UNADMITTED',repr(e),flush=True);return 2
if __name__=='__main__':raise SystemExit(main())
