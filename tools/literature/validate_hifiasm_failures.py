# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Exercise real owned assembly failures, late evidence changes, cancellation and unavailable dependency skips."""
import argparse,concurrent.futures,copy,json,subprocess,time,traceback
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from validate_mashr_native import JAVA21


def linux(path):return '/mnt/d/projects/JLinAlg/'+path.relative_to(ROOT).as_posix()


def main():
    p=argparse.ArgumentParser(description=__doc__)
    for name in ['build','requests','out']:p.add_argument('--'+name,type=Path,required=True)
    a=p.parse_args();out=a.out.resolve();assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/hifiasm-runtime');out.mkdir(parents=True);state=dict(status='FAIL2_HIFIASM_FAILURE_CONTROLS',cases={},analysis_admitted=False,implementation_credit=False)
    try:
        built=json.loads((a.build/'COMPLETED.json').read_bytes());jar=ROOT/built['artifact']['path'];assert pin(jar)=={k:built['artifact'][k] for k in ['bytes','sha256']};original=json.loads((a.requests/'0.14.2-r315.json').read_bytes());base=copy.deepcopy(original);base['analyses']=[copy.deepcopy(original['analyses'][0])];base['analyses'][0]['id']='first';base['inputs']={k:v for k,v in base['inputs'].items() if k in base['analyses'][0]['reads']};tiny=out/'tiny.fa';tiny.write_bytes(b'>tiny\nACGTACGTACGTACGT\n');before={ROOT/v['path'].removeprefix('/mnt/d/projects/JLinAlg/'):dict(bytes=v['bytes'],sha256=v['sha256']) for v in base['inputs'].values()}
        names=['windows21-skip','windows25-skip','missing-runtime','legacy-hic-skip','timeout','output-capacity','cancellation','late-original-failure','late-graph-mutation','late-file-addition']
        def execute(name):
            home=out/name;home.mkdir();request=copy.deepcopy(base);request['out']=linux(home/'analysis');expected=3 if name.endswith('-skip') or name=='missing-runtime' else 2;is_windows=name.startswith('windows');action=None
            if is_windows:
                request['out']=str(home/'analysis');request['runtime']=str(out/'not-a-runtime.json')
                for spec in request['inputs'].values():spec['path']=str(ROOT/spec['path'].removeprefix('/mnt/d/projects/JLinAlg/'))
            elif name=='missing-runtime':request['runtime']=linux(out/'not-a-runtime.json')
            elif name=='legacy-hic-skip':
                request['analyses'][0]['mode']='hic';request['analyses'][0]['hic_pairs']=[dict(first='hic1',second='hic2')]
                for key in ['hic1','hic2']:
                    source=ROOT/'build/literature-review/hifiasm-runtime/paper-mode-fixtures-1'/(key+'.fa');request['inputs'][key]=dict(path=linux(source),**pin(source),kind='reads')
            elif name=='timeout':request['limits']=dict(wall_seconds=1)
            elif name=='output-capacity':request['limits']=dict(file_bytes=65536)
            elif name=='cancellation':action='cancel'
            elif name=='late-original-failure':
                request['inputs']['tiny']=dict(path=linux(tiny),**pin(tiny),kind='reads');request['analyses'].append(dict(id='second',mode='hifi',reads=['tiny'],threads=1,bloom_bits=0))
            elif name in ['late-graph-mutation','late-file-addition']:
                second=copy.deepcopy(request['analyses'][0]);second['id']='second';request['analyses'].append(second);action=name
            file=home/'request.json';save(file,request);prefix=[str(JAVA21/'java.exe')] if name=='windows21-skip' else ['C:/Program Files/Common Files/Oracle/Java/javapath/java.exe'] if is_windows else ['wsl','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/usr/bin/java'];command=prefix+['-Xmx3g','-cp',str(jar) if is_windows else linux(jar),'org.jlinalg.cli.HifiasmWorkflowCli','--request',str(file) if is_windows else linux(file)];print('Real failure/skip control',name,flush=True);changed=False;analysis=home/'analysis'
            with (home/'original.stdout').open('xb') as first,(home/'original.stderr').open('xb') as second:
                process=subprocess.Popen(command,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=first,stderr=second);started=time.monotonic()
                while process.poll() is None:
                    assert time.monotonic()-started<1800,'Owned control observation deadline exceeded; preserve and inspect actual process before any retry'
                    watch=analysis/'analyses'/('second' if action in ['late-graph-mutation','late-file-addition'] else 'first')/'process/launch.json'
                    if action and not changed and watch.exists():
                        launch=json.loads(watch.read_bytes());assert launch['owned_launcher_pid']>0
                        if action=='cancel':(analysis/'CANCEL_REQUESTED').write_text('Controlled live cancellation\n',encoding='utf8')
                        elif action=='late-graph-mutation':
                            target=next((analysis/'analyses/first/original').glob('*.gfa'));old=pin(target);target.write_bytes(target.read_bytes()+b'# controlled late mutation\n');save(home/'mutation.json',dict(kind=action,path=target.relative_to(ROOT).as_posix(),before=old,after=pin(target),second_process_launch=launch))
                        else:
                            target=analysis/'analyses/first/original/unexpected.gfa';target.write_bytes(b'S\tunexpected\tA\tLN:i:1\n');save(home/'mutation.json',dict(kind=action,path=target.relative_to(ROOT).as_posix(),pin=pin(target),second_process_launch=launch))
                        changed=True
                    time.sleep(.25)
                code=process.wait(timeout=10)
            save(home/'process.json',dict(command=command,exit_code=code));assert code==expected,(name,code,expected,(home/'original.stderr').read_text(errors='replace')[-2000:]);report=json.loads((analysis/'COMPLETED.json').read_bytes());assert not report['analysis_admitted'] and report['status'].startswith('SKIP3_' if expected==3 else 'FAIL2_');assert (analysis/'WARNINGS.md').is_file() and 'ACTION REQUIRED:' in (analysis/'WARNINGS.md').read_text();assert not (analysis/'result-files.json').exists();receipts=[]
            for receipt in (analysis/'analyses').glob('*/process/process.json') if (analysis/'analyses').exists() else []:
                record=json.loads(receipt.read_bytes());assert not record['owned_live_after_cleanup'];receipts.append(record)
            if action:assert changed and receipts
            if name.startswith('late-'):assert report['earlier_cases_are_provisional_unless_entire_receipt_passes'] and all(not c['analysis_admitted'] for c in report['earlier_cases_are_provisional_unless_entire_receipt_passes'])
            if name=='late-graph-mutation':assert 'changed' in report['error']
            if name=='late-file-addition':assert 'population changed' in report['error']
            if expected==3:assert not (analysis/'analyses').exists()
            print(name,report['status'],flush=True);return name,dict(exit_code=code,status=report['status'],error=report['error'],owned_process_receipts=len(receipts),earlier_provisional_cases=len(report['earlier_cases_are_provisional_unless_entire_receipt_passes']),control_action_observed=changed)
        with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
            for name,record in pool.map(execute,names):state['cases'][name]=record
        assert len(state['cases'])==10 and all(pin(p)==v for p,v in before.items()) and all(pin(ROOT/n)==v for n,v in built['source_pins'].items());state.update(status='passed-ten-real-hifiasm-skip-owned-failure-and-atomic-admission-controls',artifact=built['artifact'],source_pins={Path(__file__).resolve().relative_to(ROOT).as_posix():pin(Path(__file__).resolve())})
    except Exception as error:state['error']=repr(error);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''));return 0 if state['status'].startswith('passed-') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable actual failure controls')
    raise SystemExit(main())
