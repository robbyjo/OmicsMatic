# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Run six unchanged GPL source candidates, without claiming package installation."""
import argparse,json,subprocess,traceback
from pathlib import Path
from setup_deseq2_reference import ROOT,RSCRIPT,BASES,environment,pin,save
from intake_cellchat_sources import SELECTED

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);p.add_argument('--cases',type=Path,required=True);a=p.parse_args()
    out=a.out.resolve();base=ROOT/'build/literature-review/cellchat-runtime';assert not out.exists() and out.is_relative_to(base);out.mkdir(parents=True)
    state=dict(status='SKIP3_CELLCHAT_ORIGINAL_R_REFERENCE_UNAVAILABLE',analysis_admitted=False,implementation_credit=False,whole_goal_complete=False)
    try:
        intake=base/'source-intake-1';manifest=json.loads((intake/'COMPLETED.json').read_bytes());assert manifest['status']=='PASS_SIX_IMMUTABLE_GPL_CELLCHAT_SOURCE_CANDIDATES_ONLY'
        cases=a.cases.resolve();assert cases.is_relative_to(base) and cases.is_file();records=[]
        script=ROOT/'tools/literature/cellchat_reference_probe.R';source_pins={script.relative_to(ROOT).as_posix():pin(script),Path(__file__).resolve().relative_to(ROOT).as_posix():pin(Path(__file__))}
        for label,repository,commit in SELECTED:
            source=intake/label/'source';member_file=intake/label/'original-members.json';entry=next(r for r in manifest['candidates'] if r['label']==label);assert pin(member_file)==entry['members']
            members=json.loads(member_file.read_bytes())
            for name,wanted in members.items():assert pin(source/name)==wanted,(label,name)
            home=out/label;command=[str(RSCRIPT),'--vanilla',str(script),str(cases),str(source),label,str(home),*map(str,BASES)]
            with (out/(label+'.stdout')).open('xb') as stdout,(out/(label+'.stderr')).open('xb') as stderr:
                run=subprocess.run(command,cwd=ROOT,env=environment(),stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=600)
            save(out/(label+'-process.json'),dict(command=command,exit_code=run.returncode))
            assert run.returncode==0,'Original '+label+' reference failed; inspect retained stdout/stderr'
            report=json.loads((home/'COMPLETED.json').read_bytes());assert report['status']=='PASS_ORIGINAL_CELLCHAT_RNA_FUNCTION_BOUNDARY_ONLY'
            for name,wanted in members.items():assert pin(source/name)==wanted,(label,name)
            records.append(dict(label=label,repository=repository,commit=commit,cases=len(report['cases']),receipt=pin(home/'COMPLETED.json'),original_source_unmodified=True))
            print('PASS original CellChat '+label+' cases='+str(len(report['cases'])),flush=True)
        assert all(pin(ROOT/name)==wanted for name,wanted in source_pins.items())
        state.update(status='PASS_SIX_UNCHANGED_CELLCHAT_RNA_FUNCTION_REFERENCES_ONLY',references=records,cases=pin(cases),source_pins=source_pins,original_package_installed=False)
    except Exception as error:
        state['error']=repr(error);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state)
    (out/'WARNINGS.md').write_text('# '+state['status']+'\n\n'+state.get('error','Unchanged original S4/modeling definitions ran on prepared RNA input in R4.6.1; no CellChat package installation, full workflow or historical paper runtime is established.')+'\n\nRequired action: when a named runtime or dependency is unavailable or forbidden, skip dependent analysis. Where permitted install it in a NEW private library and rerun to NEW output paths. Preserve this attempt. Do not substitute another source edition or report prepared-input checks as a paper reproduction.\n',encoding='utf8')
    print(state['status'],state.get('error',''),flush=True);return 0 if state['status'].startswith('PASS_') else 3

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable original source integrity checks')
    raise SystemExit(main())
