# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Explicit opt-in offline source install into a NEW private library; missing/forbidden dependency => SKIP3."""
import argparse,json,os,subprocess
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from setup_chipseeker_reference import inventory


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--source-home',type=Path,required=True);p.add_argument('--rscript',type=Path,required=True);p.add_argument('--dependency-library',action='append',type=Path,default=[]);p.add_argument('--out',type=Path,required=True);p.add_argument('--allow-install',action='store_true');a=p.parse_args();out=a.out.resolve();source=a.source_home.resolve();assert out.is_relative_to(ROOT/'build/literature-review/soupx-runtime') and not out.exists(),'Choose a NEW private output under build/literature-review/soupx-runtime';assert source.is_relative_to(ROOT),'Export the original source into this checkout first';out.mkdir()
    libraries=[p.resolve() for p in a.dependency_library];script=ROOT/'tools/literature/install_soupx_reference.R';profile_path=ROOT/'src/main/resources/org/jlinalg/omics/soupx-workflow/SOURCE-PROFILE.json';profile=json.loads(profile_path.read_bytes());pins={p.relative_to(ROOT).as_posix():pin(p) for p in [Path(__file__).resolve(),script,profile_path]};before=inventory(libraries);save(out/'LIBRARIES-BEFORE.json',before)
    action='Preserve this attempt, WARNINGS.md and installer logs. If installation is forbidden, skip dependent SoupX analysis. Otherwise provision Windows R4.6.1 x86_64-w64-mingw32 and the exact selected Seurat5.6.0/SeuratObject5.4.0/Matrix1.7-5/ggplot2 4.0.3/jsonlite2.0.0 dependencies in permitted private libraries, then retry NEW paths with --allow-install. This helper uses the exported local source only, installs no dependencies, modifies no existing library and substitutes no version. A successful health check alone does not admit user analysis.'
    state=dict(status='SKIP3_SOUPX_REFERENCE_UNAVAILABLE',analysis_admitted=False,required_action=action,source_pins=pins)
    try:
        receipt=json.loads((source/'SOURCE.json').read_bytes());version=receipt['version'];state['version']=version;assert version in profile['editions'];assert all(pin(source/'source'/n)==v for n,v in receipt['source_members'].items());assert all(receipt['source_members'][n]==v for n,v in profile['editions'][version]['files'].items());assert a.allow_install,'Installation not enabled; --allow-install is required only when installation is permitted';assert a.rscript.is_file(),'Named Rscript is unavailable';assert libraries and all(p.is_dir() for p in libraries),'Supply the existing permitted dependency libraries'
        library=out/'R-lib';library.mkdir();command=[str(a.rscript.resolve()),'--vanilla',str(script),str(out),str(source/'source'),version,str(library),*map(str,libraries)];save(out/'STARTED.json',dict(command=command,libraries=list(map(str,libraries)),source_pins=pins,original_source=receipt))
        env=dict(os.environ,LANG='C',LC_ALL='C',LANGUAGE='en',R_LIBS=';'.join(map(str,[library,*libraries])),OMP_NUM_THREADS='1',OPENBLAS_NUM_THREADS='1',MKL_NUM_THREADS='1',TMPDIR=str(out),R_USER_CACHE_DIR=str(out))
        with (out/'installer.stdout').open('xb') as stdout,(out/'installer.stderr').open('xb') as stderr:r=subprocess.run(command,cwd=ROOT,env=env,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=600)
        save(out/'process.json',dict(command=command,exit_code=r.returncode));assert r.returncode==0,'Original install/health failed; read retained installer.stderr';report=json.loads((out/'R-RESULT.json').read_bytes());assert report['status']=='loaded-source-bound-original-SoupX-reference-only' and report['R_version']==profile['R_version'] and report['platform']==profile['platform'] and report['versions']==profile['editions'][version]['packages'],'Installed runtime does not match the selected exact profile';state.update(status='installed-source-bound-original-SoupX-reference-only',profile=report,source_tree=source.relative_to(ROOT).as_posix())
    except Exception as error:state['detail']=str(error)
    finally:
        after=inventory(libraries);save(out/'LIBRARIES-AFTER.json',after);state['preexisting_libraries_unchanged']=before==after;assert all(pin(ROOT/n)==v for n,v in pins.items())
        if before!=after:state.update(status='FAIL2_PREEXISTING_LIBRARY_CHANGED',detail='Inspect the before/after inventories; no analysis admitted')
        save(out/'COMPLETED.json',state);(out/'WARNINGS.md').write_text('# '+state['status']+'\n\n'+state.get('detail','')+'\n\n**REQUIRED ACTION:** '+action+'\n',encoding='utf8',newline='\n')
    print(state['status']+': '+state.get('detail','Original runtime health passed.'));print('REQUIRED ACTION: '+action);return 0 if state['status']=='installed-source-bound-original-SoupX-reference-only' else 2 if state['status'].startswith('FAIL2') else 3


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable source/library checks')
    raise SystemExit(main())
