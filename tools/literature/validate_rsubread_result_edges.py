# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Validate eighteen fresh original result controls on three JVMs and compare all SAM/BAM output fields in R."""
import argparse,json,os,subprocess,traceback
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save,environment
from create_rsubread_result_edges import run


def linux(path):return '/mnt/d/projects/JLinAlg/'+path.relative_to(ROOT).as_posix()


def main():
    p=argparse.ArgumentParser(description=__doc__)
    for name in ['build','controls','out']:p.add_argument('--'+name,type=Path,required=True)
    a=p.parse_args();out=a.out.resolve();assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/rsubread-runtime');out.mkdir(parents=True)
    state=dict(status='FAIL2_RSUBREAD_FRESH_RESULT_EDGE_REPLAY',analysis_admitted=False,implementation_credit=False,full_adapter_available=False,whole_goal_complete=False,runs={})
    try:
        build=json.loads((a.build/'COMPLETED.json').read_bytes());assert build['status']=='passed-JDK-only-native-Rsubread-result-value-API-build';jar=ROOT/build['artifact']['path'];assert pin(jar)=={k:build['artifact'][k] for k in ['bytes','sha256']}
        controls=a.controls.resolve();proof=json.loads((controls/'COMPLETED.json').read_bytes());assert proof['status']=='passed-eighteen-fresh-original-Rsubread-result-edge-and-BAM-controls-only';before={q.relative_to(controls).as_posix():pin(q) for q in controls.rglob('*') if q.is_file()}
        source=ROOT/'tools/literature/java/org/jlinalg/cli/RsubreadResultEdgeAudit.java';script=ROOT/'tools/literature/compare_rsubread_bam_controls.R';jdk=Path('C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin');classes=out/'classes';classes.mkdir()
        run([str(jdk/'javac.exe'),'--release','17','-Xlint:all','-Werror','-g','-encoding','UTF-8','-cp',str(jar),'-d',str(classes),str(source)],out,'compile')
        for name in ['2260','2232-R441']:
            state['runs'][name]={}
            for label,prefix in [('windows21',[str(jdk/'java.exe')]),('windows25',['C:/Program Files/Common Files/Oracle/Java/javapath/java.exe']),('linux21',['wsl','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/usr/bin/java'])]:
                islinux=label=='linux21';home=out/(name+'-'+label);classpath=linux(classes)+':'+linux(jar) if islinux else str(classes)+os.pathsep+str(jar)
                run(prefix+['-Xmx128m','-cp',classpath,'org.jlinalg.cli.RsubreadResultEdgeAudit',linux(controls/name) if islinux else str(controls/name),linux(home) if islinux else str(home)],out,name+'-'+label)
                report=json.loads((home/'audit.json').read_bytes());assert report['status']=='passed-nine-fresh-original-Rsubread-result-edge-controls-only' and report['original_results']==9 and report['contract_rejections']==4;state['runs'][name][label]=report
            base=ROOT/'build/literature-review/rsubread-runtime';rscript=Path('C:/Program Files/R/R-4.6.1/bin/Rscript.exe') if name=='2260' else base/'r441-setup-1/R441/root/bin/x64/Rscript.exe';original=base/('original-controls-3' if name=='2260' else 'original2232-controls-1')/'original';env=environment()
            if name=='2232-R441':env['R_HOME']=rscript.parents[2].as_posix()
            home=out/(name+'-SAM-BAM');home.mkdir();run([str(rscript),'--vanilla',str(script),str(controls/name),str(original),str(home)],out,name+'-SAM-BAM',env)
            assert (home/'R-status.txt').read_text().strip()=='PASS4_COMPLETE_ORIGINAL_SAM_BAM_COUNT_ANNOTATION_SUMMARY_JUNCTION_COMPARISONS_WITH_EXPLICIT_TARGET_LEDGER'
        assert all(pin(controls/n)==v for n,v in before.items()) and all(pin(ROOT/n)==v for n,v in build['source_pins'].items())
        state.update(status='passed-eighteen-fresh-original-Rsubread-results-on-three-JVMs-and-eight-complete-SAM-BAM-comparisons',artifact=build['artifact'],
                     controls_receipt=dict(path=(controls/'COMPLETED.json').relative_to(ROOT).as_posix(),**pin(controls/'COMPLETED.json')),
                     source_pins={q.relative_to(ROOT).as_posix():pin(q) for q in [Path(__file__).resolve(),source,script,ROOT/'tools/literature/create_rsubread_result_edges.py']},original_evidence_unchanged=True,independent_read_counting=False,production_process_supervision=False)
    except Exception as error:state['error']=repr(error);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''));return 0 if state['status'].startswith('passed-') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete original/reference/edge-case gates')
    raise SystemExit(main())
