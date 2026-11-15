#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Actual packaged public native workflows, complete original comparisons and retained replay/failure records."""
from pathlib import Path,PurePosixPath
import argparse,datetime,hashlib,json,math,os,shutil,struct,subprocess,time,zipfile

ROOT=Path(__file__).resolve().parents[3]
RESOURCE=ROOT/'src/test/resources/org/jlinalg/sequence'
ARCHIVES=[('tpmcalculator-original-controls.zip','03acc35d80b0f5f8f58ff4fb25df01040f4aa8a8a7e8e5d476196de6cb42c07f'),
 ('tpmcalculator-original-edges.zip','572142c0845b54b1d8201f88cb30fe49e55a506b4550c23cdccb2d7da43e573e'),
 ('tpmcalculator-original-workflow.zip','7c1012bbca813363d381df829e47857942c1b3c1429f56e5732efce706ad8594')]
PROFILE='native-java-tpmcalculator-0.0.4-gnu64'
def pin(path):
 h=hashlib.sha256();n=0
 with path.open('rb') as f:
  for b in iter(lambda:f.read(1048576),b''):h.update(b);n+=len(b)
 return dict(bytes=n,sha256=h.hexdigest())
def write(path,value):path.write_text(json.dumps(value,indent=2,ensure_ascii=False)+'\n',encoding='utf-8')
def request(folder,case):
 return dict(schema=1,profile=PROFILE,annotation=str(folder/'annotation.gtf'),samples=[dict(name=n,bam=str(folder/'bams'/(n+'.bam'))) for n in case['sample_order']],
  options=dict(gene_key=case.get('gene_key','gene_id'),transcript_key=case.get('transcript_key','transcript_id'),intron_cutoff=case['cutoff'],minimum_mapq=case['mapq'],minimum_overlap=case['overlap'],properly_paired_only=case['proper'],extended_output=case.get('extended',True),all_features=case.get('all',True)),
  limits=dict(input_bytes=536870912,bundle_bytes=536870912,files=10000,wall_seconds=120,heap_mib=256))
def extract(archive,name,folder):
 with zipfile.ZipFile(archive) as z:
  for n in z.namelist():
   if not n.startswith(name+'/'):continue
   member=PurePosixPath(n);assert not member.is_absolute() and '..' not in member.parts
   p=folder.joinpath(*member.parts[1:]);p.parent.mkdir(parents=True,exist_ok=True)
   if n.endswith('/'):p.mkdir(exist_ok=True)
   else:p.write_bytes(z.read(n))
def state(folder,analysis):
 source=folder/'original-state/process.stdout'
 if not source.is_file():return dict(original_full_state_available=False)
 expected={};counts={}
 for line in source.read_text(encoding='utf-8').split('\n'):
  if not line:continue
  fields=line.split('\t')
  if fields[0]=='#sample':counts[fields[1]]=int(fields[2]);continue
  assert len(fields)==32
  key=tuple(fields[:6]);assert key not in expected;expected[key]=fields
 got={}
 for line in (analysis/'products/state-raw.jsonl').read_text(encoding='utf-8').splitlines():
  row=json.loads(line);assert len(row)==32;key=tuple(str(v) for v in row[:6]);assert key not in got;got[key]=row
 assert set(got)==set(expected),(folder.name,'full native state inventory')
 values=0
 for key,want in expected.items():
  row=got[key]
  for k in range(32):
   if k<18 or k%2==0:
    actual=str(int(row[k])) if isinstance(row[k],bool) else str(row[k]);assert actual==want[k],(folder.name,key,k,actual,want[k])
   else:
    before=float(want[k]);after=struct.unpack('>d',bytes.fromhex(row[k]))[0]
    assert math.isnan(after) if math.isnan(before) else after==before if math.isinf(before) else abs(after-before)<=64*math.ulp(before),(folder.name,key,k,before,after)
   values+=1
 conditions=json.loads((analysis/'products/CONDITIONS.json').read_bytes());assert {s['name']:s['processed_alignments'] for s in conditions['samples']}==counts
 return dict(original_full_state_available=True,rows=len(got),fields_compared=values)
