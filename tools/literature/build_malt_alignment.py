# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Build the MALT0.4.1 alignment/replay component from its minimal source closure."""
from pathlib import Path
import argparse,hashlib,json,os,re,shutil,subprocess,sys,traceback,urllib.error,urllib.request
ROOT=Path(__file__).resolve().parents[2]
URL='https://repo.maven.apache.org/maven2/com/github/samtools/htsjdk/5.0.0/htsjdk-5.0.0.jar'
HTSJDK=dict(bytes=2087306,sha256='59d862b74ec3e847ec84bc87bed34cecd1a80095495d965b9403f863e374e99c')
SOURCES=['src/main/java/org/jlinalg/metagenomics/'+n+'.java' for n in ['MaltTaxonomy','MaltAssignment','MaltSupport','MaltRmaReader','MaltSupportThreshold','MaltReassignment','MaltAlignmentAudit']]+['src/main/java/org/jlinalg/cli/'+n+'.java' for n in ['MaltAlignCli','NextflowFiles','NextflowJson','NextflowParameters','SimpleJson','NfcoreProcess','NextflowProcess']]+['src/main/java/org/jlinalg/workflow/LinuxProcessSnapshot.java','src/main/java/org/jlinalg/workflow/NfcoreEagerScreeningReads.java','src/main/java/org/jlinalg/sequence/FastqIO.java']
ACTION='Required action: supply a permitted JDK21 or JDK25 with --java-bin and the complete exact HTSJDK5.0.0 jar with --htsjdk-jar, or allow --download-htsjdk. Choose a NEW output directory. If a required download/install/compiler is unavailable or forbidden, skip this dependent build/analysis. Retain failed directories and diagnostics. This builds the Java controller/auditor only; MALT/Java8 alignment prerequisites remain external and separately qualified.'
def pin(p):
 h=hashlib.sha256();size=0
 with p.open('rb') as f:
  for b in iter(lambda:f.read(1048576),b''):h.update(b);size+=len(b)
 return dict(bytes=size,sha256=h.hexdigest())
