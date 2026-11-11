# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Run the actual standalone Java CellChat controller and retained-bundle verifier."""
import argparse,json,subprocess,traceback
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save

BASE=ROOT/'build/literature-review/cellchat-runtime'
JAVAS={'windows21':'C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/java.exe','windows25':'C:/Program Files/Common Files/Oracle/Java/javapath/java.exe'}

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);p.add_argument('--build',default='workflow-build21-5');p.add_argument('--platform',choices=list(JAVAS),default='windows21');a=p.parse_args();out=a.out.resolve()
    assert not out.exists() and out.is_relative_to(BASE);out.mkdir(parents=True);state=dict(status='FAIL2_CELLCHAT_TYPED_CONTROLS',implementation_credit=False,whole_goal_complete=False)
    try:
        built=json.loads((BASE/a.build/'COMPLETED.json').read_bytes());assert built['status']=='PASS_ISOLATED_CELLCHAT_TYPED_WORKFLOW_BUILD_ONLY'
        jar=ROOT/built['artifact']['path'];assert pin(jar)=={k:built['artifact'][k] for k in ['bytes','sha256']}
        for name,wanted in built['source_pins'].items():assert pin(ROOT/name)==wanted
        profile=BASE/'runtime-profile-1/runtime-profile.json';ready=json.loads((profile.parent/'COMPLETED.json').read_bytes());assert pin(profile)==ready['profile']
        analysis=json.loads((BASE/'original-workflow-controls-1/inputs/request.json').read_bytes())
        request=dict(schema_version=1,out=str(out/'analysis'),runtime_profile=str(profile),analysis=analysis,limits=dict(input_bytes=268435456,output_bytes=1073741824,wall_seconds=1800,max_genes=100000,max_cells=100000,max_files=200000,runtime_bytes=34359738368))
        helper=Path(__file__).resolve();snapshot=out/'source-snapshot'/helper.relative_to(ROOT);snapshot.parent.mkdir(parents=True);snapshot.write_bytes(helper.read_bytes())
        save(out/'request.json',request);save(out/'STARTED.json',dict(build=built,platform=a.platform,harness=pin(helper),request=pin(out/'request.json'),runtime_profile=pin(profile),analysis_admitted=False))
        def run(name,arguments):
            command=[JAVAS[a.platform],'-Xmx512m','-jar',str(jar),*arguments]
            with (out/(name+'.stdout')).open('xb') as stdout,(out/(name+'.stderr')).open('xb') as stderr:
                process=subprocess.run(command,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=2400)
            save(out/(name+'-process.json'),dict(command=command,exit_code=process.returncode));return process.returncode
        assert run('analysis',['--request',str(out/'request.json')])==0,'Read analysis.stderr and retained analysis/worker reports'
        completion=json.loads((out/'analysis/COMPLETED.json').read_bytes());assert completion['result_admitted'] is True
        assert run('verify',['--verify',str(out/'analysis')])==0,'Read verify.stderr'
        assert helper.read_bytes()==snapshot.read_bytes() and all(pin(ROOT/n)==v for n,v in built['source_pins'].items())
        state.update(status='PASS_ACTUAL_JAR_ORIGINAL_CELLCHAT_TWO_CONDITION_SIX_SAMPLE_WORKFLOW_ONLY',platform=a.platform,harness=pin(helper),artifact=pin(jar),analysis_receipt=pin(out/'analysis/COMPLETED.json'),native_provenance=pin(out/'analysis/provenance.json'),source_pins=built['source_pins'],retained_original_centrality_fallbacks=completion['original_centrality_fallbacks'],scope='One original Windows R4.6.1 CellChat2.2.0/Presto1.0.0 expression-to-network workflow; no other source editions, spatial model, full catalog credit or paper reproduction')
    except Exception as error:state['error']=repr(error);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''),flush=True);return 0 if state['status'].startswith('PASS_') else 2

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable compiled artifact/source checks')
    raise SystemExit(main())
