# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Exercise real source/policy failures and supervised owned processes; never admit an earlier partial case."""
import argparse,concurrent.futures,json,os,subprocess,sys,time,traceback
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent))
from setup_deseq2_reference import ROOT,pin,save

def linux(path):return '/mnt/d/projects/JLinAlg/'+path.relative_to(ROOT).as_posix()

def main():
    p=argparse.ArgumentParser(description=__doc__)
    for name in ['build','fixtures','out']:p.add_argument('--'+name,type=Path,required=True)
    p.add_argument('--cases',nargs='+');a=p.parse_args();out=a.out.resolve();assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/gseapy-runtime');out.mkdir(parents=True);state=dict(status='FAIL2_GSEAPY_SOURCE_AND_SUPERVISION_CONTROLS',checks={},whole_goal_complete=False)
    try:
        built=json.loads((a.build/'COMPLETED.json').read_bytes());jar=ROOT/built['artifact']['path'];assert pin(jar)=={k:built['artifact'][k] for k in ['bytes','sha256']};fixtures=a.fixtures.resolve();producer=json.loads((fixtures/'COMPLETED.json').read_bytes());names=a.cases or list(producer['requests']);assert set(names)<=set(producer['requests']);pins={p.relative_to(ROOT).as_posix():pin(p) for p in fixtures.glob('*.json')}
        def run(name):
            file=fixtures/(name+'.json');assert pin(file)==producer['requests'][name]['request'];request=json.loads(file.read_bytes());destination=ROOT/Path(request['out']).relative_to('/mnt/d/projects/JLinAlg');assert destination.parent==out and not destination.exists();command=['wsl','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/usr/bin/java','-Xmx2g','-cp',linux(jar),'org.jlinalg.cli.GseapyWorkflowCli','--request',linux(file)];cancel=None
            with (out/(name+'.stdout')).open('xb') as first,(out/(name+'.stderr')).open('xb') as second:
                child=subprocess.Popen(command,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=first,stderr=second)
                if name=='cancellation':
                    deadline=time.monotonic()+1200;started=destination/'worker/original.STARTED.json'
                    while time.monotonic()<deadline:
                        if child.poll() is not None:raise RuntimeError('Original batch ended before confirmed live cancellation; preserve this attempt')
                        if started.is_file():
                            original=json.loads(started.read_bytes());pid=int(original['owned_process_group']);probe=subprocess.run(['wsl','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/bin/cat','/proc/'+str(pid)+'/stat'],capture_output=True,timeout=20)
                            if probe.returncode==0:
                                raw=probe.stdout.decode();fields=raw[raw.rindex(')')+2:].split()
                                if fields[0]!='Z' and int(fields[2])==pid:
                                    cancel=dict(owned_process_group=pid,actual_proc_stat=raw,confirmed_live_before_cancellation=True);(destination/'worker/CANCEL_REQUESTED').write_text('Requested after authoritative live owned-process check\n',encoding='utf8');save(out/'cancellation-live-confirmation.json',cancel);break
                        time.sleep(0.05)
                    else:raise TimeoutError('No live original worker observed; do not restart this possibly active batch')
                code=child.wait(timeout=1500)
            save(out/(name+'.process.json'),dict(command=command,exit_code=code));status=json.loads((destination/'analysis-status.json').read_bytes());assert code==2 and not status['result_admitted'] and status['status'].startswith('FAIL2'),(name,status)
            source=destination/'worker';receipt=json.loads((source/'original.process.json').read_bytes());assert not receipt['owned_live_after_cleanup'];java=json.loads((source/'java-process.json').read_bytes());assert not java['owned_live_after_cleanup'];record=dict(exit_code=code,status=status,owned_live_after_cleanup=[],original_process=pin(source/'original.process.json'))
            if name.startswith('late-'):
                assert (source/'earlier-complete/full-result.tsv').is_file();report=json.loads((source/'COMPLETED.json').read_bytes())
                if name in ['late-numeric-background','late-empty-library']:
                    assert report['status']=='FAIL2_ORIGINAL_GSEAPY_BATCH_UNADMITTED' and len(report['earlier_complete_cases_are_provisional'])==1;trace=(source/'original-error.traceback').read_text();assert ('TypeError' if name=='late-numeric-background' else 'LookupError') in trace
                else:assert report['status']=='PASS_COMPLETE_ORIGINAL_GSEAPY_WORKFLOWS_ONLY' and len(report['cases'])==2 and 'Declared source_input_changes=fail' in status['error']
                record['earlier_case_preserved_unadmitted']=True
            elif name=='timeout':assert 'TimeoutError' in receipt.get('error','')
            elif name=='output-capacity':assert 'capacity exceeded' in receipt.get('error','')
            elif name=='cancellation':assert cancel and cancel['confirmed_live_before_cancellation'] and 'InterruptedError' in receipt.get('error','')
            return name,record
        with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
            for name,report in pool.map(run,names):state['checks'][name]=report;print(name,'expectedFAIL2; no partial admission; owned processes clean',flush=True)
        assert all(pin(ROOT/n)==v for n,v in pins.items()) and all(pin(ROOT/n)==v for n,v in built['source_pins'].items());state.update(status='passed-requested-real-GSEApy-source-policy-and-supervision-controls',artifact=built['artifact'],request_pins=pins,source_pins={Path(__file__).resolve().relative_to(ROOT).as_posix():pin(Path(__file__).resolve())},analysis_admitted=False)
    except Exception as error:state['error']=repr(error);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''));return 0 if state['status'].startswith('passed-') else 2

if __name__=='__main__':raise SystemExit(main())
