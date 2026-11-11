# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Install unchanged legacy future packages privately for original CellChat1.1.3 network clustering."""
import argparse,json,subprocess,traceback
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save,environment
from setup_chipseeker_reference import inventory
BASE=ROOT/'build/literature-review/cellchat-runtime'

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);p.add_argument('--allow-install',action='store_true');a=p.parse_args();out=a.out.resolve();assert not out.exists() and out.is_relative_to(BASE);out.mkdir(parents=True)
    action='If the exact permitted original CellChat network/Python/future dependency is missing or installation is forbidden, SKIP dependent embedding/clustering analysis. Preserve all prior libraries/attempts. Supply the named prerequisite in NEW private paths where permitted and retry NEW outputs. Do not patch original CellChat functions or silently select sequential clustering, another UMAP engine, metric or data orientation. Setup health alone admits no user analysis.'
    state=dict(status='SKIP3_CELLCHAT_NETWORK_REFERENCE_UNAVAILABLE',analysis_admitted=False,implementation_credit=False,whole_goal_complete=False,installation_authorized=a.allow_install,required_action=action);libraries=[];before={}
    try:
        assert a.allow_install,'Installation not authorized; dependent analysis skipped'
        runtime=json.loads((BASE/'legacy-runtime-profile-1/runtime-profile.json').read_bytes());libraries=[Path(p) for p in runtime['libraries']];before=inventory(libraries);save(out/'LIBRARIES-BEFORE.json',before)
        intake=BASE/'network-dependency-intake-1';source=json.loads((intake/'COMPLETED.json').read_bytes());assert source['status']=='PASS_ORIGINAL_NETWORK_COMPATIBILITY_SOURCE_INTAKE_ONLY';archives=[]
        for row in source['records']:
            archive=intake/(row['package']+'_'+row['version']+'.tar.gz');assert pin(archive)=={k:row[k] for k in ['bytes','sha256']};archives.append(archive)
        script=ROOT/'tools/literature/install_cellchat_network_reference.R';bindings={f.relative_to(ROOT).as_posix():pin(f) for f in [Path(__file__).resolve(),script,*archives]}
        for f in [Path(__file__).resolve(),script]:
            dest=out/'source-snapshot'/f.relative_to(ROOT);dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes(f.read_bytes())
        save(out/'STARTED.json',dict(source_pins=bindings,libraries=list(map(str,libraries)),archives=list(map(str,archives)),analysis_admitted=False))
        python=ROOT/'build/omics-cell-tools-env/Scripts/python.exe';env=environment();env.update(RETICULATE_PYTHON=str(python),RETICULATE_USE_MANAGED_VENV='no',RETICULATE_AUTOCONFIGURE='FALSE',PYTHONDONTWRITEBYTECODE='1',PYTHONNOUSERSITE='1',NUMBA_NUM_THREADS='1',NUMBA_CACHE_DIR=str(out/'numba-cache'),TMPDIR=str(out/'tmp'));(out/'tmp').mkdir()
        command=[runtime['rscript'],'--vanilla',str(out/'source-snapshot/tools/literature/install_cellchat_network_reference.R'),str(out),*map(str,archives),*map(str,libraries)]
        with (out/'installer.stdout').open('xb') as so,(out/'installer.stderr').open('xb') as se:r=subprocess.run(command,env=env,stdout=so,stderr=se,timeout=900)
        save(out/'process.json',dict(command=command,exit_code=r.returncode));report=json.loads((out/'R-RESULT.json').read_bytes());assert r.returncode==0 and report['status']=='PASS_ORIGINAL_NETWORK_REFERENCE_SETUP_ONLY',report
        state.update(report);state.update(source_pins=bindings,private_library_inventory=inventory([out/'R-lib']),historical_paper_environment=False)
    except Exception as error:state['detail']=str(error);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    finally:
        after=inventory(libraries);save(out/'LIBRARIES-AFTER.json',after);state['preexisting_libraries_unchanged']=before==after
        if before!=after:state.update(status='FAIL2_EXISTING_LIBRARIES_CHANGED',analysis_admitted=False)
        save(out/'COMPLETED.json',state);(out/'WARNINGS.md').write_text('# '+state['status']+'\n\n'+state.get('detail','Setup health only; no user analysis admitted.')+'\n\nREQUIRED ACTION: '+action+'\n',encoding='utf8')
    print(state['status'],state.get('detail',''),flush=True);return 0 if state['status'].startswith('PASS_') else 2 if state['status'].startswith('FAIL2') else 3

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable source/library preservation checks')
    raise SystemExit(main())
