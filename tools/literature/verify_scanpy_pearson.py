#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Read saved native Pearson proof; optionally restore the small Java library. No scientific rerun."""
from pathlib import Path,PurePosixPath
import argparse,hashlib,io,json,sys,zipfile,xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parents[2]
ACTION='SKIP3: restore the exact named permitted source/evidence dependency and retry NEW output; skip its dependent audit meanwhile. FAIL2: preserve changed/invalid evidence and diagnostics, correct the cause and retry NEW output. Do not refresh old hashes. Saved proof is not a fresh original-package rerun, biological validation or authentication against coordinated forgery.'
class Missing(Exception):pass
def read(path,cap=64000000):
 try:
  if not path.is_file() or path.is_symlink():raise Missing('Required readable regular source/evidence missing: '+str(path))
  if path.stat().st_size>cap:raise ValueError('Byte capacity exceeded: '+str(path))
  data=path.read_bytes()
 except (FileNotFoundError,PermissionError) as error:raise Missing('Restore exact readable permitted dependency: '+str(path)) from error
 if len(data)>cap:raise ValueError('Evidence grew beyond capacity')
 return data
def pin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
def name(n):
 p=PurePosixPath(n)
 if p.is_absolute() or '..' in p.parts or '\\' in n or str(p)!=n:raise ValueError('Unsafe relative evidence path')
 return str(p)
def unpack(raw,expected,cap=128000000):
 with zipfile.ZipFile(io.BytesIO(raw)) as z:
  names=z.namelist()
  if len(names)!=len(set(names)) or set(names)!=set(expected):raise ValueError('Complete ZIP payload population differs')
  if sum(i.file_size for i in z.infolist())>cap:raise ValueError('ZIP decompression capacity exceeded')
  result={}
  for n in names:
   name(n);data=z.read(n)
   if pin(data)!=expected[n]:raise ValueError('ZIP payload differs: '+n)
   result[n]=data
  return result
def fail(out,code,status,error):
 record=dict(status=status,error=str(error),required_action=ACTION,results_admitted=False)
 (out/'FAILED.json').write_text(json.dumps(record,indent=2)+'\n',encoding='utf-8');print(status+': '+str(error)+'\n'+ACTION,file=sys.stderr);return code
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',required=True);p.add_argument('--bundle',default=str(ROOT/'docs/literature/scanpy-pearson-checks'));p.add_argument('--restore-component',action='store_true');args=p.parse_args();out=Path(args.out).resolve();folder=Path(args.bundle).resolve()
 if out.exists():print('FAIL2: preserve existing output and choose NEW paths. '+ACTION,file=sys.stderr);return 2
 out.mkdir(parents=True)
 try:
  raw=read(folder/'QUALIFICATION.json');q=json.loads(raw);r=json.loads(read(folder/'READBACK.json'))
  if q['status']!='PASS_NATIVE_SCANPY_PEARSON_SAVED_QUALIFICATION' or pin(raw)!=r['qualification']:raise ValueError('Qualification identity/status differs')
  for n,wanted in q['sources'].items():
   if pin(read(ROOT/name(n)))!=wanted:raise ValueError('Current source differs: '+n)
  archive=read(folder/'evidence.zip')
  if pin(archive)!=q['evidence_archive'] or pin(archive)!=r['evidence_archive']:raise ValueError('Evidence archive identity differs')
  payloads=unpack(archive,q['evidence_payloads'])
  component=payloads['component/omicsmatic-scanpy-pearson-development.jar']
  if pin(component)!=q['component']:raise ValueError('Component identity differs')
  unpack(component,q['component_payloads'])
  fixture=read(ROOT/name(q['reference_fixture']['path']))
  if pin(fixture)!=q['reference_fixture']['pin']:raise ValueError('Original reference fixture differs')
  with zipfile.ZipFile(io.BytesIO(fixture)) as z:
   combined=json.loads(z.read('INPUTS.json'));base=json.loads(z.read('original/base/INPUTS.json'));extra=json.loads(z.read('original/implicit-zeros/INPUTS.json'))
   if combined['matrices']!=base['matrices']+extra['matrices']:raise ValueError('Derived input view differs')
   for version in ['1.10.4','1.13.0']:
    view=json.loads(z.read(version+'.json'));parts=[json.loads(z.read('original/'+label+'/'+version+'.json')) for label in ['base','implicit-zeros']]
    if len(view['cases'])!=360 or view['cases']!=parts[0]['cases']+parts[1]['cases'] or any(part['source_patched'] for part in parts):raise ValueError('Immutable original case population differs')
    if any(c['status']!='original-returned' or not c['input_unchanged'] for c in view['cases']):raise ValueError('Original input/return controls differ')
  for jvm in ['windows21','windows25','linux21']:
   suites=[ET.fromstring(raw) for n,raw in payloads.items() if n.startswith('junit/'+jvm+'/') and n.endswith('.xml')]
   totals={k:sum(int(s.get(k,0)) for s in suites) for k in ['tests','errors','failures','skipped']}
   if totals!=dict(tests=735,errors=0,failures=0,skipped=0):raise ValueError('Three-JVM check proof differs')
  for label in ['base','implicit-zeros']:
   for version in ['1.10.4','1.13.0']:
    before=json.loads(payloads['runtime/'+label+'/'+version+'-before.json']);after=json.loads(payloads['runtime/'+label+'/'+version+'-after.json'])
    if before!=after:raise ValueError('Original bound runtime changed')
  if args.restore_component:
   target=out/'component/omicsmatic-scanpy-pearson-development.jar';target.parent.mkdir()
   with target.open('xb') as stream:stream.write(component)
  result=dict(status='PASS_SAVED_NATIVE_PEARSON_PROOF',sources=len(q['sources']),payloads=len(payloads),original_cases=720,tests_per_JVM=735,component_restored=args.restore_component,fresh_scientific_programs_run=False,new_family_credit=0,whole_goal_complete=False,required_action=ACTION)
  (out/'COMPLETED.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8');print(json.dumps(result));return 0
 except Missing as error:return fail(out,3,'SKIP3_DEPENDENT_AUDIT_SKIPPED',error)
 except Exception as error:return fail(out,2,'FAIL2_NO_ADMITTED_AUDIT',error)
if __name__=='__main__':raise SystemExit(main())
