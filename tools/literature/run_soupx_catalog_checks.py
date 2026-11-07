# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Record strict SoupX marker/core/workflow and unchanged original scientific catalog gates."""
import argparse,contextlib,importlib,io,json
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(ROOT/'build/literature-review/soupx-runtime') and not out.exists();out.mkdir();checks={}
    for name in ['soupx_markers_evidence','soupx_ambient_core_evidence','soupx_workflow_evidence','verify_soupx_review_context']:
        module=importlib.import_module(name);captured=io.StringIO()
        with contextlib.redirect_stdout(captured):result=module.verify()
        (out/(name+'.stdout')).write_text(captured.getvalue(),encoding='utf8',newline='\n');checks[name]={k:result[k] for k in ['status','artifact','tests_per_JVM','current_reviews','prior_reviews_preserved','original_gates'] if k in result};print(name+': passed',flush=True)
    save(out/'COMPLETED.json',dict(status='passed-SoupX-and-frozen-scientific-current-gates',checks=checks,source_pins={p.relative_to(ROOT).as_posix():pin(p) for p in [Path(__file__).resolve(),*[ROOT/'tools/literature'/(n+'.py') for n in checks]]},papers_pin=pin(ROOT/'docs/literature/omics-2023-2026/papers.json'),reviews_pin=pin(ROOT/'tools/literature/reviewed-uses.json'),whole_goal_complete=False))


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable scientific/source gates')
    main()
