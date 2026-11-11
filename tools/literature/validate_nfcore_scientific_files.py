# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Check physical input identity and named gzip views on a complete real engine capture."""
import argparse,json,subprocess,sys,traceback,gzip,time
sys.stdout.reconfigure(encoding="utf-8")
from pathlib import Path
sys.path.insert(0,str(Path('tools/literature').resolve()))
from setup_deseq2_reference import ROOT,pin,save
from validate_nfcore_engine import BASE,DISTRO,JAVA,JAVA25,linux,wsl
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--out',type=Path,required=True)
parser.add_argument('--matrix',type=Path,required=True)
parser.add_argument('--setup-reports',type=Path,required=True)
parser.add_argument('--captured-artifact',type=Path,required=True)
parser.add_argument('--scientific-artifact21',type=Path,required=True)
parser.add_argument('--scientific-artifact25',type=Path,required=True)
args=parser.parse_args()
assert __debug__, 'Never disable identity or complete-capture assertions'
out=args.out.resolve();assert out.is_relative_to(BASE) and not out.exists();out.mkdir();state={'status':'FAIL2_SCIENTIFIC_FILE_CONTROLS','new_catalog_credit':0}
try:
 remote=wsl('/usr/bin/python3','-c','import tempfile;print(tempfile.mkdtemp(prefix="omicsmatic-nfcore-native-identity-",dir="/var/tmp"))').decode().strip();save(out/'OWNER.json',{'owned_linux_root':remote,'cleanup':'Retain every input, complete attempt and failure; no deletion'})
 prior=args.matrix.resolve()/'21.04.1/success'
 material=remote+'/materialized';session=remote+'/engine'
 wsl('/usr/bin/python3','-c', r'''import sys,shutil,gzip,os
from pathlib import Path
p=Path(sys.argv[2]);shutil.copytree(sys.argv[1],p)
(p/'paths.txt').write_text(sys.argv[3]+"/workspace/resources/r0/materialized/inputs/α's read data.txt"+chr(10))
x=p/'identity';(x/'a').mkdir(parents=True);(x/'b').mkdir()
raw=gzip.compress(b'@same/1\nACGT\n+\nIIII\n',mtime=0)
(x/'a/reads.fq.gz').write_bytes(raw);(x/'b/reads.fq.gz').write_bytes(raw)
(x/'a/alias.fq.gz').symlink_to('reads.fq.gz');os.link(x/'a/reads.fq.gz',x/'a/hard.fq.gz')
''',linux(prior/'materialized'),material,session)
 recipe=json.loads((prior/'recipe.json').read_bytes());recipe['workflow']['path']=material+"/workflow-α's data";recipe['resources']['bundle']['path']=material;recipe['resources']['sheet']['path']=material+'/paths.txt';save(out/'recipe.json',recipe)
 def run(label,cmd,expected=0):
  with (out/(label+'.stdout')).open('xb') as so,(out/(label+'.stderr')).open('xb') as se:r=subprocess.run(list(map(str,cmd)),stdout=so,stderr=se,stdin=subprocess.DEVNULL,timeout=300)
  save(out/(label+'.process.json'),{'command':list(map(str,cmd)),'expected_exit':expected,'exit_code':r.returncode})
  assert r.returncode==expected,(label,r.returncode,(out/(label+'.stderr')).read_text(errors='replace')[-3000:]);print('PASS',label,flush=True)
 engine=args.captured_artifact.resolve();home=ROOT/'build/literature-review/nextflow-runtime/managed-setup-development-1/home-21-v2';report=args.setup_reports.resolve()/'report-21-v2'
 run('actual-captured-engine',['wsl.exe','-d',DISTRO,'--exec','/usr/bin/java','-Xmx256m','-XX:ActiveProcessorCount=2','-jar',linux(engine),'--recipe',linux(out/'recipe.json'),'--out',session,'--home',linux(home),'--setup-report',linux(report)])
 retained=out/'complete-attempt';wsl('/usr/bin/python3','-c','import shutil,sys;shutil.copytree(sys.argv[1],sys.argv[2])',session+'/attempts/000001',linux(retained))
 results=[]
 for compiler in ['21','25']:
  jar=Path(getattr(args,'scientific_artifact'+compiler)).resolve();classes=out/('classes'+compiler);classes.mkdir();tools=JAVA if compiler=='21' else JAVA25
  run('javac'+compiler,[tools/'javac.exe','--release','17','-Xlint:all','-Werror','-encoding','UTF-8','-cp',jar,'-d',classes,ROOT/'tools/literature/java/NfcoreScientificFilesControl.java'])
  for label,vm in [('windows21',JAVA/'java.exe'),('windows25',JAVA25/'java.exe')]:
   name=label+'-compiler'+compiler
   run(name,[vm,'-cp',str(jar)+';'+str(classes),'org.jlinalg.cli.NfcoreScientificFilesControl',retained,out/(name+'-views')]);results.append(name)
  name='linux21-compiler'+compiler
  run(name,['wsl.exe','-d',DISTRO,'--exec','/usr/bin/java','-cp',linux(jar)+':'+linux(classes),'org.jlinalg.cli.NfcoreScientificFilesControl',linux(retained),linux(out/(name+'-views'))]);results.append(name)
  run('wrong-source-compiler'+compiler,[tools/'java.exe','-jar',jar,'--engine-result',retained,'--peak-type','broadPeak','--read-mode','paired','--out',out/('wrong-source-compiler'+compiler)],2)
 state.update(status='PASS_ORIGINAL_FILE_IDENTITY_AND_NAMED_GZIP_CAPTURE_CONTROLS_ONLY',platforms=results,checks_per_platform=18,complete_real_engine_fixture=True,source_pins={p.relative_to(ROOT).as_posix():pin(p) for p in [ROOT/'tools/literature/java/NfcoreScientificFilesControl.java',ROOT/'src/main/java/org/jlinalg/cli/NfcoreScientificFiles.java',Path(__file__)]})
except Exception as e:state.update(cause=repr(e),traceback=traceback.format_exc())
save(out/'COMPLETED.json',state);print(state['status'],state.get('cause',''),flush=True)
if not state['status'].startswith('PASS_'):
 (out/'WARNINGS.md').write_text('# FAIL2 NO COMPONENT RESULT\n\n'+state.get('cause','')+'\n\nRequired action: preserve all evidence, inspect the named cause and rerun corrected controls to a new path. Do not shorten data, infer identifiers or weaken verification.\n',encoding='utf8')
raise SystemExit(0 if state['status'].startswith('PASS_') else 2)
