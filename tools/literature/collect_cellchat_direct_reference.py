# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Run a separately written fixed original-package recipe for the eight control fits."""
import argparse,json,subprocess,traceback
from pathlib import Path
from setup_deseq2_reference import ROOT,RSCRIPT,environment,pin,save

BASE=ROOT/'build/literature-review/cellchat-runtime'

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve();assert not out.exists() and out.is_relative_to(BASE);out.mkdir(parents=True)
    libraries=[BASE/'presto-reference-setup-2/R-lib',BASE/'reference-setup-1/R-lib',*[Path(p) for p in json.loads((BASE/'reference-setup-1/STARTED.json').read_bytes())['libraries']]]
    script=ROOT/'tools/literature/cellchat_direct_reference.R';source=BASE/'source-intake-1/2.2.0-2025-release/source';inputs=BASE/'original-workflow-controls-1/inputs'
    pins={p.relative_to(ROOT).as_posix():pin(p) for p in [Path(__file__).resolve(),script,inputs/'expression.tsv',inputs/'metadata.tsv']};state=dict(status='FAIL2_DIRECT_CELLCHAT_REFERENCE',analysis_admitted=False,implementation_credit=False,whole_goal_complete=False)
    try:
        for file in [Path(__file__).resolve(),script]:
            target=out/'source-snapshot'/file.relative_to(ROOT);target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(file.read_bytes())
        command=[str(RSCRIPT),'--vanilla',str(out/'source-snapshot/tools/literature/cellchat_direct_reference.R'),str(inputs),str(source),str(out/'reference'),*map(str,libraries)]
        with (out/'original.stdout').open('xb') as stdout,(out/'original.stderr').open('xb') as stderr:run=subprocess.run(command,cwd=ROOT,env=environment(),stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=600)
        save(out/'process.json',dict(command=command,exit_code=run.returncode));assert run.returncode==0,'Read original stderr'
        receipt=json.loads((out/'reference/COMPLETED.json').read_bytes());assert receipt['status']=='PASS_EIGHT_DIRECT_ORIGINAL_CELLCHAT_PACKAGE_REFITS_ONLY' and len(receipt['cases'])==8
        assert all(pin(ROOT/n)==v for n,v in pins.items());state.update(status='PASS_EIGHT_SEPARATE_DIRECT_CELLCHAT_PACKAGE_REFITS_ONLY',source_pins=pins,receipt=pin(out/'reference/COMPLETED.json'))
    except Exception as error:state['error']=repr(error);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''),flush=True);return 0 if state['status'].startswith('PASS_') else 2

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable source/reference comparisons')
    raise SystemExit(main())
