#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Reject overlap before analysis and detect changes to already admitted metadata."""
import argparse
import json
from pathlib import Path
import shutil
import sys
sys.path.insert(0,str(Path(__file__).resolve().parent))
import mashr_preparation as bridge
from mashr_original_runtime import ProcessFailure,census,environment,pin,run_owned,write

ROOT=Path(__file__).resolve().parents[2]
BASE=ROOT/'build/literature-review/mashr-runtime'
JAVA=Path('C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/java.exe')


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--runtime',type=Path,required=True);parser.add_argument('--out',type=Path,required=True);args=parser.parse_args();out=args.out.resolve();assert out.is_relative_to(BASE.resolve()) and not out.exists();out.mkdir()
    try:
        rt=bridge.runtime(args.runtime);app=bridge.native(None,JAVA);controls=[]
        sources=[Path(__file__),ROOT/'tools/literature/mashr_preparation.py',ROOT/'tools/literature/mashr_original_runtime.py'];bound={p.relative_to(ROOT).as_posix():pin(p) for p in sources}
        report_before=census(rt['report'].parent);inputs=out/'input';shutil.copytree(ROOT/'examples/mashr-preparation',inputs,ignore=shutil.ignore_patterns('README.md','.gitattributes'));input_before=census(inputs)
        for name in ['output-in-report','scratch-in-report','scratch-in-input']:
            folder=out/name;folder.mkdir();result=rt['report'].parent/'FORBIDDEN-ANALYSIS-OUTPUT' if name=='output-in-report' else folder/'result'
            command=[sys.executable,'-I','-B',ROOT/'tools/literature/mashr_preparation.py','--runtime',args.runtime.resolve(),'--java',JAVA,'--input',inputs,'--out',result]
            if name!='output-in-report':command+=['--scratch-root',rt['report'].parent if name=='scratch-in-report' else inputs]
            try:process=run_owned(command,folder,'controller',environment(rt['rhome'],rt['home'],folder/'scratch'),rt['monitor'])
            except ProcessFailure as failure:process=failure.result
            error=(folder/'controller.stderr').read_text(encoding='utf-8');assert process['exit_code']==2 and 'FAIL2' in error and 'Required action' in error and not result.exists(),(name,error)
            assert report_before==census(rt['report'].parent) and input_before==census(inputs)
            controls.append(dict(name=name,exit_code=2,analysis_output_created=False,completed_runtime_report_unchanged=True,input_unchanged=True,process=process));print(name+': FAIL2 before output creation; report and input unchanged',flush=True)
        fake=dict(rt);copied=out/'copied-setup';shutil.copytree(rt['report'].parent,copied);fake['report']=copied/'COMPLETED.json';(copied/'UNEXPECTED.txt').write_text('changed report graph\n',encoding='utf-8');failed=False
        try:bridge.checked_state(fake,app)
        except ValueError as error:failed=True;assert 'setup' in str(error).lower();write(out/'changed-setup-error.json',dict(error=str(error),expected='FAIL2'))
        assert failed;controls.append(dict(name='changed-admitted-setup-graph',expected='FAIL2',producer_report_unchanged=report_before==census(rt['report'].parent)))
        fake=dict(rt);fake['rhome']=out/'missing-R-home';fake['rhome'].mkdir();failed=False
        try:bridge.checked_state(fake,app)
        except ValueError as error:failed=True;assert 'Previously admitted' in str(error);write(out/'missing-admitted-runtime-error.json',dict(error=str(error),expected='FAIL2'))
        assert failed;controls.append(dict(name='unavailable-after-admission',expected='FAIL2',real_runtime_unchanged=True))
        bridge.checked_state(rt,app);assert all(pin(ROOT/n)==v for n,v in bound.items()) and report_before==census(rt['report'].parent) and input_before==census(inputs)
        write(out/'COMPLETED.json',dict(schema_version=1,status='passed-mashr-preparation-path-and-admitted-metadata-controls',controls=controls,source_pins=bound,files={p.relative_to(out).as_posix():pin(p) for p in sorted(out.rglob('*')) if p.is_file()}));return 0
    except Exception as failure:write(out/'FAILURE.json',dict(status='FAIL2_PREPARATION_ADMISSION_CONTROLS',error=str(failure)));raise


if __name__=='__main__':
    try:sys.exit(main())
    except Exception as failure:print('FAIL2_PREPARATION_ADMISSION_CONTROLS: '+str(failure),file=sys.stderr);sys.exit(2)
