# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Run the new Hail gate plus all unchanged preceding scientific gates; explicit archived952/current960 metadata relationship only."""
import argparse,contextlib,importlib,io
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from hail_catalog import current_catalog_review_context

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',required=True,type=Path);a=p.parse_args();out=a.out.resolve();assert not out.exists();out.mkdir(parents=True);checks={};names=['soupx_markers_evidence','soupx_ambient_core_evidence','soupx_workflow_evidence','dndscv_workflow_evidence','smr_statistics_evidence','smr_workflow_evidence','loftee_audit_evidence','loftee_workflow_evidence','verify_hail_review_context','biomart_workflow_evidence','hail_sample_qc_evidence','hail_workflow_evidence']
    with current_catalog_review_context():
        for name in names:
            module=importlib.import_module(name);captured=io.StringIO()
            with contextlib.redirect_stdout(captured):result=module.verify()
            (out/(name+'.stdout')).write_text(captured.getvalue(),encoding='utf8',newline='\n');checks[name]={k:result[k] for k in ['status','artifact','tests_per_JVM','native_tests_per_JVM','current_reviews','prior_reviews_preserved','original_gates','complete_independent_original_dataframes'] if k in result};print(name+': passed',flush=True)
    save(out/'COMPLETED.json',dict(status='passed-Hail-and-all-eleven-inherited-scientific-component-gates',checks=checks,source_pins={f.relative_to(ROOT).as_posix():pin(f) for f in [Path(__file__).resolve(),ROOT/'tools/literature/hail_catalog.py',*[ROOT/'tools/literature'/(n+'.py') for n in names]]},papers_pin=pin(ROOT/'docs/literature/omics-2023-2026/papers.json'),reviews_pin=pin(ROOT/'tools/literature/reviewed-uses.json'),frozen_scientific_sources_or_verifiers_changed=False,whole_goal_complete=False))

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable scientific checks')
    main()
