# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Bind a read-only original Windows R/biomaRt installation to a captured complete file profile. Installs nothing."""
import argparse,json,os,subprocess
from pathlib import Path
from setup_deseq2_reference import ROOT,RSCRIPT,environment,pin,save

RESOURCE=ROOT/'src/main/resources/org/jlinalg/omics/biomart-workflow'
ACTION='SKIP3: dependent biomaRt analysis remains skipped. Supply the exact original R4.6.1 Windows installation and captured biomaRt/dependency files. Where permitted use download_biomart_reference.py and setup_biomart_reference.py --allow-install in NEW private paths, then rerun this read-only preparation. Inspect the named missing/changed package/file and logs. Never refresh a frozen runtime profile to accept different scientific code. Preserve failed attempts and existing libraries.'

def tree(root):
    result={}
    for f in sorted(root.rglob('*')):
        if f.is_symlink():raise RuntimeError('Linked runtime member: '+str(f))
        if f.is_file():result[f.relative_to(root).as_posix()]=pin(f)
        elif not f.is_dir():raise RuntimeError('Nonregular runtime member: '+str(f))
    return result

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--setup',required=True,type=Path);p.add_argument('--version',required=True,choices=['2.60.1','2.68.0']);p.add_argument('--out',required=True,type=Path);p.add_argument('--capture-initial-profile',action='store_true',help='Development intake ONLY; refuses an existing profile. Never use to replace a frozen profile.');a=p.parse_args();out=a.out.resolve();assert not out.exists();out.mkdir(parents=True);state=dict(status='SKIP3_BIOMART_RUNTIME_UNAVAILABLE',result_admitted=False,required_action=ACTION)
    try:
        assert os.name=='nt','Captured original runtime profile requires Windows x86_64';setup=json.loads((a.setup/'COMPLETED.json').read_bytes());assert setup['status']=='PASS_PRIVATE_ORIGINAL_BIOMART_PACKAGES_AND_SOURCE_BODIES_ONLY';libraries=setup['versions'][a.version]['libraries'];temporary=out/'private-temp';temporary.mkdir();env=environment();env.update(TMPDIR=str(temporary),TMP=str(temporary),TEMP=str(temporary),BIOMART_CACHE=str(out/'private-cache'));command=[str(RSCRIPT),'--vanilla',str(ROOT/'tools/literature/biomart_runtime_inventory.R'),str(out/'resolved-packages.json'),*libraries]
        with (out/'inventory.stdout').open('xb') as first,(out/'inventory.stderr').open('xb') as second:r=subprocess.run(command,cwd=ROOT,env=env,stdin=subprocess.DEVNULL,stdout=first,stderr=second,timeout=120,creationflags=subprocess.CREATE_NO_WINDOW)
        save(out/'process.json',dict(command=command,exit_code=r.returncode));assert r.returncode==0,'Package resolution failed; inspect inventory.stderr';resolved=json.loads((out/'resolved-packages.json').read_bytes());assert resolved['version']==a.version and resolved['R']=='R version 4.6.1 (2026-06-24 ucrt)';rhome=Path(resolved['r_home']);roots={'r-home':rhome};roots.update({'pkg-'+n:Path(d) for n,d in resolved['packages'].items() if not Path(d).is_relative_to(rhome)})
        print('Checking',len(roots),'complete original runtime trees',flush=True);trees={n:tree(d) for n,d in roots.items()};profile=dict(schema_version=1,platform='Windows-x86_64',edition=a.version,R=resolved['R'],trees=trees);target=RESOURCE/('runtime-'+a.version+'.json')
        if a.capture_initial_profile:
            assert not target.exists(),'Initial profile already exists; never repin frozen scientific runtime';RESOURCE.mkdir(parents=True,exist_ok=True);save(target,profile)
        else:assert json.loads(target.read_bytes())==profile,'Original runtime byte profile differs; no dependency substitution admitted'
        manifest=dict(schema_version=1,profile='original-biomaRt-'+a.version+'-Windows-R4.6.1',edition=a.version,rscript=str(RSCRIPT),libraries=libraries,roots={n:str(d) for n,d in roots.items()},packages=resolved['packages'],profile_sha256=pin(target)['sha256']);save(out/'runtime.json',manifest);assert all(tree(d)==trees[n] for n,d in roots.items()),'Runtime changed during capture';state.update(status='PASS_READONLY_EXACT_BIOMART_RUNTIME_PREPARED_ONLY',runtime_manifest=dict(path=str(out/'runtime.json'),**pin(out/'runtime.json')),profile=pin(target),runtime_trees=len(trees),runtime_files=sum(len(v) for v in trees.values()),annotation_queries_executed=False,whole_goal_complete=False)
    except Exception as error:state['error']=repr(error)
    save(out/'COMPLETED.json',state);(out/'WARNINGS.md').write_text('# '+state['status']+'\n\n'+state.get('error','No annotation analysis was run.')+'\n\nREQUIRED ACTION: '+ACTION+'\n',encoding='utf8',newline='\n');print(state['status'],state.get('error',''),flush=True);return 0 if state['status'].startswith('PASS_') else 3

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete runtime identity checks')
    raise SystemExit(main())
