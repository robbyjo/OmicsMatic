# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Run the source-bound model on Windows Java25 and verify explicit Linux SKIP3 without R interop."""
import argparse,copy,json,shutil,subprocess
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from setup_chipseeker_reference import inventory
from validate_dndscv_runtime import compare
from probe_dndscv_worker import base_analysis


def wsl(path):
    p=Path(path).resolve();return '/mnt/'+p.drive[0].lower()+'/'+p.as_posix().split(':/',1)[1]


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--build',type=Path,required=True);p.add_argument('--out',type=Path,required=True);a=p.parse_args();base=ROOT/'build/literature-review/dndscv-runtime';out=a.out.resolve();assert out.is_relative_to(base) and not out.exists();out.mkdir();build=json.loads((a.build/'COMPLETED.json').read_bytes());jar=ROOT/build['artifact']['path'];assert pin(jar)=={k:build['artifact'][k] for k in ['bytes','sha256']};template=json.loads((base/'workflow-probe-1/request.json').read_bytes());libs=list(map(Path,template['r_libraries']));before=inventory(libs);save(out/'LIBRARIES-BEFORE.json',before);pins={p.relative_to(ROOT).as_posix():pin(p) for p in [Path(__file__).resolve(),ROOT/'tools/literature/validate_dndscv_runtime.py',ROOT/'tools/literature/probe_dndscv_worker.py']};reports=[]
    for platform in ['windows-java25','linux-java21-profile-skip']:
        home=out/platform;home.mkdir();request=copy.deepcopy(template);request['analyses']=[base_analysis('targeted-2','2r_3w')];request['out']='result'
        for name in ['mutations.tsv','genes.tsv']:shutil.copyfile(base/'workflow-probe-1'/name,home/name)
        if platform.startswith('linux'):
            for name in ['source','rscript','r_home']:request[name]=wsl(request[name])
            request['r_libraries']=[wsl(p) for p in libs];command=['wsl','-d','JLinAlg-Omics-SAIGE-20261026','--','/usr/bin/java','-Xmx2g','-cp',wsl(jar),'org.jlinalg.cli.DndscvWorkflowCli','--request',wsl(home/'request.json')];expected=3
        else:command=['C:/Program Files/Common Files/Oracle/Java/javapath/java.exe','-Xmx2g','-cp',str(jar),'org.jlinalg.cli.DndscvWorkflowCli','--request',str(home/'request.json')];expected=0
        save(home/'request.json',request)
        with (home/'stdout').open('xb') as stdout,(home/'stderr').open('xb') as stderr:r=subprocess.run(command,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=900)
        save(home/'process.json',dict(command=command,exit_code=r.returncode));assert r.returncode==expected,(platform,(home/'stderr').read_text(encoding='utf8',errors='replace')[-1800:]);result=home/'result';state=json.loads((result/'analysis-status.json').read_bytes());assert state['result_admitted']==(expected==0);comparison=None
        if not expected:
            comparison=compare(result/'results/targeted-2',base/'original-controls-1/targeted-2');receipt=json.loads((result/'worker/process-receipt.json').read_bytes());assert all(not p['alive_after_cleanup'] for p in receipt['owned_process_cleanup'])
        else:assert state['status']=='SKIP3_DEPENDENCY_UNAVAILABLE' and 'Windows Java' in state['cause'] and not (result/'worker/process-receipt.json').exists() and not (result/'results').exists()
        reports.append(dict(platform=platform,exit_code=expected,result_admitted=expected==0,reference_comparison=comparison,source_or_runtime_fallback=False))
    after=inventory(libs);save(out/'LIBRARIES-AFTER.json',after);assert before==after and all(pin(ROOT/n)==v for n,v in [*pins.items(),*build['source_pins'].items()]);save(out/'COMPLETED.json',dict(status='passed-original-dndscv-Windows25-and-explicit-Linux-profile-skip',artifact=build['artifact'],source_pins=pins,protocols=reports,preexisting_libraries_unchanged=True,implementation_credit=False,whole_goal_complete=False));print('Passed original Java25 execution and Linux SKIP3 with no R process')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable platform/source checks')
    main()
