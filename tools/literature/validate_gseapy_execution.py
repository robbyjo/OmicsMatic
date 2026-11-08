# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Execute every requested complete original-class workflow through the actual Linux Java entry point."""
import argparse,concurrent.futures,json,subprocess,sys,traceback
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent))
from setup_deseq2_reference import ROOT,pin,save

def linux(path):return '/mnt/d/projects/JLinAlg/'+path.relative_to(ROOT).as_posix()

def main():
    p=argparse.ArgumentParser(description=__doc__)
    for name in ['build','fixtures','out']:p.add_argument('--'+name,type=Path,required=True)
    a=p.parse_args();out=a.out.resolve();assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/gseapy-runtime');out.mkdir(parents=True);state=dict(status='FAIL2_GSEAPY_COMPLETE_JAVA_EXECUTION_VALIDATION',runs={},whole_goal_complete=False)
    try:
        built=json.loads((a.build/'COMPLETED.json').read_bytes());assert built['status']=='passed-compiled-additive-GSEApy-analysis-entry-sources-only';jar=ROOT/built['artifact']['path'];assert pin(jar)=={k:built['artifact'][k] for k in ['bytes','sha256']};fixtures=a.fixtures.resolve();producer=json.loads((fixtures/'COMPLETED.json').read_bytes());pins={p.relative_to(ROOT).as_posix():pin(p) for p in fixtures.glob('*.json')}
        def run(edition):
            request=fixtures/(edition+'.json');assert pin(request)==producer['requests'][edition]['request'];spec=json.loads(request.read_bytes());destination=ROOT/Path(spec['out']).relative_to('/mnt/d/projects/JLinAlg');assert not destination.exists() and destination.parent==out;command=['wsl','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/usr/bin/java','-Xmx2g','-cp',linux(jar),'org.jlinalg.cli.GseapyWorkflowCli','--request',linux(request)]
            with (out/(edition+'.stdout')).open('xb') as first,(out/(edition+'.stderr')).open('xb') as second:result=subprocess.run(command,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=first,stderr=second,timeout=1200)
            save(out/(edition+'.process.json'),dict(command=command,exit_code=result.returncode));status=json.loads((destination/'analysis-status.json').read_bytes());return edition,dict(exit_code=result.returncode,status=status)
        with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
            for edition,report in pool.map(run,['1.0.4','1.1.2','1.1.3','1.1.8']):state['runs'][edition]=report;print(edition,report['status']['status'],flush=True)
        assert all(r['exit_code']==0 and r['status']['status']=='PASS_ORIGINAL_GSEAPY_COMPLETE_BATCH' and r['status']['result_admitted'] for r in state['runs'].values()),'No complete validation admitted: read retained exact per-edition stdout/stderr/status and every earlier provisional case'
        assert sum(len(r['status']['cases']) for r in state['runs'].values())==33 and all(pin(ROOT/n)==v for n,v in built['source_pins'].items()) and all(pin(ROOT/n)==v for n,v in pins.items());state.update(status='passed-33-complete-original-GSEApy-workflows-through-Linux-Java-CLI',artifact=built['artifact'],source_pins={Path(__file__).resolve().relative_to(ROOT).as_posix():pin(Path(__file__).resolve())},request_pins=pins,implementation_credit=False)
    except Exception as error:state['error']=repr(error);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''));return 0 if state['status'].startswith('passed-') else 2

if __name__=='__main__':raise SystemExit(main())
