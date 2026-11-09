# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Retain a genuinely passed299-check general capture after a failed focused assertion."""
import argparse,json,shutil
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args();base=ROOT/'build/literature-review/cistarget-runtime';out=a.out.resolve();capture=base/'catalog-ui-1'
    assert not out.exists() and out.is_relative_to(base);out.mkdir(parents=True)
    report=json.loads((capture/'general/ui-validation.json').read_bytes());assert report['status']=='passed' and len(report['checks'])==299
    assert json.loads((capture/'general.process.json').read_bytes())['exit_code']==0 and json.loads((capture/'cistarget.process.json').read_bytes())['exit_code']!=0
    names=['tools/literature/validate_cistarget_catalog_ui.py','tools/literature/check_cistarget_general_review_ui.cjs','site/literature-review.html','site/literature-warnings.html']
    source_pins={name:pin(capture/'source-snapshot'/name) for name in names};assert all(pin(ROOT/name)==v for name,v in source_pins.items())
    render=json.loads((base/'catalog-render-3/COMPLETED.json').read_bytes());roles=json.loads((base/'catalog-roles-applied-1/COMPLETED.json').read_bytes())
    data=ROOT/'docs/literature/omics-2023-2026';inputs=[data/'papers.json',data/'tool-inventory.json',data/'summary.json'];input_pins={p.relative_to(ROOT).as_posix():pin(p) for p in inputs}
    for path in inputs[1:]:assert input_pins[path.relative_to(ROOT).as_posix()]==render['rendered_files'][path.relative_to(ROOT).as_posix()]
    assert input_pins[inputs[0].relative_to(ROOT).as_posix()]['sha256']==roles['papers_sha256']
    # The complete rendered pages and the pre-capture data producers remain byte-identical.
    shutil.copytree(capture/'general',out/'general')
    save(out/'GENERAL-COMPLETED.json',dict(status='passed-general-only-from-retained-failed-focused-run',focused_run_passed=False,checks=299,
        page=pin(ROOT/'site/literature-review.html'),warning_page=pin(ROOT/'site/literature-warnings.html'),general=pin(out/'general/ui-validation.json'),
        source_pins=source_pins,input_pins=input_pins,capture_process=pin(capture/'general.process.json'),
        source_capture=capture.relative_to(ROOT).as_posix(),input_producers={p.relative_to(ROOT).as_posix():pin(p) for p in [base/'catalog-render-3/COMPLETED.json',base/'catalog-roles-applied-1/COMPLETED.json']},
        certificate_producer=pin(Path(__file__)),whole_goal_complete=False));print('Retained299 actual passed general checks; focused failure remains preserved; pages/data unchanged')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable passed-capture and identical-input checks')
    main()