def save(p,v):p.write_text(json.dumps(v,indent=2)+'\n',encoding='utf8',newline='\n')
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',required=True,type=Path);p.add_argument('--java-bin',type=Path);g=p.add_mutually_exclusive_group();g.add_argument('--htsjdk-jar',type=Path);g.add_argument('--download-htsjdk',action='store_true');a=p.parse_args();out=a.out.absolute();owned=False;state=dict(status='FAIL2_MALT_ALIGNMENT_MINIMAL_BUILD',original_alignment_executed=False,whole_goal_complete=False)
 try:
  if out!=out.resolve():raise OSError('Canonical unlinked output path required')
  out.mkdir(parents=True,exist_ok=False);owned=True
  default=shutil.which('javac');java=a.java_bin.resolve() if a.java_bin else Path(default).resolve().parent if default else None
  if java is None:raise FileNotFoundError('JDK compiler unavailable')
  suffix='.exe' if os.name=='nt' else '';env={k:v for k,v in os.environ.items() if k.upper() not in ['JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS','_JAVA_OPTIONS']};versions={}
  for name in ['java','javac','jar']:
   exe=java/(name+suffix)
   if not exe.is_file():raise FileNotFoundError('Required JDK executable unavailable: '+str(exe))
   r=subprocess.run([str(exe),'--version'],stdin=subprocess.DEVNULL,capture_output=True,timeout=60,env=env);text=(r.stdout+r.stderr).decode(errors='replace');match=re.search(r'(?:openjdk|java|javac|jar) (?:version )?"?(\d+)(?:[.\-"\s]|$)',text)
   if r.returncode!=0 or not match or int(match.group(1)) not in [21,25]:raise OSError('Matching qualified JDK21/25 tool required: '+name)
   versions[name]=dict(major=int(match.group(1)),output=text)
  if len({r['major'] for r in versions.values()})!=1:raise OSError('Compiler/runtime/archive tool versions differ')
  dependency=out/'htsjdk-5.0.0.jar'
  if a.htsjdk_jar:
   source=a.htsjdk_jar.resolve()
   if pin(source)!=HTSJDK:raise ValueError('Exact complete HTSJDK dependency bytes differ')
   shutil.copyfile(source,dependency)
  elif a.download_htsjdk:
   with urllib.request.urlopen(urllib.request.Request(URL,headers={'User-Agent':'OmicsMatic-MALT-minimal-source-build'}),timeout=90) as response,dependency.open('xb') as f:
    if response.status!=200:raise OSError('HTSJDK dependency HTTP'+str(response.status))
    size=0
    for raw in iter(lambda:response.read(1048576),b''):
     size+=len(raw)
     if size>16777216:raise ValueError('Complete HTSJDK dependency exceeds byte capacity')
     f.write(raw)
  else:raise FileNotFoundError('Required --htsjdk-jar or permitted --download-htsjdk not supplied')
  if pin(dependency)!=HTSJDK:raise ValueError('Complete downloaded/copied HTSJDK dependency SHA256 differs')
  sources={n:pin(ROOT/n) for n in SOURCES};classes=out/'classes';classes.mkdir()
  def run(label,command):
   r=subprocess.run(command,cwd=out,stdin=subprocess.DEVNULL,capture_output=True,timeout=240,env=env);(out/(label+'.stdout')).write_bytes(r.stdout);(out/(label+'.stderr')).write_bytes(r.stderr);save(out/(label+'.process.json'),dict(command=command,exit_code=r.returncode))
   if r.returncode!=0:raise ValueError('Build command failed: '+label+'; inspect full diagnostics')
  run('javac',[str(java/('javac'+suffix)),'--release','17','-encoding','UTF-8','-Xlint:all','-Werror','-sourcepath','','-cp',str(dependency),'-d',str(classes),*[str(ROOT/n) for n in SOURCES]])
  manifest=out/'MANIFEST.MF';manifest.write_text('Manifest-Version: 1.0\nMain-Class: org.jlinalg.cli.MaltAlignCli\nClass-Path: htsjdk-5.0.0.jar\n\n',encoding='ascii',newline='\n');artifact=out/'malt041-alignment-development.jar';run('jar',[str(java/('jar'+suffix)),'--create','--file',str(artifact),'--manifest',str(manifest),'-C',str(classes),'.']);run('help',[str(java/('java'+suffix)),'-jar',str(artifact),'--help'])
  if b'Required action:' not in (out/'help.stdout').read_bytes():raise ValueError('Packaged entry point lacks required recovery instructions')
  if any(pin(ROOT/n)!=v for n,v in sources.items()) or pin(dependency)!=HTSJDK:raise ValueError('Source/dependency changed during build')
  license_file=ROOT/'LICENSE'
  if license_file.is_file():shutil.copyfile(license_file,out/'LICENSE-project.txt')
  state.update(status='READY_MINIMAL_MALT041_ALIGNMENT_DEVELOPMENT_BUILD',compiler_runtime_versions=versions,sources=sources,htsjdk_dependency=dict(source_url=URL,**HTSJDK),artifact=pin(artifact),classpath_dependency_must_remain_adjacent='htsjdk-5.0.0.jar',main_class='org.jlinalg.cli.MaltAlignCli',bytecode_target=17,alignment_qualified_by_this_build=False,warnings=['Keep the adjacent exact HTSJDK library and project license with the component jar. This does not bundle original MALT or Java8, alter system Java, or establish a new release.','Version/help and successful compilation do not establish scientific output equivalence. Use the separate qualified alignment profiles and saved replay evidence.']);save(out/'BUILD.json',state);(out/'WARNINGS.md').write_text('# MALT DEVELOPMENT BUILD ONLY\n\n'+'\n\n'.join(state['warnings'])+'\n\n'+ACTION+'\n',encoding='utf8',newline='\n');print(state['status'],str(artifact),'\n'+ACTION,flush=True);return 0
 except Exception as error:
  skip=isinstance(error,(OSError,urllib.error.URLError));state.update(status='SKIP3_MALT_ALIGNMENT_BUILD_PREREQUISITE_UNAVAILABLE' if skip else 'FAIL2_MALT_ALIGNMENT_MINIMAL_BUILD',cause=repr(error),traceback=traceback.format_exc(),dependent_analysis_skipped=True,required_action=ACTION)
  if owned:save(out/'FAILURE.json',state);(out/'WARNINGS_FAILURE.md').write_text('# '+state['status']+'\n\n'+str(error)+'\n\n'+ACTION+'\n',encoding='utf8',newline='\n')
  print(state['status'],repr(error),'\n'+ACTION,flush=True);return 3 if skip else 2
if __name__=='__main__':
 if not __debug__:raise RuntimeError('Never disable source/dependency integrity checks')
 raise SystemExit(main())
