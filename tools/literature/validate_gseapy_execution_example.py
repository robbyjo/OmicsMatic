# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Run the documented six-gene example through the real Linux Java workflow."""
import argparse,json,subprocess,sys,traceback
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent))
from setup_deseq2_reference import ROOT,pin,save


def linux(path):return '/mnt/d/projects/JLinAlg/'+path.relative_to(ROOT).as_posix()


def main():
    p=argparse.ArgumentParser(description=__doc__)
    for name in ['build','out']:p.add_argument('--'+name,type=Path,required=True)
    a=p.parse_args();out=a.out.resolve();assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/gseapy-runtime');out.mkdir(parents=True);state=dict(status='FAIL2_GSEAPY_GUIDE_EXAMPLE',whole_goal_complete=False)
    try:
        built=json.loads((a.build/'COMPLETED.json').read_bytes());jar=ROOT/built['artifact']['path'];assert pin(jar)=={k:built['artifact'][k] for k in ['bytes','sha256']};creator=ROOT/'tools/literature/create_gseapy_example.py';example=out/'example';command=[sys.executable,'-B',str(creator),'--runtime',linux(ROOT/'build/literature-review/gseapy-runtime/workflow-setup-1/COMPLETED.json'),'--out',str(example),'--path-style','wsl'];run=subprocess.run(command,capture_output=True,timeout=30);save(out/'prepare.process.json',dict(command=command,exit_code=run.returncode));(out/'prepare.stdout').write_bytes(run.stdout);(out/'prepare.stderr').write_bytes(run.stderr);assert run.returncode==0;before={p.relative_to(out).as_posix():pin(p) for p in example.glob('*.json')}
        command=['wsl','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/usr/bin/java','-Xmx2g','-cp',linux(jar),'org.jlinalg.cli.GseapyWorkflowCli','--request',linux(example/'request.json')]
        with (out/'analysis.stdout').open('xb') as first,(out/'analysis.stderr').open('xb') as second:run=subprocess.run(command,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=first,stderr=second,timeout=1500)
        save(out/'analysis.process.json',dict(command=command,exit_code=run.returncode));status=json.loads((example/'result/analysis-status.json').read_bytes());assert run.returncode==0 and status['result_admitted'] and status['status']=='PASS_ORIGINAL_GSEAPY_COMPLETE_BATCH';case=status['cases'][0];assert len(status['cases'])==1 and case['rows']==case['native_local_tests']==2 and case['one_sided_upper_tail'];assert all(pin(out/n)==v for n,v in before.items()) and all(pin(ROOT/n)==v for n,v in built['source_pins'].items());state.update(status='passed-exact-six-gene-local-Enrichr-guide-example',artifact=built['artifact'],input_pins=before,source_pins={p.relative_to(ROOT).as_posix():pin(p) for p in [creator,Path(__file__).resolve()]},two_positive_overlap_terms=True,zero_overlap_term_excluded_before_BH=True)
    except Exception as error:state['error']=repr(error);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''));return 0 if state['status'].startswith('passed-') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable example evidence checks')
    raise SystemExit(main())
