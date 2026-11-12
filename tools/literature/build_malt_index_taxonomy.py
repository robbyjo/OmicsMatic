#!/usr/bin/env python3
"""Build the native MALT index mapping command using only a permitted JDK (no third-party runtime)."""
import argparse,hashlib,json,os,re,shutil,subprocess,traceback
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
SOURCES=['src/main/java/org/jlinalg/metagenomics/'+n+'.java' for n in ['MaltIndexTaxonomy','MaltTaxonomy']]+['src/main/java/org/jlinalg/cli/'+n+'.java' for n in ['MaltIndexTaxonomyCli','NextflowFiles','NextflowJson','NextflowParameters','SimpleJson']]
ACTION='Required action: supply a permitted complete JDK21 or JDK25 via --java-bin and choose a NEW --out directory. If Java installation/compiler access is unavailable or forbidden, skip this dependent build/analysis. Retain failed directories and diagnostics. This builds a native alternative index-mapping stage; upstream MALT alignment remains separately required and qualified.'
def pin(p):
 h=hashlib.sha256();size=0
 with p.open('rb') as f:
  for b in iter(lambda:f.read(1048576),b''):h.update(b);size+=len(b)
 return dict(bytes=size,sha256=h.hexdigest())
def save(p,v):p.write_text(json.dumps(v,indent=2)+'\n',encoding='utf-8',newline='\n')
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',required=True,type=Path);p.add_argument('--java-bin',required=True,type=Path);a=p.parse_args();out=a.out.absolute();owned=False;state=dict(status='FAIL2_NATIVE_MALT_INDEX_BUILD',whole_goal_complete=False)
 try:
  if out!=out.resolve():raise ValueError('Canonical, unlinked NEW output required')
  out.mkdir(parents=True,exist_ok=False);owned=True;java=a.java_bin.resolve();suffix='.exe' if os.name=='nt' else '';env={k:v for k,v in os.environ.items() if k.upper() not in ['JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS','_JAVA_OPTIONS']};versions={}
  for name in ['java','javac','jar']:
   binary=java/(name+suffix)
   if not binary.is_file():raise FileNotFoundError('Missing required JDK tool: '+str(binary))
   r=subprocess.run([str(binary),'--version'],stdin=subprocess.DEVNULL,capture_output=True,env=env,timeout=30);version=(r.stdout+r.stderr).decode(errors='replace');match=re.search(r'(?:openjdk|java|javac|jar) (?:version )?"?(\d+)(?:[.\-"\s]|$)',version)
   if r.returncode or not match or int(match.group(1)) not in [21,25]:raise ValueError('A matching qualified JDK21/25 is required: '+name)
   versions[name]=dict(major=int(match.group(1)),output=version)
  if len({v['major'] for v in versions.values()})!=1:raise ValueError('JDK tools differ in version')
  sources={n:pin(ROOT/n) for n in SOURCES};classes=out/'classes';classes.mkdir()
  def run(label,cmd):
   r=subprocess.run(cmd,cwd=out,stdin=subprocess.DEVNULL,capture_output=True,env=env,timeout=120);(out/(label+'.stdout')).write_bytes(r.stdout);(out/(label+'.stderr')).write_bytes(r.stderr);save(out/(label+'.process.json'),dict(command=cmd,exit_code=r.returncode))
   if r.returncode:raise ValueError(label+' failed; inspect full saved diagnostics')
  run('javac',[str(java/('javac'+suffix)),'--release','17','-encoding','UTF-8','-Xlint:all','-Werror','-sourcepath','','-d',str(classes),*[str(ROOT/n) for n in SOURCES]])
  artifact=out/'malt-index-taxonomy-development.jar';run('jar',[str(java/('jar'+suffix)),'--create','--file',str(artifact),'--main-class','org.jlinalg.cli.MaltIndexTaxonomyCli','-C',str(classes),'.']);run('help',[str(java/('java'+suffix)),'-jar',str(artifact),'--help'])
  if b'Required action:' not in (out/'help.stdout').read_bytes():raise ValueError('Required user recovery instructions absent')
  if any(pin(ROOT/n)!=v for n,v in sources.items()):raise ValueError('Build source changed')
  shutil.copyfile(ROOT/'LICENSE',out/'LICENSE-project.txt');state.update(status='READY_NATIVE_MALT_INDEX_TAXONOMY_DEVELOPMENT_BUILD',sources=sources,versions=versions,artifact=pin(artifact),bytecode_target=17,external_programs_bundled=False,upstream_alignment_qualified=False);save(out/'BUILD.json',state);(out/'WARNINGS.md').write_text('# Native index mapping alternative only\n\n'+ACTION+'\n',encoding='utf-8',newline='\n');print(state['status'],artifact,flush=True);return 0
 except Exception as e:
  code=3 if isinstance(e,OSError) else 2;state.update(status='SKIP3_BUILD_PREREQUISITE_UNAVAILABLE' if code==3 else 'FAIL2_NATIVE_INDEX_BUILD',cause=repr(e),traceback=traceback.format_exc(),dependent_analysis_skipped=True,required_action=ACTION)
  if owned:save(out/'FAILURE.json',state);(out/'WARNINGS_FAILURE.md').write_text('# '+state['status']+'\n\n'+str(e)+'\n\n'+ACTION+'\n',encoding='utf-8',newline='\n')
  print(state['status'],repr(e),'\n'+ACTION,flush=True);return code
if __name__=='__main__':raise SystemExit(main())
