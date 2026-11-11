# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Verify the saved screening checkpoint and replay both native builds without upstream/legacy GATK installation."""
import argparse,hashlib,io,json,lzma,subprocess,traceback,zipfile
from pathlib import Path,PurePosixPath
from setup_deseq2_reference import ROOT,pin,save
from validate_nfcore_engine import BASE,JAVA

def extract(raw,target,expected):
 target.mkdir();seen=set();total=0
 with zipfile.ZipFile(io.BytesIO(raw)) as z:
  for item in z.infolist():
   name=item.filename;parts=PurePosixPath(name);assert not parts.is_absolute() and all(x not in ['.','..'] for x in parts.parts) and '\\' not in name and ':' not in name and not item.is_dir() and name not in seen and item.file_size<=67108864;seen.add(name);total+=item.file_size;assert len(seen)<=5000 and total<=268435456;data=z.read(item);assert dict(bytes=len(data),sha256=hashlib.sha256(data).hexdigest())==expected[name];path=target.joinpath(*parts.parts);path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(data)
 assert seen==set(expected)

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);p.add_argument('--jar21',type=Path,required=True);p.add_argument('--jar25',type=Path,required=True);a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(BASE) and not out.exists();out.mkdir();state=dict(status='FAIL2_EAGER_SCREENING_SAVED_CHECKPOINT',new_tool_credit=0,whole_goal_complete=False)
 try:
  public=ROOT/'docs/literature/nfcore-eager-screening-development';proof=json.loads((public/'VALIDATION.json').read_bytes());assert proof['new_tool_credit']==0 and proof['full_eager_adapter_admitted'] is False and proof['whole_goal_complete'] is False
  for name,wanted in proof['source_pins'].items():assert pin(ROOT/name)==wanted,name
  archive=public/'controls.zip.xz';assert pin(archive)==proof['evidence_archive'];raw=lzma.decompress(archive.read_bytes());assert len(raw)<=268435456
  with zipfile.ZipFile(io.BytesIO(raw)) as z:manifest=json.loads(z.read('MANIFEST.json'));expected=dict(manifest['files']);data=z.read('MANIFEST.json');expected['MANIFEST.json']=dict(bytes=len(data),sha256=hashlib.sha256(data).hexdigest())
  extract(raw,out/'evidence',expected);assert not any(p.suffix.lower() in ['.jar','.sif','.class'] for p in (out/'evidence').rglob('*') if p.is_file())
  fixture=ROOT/'src/test/resources/org/jlinalg/workflow/nfcore-eager-screening';metadata=json.loads((fixture/'SOURCE.json').read_bytes());assert pin(fixture/'reference.zip')==metadata['reference_zip'];extract((fixture/'reference.zip').read_bytes(),out/'fixture',metadata['reference_files']);helper=ROOT/'tools/literature/java/NfcoreEagerScreeningReplay.java';results=[]
  for compiler in ['21','25']:
   jar=Path(getattr(a,'jar'+compiler)).resolve();assert pin(jar)==proof['development_artifacts'][compiler],compiler;classes=out/('helper'+compiler);classes.mkdir();cmd=[str(JAVA/'javac.exe'),'--release','17','-encoding','UTF-8','-Xlint:all','-Werror','-sourcepath','','-cp',str(jar),'-d',str(classes),str(helper)];r=subprocess.run(cmd,capture_output=True,timeout=90);(out/('javac'+compiler+'.stderr')).write_bytes(r.stderr);assert r.returncode==0,r.stderr;target=out/('replay'+compiler);cmd=[str(JAVA/'java.exe'),'-Xmx512m','-cp',str(classes)+';'+str(jar),'org.jlinalg.cli.NfcoreEagerScreeningReplay',str(out/'fixture/reference'),str(out/'fixture/expected'),str(out/'fixture/mutations'),str(target)];r=subprocess.run(cmd,capture_output=True,timeout=180);(out/('replay'+compiler+'.stdout')).write_bytes(r.stdout);(out/('replay'+compiler+'.stderr')).write_bytes(r.stderr);save(out/('replay'+compiler+'.process.json'),dict(command=cmd,exit_code=r.returncode));assert r.returncode==0,r.stderr;report=json.loads((target/'COMPLETED.json').read_bytes());assert len(report['libraries'])==2 and report['deliberate_refusals']==24 and report['empty_statistics_preserved_absent']==1;results.append(dict(compiler=compiler,report=pin(target/'COMPLETED.json')))
  for name,wanted in proof['source_pins'].items():assert pin(ROOT/name)==wanted,name
  assert pin(archive)==proof['evidence_archive'];state.update(status='PASS_SAVED_SCREENING_CHECKPOINT_AND_BOTH_NATIVE_BUILD_FIXTURE_REPLAYS_ONLY',evidence_files=len(manifest['files']),native_complete_libraries=4,native_refusals=48,native_empty_statistics_preserved_absent=2,results=results,full_eager_adapter_admitted=False,private_gatk_engine_replayed=False,biological_result_admitted=False)
 except Exception as error:state.update(cause=repr(error),traceback=traceback.format_exc())
 save(out/'COMPLETED.json',state);print(state['status'],state.get('cause',''),flush=True);return 0 if state['status'].startswith('PASS_') else 2
if __name__=='__main__':
 if not __debug__:raise RuntimeError('Never disable saved evidence/source checks')
 raise SystemExit(main())
