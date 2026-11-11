# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Validate the independent nf-core process candidate, including cleanup after unassessable gauges."""
import argparse,json,subprocess,traceback
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from validate_nfcore_engine import BASE,DISTRO,JAVA,JAVA25,linux,wsl

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--artifact',type=Path,required=True);p.add_argument('--out',type=Path,required=True);a=p.parse_args();jar=a.artifact.resolve();out=a.out.resolve();assert jar.is_relative_to(BASE) and out.is_relative_to(BASE) and not out.exists();out.mkdir()
 build=json.loads((jar.parent/'COMPLETED.json').read_bytes());assert pin(jar)=={k:build['artifact'][k] for k in ['bytes','sha256']};assert all(pin(ROOT/n)==v for n,v in build['source_pins'].items())
 sources=[ROOT/'tools/literature/java/NfcoreProcessSnapshotControl.java',ROOT/'tools/literature/fixtures/nfcore/process_thread_exit.c',Path(__file__)]
 pins={p.relative_to(ROOT).as_posix():pin(p) for p in sources};canonical=pin(ROOT/'build/cli/omicsmatic-0.3.7.jar');state=dict(status='FAIL2_NFCORE_PROCESS_CONTROLS',new_catalog_credit=False,artifact=pin(jar))
 try:
  for path in sources:
   target=out/'source-snapshot'/path.relative_to(ROOT);target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(path.read_bytes())
  classes=out/'classes';classes.mkdir();r=subprocess.run([str(JAVA/'javac.exe'),'--release','17','-encoding','UTF-8','-Xlint:all','-Werror','-cp',str(jar),'-d',str(classes),str(sources[0])],capture_output=True,timeout=90);(out/'javac.stderr').write_bytes(r.stderr);assert r.returncode==0,r.stderr
  for platform,exe in [('windows21',JAVA/'java.exe'),('windows25',JAVA25/'java.exe')]:
   r=subprocess.run([str(exe),'-cp',str(jar)+';'+str(classes),'org.jlinalg.cli.NfcoreProcessSnapshotControl','--portable'],capture_output=True,timeout=60);(out/(platform+'.stdout')).write_bytes(r.stdout);(out/(platform+'.stderr')).write_bytes(r.stderr);assert r.returncode==0,(platform,r.stderr)
  remote=wsl('/usr/bin/python3','-c','import tempfile;print(tempfile.mkdtemp(prefix="omicsmatic-nfcore-process-independent-",dir="/var/tmp"))').decode().strip();save(out/'OWNER.json',dict(owned_linux_root=remote))
  command=['wsl.exe','-d',DISTRO,'--exec','/usr/bin/gcc','-std=c11','-Wall','-Wextra','-Werror','-pedantic','-O2','-pthread',linux(sources[1]),'-o',remote+'/thread-helper']
  r=subprocess.run(command,capture_output=True,timeout=90);(out/'gcc.stdout').write_bytes(r.stdout);(out/'gcc.stderr').write_bytes(r.stderr);save(out/'gcc.process.json',dict(command=command,exit_code=r.returncode));assert r.returncode==0,r.stderr
  command=['wsl.exe','-d',DISTRO,'--exec','/usr/bin/java','-Xmx256m','-cp',linux(jar)+':'+linux(classes),'org.jlinalg.cli.NfcoreProcessSnapshotControl',remote+'/owned',remote+'/thread-helper']
  with (out/'linux.stdout').open('xb') as so,(out/'linux.stderr').open('xb') as se:r=subprocess.run(command,stdin=subprocess.DEVNULL,stdout=so,stderr=se,timeout=240)
  save(out/'linux.process.json',dict(command=command,exit_code=r.returncode));assert r.returncode==0,('Real process controls failed',r.returncode)
  copied=wsl('/usr/bin/python3','-c', '''import shutil,sys,json
from pathlib import Path
s,d=map(Path,sys.argv[1:]);shutil.copytree(s,d)
for p in d.rglob('process.json'):
 j=json.loads(p.read_bytes());assert j['owned_live_after_cleanup']==[],str(p)
''',remote+'/owned',linux(out/'retained'))
  assert all(pin(ROOT/n)==v for n,v in pins.items()) and all(pin(ROOT/n)==v for n,v in build['source_pins'].items())
  state.update(status='PASS_NFCORE_ANCHORED_PROCESS_IDENTITY_AND_INDEPENDENT_CLEANUP',source_pins=pins,build_source_pins=build['source_pins'],portable_platforms=['windows21','windows25','linux21'],real_child_churn=True,nonzero_exit_cleanup=True,capacity_refusal_cleanup=True,integrity_refusal_cleanup=True,zombie_leader_live_worker_refused_and_cleaned=True)
 except Exception as e:state.update(cause=repr(e),traceback=traceback.format_exc())
 assert pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==canonical;state['canonical_executable_unchanged']=canonical;save(out/'COMPLETED.json',state)
 if not state['status'].startswith('PASS_'):(out/'WARNINGS.md').write_text('# FAIL2 process-control validation\n\n'+state.get('cause','')+'\n\nRequired action: preserve owned processes, identities and raw logs. No result admitted. Inspect known live identities before a new run; never weaken metrics, alter IDs or rewrite failed evidence.\n')
 print(state['status'],state.get('cause',''),flush=True);return 0 if state['status'].startswith('PASS_') else 2

if __name__=='__main__':
 if not __debug__:raise RuntimeError('Do not disable source, real-process or cleanup checks')
 raise SystemExit(main())
