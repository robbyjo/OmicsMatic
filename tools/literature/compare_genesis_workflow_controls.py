# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Compare original GENESIS pilot objects with development adapter results; no fits, downloads or installation."""
import argparse
import csv
import importlib.util
import json
import os
import shutil
import subprocess
import traceback
from pathlib import Path
from setup_deseq2_reference import ROOT, pin, save
BASE = ROOT/'build/literature-review/genesis-runtime'
ACTION='Missing or forbidden exact Windows R4.6.1/private GENESIS runtime: SKIP retained-object comparison; supply the named permitted prerequisites or leave comparison skipped. This helper installs nothing and performs no fits. FAIL2: preserve the attempt and resolve the stated source/control/integrity cause before retrying NEW paths; no comparison result is admitted.'


class Unavailable(RuntimeError):pass


def main():
    p=argparse.ArgumentParser(description=__doc__)
    for name in ['profile','reference','controls','out']:p.add_argument('--'+name,type=Path,required=True)
    a=p.parse_args();out=a.out.resolve();assert not out.exists() and out.is_relative_to(BASE);out.mkdir(parents=True)
    state=dict(status='FAIL2_ORIGINAL_GENESIS_FIELD_COMPARISON',original_fits_performed=False,new_catalog_credit=False,whole_goal_complete=False);code=2;protected={};runtime=None;roots=[]
    try:
        if os.name!='nt' or not a.profile.is_file():raise Unavailable('The exact private Windows R4.6.1 runtime profile is unavailable')
        profile=json.loads(a.profile.read_bytes());assert profile['tool']=='GENESIS' and profile['version']=='2.42.0'
        for key in ['rscript','r_home','private_library']:
            if not Path(profile[key]).exists():raise Unavailable('Required exact runtime prerequisite is missing: '+profile[key])
        helper=ROOT/'src/main/resources/org/jlinalg/omics/genesis/prepare_genesis_runtime.py';spec=importlib.util.spec_from_file_location('genesis_runtime_pin',helper);module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
        roots=[Path(profile['r_home']),Path(profile['private_library'])];runtime=module.closure(roots);assert runtime==profile['files']
        source=ROOT/'src/main/resources/org/jlinalg/omics/genesis/compare_workflow_reference.R'
        protected={str(f.resolve()):pin(f) for f in [a.profile,helper,source,Path(__file__)]}
        for home in [a.reference.resolve(),a.controls.resolve()]:
            assert not out.is_relative_to(home) and not home.is_relative_to(out)
            protected.update({str(f):pin(f) for f in home.rglob('*') if f.is_file()})
        script=out/'compare.R';shutil.copyfile(source,script);temp=out/'tmp';temp.mkdir()
        command=[profile['rscript'],'--vanilla',str(script),profile['private_library'],str(a.reference.resolve()),str(a.controls.resolve()),str(out)]
        environment=dict(os.environ,R_HOME=profile['r_home'],R_LIBS_USER=profile['private_library'],R_LIBS_SITE='',R_DEFAULT_PACKAGES='base',TMPDIR=str(temp),LC_ALL='C',LANG='C')
        r=subprocess.run(command,cwd=out,env=environment,stdin=subprocess.DEVNULL,capture_output=True,timeout=180)
        (out/'stdout').write_bytes(r.stdout);(out/'stderr').write_bytes(r.stderr);save(out/'process.json',dict(command=command,exit_code=r.returncode));assert r.returncode==0,(r.stdout[-2000:],r.stderr[-5000:])
        assert b'PASS_RETAINED_ORIGINAL_GENESIS_FIELDS_NO_REFITTING' in r.stdout
        with (out/'complete-original-field-comparisons.tsv').open(encoding='utf8',newline='') as stream:records=list(csv.DictReader(stream,delimiter='\t'))
        state.update(status='PASS_COMPLETE_RETAINED_ORIGINAL_GENESIS_WORKFLOW_FIELD_COMPARISONS',comparisons=len(records),compared_cells=sum(int(r['compared_cells']) for r in records),
            producer=pin(Path(__file__)),worker=pin(source),reference_completed=pin(a.reference/'COMPLETED.json'),controls_completed=pin(a.controls/'COMPLETED.json'),
            scope='Original retained PC-AiR including projected relatives, PC-Relate, covariance and all four model/association scientific fields at1e-12, with explicit NA/NaN distinction. Null-only sample aliases are omitted from selected matrix/frame attributes after retaining phenotype-order identity transport. No new fits or independent estimator refitting.');code=0
    except Unavailable as error:state.update(status='SKIP3_EXACT_GENESIS_COMPARISON_RUNTIME_UNAVAILABLE',cause=repr(error));code=3
    except Exception as error:
        state['cause']=repr(error);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    finally:
        try:
            assert all(pin(Path(n))==v for n,v in protected.items())
            if runtime is not None:assert module.closure(roots)==runtime
        except Exception as error:state.update(status='FAIL2_GENESIS_COMPARISON_PRESERVATION',cause=repr(error));code=2
        state['required_action']=ACTION;(out/'WARNINGS.md').write_text('# '+state['status']+'\n\n'+state.get('cause','Original objects were compared without refitting. No adapter/catalog/whole-paper completion follows.')+'\n\nREQUIRED ACTION: '+ACTION+'\n',encoding='utf8',newline='\n')
        save(out/'COMPLETED.json',state)
    print(state['status'],state.get('cause','')[:7000],flush=True);return code


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable original/control/runtime preservation')
    raise SystemExit(main())
