#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Real streaming-profile path, Linux churn, counterfeit, recording and process controls."""
from pathlib import Path
import argparse,json,subprocess,sys,time
from adversarial import request
from run_large import pin,save

def main():
    p=argparse.ArgumentParser(description=__doc__)
    for key in ['jar','binary','small','stress','out']:p.add_argument('--'+key,type=Path,required=True)
    a=p.parse_args()
    if sys.platform!='linux':raise FileNotFoundError('Run these controls in the approved Linux/WSL Python and JDK')
    out=a.out.resolve();out.mkdir(parents=True,exist_ok=False);candidate=pin(a.jar);checks=[]
    def run(label,command):
        command=list(map(str,command));start=time.monotonic()
        with (out/(label+'.stdout')).open('xb') as so,(out/(label+'.stderr')).open('xb') as se:
            child=subprocess.run(command,stdin=subprocess.DEVNULL,stdout=so,stderr=se,timeout=900)
        row=dict(case=label,command=command,exit_code=child.returncode,seconds=round(time.monotonic()-start,2));save(out/(label+'.process.json'),row);checks.append(row)
        print(json.dumps(row),flush=True)
        if child.returncode:raise RuntimeError(label+' failed; inspect the preserved consoles')
    where=out/"space and 'quote'";where.mkdir();req=where/'request.properties';request(req,a.small,a.binary)
    common=['/usr/bin/java','-Xms16m','-Xmx64m','-cp',str(a.jar.resolve()),'org.jlinalg.cli.SambambaStreamingWorkflowCli']
    run('literal-space-quote-path',common+['--request',req,'--out',where/'analysis'])
    assert (where/'analysis/COMPLETED.properties').is_file()
    classes=out/'probe-classes';classes.mkdir()
    run('compile-linux-race',['/usr/bin/javac','--release','17','-cp',a.jar,'-d',classes,Path(__file__).with_name('SamplingRace.java')])
    run('linux-concurrent-tempfiles',['/usr/bin/java','-Xmx64m','-cp',str(a.jar.resolve())+':'+str(classes),'org.jlinalg.cli.SamplingRace',out/'churn',req])
    assert 'permission_errors_suppressed=false' in (out/'linux-concurrent-tempfiles.stdout').read_text()
    run('adversarial-and-late-recording',[sys.executable,'-B',Path(__file__).with_name('adversarial.py'),'--jar',a.jar,'--base',where/'analysis','--binary',a.binary,'--out',out/'adversarial'])
    run('real-process-limits-and-parent-death',[sys.executable,'-B',Path(__file__).with_name('guard_controls.py'),'--jar',a.jar,'--binary',a.binary,'--small',a.small,'--stress',a.stress,'--out',out/'process-guards'])
    if pin(a.jar)!=candidate:raise ValueError('Actual candidate changed during controls')
    adversarial=json.loads((out/'adversarial/COMPLETED.json').read_bytes());guards=json.loads((out/'process-guards/COMPLETED.json').read_bytes())
    save(out/'COMPLETED.json',dict(status='PASS_STREAMING_V2_PATH_CHURN_ADVERSARIAL_LATE_RECORDING_AND_REAL_PROCESS_CONTROLS',candidate=candidate,
        checks=checks,adversarial=adversarial,process_guards=guards,scientific_functions_patched=False,permission_errors_suppressed=False,
        BAI_independently_qualified=False,new_catalog_credit=0,whole_goal_complete=False))
    print('PASS_ALL_STREAMING_V2_POSTBUILD_CONTROLS',flush=True)
if __name__=='__main__':
    try:main()
    except FileNotFoundError as e:print('SKIP3_STREAMING_POSTBUILD_PREREQUISITE',repr(e),flush=True);raise SystemExit(3)
    except Exception as e:print('FAIL2_STREAMING_POSTBUILD_VALIDATION_UNADMITTED',repr(e),'Preserve partials and errors; no accepted validation.',flush=True);raise
