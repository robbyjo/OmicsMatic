# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Run unchanged installed ctxcore functions on48 extra NumPy reduction boundaries."""
import argparse,json,subprocess,traceback
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save,environment


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args();base=ROOT/'build/literature-review/cistarget-runtime';out=a.out.resolve()
    assert not out.exists() and out.is_relative_to(base);out.mkdir(parents=True);state=dict(status='SKIP3_CISTARGET_PAIRWISE_REFERENCE_UNAVAILABLE',implementation_credit=False,whole_goal_complete=False)
    try:
        setup=json.loads((base/'reference-setup-3/COMPLETED.json').read_bytes());assert setup['status']=='PASS_PRIVATE_CTXCORE_020_REFERENCE_INSTALLATION_ONLY';python=ROOT/setup['python'];assert python.is_file()
        old=json.loads((base/'reference-controls-1/COMPLETED.json').read_bytes());pins=old['original_source_pins_unchanged'];assert all(pin(ROOT/n)==v for n,v in pins.items())
        fixtures=base/'pairwise-controls-1';ready=json.loads((fixtures/'COMPLETED.json').read_bytes());assert ready['cases']==48 and all(pin(fixtures/n)==v for n,v in ready['files'].items())
        state['status']='FAIL2_CISTARGET_PAIRWISE_REFERENCE';probe=ROOT/'tools/literature/cistarget_pairwise_reference_probe.py';command=[str(python),'-I',str(probe),str(fixtures/'cases.json'),str(out/'ctxcore')];home=out/'python';home.mkdir();save(home/'command.json',command)
        with (home/'stdout').open('xb') as stdout,(home/'stderr').open('xb') as stderr:process=subprocess.run(command,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=300,env=environment())
        save(home/'process.json',dict(exit_code=process.returncode));assert process.returncode==0,'Read retained reference stdout/stderr'
        result=json.loads((out/'ctxcore/COMPLETED.json').read_bytes());assert result['status']=='PASS48_ORIGINAL_CTXCORE_NUMPY_REDUCTION_BOUNDARY_FAMILIES_ONLY' and len(result['cases'])==48
        assert all(pin(ROOT/n)==v for n,v in pins.items()) and all(pin(fixtures/n)==v for n,v in ready['files'].items())
        state.update(status='PASS48_ORIGINAL_CTXCORE_NUMPY_REDUCTION_BOUNDARY_FAMILIES_ONLY',original_source_pins_unchanged=pins,complete_cases=48,
            source_pins={p.relative_to(ROOT).as_posix():pin(p) for p in [Path(__file__).resolve(),probe]},full_R_package_installed=False,full_database_file_workflow_executed=False)
    except Exception as failure:
        state['error']=repr(failure);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''),flush=True);return 0 if state['status'].startswith('PASS') else 3 if state['status'].startswith('SKIP3') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable original-source controls')
    raise SystemExit(main())
