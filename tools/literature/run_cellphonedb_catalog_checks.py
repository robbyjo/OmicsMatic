# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Run CellPhoneDB native/execution evidence and all predecessor scientific gates; only current role context is extended."""
import argparse,contextlib,importlib,io
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from cellphonedb_catalog import current_catalog_review_context


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',required=True,type=Path);args=parser.parse_args();out=args.out.resolve();assert out.is_relative_to(ROOT/'build/literature-review/cellphonedb-runtime') and not out.exists();out.mkdir(parents=True);checks={};names=['soupx_markers_evidence','soupx_ambient_core_evidence','soupx_workflow_evidence','dndscv_workflow_evidence','smr_statistics_evidence','smr_workflow_evidence','loftee_audit_evidence','loftee_workflow_evidence','verify_cellphonedb_review_context','biomart_workflow_evidence','hail_sample_qc_evidence','hail_workflow_evidence','leafcutter_clusters_evidence','leafcutter_phenotypes_evidence','leafcutter_models_evidence','leafcutter_workflow_evidence','cellphonedb_statistics_evidence','cellphonedb_preparation_evidence','validate_cellphonedb_integration_evidence']
    with current_catalog_review_context():
        for name in names:
            module=importlib.import_module(name);captured=io.StringIO()
            with contextlib.redirect_stdout(captured):result=module.verify()
            (out/(name+'.stdout')).write_text(captured.getvalue(),encoding='utf8',newline='\n');checks[name]={k:result[k] for k in ['status','artifact','tests_per_JVM','native_tests_per_JVM','current_reviews','prior_reviews_preserved','original_gates','complete_independent_original_dataframes','public_cases','native_audit_cases_per_jvm'] if k in result};print(name+': passed',flush=True)
    save(out/'COMPLETED.json',dict(status='passed-nineteen-current-and-predecessor-scientific-gates',checks=checks,source_pins={f.relative_to(ROOT).as_posix():pin(f) for f in [Path(__file__).resolve(),ROOT/'tools/literature/cellphonedb_catalog.py',*[ROOT/'tools/literature'/(n+'.py') for n in names]]},papers_pin=pin(ROOT/'docs/literature/omics-2023-2026/papers.json'),reviews_pin=pin(ROOT/'tools/literature/reviewed-uses.json'),frozen_scientific_sources_or_verifiers_changed=False,whole_goal_complete=False))


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable scientific gates')
    main()
