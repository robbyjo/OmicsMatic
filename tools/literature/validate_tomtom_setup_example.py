# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Validate exported Tomtom source setup, owned-worker recovery and both documented public modes."""
import argparse
import json
from pathlib import Path
import subprocess
import sys
import uuid

ROOT=Path(__file__).resolve().parents[2]
BASE=ROOT/'build/literature-review/meme-runtime'
sys.path.insert(0,str(ROOT/'tools/literature'))
from validate_mashr_native import linux,pin

DRIVER=r'''
import importlib.util,json,os,signal,subprocess,sys,time
from pathlib import Path
root,jar,checkout,archive=map(Path,sys.argv[1:]);assert not root.exists();root.mkdir(mode=0o700)
def write(path,value):path.write_text(json.dumps(value,indent=2)+'\n')
def run(name,command,expected=0,timeout=4000):
 with (root/(name+'.stdout')).open('xb') as out,(root/(name+'.stderr')).open('xb') as err:r=subprocess.run(list(map(str,command)),stdin=subprocess.DEVNULL,stdout=out,stderr=err,timeout=timeout)
 write(root/(name+'.json'),dict(command=list(map(str,command)),exit_code=r.returncode));assert r.returncode==expected,(name,r.returncode,expected);print(name+': exit '+str(r.returncode),flush=True)
java=['/usr/bin/java','-Xmx1024m','-XX:ActiveProcessorCount=2','-jar',jar,'tomtom-tools'];kit=root/'kit'
run('export',[*java,'--export-runtime-setup',kit]);setup=kit/'setup_tomtom.py'
run('permission-skip',['/usr/bin/python3','-I','-B',setup,'--report',root/'permission-skip'],3)
assert 'SKIP3' in (root/'permission-skip/WARNINGS.md').read_text() and not (root/'permission-skip/COMPLETED.json').exists()
(root/'bad-archive.tar.gz').write_bytes(b'not the exact source archive')
run('archive-integrity-failure',['/usr/bin/python3','-I','-B',setup,'--report',root/'archive-integrity-failure','--archive',root/'bad-archive.tar.gz','--allow-build','--research-nonprofit'],2)
assert 'FAIL2' in (root/'archive-integrity-failure/WARNINGS.md').read_text() and not (root/'archive-integrity-failure/COMPLETED.json').exists()
# Exercise the actual exported supervisor with real detached descendants, not a fake scientific runtime.
guard=root/'guard.py';guard.write_text("""import importlib.util,json,signal,sys
from pathlib import Path
spec=importlib.util.spec_from_file_location('guarded_setup',sys.argv[1]);module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
def stop(sig,frame):raise module.Cancelled('controlled signal')
for sig in [signal.SIGTERM,signal.SIGINT]:signal.signal(sig,stop)
report=Path(sys.argv[2]);mode=sys.argv[3];report.mkdir(mode=0o700)
code='import json,os,subprocess,sys,time; from pathlib import Path; p=subprocess.Popen([sys.executable,"-c","import time; time.sleep(120)"],start_new_session=True); Path(sys.argv[1]).write_text(json.dumps(dict(parent=os.getpid(),child=p.pid))); '+('sys.exit(4)' if mode=='nonzero' else 'time.sleep(120)')
try:module.supervise([sys.executable,'-I','-B','-c',code,str(report/'workers.json')],report,timeout=1 if mode=='timeout' else 120)
except (Exception,KeyboardInterrupt) as error:(report/'CONTROL-ERROR.txt').write_text(str(error)+'\\n');sys.exit(2)
raise AssertionError('Control unexpectedly succeeded')
""")
guards=[]
for mode in ['cancel','timeout','nonzero']:
 report=root/('guard-'+mode)
 with (root/('guard-'+mode+'.stdout')).open('xb') as out,(root/('guard-'+mode+'.stderr')).open('xb') as err:
  command=['/usr/bin/python3','-I','-B',str(guard),str(setup),str(report),mode];process=subprocess.Popen(command,stdout=out,stderr=err,start_new_session=True)
  deadline=time.monotonic()+30
  while not (report/'workers.json').exists():
   assert process.poll() is None and time.monotonic()<deadline,'Owned-worker control failed before launch';time.sleep(.02)
  workers=json.loads((report/'workers.json').read_bytes())
  if mode=='cancel':process.send_signal(signal.SIGTERM)
  assert process.wait(timeout=30)==2
 assert all(not Path('/proc',str(pid)).exists() for pid in workers.values()),('owned worker survived',mode,workers)
 receipt=json.loads((report/'process.json').read_bytes());assert receipt['owned_live_after_cleanup']==[];guards.append(dict(mode=mode,workers=workers,owned_live_after_cleanup=[]));print('guard-'+mode+': owned descendants reaped',flush=True)
write(root/'GUARD-CONTROLS.json',guards)
run('source-setup',['/usr/bin/python3','-I','-B',setup,'--report',root/'source-setup','--archive',archive,'--allow-build','--research-nonprofit'])
completion=json.loads((root/'source-setup/COMPLETED.json').read_bytes());home,build=completion['home'],completion['build_evidence'];assert completion['owned_live_after_cleanup']==[]
run('documented-recipes',['/usr/bin/python3','-I','-B',checkout/'examples/tomtom/make_recipes.py','--out',root/'recipes'])
(root/'outputs').mkdir(mode=0o700)
for label in ['text','full']:
 run('documented-'+label,[*java,'--recipe',root/'recipes'/(label+'.json'),'--home',home,'--build-evidence',build,'--out',root/'outputs'/label])
 run('documented-verify-'+label,[*java,'--verify',root/'outputs'/label])
 doc=json.loads((root/'outputs'/label/'OUTPUT-AUDIT.json').read_bytes());assert doc['complete_reported_matches']==doc['complete_declared_pairs']==9
write(root/'COMPLETED.json',dict(status='PASSED_EXPORTED_SOURCE_SETUP_AND_BOTH_DOCUMENTED_PUBLIC_TOMTOM_WORKFLOWS',home=home,build_evidence=build,permission_skip=True,archive_failure=True,owned_worker_controls=guards,documented_workflows=2,public_analysis_admitted=True,biological_accuracy_certified=False))
'''


