# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Replay sealed MALT0.4.1 captured profiles with minimal public source builds; no original image."""
from pathlib import Path,PurePosixPath
import argparse,hashlib,io,json,lzma,os,re,shutil,subprocess,sys,time,traceback,zipfile
from build_malt_alignment import ROOT,HTSJDK,pin,save
BASE=ROOT/'build/literature-review/nfcore-runtime'
JAVA21=Path('C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin')
JAVA25=Path('C:/Program Files/Java/jdk-25/bin')
ACTION='Required action: restore the exact source/evidence checkout, provide the permitted exact MALT0.4.1 two-jar reader provider with --reader-runtime, JDK21/JDK25 and WSL Linux Java21, plus --htsjdk-jar or permitted --download-htsjdk. Retry a NEW output directory. If a prerequisite cannot be installed or used, skip this dependent replay/analysis. Retain all diagnostics. Replay checks captured software output consistency, not an alignment refit, producer authentication, full MALT/nf-core or biological result.'
PROGRAM=dict(bytes=4641119,sha256='9106de3134a8f9d96bcd35986ee7a61738a1b51fca1683bb32bb108e82e92cf4')
DATA=dict(bytes=24731618,sha256='718a23ad8defbe34a35c9b0967c5fa616900adba34e8b961fb5531cd2382d2e3')
def linux(p):
 s=str(p)
 if not re.match(r'^[A-Za-z]:[\\/]',s):raise ValueError('Require local drive paths mapped into WSL')
 return '/mnt/'+s[0].lower()+s[2:].replace('\\','/')
