# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Execute original GPL Python and original R function-boundary references separately."""
import argparse,concurrent.futures,json,subprocess,traceback
from pathlib import Path
from setup_deseq2_reference import ROOT,RSCRIPT,pin,save,environment


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args()
    base=ROOT/'build/literature-review/cistarget-runtime';out=a.out.resolve();assert not out.exists() and out.is_relative_to(base);out.mkdir(parents=True)
    state=dict(status='SKIP3_CISTARGET_REFERENCE_UNAVAILABLE',implementation_credit=False,analysis_admitted=False,whole_goal_complete=False)
    try:
        intake=base/'source-intake-1';setup=base/'reference-setup-3';ready=json.loads((setup/'COMPLETED.json').read_bytes());assert ready['status']=='PASS_PRIVATE_CTXCORE_020_REFERENCE_INSTALLATION_ONLY'
        controls=base/'controls-1';fixtures=json.loads((controls/'COMPLETED.json').read_bytes());assert all(pin(controls/n)==v for n,v in fixtures['files'].items())
        source_pins={}
        for folder,manifest in [('ctxcore-original','ctxcore-members.json'),('rcistarget-original','rcistarget-members.json')]:
            for name,value in json.loads((intake/manifest).read_bytes()).items():
                path=intake/folder/name;assert pin(path)==value;source_pins[path.relative_to(ROOT).as_posix()]=value
        python=ROOT/ready['python'];assert python.is_file() and RSCRIPT.is_file(),'Required permitted reference runtime absent; skip dependent original validation'
        state['status']='FAIL2_CISTARGET_ORIGINAL_FUNCTION_CONTROLS';env=environment();env.update(PYTHONNOUSERSITE='1',NUMBA_NUM_THREADS='1')
        def execute(item):
            name,command=item;home=out/name;home.mkdir();save(home/'command.json',command)
            with (home/'stdout').open('xb') as first,(home/'stderr').open('xb') as second:run=subprocess.run(command,cwd=ROOT,env=env,stdin=subprocess.DEVNULL,stdout=first,stderr=second,timeout=900,creationflags=subprocess.CREATE_NO_WINDOW)
            save(home/'process.json',dict(exit_code=run.returncode));assert run.returncode==0,'Read retained '+name+' stdout/stderr'
        jobs=[('python',[str(python),'-I',str(ROOT/'tools/literature/cistarget_reference_probe.py'),str(controls/'cases.json'),str(out/'ctxcore')]),
            ('R',[str(RSCRIPT),'--vanilla',str(ROOT/'tools/literature/cistarget_reference_probe.R'),str(controls/'controls.txt'),str(intake/'rcistarget-original'),str(out/'rcistarget')])]
        with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:list(pool.map(execute,jobs))
        assert all(pin(ROOT/n)==v for n,v in source_pins.items()) and all(pin(controls/n)==v for n,v in fixtures['files'].items())
        assert len(json.loads((out/'ctxcore/COMPLETED.json').read_bytes())['cases'])==32 and (out/'rcistarget/COMPLETED.txt').read_text().splitlines()[0]=='PASS_32_UNCHANGED_RCISTARGET_FUNCTION_BOUNDARY_CONTROLS_ONLY'
        helpers=[Path(__file__).resolve(),ROOT/'tools/literature/cistarget_reference_probe.py',ROOT/'tools/literature/cistarget_reference_probe.R']
        state.update(status='PASS_64_ORIGINAL_CISTARGET_PREPARED_RECOVERY_AND_LEADING_EDGE_FAMILIES_ONLY',original_source_pins_unchanged=source_pins,
            source_pins={p.relative_to(ROOT).as_posix():pin(p) for p in helpers},full_R_package_installed=False,full_database_file_workflow_executed=False,
            scope='Actual installed GPL ctxcore0.2.0 public recovery/enrichment/leading functions with a synthetic prepared-rank provider. Exact unmodified GPL RcisTarget1.29.0 function ASTs execute under base R; no S4/database/annotation wrapper or historical full-package execution is claimed.')
    except Exception as failure:
        state['error']=repr(failure);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''),flush=True);return 0 if state['status'].startswith('PASS_') else 3 if state['status'].startswith('SKIP3_') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable original-source controls')
    raise SystemExit(main())
