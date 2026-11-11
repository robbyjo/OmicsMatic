# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Verify the separate eager component and replay every original parsed-row case; no analysis/install/network."""
import argparse,gzip,hashlib,io,json,lzma,os,shutil,subprocess,sys,tempfile,zipfile
from pathlib import Path,PurePosixPath
ROOT=Path(__file__).resolve().parents[2]
def identity(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
def pin(path):return identity(path.read_bytes())
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--java',default='java');p.add_argument('--package',type=Path,default=ROOT/'docs/literature/nfcore-eager-development');a=p.parse_args()
 try:
  package=a.package.resolve();proof=json.loads((package/'VALIDATION.json').read_bytes());assert proof['status']=='PASS_NATIVE_EAGER_PARSED_ROWS_AND_THREE_ROLE_SOURCE_AUDIT_ONLY';assert proof['new_tool_credit']==0 and proof['scientific_analysis_executed'] is False and proof['full_eager_adapter_admitted'] is False
  assert proof['case_comparisons']==588 and proof['original_cases_per_version']==49
  for name,wanted in proof['source_pins'].items():
   path=PurePosixPath(name);assert not path.is_absolute() and '..' not in path.parts and str(path)==name;assert pin(ROOT/name)==wanted,('Workspace source differs',name)
  for name,wanted in proof['artifacts'].items():assert PurePosixPath(name).name==name and pin(package/name)==wanted,('Checkpoint artifact differs',name)
  sums={row.split('  ',1)[1]:row.split('  ',1)[0] for row in (package/'SHA256SUMS').read_text().splitlines()};assert set(sums)==set(proof['artifacts'])|{'VALIDATION.json','README.md','.gitattributes'}
  for name,wanted in sums.items():assert pin(package/name)['sha256']==wanted,('Checksum differs',name)
  raw={};total=0
  decoder=lzma.LZMADecompressor(memlimit=268435456);archive=decoder.decompress((package/'controls.zip.xz').read_bytes(),max_length=209715201);assert len(archive)<=209715200 and decoder.eof and not decoder.unused_data,'Complete bounded XZ envelope required'
  with zipfile.ZipFile(io.BytesIO(archive)) as z:
   members=proof['controls_members'];assert proof['control_storage']=='content-addressed-witnesses-v1';expected={'objects/'+x['sha256'] for x in members.values()}|{'MANIFEST.json'}
   assert len(z.namelist())==len(set(z.namelist())) and set(z.namelist())==expected
   assert json.loads(z.read('MANIFEST.json'))==members
   for info in z.infolist():
    name=PurePosixPath(info.filename);total+=info.file_size;assert not info.is_dir() and not name.is_absolute() and '..' not in name.parts and str(name)==info.filename and info.file_size<=50000000 and total<=160000000
   for logical,wanted in members.items():
    name=PurePosixPath(logical);assert not name.is_absolute() and '..' not in name.parts and str(name)==logical
    data=z.read('objects/'+wanted['sha256']);assert identity(data)==wanted,('Control witness differs',logical)
    if logical.startswith('project-sources/'):assert identity(data)==proof['source_pins'][logical[len('project-sources/'):]],'Frozen control source differs from current checkpoint source'
    if logical in ['reference/cases.json','reference/observed-2.3.4.json','reference/observed-2.4.4.json','reference/COMPLETED.json','helper/org/jlinalg/cli/NfcoreEagerSamplesControl.class','roles/role-drafts.json','roles/WARNINGS.json','source-paper/PMC11964931.xml.gz','source-paper/supplement.docx']:raw[logical]=data
  cases=json.loads(raw['reference/cases.json']);assert len(cases)==49 and len({x['name'] for x in cases})==49
  for version in ['2.3.4','2.4.4']:
   records=json.loads(raw['reference/observed-'+version+'.json']);assert len(records)==49 and [x['name'] for x in records]==[x['name'] for x in cases];assert sum(x['status']=='accepted-row-inspection-only' for x in records)==13
  roles=json.loads(raw['roles/role-drafts.json']);assert {x['role_id'] for x in roles}=={'P002-eager-pathogen-screening','P002-eager-treponemal-capture-mapping','P002-eager-human-ancestry'};assert len(roles)==3
  capture=next(x for x in roles if x['paragraph_index']==7);assert capture['pipeline_version']=='2.4.4' and capture['reported_execution_profile']=='conda' and capture['nextflow_version'] is None
  ancestry=next(x for x in roles if x['paragraph_index']==32);assert ancestry['pipeline_version']=='2.3.4' and ancestry['nextflow_version'] is None
  assert all(x['catalog_credit'] is False and x['implementation_status']=='missing' for x in roles)
  assert hashlib.sha256(gzip.decompress(raw['source-paper/PMC11964931.xml.gz'])).hexdigest()=='7970057057eb527abb957f1fb9b21306dc3c3eeb89dcdc4aa3b874df7ee15965'
  assert identity(raw['source-paper/supplement.docx'])==dict(bytes=10993402,sha256='cefa7be7b2bb5313cce9ddb23d5369020c2ef74a90b99daf78a889270db23451')
  warnings=json.loads(raw['roles/WARNINGS.json']);assert len(warnings)==7 and all(x['required_action'] for x in warnings)
  parent=ROOT/'docs/literature/nfcore-chipseq200-development/nfcore-chipseq200-javac21.jar';assert pin(parent)==proof['diagnostic_json_reader_parent']
  java=shutil.which(a.java)
  if java is None:print('EAGER CHECKPOINT SKIP3: Java unavailable. Required action: install/use permitted Java17+ and rerun verification. No analysis admitted.',file=sys.stderr);return 3
  with tempfile.TemporaryDirectory(prefix='omicsmatic-eager-native-verify-') as temporary:
   folder=Path(temporary)
   for name in ['reference/cases.json','reference/observed-2.3.4.json','reference/observed-2.4.4.json','helper/org/jlinalg/cli/NfcoreEagerSamplesControl.class']:
    path=folder/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(raw[name])
   for compiler in ['21','25']:
    jar=package/('nfcore-eager-samples-javac'+compiler+'.jar');cmd=[java,'-Xmx256m','-cp',os.pathsep.join(map(str,[folder/'helper',jar,parent])),'org.jlinalg.cli.NfcoreEagerSamplesControl',str(folder/'reference')];r=subprocess.run(cmd,stdin=subprocess.DEVNULL,capture_output=True,timeout=90);assert r.returncode==0 and b'accepted=26 refused=72' in r.stdout,('Complete native source-case replay failed',compiler,r.returncode,r.stderr)
  print('PASS_EAGER_COMPONENT_CHECKPOINT_AND_196_SOURCE_CASE_REPLAYS; full eager analyses remain unvalidated; no new catalog credit.');return 0
 except Exception as error:print('EAGER CHECKPOINT FAIL2: '+repr(error)+'. Required action: retain the checkpoint and diagnostics, restore the exact validated sources/artifacts or rebuild a new reviewed checkpoint. No analysis admitted.',file=sys.stderr);return 2
if __name__=='__main__':
 if not __debug__:raise RuntimeError('Never disable complete checkpoint/source checks')
 raise SystemExit(main())
