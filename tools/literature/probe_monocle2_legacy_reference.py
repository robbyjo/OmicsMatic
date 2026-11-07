# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Check exact original Monocle2 functions and complete differential controls in the isolated Linux candidate."""
import argparse
import json
from pathlib import Path
import subprocess
from setup_deseq2_reference import ROOT,pin,save
from validate_mashr_native import linux


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);p.add_argument('--additional-library',type=Path,action='append',default=[]);p.add_argument('--baseline-inventory',type=Path);a=p.parse_args();base=ROOT/'build/literature-review/monocle-runtime';out=a.out.resolve();assert out.is_relative_to(base) and not out.exists();out.mkdir()
    setup=base/'monocle2-linux-reference-setup-3';state=json.loads((setup/'RESULT.json').read_bytes());assert state['status']=='INSTALLED_MONOCLE2_LEGACY_CANDIDATE_NOT_VALIDATED'
    prefix=setup/'.pixi/envs/default'
    inventory_script=out/'inventory.py';inventory_script.write_text('''import os,sys,stat,hashlib,json,concurrent.futures
from pathlib import Path
root=Path(sys.argv[1]);files={};regular=[]
for directory,dirs,names in os.walk(root,followlinks=False):
 for name in dirs+names:
  path=Path(directory)/name;info=path.lstat();relative=path.relative_to(root).as_posix()
  if stat.S_ISLNK(info.st_mode):files[relative]=dict(type="symlink",target=os.readlink(path),mode=stat.S_IMODE(info.st_mode))
  elif stat.S_ISREG(info.st_mode):regular.append((path,relative,info.st_size,stat.S_IMODE(info.st_mode)))
  elif not stat.S_ISDIR(info.st_mode):raise RuntimeError("Unsupported runtime node: "+relative)
print("inventory nodes:",len(regular),"regular,",len(files),"symlinks",flush=True)
def digest(record):
 path,relative,size,mode=record;h=hashlib.sha256()
 with path.open("rb") as f:
  for data in iter(lambda:f.read(1048576),b""):h.update(data)
 return relative,dict(type="regular",bytes=size,sha256=h.hexdigest(),mode=mode)
with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
 for i,(name,value) in enumerate(pool.map(digest,regular),1):
  files[name]=value
  if i%2000==0:print("regular files hashed:",i,flush=True)
Path(sys.argv[2]).write_text(json.dumps(files,sort_keys=True)+"\\n",encoding="utf8")
''',encoding='utf8',newline='\n')
    def inventory(label,root=prefix):
        target=out/label;command=['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--user','root','--exec','/usr/bin/timeout','--signal=TERM','--kill-after=15','600','/usr/bin/python3',linux(inventory_script),linux(root),linux(target)]
        with (out/(label+'.stdout')).open('xb') as stdout,(out/(label+'.stderr')).open('xb') as stderr:
            process=subprocess.Popen(command,cwd=ROOT,stdout=stdout,stderr=stderr,stdin=subprocess.DEVNULL);save(out/(label+'.started.json'),dict(pid=process.pid,command=command));code=process.wait(timeout=660)
        save(out/(label+'.process.json'),dict(command=command,exit_code=code));assert code==0,(out/(label+'.stderr')).read_text(encoding='utf8',errors='replace')
        return json.loads(target.read_bytes())
    extras=[path.resolve(strict=True) for path in a.additional_library];assert all(path.is_relative_to(base) and path.is_dir() for path in extras)
    baseline=None;baseline_pin=None
    if a.baseline_inventory:
        baseline=a.baseline_inventory.resolve(strict=True);assert baseline.is_relative_to(base)
        prior=json.loads((baseline.parent/'PROVENANCE.json').read_bytes());started=json.loads((baseline.parent/'STARTED.json').read_bytes())
        assert prior['runtime_unchanged'] and started['runtime_lock']==pin(setup/'pixi.lock')
        before=json.loads(baseline.read_bytes());baseline_pin=pin(baseline);assert len(before)==prior['existing_runtime_files_and_links_checked']
        save(out/'RUNTIME-BEFORE.json',before)
    else:before=inventory('RUNTIME-BEFORE.json')
    extra_before=[inventory('ADDITIONAL-'+str(i)+'-BEFORE.json',path) for i,path in enumerate(extras)]
    source=ROOT/'tools/literature/probe_monocle2_legacy_reference.R';wanted=pin(source);(out/'source-used.R').write_bytes(source.read_bytes())
    original=base/'monocle2-source-intake-2';receipt=json.loads((original/'SOURCE.json').read_bytes());assert receipt['version']=='2.26.0' and all(pin(original/'source'/n)==v for n,v in receipt['source_members'].items())
    for n in ['home','tmp','cache']:(out/n).mkdir()
    command=['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--user','root','--exec','/usr/bin/timeout','--signal=TERM','--kill-after=15','600','/usr/bin/env',
        'HOME='+linux(out/'home'),'TMPDIR='+linux(out/'tmp'),'R_USER_CACHE_DIR='+linux(out/'cache'),'R_HOME='+linux(prefix/'lib/R'),'LD_LIBRARY_PATH='+linux(prefix/'lib'),
        'LANG=C','LC_ALL=C','LANGUAGE=en','OMP_NUM_THREADS=1','OPENBLAS_NUM_THREADS=1','MKL_NUM_THREADS=1',linux(prefix/'bin/Rscript'),'--vanilla',linux(source),linux(out),linux(original/'source'),*map(linux,extras),linux(prefix/'lib/R/library')]
    save(out/'STARTED.json',dict(command=command,source_pin=wanted,runner_pin=pin(Path(__file__)),original_source_receipt=pin(original/'SOURCE.json'),runtime_lock=pin(setup/'pixi.lock'),baseline_inventory=str(baseline) if baseline else None,baseline_inventory_pin=baseline_pin,additional_libraries=list(map(str,extras)),analysis_admitted=False))
    with (out/'stdout.log').open('xb') as stdout,(out/'stderr.log').open('xb') as stderr:
        process=subprocess.Popen(command,cwd=ROOT,stdout=stdout,stderr=stderr,stdin=subprocess.DEVNULL);save(out/'PROCESS-STARTED.json',dict(pid=process.pid,command=command));code=process.wait(timeout=660)
    save(out/'process.json',dict(command=command,pid=process.pid,exit_code=code));assert pin(source)==wanted
    after=inventory('RUNTIME-AFTER.json');assert before==after
    extra_after=[inventory('ADDITIONAL-'+str(i)+'-AFTER.json',path) for i,path in enumerate(extras)];assert extra_before==extra_after
    if baseline:assert pin(baseline)==baseline_pin
    save(out/'PROVENANCE.json',dict(runtime_unchanged=True,source_pin=wanted,exact_original_source='monocle 2.26.0 / Bioconductor 3.16',existing_runtime_files_and_links_checked=len(before),analysis_admitted=False,Java_adapter_validated=False))
    if (out/'RESULT.json').exists():
        result=json.loads((out/'RESULT.json').read_bytes());print(json.dumps({k:v for k,v in result.items() if k not in ['warnings','messages']}),flush=True)
        action='Reference controls only; no Java/user analysis is admitted. Preserve every attempt, original counts/metadata/CDS/tests and stdout/stderr. Missing dependencies: install exact original software into NEW private libraries only where permitted; otherwise skip dependent analysis. Non-OK source models/errors: inspect the named family/formula/dispersion cause and retry NEW paths without dropping genes or repairing values.'
        messages=[str(result.get('detail','')),*[w['stage']+': '+w['message'] for w in result.get('warnings',[])]]
        (out/'WARNINGS.md').write_text('# '+result['status']+'\n\n'+'\n\n'.join(messages)+'\n\nREQUIRED ACTION: '+action+'\n',encoding='utf8',newline='\n')
    else:print('FAIL2: no original terminal report; inspect retained stdout/stderr',flush=True)
    return code


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable original/runtime preservation checks')
    raise SystemExit(main())
