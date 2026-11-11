# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Copy an exact captured original biomaRt/R runtime into NEW private paths without changing or repinning scientific bytes."""
import argparse,json,shutil
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from prepare_biomart_runtime import RESOURCE,ACTION,tree

def export(manifest,out):
    m=json.loads(manifest.read_bytes());version=m['edition'];profile=RESOURCE/('runtime-'+version+'.json');assert pin(profile)['sha256']==m['profile_sha256'];p=json.loads(profile.read_bytes());assert set(p['trees'])==set(m['roots']);original={n:Path(d) for n,d in m['roots'].items()};before={n:tree(d) for n,d in original.items()};assert before==p['trees'],'Original runtime bytes differ; skip analysis, never repin';runtime=out/'runtime';runtime.mkdir();roots={};libraries=runtime/'R-lib';libraries.mkdir()
    for name,source in original.items():
        destination=runtime/'r-home' if name=='r-home' else libraries/name.removeprefix('pkg-');assert not destination.exists();shutil.copytree(source,destination,copy_function=shutil.copy2);assert tree(destination)==p['trees'][name],('Copied runtime differs',name);roots[name]=destination
    packages={}
    for name,source in m['packages'].items():
        key='pkg-'+name
        if key in roots:packages[name]=str(roots[key])
        else:
            relative=Path(source).relative_to(original['r-home']);packages[name]=str(roots['r-home']/relative)
    m.update(rscript=str(roots['r-home']/'bin/Rscript.exe'),libraries=[str(libraries),str(roots['r-home']/'library')],roots={n:str(d) for n,d in roots.items()},packages=packages);save(out/'runtime.json',m);assert {n:tree(d) for n,d in original.items()}==before,'Original installation changed';return dict(status='PASS_EXACT_PRIVATE_BIOMART_RUNTIME_EXPORTED_ONLY',runtime_manifest=dict(path=str(out/'runtime.json'),**pin(out/'runtime.json')),runtime_trees=len(roots),runtime_files=sum(len(v) for v in p['trees'].values()),original_bytes_unchanged=True,source_profile_unchanged=pin(profile),annotation_queries_executed=False,result_admitted=False)

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--runtime-manifest',required=True,type=Path);p.add_argument('--out',required=True,type=Path);a=p.parse_args();out=a.out.resolve();assert not out.exists();out.mkdir(parents=True)
    try:state=export(a.runtime_manifest.resolve(),out);code=0
    except Exception as error:state=dict(status='SKIP3_BIOMART_RUNTIME_EXPORT_UNAVAILABLE',error=repr(error),required_action=ACTION,result_admitted=False);code=3
    save(out/'COMPLETED.json',state);(out/'WARNINGS.md').write_text('# '+state['status']+'\n\n'+state.get('error','Exact bytes copied to new private paths. No analysis ran. R and all packages retain their own licenses and original license files. Private preparation is not permission to redistribute a runtime bundle.')+'\n\nREQUIRED ACTION: '+ACTION+'\n',encoding='utf8',newline='\n');print(state['status'],state.get('error',''),flush=True);return code

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable exact runtime-copy checks')
    raise SystemExit(main())