def main():
 p=argparse.ArgumentParser(description=__doc__)
 for name in ['java','jar','htsjdk','out']:p.add_argument('--'+name,type=Path,required=True)
 p.add_argument('--cross-bundles',type=Path);a=p.parse_args();out=a.out.resolve();out.mkdir(exist_ok=False)
 runtime=out/'artifacts';runtime.mkdir();jar=runtime/'OmicsMatic-tpm-workflow.jar';hts=runtime/'htsjdk-5.0.0.jar'
 shutil.copyfile(a.jar,jar);shutil.copyfile(a.htsjdk,hts);shutil.copyfile(Path(__file__),out/'qualify_workflow.py')
 command=[str(a.java.resolve()),'-cp',str(jar)+os.pathsep+str(hts),'org.jlinalg.cli.TpmCalculatorCli']
 write(out/'SOURCE.json',dict(java=str(a.java.resolve()),application=pin(jar),htsjdk=pin(hts),producer=pin(Path(__file__)),platform=os.name,source_archives={n:pin(RESOURCE/n) for n,_ in ARCHIVES}))
 processes=[]
 def run(folder,name,args,expected=0,inject=None):
  with (folder/(name+'.stdout')).open('xb') as so,(folder/(name+'.stderr')).open('xb') as se:
   c=subprocess.Popen(command+list(map(str,args)),cwd=ROOT,stdin=subprocess.DEVNULL,stdout=so,stderr=se)
   write(folder/(name+'-LIVE.json'),dict(pid=c.pid,command=command+list(map(str,args))))
   injected=False;began=time.monotonic()
   while c.poll() is None:
    if inject and not injected:
     try:injected=inject()
     except FileNotFoundError:pass
    if time.monotonic()-began>240:c.kill();c.wait();raise RuntimeError('Owned qualification process exceeded test bound; no replacement run was started')
    time.sleep(.01)
  r=dict(command=command+list(map(str,args)),exit_code=c.returncode,injection_completed=injected,elapsed_seconds=time.monotonic()-began)
  write(folder/(name+'-PROCESS.json'),r);processes.append(r)
  assert c.returncode==expected,(name,r,(folder/(name+'.stderr')).read_text(encoding='utf-8',errors='replace')[-5000:])
  if inject:assert injected,name
  return r
 cases=[];case_root=out/'cases';case_root.mkdir()
 for archive,sha in ARCHIVES:
  assert pin(RESOURCE/archive)['sha256']==sha
  with zipfile.ZipFile(RESOURCE/archive) as z:metadata=json.loads(z.read('CASES.json'))
  for case in metadata:
   name=case['name'];folder=case_root/name;folder.mkdir();source=folder/'reference';source.mkdir();extract(RESOURCE/archive,name,source)
   req=request(source,case);write(folder/'request.json',req)
   before={p.relative_to(source).as_posix():pin(p) for p in source.rglob('*') if p.is_file()}
   run(folder,'public-run',['run','--request',folder/'request.json','--out',folder/'analysis'])
   analysis=folder/'analysis';completion=json.loads((analysis/'COMPLETED.json').read_bytes());assert completion['result_admitted'] and not completion['paper_reproduced']
   for index,sample in enumerate(case['sample_order'],1):
    for file in (['genes.out','genes.ent','genes.uni','transcripts.out','transcripts.ent'] if case.get('extended',True) else ['genes.out','genes.ent','genes.uni']):
     assert (source/'original-main'/(sample+'_'+file)).read_bytes()==(analysis/'products'/('sample-%06d'%index)/file).read_bytes(),(name,sample,file)
   compared=state(source,analysis)
   run(folder,'public-replay',['verify','--bundle',analysis,'--out',folder/'replay'])
   assert json.loads((folder/'replay/COMPLETED.json').read_bytes())['saved_replay_verified']
   assert before=={p.relative_to(source).as_posix():pin(p) for p in source.rglob('*') if p.is_file()}
   cases.append(dict(name=name,source_archive=archive,source_tables_byte_equal=True,complete_captured_input_replay=True,reference_inputs_unchanged=True,**compared))
   write(folder/'COMPARISON.json',cases[-1]);print('PACKAGED_NATIVE_CASE_AND_REPLAY_PASS',name,flush=True)
 assert len(cases)==64
 guards=out/'guards';guards.mkdir()
 base=case_root/'flags/reference';base_case=json.loads((base/'CASE.json').read_bytes())
 guard_results=[]
 for name,expected in [('missing-input',3),('unknown-option',2),('duplicate-sample',2),('malformed-gtf',2),('sam-instead-of-bam',2),('file-capacity',2),('after-return-recording',2),('parent-finalization',2)]:
  folder=guards/name;folder.mkdir();inputs=folder/'inputs';shutil.copytree(base,inputs);req=request(inputs,base_case)
  if name=='missing-input':req['samples'][0]['bam']=str(inputs/'not-present.bam')
  if name=='unknown-option':req['options']['stranded']=True
  if name=='duplicate-sample':req['samples'].append(dict(req['samples'][0]))
  if name=='malformed-gtf':(inputs/'annotation.gtf').write_bytes(b'invalid\n')
  if name=='sam-instead-of-bam':req['samples'][0]['bam']=str(inputs/'one.sam')
  if name=='file-capacity':req['limits']['files']=5
  write(folder/'request.json',req);analysis=folder/'analysis';injection=None
  if name in ['after-return-recording','parent-finalization']:
   target=analysis/('products' if name=='after-return-recording' else 'MANIFEST.json')
   def injection(target=target):
    if not target.parent.is_dir():return False
    with target.open('xb') as f:f.write(b'preserved actual recording obstruction\n')
    return True
  run(folder,'public-run',['run','--request',folder/'request.json','--out',analysis],expected,injection)
  assert not (analysis/'COMPLETED.json').exists();failure=json.loads((analysis/'FAILURE.json').read_bytes());assert failure['result_admitted'] is False
  if name=='after-return-recording':assert b'COMPUTATION_RETURNED' in (analysis/'worker/stdout.bin').read_bytes() and b'computed=true' in (analysis/'worker/stderr.bin').read_bytes()
  if name=='parent-finalization':assert (analysis/'worker/RETURN.json').is_file() and json.loads((analysis/'worker/EXIT.json').read_bytes())['exit_code']==0
  guard_results.append(dict(name=name,exit_code=expected,complete_result_admitted=False));print('PACKAGED_NATIVE_GUARD_PASS',name,flush=True)
 # A complete result copied and re-sealed after scientific tampering must fail fresh replay.
 folder=guards/'resealed-results';folder.mkdir();bad=folder/'tampered';shutil.copytree(case_root/'flags/analysis',bad)
 with (bad/'products/sample-000001/genes.out').open('ab') as f:f.write(b'wrong scientific row\n')
 manifest=json.loads((bad/'MANIFEST.json').read_bytes())
 for name,item in manifest['files'].items():
  if item['kind']=='file':item.update(pin(bad/name))
 write(bad/'MANIFEST.json',manifest);completion=json.loads((bad/'COMPLETED.json').read_bytes());completion['manifest_sha256']=pin(bad/'MANIFEST.json')['sha256'];write(bad/'COMPLETED.json',completion)
 run(folder,'public-replay',['verify','--bundle',bad,'--out',folder/'replay'],2);assert not (folder/'replay/COMPLETED.json').exists()
 assert json.loads((folder/'replay/FAILURE.json').read_bytes())['phase']=='SEMANTIC_REPLAY_COMPARISON';guard_results.append(dict(name='resealed-results',exit_code=2,complete_result_admitted=False))
 cross=[]
 if a.cross_bundles:
  folder=out/'cross-host-replay';folder.mkdir()
  for name in ['flags','sample-state','overlapping-exons-origin','custom-keys','raw-control-identifiers']:
   source=a.cross_bundles/'cases'/name/'analysis';before=pin(source/'MANIFEST.json');run(folder,'public-'+name,['verify','--bundle',source,'--out',folder/name]);assert pin(source/'MANIFEST.json')==before;cross.append(name)
 result=dict(status='PASS_ACTUAL_PACKAGED_NATIVE_TPMCALCULATOR_FULL_REFERENCE_WORKFLOWS_REPLAY_AND_PUBLIC_GUARDS',checked_utc=datetime.datetime.now(datetime.timezone.utc).isoformat(),
  cases=cases,public_guards=guard_results,cross_host_replays=cross,actual_processes=len(processes),analysis_native_java=True,external_quantification_program=False,biological_accuracy_established=False,paper_reproduced=False,whole_goal_complete=False)
 write(out/'COMPLETED.json',result);print(json.dumps(dict(status=result['status'],cases=len(cases),guards=len(guard_results),cross_host_replays=len(cross),actual_processes=len(processes))))
if __name__=='__main__':main()
