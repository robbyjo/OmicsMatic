# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Explicit offline source installation into a NEW private library; missing/forbidden profile => SKIP3."""
import argparse,json,os,subprocess
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from setup_chipseeker_reference import inventory


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--source-home',required=True,type=Path);p.add_argument('--rscript',required=True,type=Path);p.add_argument('--dependency-library',action='append',type=Path,default=[]);p.add_argument('--out',required=True,type=Path);p.add_argument('--allow-install',action='store_true');a=p.parse_args();source=a.source_home.resolve();out=a.out.resolve();assert source.is_relative_to(ROOT) and out.is_relative_to(ROOT/'build/literature-review/dndscv-runtime') and not out.exists(),'Export source into this checkout and choose a NEW private output under build/literature-review/dndscv-runtime';out.mkdir(parents=True);libs=[p.resolve() for p in a.dependency_library];before=inventory(libs);save(out/'LIBRARIES-BEFORE.json',before);script=ROOT/'tools/literature/setup_dndscv_runtime.R';profile_path=ROOT/'src/main/resources/org/jlinalg/omics/dndscv-workflow/SOURCE-PROFILE.json';profile=json.loads(profile_path.read_bytes());pins={p.relative_to(ROOT).as_posix():pin(p) for p in [Path(__file__).resolve(),script,profile_path]};action='Read WARNINGS.md, installer logs and COMPLETED.json. If installation is forbidden or exact dependencies cannot be supplied, SKIP dependent dndscv analysis. Otherwise provision Windows R4.6.1 x86_64-w64-mingw32 and the exact packages in SOURCE-PROFILE.json in permitted private libraries; repeat --dependency-library in precedence order, then retry NEW paths with --allow-install. This helper installs only the unchanged exported local dndscv source, downloads nothing, modifies no existing library and substitutes no version. Successful preparation admits no user analysis.';state=dict(status='SKIP3_DNDSCV_REFERENCE_UNAVAILABLE',source_pins=pins,analysis_admitted=False,installation_authorized=a.allow_install,required_action=action)
    try:
        receipt=json.loads((source/'SOURCE.json').read_bytes());assert receipt['commit']==profile['commit'] and receipt['source_members']==profile['files'];assert all(pin(source/'source'/n)==v for n,v in profile['files'].items());assert a.allow_install,'Installation not enabled; dependent analysis skipped';assert a.rscript.is_file(),'Named Rscript is unavailable';assert libs and all(p.is_dir() for p in libs),'Supply exact permitted dependency libraries';library=out/'R-lib';library.mkdir();command=[str(a.rscript.resolve()),'--vanilla',str(script),str(out),str(source/'source'),str(library),str(profile_path),*map(str,libs)];save(out/'STARTED.json',dict(command=command,libraries=list(map(str,libs)),source_receipt=receipt,source_pins=pins));env=dict(os.environ,LANG='C',LC_ALL='C',LANGUAGE='en',R_LIBS=';'.join(map(str,[library,*libs])),OMP_NUM_THREADS='1',OPENBLAS_NUM_THREADS='1',MKL_NUM_THREADS='1',TMPDIR=str(out),R_USER_CACHE_DIR=str(out))
        with (out/'installer.stdout').open('xb') as stdout,(out/'installer.stderr').open('xb') as stderr:r=subprocess.run(command,cwd=ROOT,env=env,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=600)
        save(out/'process.json',dict(command=command,exit_code=r.returncode));assert r.returncode==0,'Original preparation/health failed; inspect installer.stderr';report=json.loads((out/'R-RESULT.json').read_bytes());assert report['status']=='loaded-exact-original-dndscv-runtime-only' and report['versions']==profile['packages'] and report['source_bodies_and_formals_checked'] and report['source_data_matches'];state.update(status='installed-exact-original-dndscv-runtime-only',profile=report,private_library=library.relative_to(ROOT).as_posix(),dependency_libraries=list(map(str,libs)))
    except Exception as error:state['detail']=str(error)
    finally:
        after=inventory(libs);save(out/'LIBRARIES-AFTER.json',after);state['preexisting_libraries_unchanged']=before==after;assert all(pin(ROOT/n)==v for n,v in pins.items())
        if before!=after:state.update(status='FAIL2_PREEXISTING_LIBRARY_CHANGED',detail='Inspect complete library inventories; no analysis admitted')
        save(out/'COMPLETED.json',state);(out/'WARNINGS.md').write_text('# '+state['status']+'\n\n'+state.get('detail','')+'\n\n**REQUIRED ACTION:** '+action+'\n',encoding='utf8',newline='\n')
    print(state['status']+': '+state.get('detail','Original runtime health passed.'));print('REQUIRED ACTION: '+action);return 0 if state['status']=='installed-exact-original-dndscv-runtime-only' else 2 if state['status'].startswith('FAIL2') else 3


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable source/library checks')
    raise SystemExit(main())
