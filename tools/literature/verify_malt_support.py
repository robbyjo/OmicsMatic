# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Replay native MALT minimum-support relocation against all saved original bytecode cases."""
import argparse,hashlib,io,json,lzma,os,re,shutil,subprocess,traceback,zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from validate_nfcore_engine import BASE,JAVA,JAVA25,DISTRO,linux
from verify_nfcore_eager_screening_checkpoint import extract

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);p.add_argument('--java21-bin',type=Path,default=JAVA);p.add_argument('--java25-bin',type=Path,default=JAVA25);p.add_argument('--linux-distro',default=DISTRO);a=p.parse_args();java21=a.java21_bin.resolve();java25=a.java25_bin.resolve();out=a.out.resolve();assert out.is_relative_to(BASE) and not out.exists();out.mkdir();state=dict(status='FAIL2_SAVED_NATIVE_MALT_SUPPORT_REPLAY',new_tool_credit=0,full_malt_adapter_admitted=False,biological_result_admitted=False,whole_goal_complete=False)
 def run(label,command):
  r=subprocess.run(command,stdin=subprocess.DEVNULL,capture_output=True,timeout=720);(out/(label+'.stdout')).write_bytes(r.stdout);(out/(label+'.stderr')).write_bytes(r.stderr);save(out/(label+'.process.json'),dict(command=command,exit_code=r.returncode));assert r.returncode==0,(label,r.returncode)
 try:
  public=ROOT/'docs/literature/malt-support-development';proof=json.loads((public/'VALIDATION.json').read_bytes());assert proof['status']=='VERIFIED_NATIVE_MALT_MINIMUM_SUPPORT_COMPONENT_ONLY' and proof['new_tool_credit']==0
  for name,value in proof['source_pins'].items():assert pin(ROOT/name)==value,name
  archive=public/'controls.zip.xz';assert pin(archive)==proof['evidence_archive'];raw=lzma.decompress(archive.read_bytes());assert len(raw)<=268435456
  with zipfile.ZipFile(io.BytesIO(raw)) as z:manifest=json.loads(z.read('MANIFEST.json'));wanted=dict(manifest['files']);body=z.read('MANIFEST.json');wanted['MANIFEST.json']=dict(bytes=len(body),sha256=hashlib.sha256(body).hexdigest())
  extract(raw,out/'evidence',wanted);assert not any(p.suffix.lower() in ['.jar','.class','.sif'] for p in (out/'evidence').rglob('*') if p.is_file());control=out/'evidence/evidence/malt-support-control-1';data=control/'data';reference=control/'original'
  matrix=json.loads((control/'COMPLETED.json').read_bytes());assert matrix['status']=='PASS_NATIVE_MALT_MINIMUM_SUPPORT_SOURCE_CONTROLS_ONLY' and matrix['cases_per_configuration']==8274 and len(matrix['results'])==6;assert json.loads((control/'original-oracle.process.json').read_bytes())['exit_code']==0;assert 'PASS_ORIGINAL_BYTECODE_MINIMUM_SUPPORT_CONTROLS_ONLY' in (control/'original-oracle.stdout').read_text()
  cases=(data/'cases.tsv').read_text().splitlines();assert len(cases)==8275;assert sum(line.split('\t')[1]=='complete' for line in cases[1:])==24;assert sum(line.split('\t')[1]=='wide' for line in cases[1:])==100
  import xml.etree.ElementTree as ET
  junit=ET.parse(out/'evidence/evidence/malt-support-junit-1/TEST-org.jlinalg.metagenomics.MaltSupportTest.xml').getroot();assert int(junit.attrib['tests'])==2 and all(int(junit.attrib[key])==0 for key in ['failures','errors','skipped'])
  missing=[];versions={};environment={k:v for k,v in os.environ.items() if k.upper() not in ['JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS','_JAVA_OPTIONS']}
  def version(label,command,expected):
   try:
    r=subprocess.run(command,stdin=subprocess.DEVNULL,capture_output=True,timeout=60,env=environment);text=(r.stdout+r.stderr).decode(errors='replace');match=re.search(r'(?:openjdk|java|javac) (?:version )?"?(\d+)[.\-"\s]',text);major=int(match.group(1)) if match else None;versions[label]=dict(command=command,exit_code=r.returncode,major=major,expected=expected)
    if r.returncode!=0 or major!=expected:missing.append(label+' requires major'+str(expected)+'; observed '+str(major)+' and exit'+str(r.returncode))
   except (OSError,subprocess.TimeoutExpired) as error:missing.append(label+': '+type(error).__name__)
  for major,java in [(21,java21),(25,java25)]:
   for name in ['java.exe','javac.exe']:
    if not (java/name).is_file():missing.append(str(java/name))
    else:version(str(major)+'-'+name,[str(java/name),'-version' if name=='java.exe' else '--version'],major)
  if not shutil.which('wsl.exe'):missing.append('wsl.exe with '+a.linux_distro+' and Linux Java21 at /usr/bin/java')
  else:version('linux21',['wsl.exe','-d',a.linux_distro,'--exec','/usr/bin/java','-version'],21)
  save(out/'VERSIONS.json',versions);state['versions']=versions
  if missing:state.update(status='SKIP3_NATIVE_MALT_SUPPORT_REPLAY_MISSING_PREREQUISITES',missing=missing,warnings=['SKIPPED: cross-runtime verification was not run. Required action: supply the permitted named JDK21/JDK25 and Linux Java21 runtimes, then retry a NEW directory.']);save(out/'COMPLETED.json',state);print(state['status'],state['warnings'][0],missing);return 3
  results=[]
  for compiler,java in [('21',java21),('25',java25)]:
   classes=out/('classes'+compiler);classes.mkdir();sources=[ROOT/'src/main/java/org/jlinalg/metagenomics/MaltTaxonomy.java',ROOT/'src/main/java/org/jlinalg/metagenomics/MaltSupport.java',ROOT/'tools/literature/java/MaltSupportProbe.java'];run('javac'+compiler,[str(java/'javac.exe'),'--release','17','-encoding','UTF-8','-Xlint:all','-Werror','-sourcepath','','-d',str(classes),*[str(p) for p in sources]])
   for runtime in ['windows21','windows25','linux21']:
    label=compiler+'-'+runtime;target=out/label
    if runtime=='linux21':command=['wsl.exe','-d',a.linux_distro,'--exec','/usr/bin/timeout','--signal=TERM','--kill-after=5s','600s','/usr/bin/java','-Xmx2g','-cp',linux(classes),'MaltSupportProbe',linux(data),linux(target)]
    else:command=[str((java21 if runtime=='windows21' else java25)/'java.exe'),'-Xmx2g','-cp',str(classes),'MaltSupportProbe',str(data),str(target)]
    run(label,command)
    for name in ['trees.tsv','relocations.tsv']:assert (target/name).read_bytes()==(reference/name).read_bytes(),(label,name)
    results.append(dict(compiler=compiler,runtime=runtime,cases=8274,trees=8,full_taxonomy_profiles=24));print('Saved source support references matched',label,flush=True)
  assert pin(archive)==proof['evidence_archive'] and all(pin(ROOT/name)==value for name,value in proof['source_pins'].items());state.update(status='PASS_SAVED_NATIVE_MALT_MINIMUM_SUPPORT_REPLAY_ONLY',results=results,source_image_or_original_bytecode_rerun=False,junit_evidence_checked_not_rerun=True,evidence_files=len(manifest['files']))
 except Exception as error:state.update(cause=repr(error),traceback=traceback.format_exc(),warnings=['ERROR: this run admits no new validation result. Required action: inspect the named failure and retained diagnostics; correct the prerequisite or input and retry a NEW directory.'])
 save(out/'COMPLETED.json',state);print(state['status'],state.get('cause',''),flush=True);return 0 if state['status'].startswith('PASS_') else 2
if __name__=='__main__':
 if not __debug__:raise RuntimeError('Never disable original reference comparisons')
 raise SystemExit(main())
