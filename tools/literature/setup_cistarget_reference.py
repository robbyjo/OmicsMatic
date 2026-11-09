# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Install a new private GPL ctxcore reference; never modify shared Python/R libraries."""
import argparse,json,os,subprocess,sys,traceback
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);p.add_argument('--allow-install',action='store_true');p.add_argument('--allow-download',action='store_true');a=p.parse_args()
    base=ROOT/'build/literature-review/cistarget-runtime';out=a.out.resolve();assert not out.exists() and out.is_relative_to(base);out.mkdir(parents=True)
    state=dict(status='SKIP3_CISTARGET_REFERENCE_SETUP_UNAVAILABLE',analysis_admitted=False,implementation_credit=False,whole_goal_complete=False)
    try:
        assert a.allow_install and a.allow_download,'Explicit download/install flags absent; skip reference setup without installing anything'
        intake=base/'source-intake-1';receipt=json.loads((intake/'COMPLETED.json').read_bytes());assert receipt['status']=='PASS_GPL_CISTARGET_PRIVATE_REFERENCES_ONLY'
        original=json.loads((intake/'ctxcore-members.json').read_bytes());assert all(pin(intake/'ctxcore-original'/n)==v for n,v in original.items())
        wheel=intake/'ctxcore-0.2.0.whl';assert pin(wheel)['sha256']==receipt['ctxcore_sha256']
        canonical=ROOT/'build/cli/omicsmatic-0.3.7.jar';before=pin(canonical);env=dict(os.environ)
        for key in ['PYTHONPATH','PYTHONHOME','PIP_TARGET','PIP_PREFIX','PIP_USER','PYTHONUSERBASE']:env.pop(key,None)
        env.update(PYTHONNOUSERSITE='1',PIP_DISABLE_PIP_VERSION_CHECK='1',PIP_CONFIG_FILE=os.devnull,PIP_CACHE_DIR=str(out/'pip-cache'),NUMBA_NUM_THREADS='1',OMP_NUM_THREADS='1',OPENBLAS_NUM_THREADS='1')
        def run(name,command,timeout=180):
            with (out/(name+'.stdout')).open('xb') as first,(out/(name+'.stderr')).open('xb') as second:
                process=subprocess.run(command,env=env,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=first,stderr=second,timeout=timeout,creationflags=subprocess.CREATE_NO_WINDOW)
            save(out/(name+'.process.json'),dict(command=command,exit_code=process.returncode));assert process.returncode==0,'Read retained '+name+' stdout/stderr'
        venv=out/'venv';run('venv',[sys.executable,'-m','venv',str(venv)]);python=venv/'Scripts/python.exe'
        requirements=['setuptools==75.8.2','attrs==24.3.0','numpy==1.26.4','pandas==2.2.3','numba==0.60.0','pyarrow==17.0.0','cytoolz==1.0.1','frozendict==2.4.6','pyyaml==6.0.2','tqdm==4.67.1']
        run('dependencies',[str(python),'-I','-m','pip','install','--only-binary=:all:','--report',str(out/'pip-report.json'),*requirements],900)
        # Give the original wheel its canonical filename; keep the captured wheel unchanged.
        local=out/'ctxcore-0.2.0-py3-none-any.whl';local.write_bytes(wheel.read_bytes())
        run('ctxcore',[str(python),'-I','-m','pip','install','--no-deps',str(local)])
        run('health',[str(python),'-I','-c','import ctxcore.recovery,ctxcore.rnkdb,ctxcore.ctdb,numpy,pandas,numba,pyarrow; import importlib.metadata as m; print({n:m.version(n) for n in ["ctxcore","numpy","pandas","numba","pyarrow","cytoolz","frozendict","pyyaml","tqdm"]})'])
        run('freeze',[str(python),'-I','-m','pip','freeze','--all'])
        assert pin(canonical)==before and all(pin(intake/'ctxcore-original'/n)==v for n,v in original.items())
        installed=venv/'Lib/site-packages/ctxcore';unchanged={}
        for name,value in original.items():
            if name.startswith('ctxcore/') and name.endswith('.py'):
                target=installed/name[len('ctxcore/'):];assert pin(target)==value;unchanged[name]=value
        state.update(status='PASS_PRIVATE_CTXCORE_020_REFERENCE_INSTALLATION_ONLY',python=str(python.relative_to(ROOT)),requirements=requirements,
            original_python_sources_unchanged=unchanged,ctxcore_source_wheel=pin(wheel),canonical_executable_unchanged=before,
            source_pins={Path(__file__).resolve().relative_to(ROOT).as_posix():pin(Path(__file__))},
            restricted_pycistarget_installed=False,shared_libraries_modified=False,analysis_installs_software=False)
    except Exception as failure:
        state['error']=repr(failure);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state)
    (out/'WARNINGS.md').write_text('# '+state['status']+'\n\n'+state.get('error','Private GPL reference loaded; no user analysis admitted.')+'\n\nRequired action: if installation/download/execution is forbidden or unavailable, skip dependent original reference analysis. Keep existing libraries and all failed attempts unchanged. Supply the named permitted prerequisite and retry NEW paths; native analysis will not install software. This is reference health, not implementation or scientific-analysis credit.\n',encoding='utf8')
    print(state['status'],state.get('error',''),flush=True);return 0 if state['status'].startswith('PASS_') else 3


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable source and shared-runtime preservation checks')
    raise SystemExit(main())