def write(path,value):path.write_text(json.dumps(value,indent=2)+'\n',encoding='utf-8',newline='\n')


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--artifact',type=Path,required=True);parser.add_argument('--out',type=Path,required=True);args=parser.parse_args();jar,out=args.artifact.resolve(),args.out.resolve();assert jar.is_relative_to(BASE.resolve()) and out.is_relative_to(BASE.resolve()) and not out.exists();out.mkdir(parents=True)
    canonical=pin(ROOT/'build/cli/omicsmatic-0.3.7.jar');artifact=pin(jar);paths=[Path(__file__),ROOT/'src/main/java/org/jlinalg/cli/TomtomSetupExport.java',ROOT/'src/main/resources/org/jlinalg/omics/meme-runtime-setup/setup_tomtom.py',ROOT/'src/main/resources/org/jlinalg/omics/meme-runtime-setup/build_meme_linux_reference.py'];paths.extend(p for p in (ROOT/'examples/tomtom').rglob('*') if p.is_file());sources={p.relative_to(ROOT).as_posix():pin(p) for p in paths};write(out/'SOURCE-PINS.json',sources)
    for name in sources:
        target=out/'source-snapshot'/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes((ROOT/name).read_bytes())
    driver=out/'native-driver.py';driver.write_text(DRIVER,encoding='utf-8',newline='\n');native='/var/tmp/omicsmatic-tomtom-setup-example-'+uuid.uuid4().hex;write(out/'NATIVE-OWNER.json',dict(linux_root=native,status='starting-exported-setup-and-documented-workflows',artifact=artifact))
    command=['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/usr/bin/python3','-I','-B',linux(driver),native,linux(jar),linux(ROOT),linux(ROOT/'build/literature-review/fimo-runtime/meme-5.5.9.tar.gz')]
    with (out/'native-driver.stdout').open('xb') as stdout,(out/'native-driver.stderr').open('xb') as stderr:result=subprocess.run(command,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=5000)
    write(out/'native-driver.json',dict(command=command,exit_code=result.returncode))
    exporter="""from pathlib import Path
import json,shutil,sys
sys.path.insert(0,sys.argv[1]);from collect_meme_original_controls import census
source,dest=Path(sys.argv[2]),Path(sys.argv[3]);assert source.is_dir() and not dest.exists();before=census(source);shutil.copytree(source,dest);after=census(dest);assert set(before)==set(after)
for name,value in before.items():assert {k:v for k,v in value.items() if k!='mode'}=={k:v for k,v in after[name].items() if k!='mode'}
assert census(source)==before
(dest.parent/'NATIVE-INVENTORY.json').write_text(json.dumps(before,indent=2)+'\\n');print('Complete exported setup/example capture:',len(before),'nodes')
"""
    capture=['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/usr/bin/python3','-I','-B','-c',exporter,linux(ROOT/'tools/literature'),native,linux(out/'captured')];copy=subprocess.run(capture,capture_output=True,timeout=300);(out/'capture.stdout').write_bytes(copy.stdout);(out/'capture.stderr').write_bytes(copy.stderr);assert copy.returncode==0;assert result.returncode==0,'Inspect preserved complete native setup/example attempt'
    completion=json.loads((out/'captured/COMPLETED.json').read_bytes());assert completion['documented_workflows']==2 and pin(jar)==artifact and pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==canonical and all(pin(ROOT/name)==value for name,value in sources.items())
    write(out/'COMPLETED.json',dict(status='passed-exported-source-setup-and-documented-public-workflows',artifact=dict(path=jar.relative_to(ROOT).as_posix(),**artifact),source_pins=sources,canonical_executable_unchanged=canonical,native_root=native,native_completion=completion,implementation_credit=False,released=False,pushed=False,deployed=False,whole_goal_complete=False,scope='Actual exported exact original source setup, two explicit skip/failure controls, three detached-worker cancellation/timeout/nonzero ownership controls, both documented current5.5.9 public workflows and native portable verification. Network acquisition path is implemented but this run uses the complete previously downloaded official archive; it does not claim a fresh download or paper reproduction.'))


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable full setup/example evidence checks')
    main()