def member(name):
 p=PurePosixPath(name)
 if not name or p.is_absolute() or any(x in ['.','..'] for x in p.parts) or '\\' in name or ':' in name or any(ord(c)<32 for c in name):raise ValueError('Unsafe evidence member '+name)
 return p

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',required=True,type=Path);p.add_argument('--reader-runtime',type=Path);g=p.add_mutually_exclusive_group();g.add_argument('--htsjdk-jar',type=Path);g.add_argument('--download-htsjdk',action='store_true');p.add_argument('--java21-bin',type=Path,default=JAVA21);p.add_argument('--java25-bin',type=Path,default=JAVA25);p.add_argument('--linux-distro',default='JLinAlg-Omics-SAIGE-20261026');a=p.parse_args();out=a.out.resolve()
 if out.exists() or not out.is_relative_to(BASE):p.error('Require NEW --out under '+str(BASE))
 out.mkdir(parents=True);state=dict(status='FAIL2_SAVED_MALT_ALIGNMENT_REPLAY',original_alignment_rerun=False,private_image_required=False,full_malt_adapter_admitted=False,new_tool_credit=0,biological_result_admitted=False,whole_goal_complete=False);env={k:v for k,v in os.environ.items() if k.upper() not in ['JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS','_JAVA_OPTIONS']}
 def finish(code):
  state['required_action']=ACTION;save(out/'COMPLETED.json',state);(out/'WARNINGS.md').write_text('# '+state['status']+'\n\n'+str(state.get('cause','Original source fits were not rerun; complete captured files and scientific/refusal checks were replayed.'))+'\n\n'+ACTION+'\n',encoding='utf8',newline='\n');print(state['status'],state.get('cause',''),'\n'+ACTION,flush=True);return code
 def run(label,command,expected=0):
  start=time.time()
  with (out/(label+'.stdout')).open('xb') as so,(out/(label+'.stderr')).open('xb') as se:
   child=subprocess.Popen(command,stdin=subprocess.DEVNULL,stdout=so,stderr=se,env=env);save(out/(label+'.live.json'),dict(pid=child.pid,command=command,started_unix=start))
   try:code=child.wait(timeout=900)
   except subprocess.TimeoutExpired:child.kill();child.wait(timeout=30);raise
  save(out/(label+'.process.json'),dict(command=command,exit_code=code,elapsed_seconds=time.time()-start));assert code==expected,(label,code,expected)
 try:
  public=ROOT/'docs/literature/malt-alignment-development';proof=json.loads((public/'VALIDATION.json').read_bytes());assert proof['status']=='VERIFIED_ORIGINAL_MALT041_BLASTN_ADAPTER_DEVELOPMENT_ONLY' and proof['new_tool_credit']==0;guards=proof['source_pins'];assert all(pin(ROOT/n)==v for n,v in guards.items())
  archive=public/'controls.zip.xz';assert pin(archive)==proof['evidence_archive'];missing=[];versions={};java21=a.java21_bin.resolve();java25=a.java25_bin.resolve();reader=a.reader_runtime.resolve() if a.reader_runtime else None
  if os.name!='nt':missing.append('This complete six-configuration replay requires Windows JDK21/25 plus WSL Java21; single-bundle verification also works directly on Linux.')
  for label,java in [('21',java21),('25',java25)]:
   for name in ['java.exe','javac.exe','jar.exe']:
    exe=java/name
    if not exe.is_file():missing.append(str(exe));continue
    r=subprocess.run([str(exe),'--version'],stdin=subprocess.DEVNULL,capture_output=True,timeout=60,env=env);text=(r.stdout+r.stderr).decode(errors='replace');match=re.search(r'(?:openjdk|java|javac|jar) (?:version )?"?(\d+)(?:[.\-"\s]|$)',text);versions[label+'-'+name]=dict(exit_code=r.returncode,expected=int(label),observed=int(match.group(1)) if match else None)
    if r.returncode!=0 or not match or match.group(1)!=label:missing.append('Qualified JDK'+label+' '+name)
  if not shutil.which('wsl.exe'):missing.append('WSL with '+a.linux_distro+' Linux Java21')
  else:
   r=subprocess.run(['wsl.exe','-d',a.linux_distro,'--exec','/usr/bin/java','-version'],stdin=subprocess.DEVNULL,capture_output=True,timeout=60);text=(r.stdout+r.stderr).decode(errors='replace');match=re.search(r'version "(\d+)\.',text);versions['linux21']=dict(exit_code=r.returncode,observed=int(match.group(1)) if match else None,expected=21)
   if r.returncode!=0 or not match or match.group(1)!='21':missing.append('Linux Java21 in '+a.linux_distro)
  for n,expected in [('MALT.jar',PROGRAM),('data.jar',DATA)]:
   if reader is None or not (reader/n).is_file():missing.append('Exact reader '+n)
   else:assert pin(reader/n)==expected,'Reader dependency integrity differs: '+n
  if a.htsjdk_jar:
   if not a.htsjdk_jar.is_file():missing.append('Complete HTSJDK5.0.0 jar')
   else:assert pin(a.htsjdk_jar)==HTSJDK,'HTSJDK dependency integrity differs'
  elif not a.download_htsjdk:missing.append('--htsjdk-jar or permitted --download-htsjdk')
  save(out/'VERSIONS.json',versions)
  if missing:state.update(status='SKIP3_MALT_ALIGNMENT_REPLAY_MISSING_PREREQUISITES',missing=missing,cause='Dependent complete replay skipped: '+', '.join(missing));return finish(3)
  decoder=lzma.LZMADecompressor();raw=decoder.decompress(archive.read_bytes(),max_length=268435457);assert decoder.eof and not decoder.unused_data and len(raw)<=268435456
  target=out/'evidence';target.mkdir()
  with zipfile.ZipFile(io.BytesIO(raw)) as z:
   manifest=json.loads(z.read('MANIFEST.json'));expected=dict(manifest['files']);body=z.read('MANIFEST.json');expected['MANIFEST.json']=dict(bytes=len(body),sha256=hashlib.sha256(body).hexdigest());seen=set();total=0
   for info in z.infolist():
    name=info.filename;parts=member(name);assert name not in seen and not info.is_dir() and info.file_size<=67108864;seen.add(name);total+=info.file_size;assert total<=268435456 and len(seen)<=10000;data=z.read(info);assert dict(bytes=len(data),sha256=hashlib.sha256(data).hexdigest())==expected[name];path=target.joinpath(*parts.parts);path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(data)
   assert seen==set(expected)
  index=json.loads((target/'BUNDLE_INDEX.json').read_bytes());assert len(index['cases'])==24;bundles=out/'bundles';bundles.mkdir();hardlinks=0
  for case in index['cases']:
   assert re.fullmatch('[A-Za-z0-9_-]+',case['id']);root=bundles/case['id'];root.mkdir()
   for name,value in case['files'].items():
    path=root.joinpath(*member(name).parts);path.parent.mkdir(parents=True,exist_ok=True);obj=target/'objects'/value['sha256'];assert pin(obj)==value
    try:os.link(obj,path);hardlinks+=1
    except OSError:shutil.copyfile(obj,path)
  selected=[c for c in index['cases'] if c['compiler']=='21'];assert len(selected)==12 and sum(c['kind']=='admitted' for c in selected)==9;results=[]
  for compiler,java in [('21',java21),('25',java25)]:
   build=out/('build'+compiler);command=[sys.executable,str(ROOT/'tools/literature/build_malt_alignment.py'),'--out',str(build),'--java-bin',str(java)]
   command+=['--htsjdk-jar',str(a.htsjdk_jar.resolve())] if a.htsjdk_jar else ['--download-htsjdk'];run('build'+compiler,command);jar=build/'malt041-alignment-development.jar';helper=out/('helper'+compiler);helper.mkdir();run('helper'+compiler,[str(java/'javac.exe'),'--release','17','-encoding','UTF-8','-Xlint:all','-Werror','-sourcepath','','-cp',str(jar),'-d',str(helper),str(ROOT/'tools/literature/java/MaltAlignmentRefusalReplay.java')])
   for runtime in ['windows21','windows25','linux21']:
    label=compiler+'-'+runtime;done=0;refused=0;convert=linux if runtime=='linux21' else str
    for case in selected:
     root=bundles/case['id'];name=label+'-'+case['name']
     if case['kind']=='admitted':
      args=['-Xmx3g','-jar',convert(jar),'--verify',convert(root),'--runtime',convert(reader)]
     else:
      result=out/(name+'-refusal');cp=(':'.join(linux(x) for x in [helper,jar])) if runtime=='linux21' else ';'.join(str(x) for x in [helper,jar]);args=['-Xmx3g','-cp',cp,'org.jlinalg.cli.MaltAlignmentRefusalReplay',convert(root),case['refusal_kind'],convert(result)]
     if runtime=='linux21':command=['wsl.exe','-d',a.linux_distro,'--exec','/usr/bin/timeout','--signal=TERM','--kill-after=5s','780s','/usr/bin/java',*args]
     else:command=[str((java21 if runtime=='windows21' else java25)/'java.exe'),*args]
     run(name,command);done+=case['kind']=='admitted';refused+=case['kind']!='admitted'
    assert done==9 and refused==3;results.append(dict(compiler=compiler,runtime=runtime,complete_captured_profile_replays=done,retained_source_refusals=refused));print('Minimal saved MALT profiles and source refusals replayed',label,flush=True)
  for case in index['cases']:
   assert all(pin(bundles/case['id']/name)==v for name,v in case['files'].items())
  assert pin(archive)==proof['evidence_archive'] and all(pin(ROOT/n)==v for n,v in guards.items());state.update(status='PASS_SAVED_MALT_ALIGNMENT_FIFTYFOUR_COMPLETE_PROFILE_REPLAYS_AND_EIGHTEEN_SOURCE_REFUSALS_ONLY',results=results,evidence_files=len(manifest['files']),source_image_or_legacy_java_required=False,hardlinks_used_for_unchanged_complete_data=hardlinks)
 except Exception as error:state.update(cause=repr(error),traceback=traceback.format_exc());return finish(2)
 return finish(0)
if __name__=='__main__':
 if not __debug__:raise RuntimeError('Never disable evidence/source checks')
 raise SystemExit(main())
