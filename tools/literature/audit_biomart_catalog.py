# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Rerun the unchanged250-primary-source/952-role audit into a NEW evidence path; preserve all existing reviews and frozen audit bytes."""
import argparse,contextlib,io,json
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from biomart_catalog import current_catalog_review_context

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',required=True,type=Path);a=p.parse_args();out=a.out.resolve();assert not out.exists();out.mkdir(parents=True);data=ROOT/'docs/literature/omics-2023-2026';names=['papers.json','loftee-source-audit.json','loftee-role-coverage.json'];before={n:pin(data/n) for n in names};reviews_pin=pin(ROOT/'tools/literature/reviewed-uses.json');import audit_loftee_catalog as original
    original_save=original.save;captured=[]
    def private_sink(destination,value):
        assert destination==data/'loftee-source-audit.json' and value['status']=='passed-250-current-primary-source-and-952-role-audit';captured.append(value);save(out/'primary-source-audit.json',value)
    log=io.StringIO()
    try:
        original.save=private_sink
        with current_catalog_review_context(),contextlib.redirect_stdout(log):original.main()
    finally:original.save=original_save
    (out/'audit.stdout').write_text(log.getvalue(),encoding='utf8',newline='\n');assert len(captured)==1 and captured[0]==json.loads((data/'loftee-source-audit.json').read_bytes());assert all(pin(data/n)==v for n,v in before.items()) and pin(ROOT/'tools/literature/reviewed-uses.json')==reviews_pin;papers=json.loads((data/'papers.json').read_bytes());reviews=json.loads((ROOT/'tools/literature/reviewed-uses.json').read_bytes())['reviews'];roles=[]
    for paper in papers:
        observations=[t for t in paper['tools'] if t['tool_id']=='biomart']
        if not observations:continue
        assert len(observations)==1;row=observations[0];review=[r for r in reviews if r['pmcid']==paper['pmcid'] and r['tool_id']=='biomart'];assert len(review)==1 and row['review_status']=='usage-summary-reviewed; paper-wide-completeness-review-open';roles.append(dict(paper_id=paper['paper_id'],pmcid=paper['pmcid'],doi=paper['doi'],evidence_paragraph_index=row['evidence_paragraph_index'],evidence_paragraph_sha256=row['evidence_paragraph_sha256'],use_summary=row['use_summary'],implementation_scope_note=row.get('implementation_scope_note',''),version_reported_near_name=row.get('version_reported_near_name'),supporting_evidence=row.get('supporting_evidence',[])))
    assert [r['paper_id'] for r in roles]==['P049','P117','P134','P162','P223','P238','P250'];coverage=dict(schema_version=1,status='passed-seven-existing-biomaRt-primary-role-reviews',roles=roles,new_reviews_added=0,reviewed_uses_preserved=952,observations_preserved=2450,supporting_pointers_preserved=328,papers_pin=pin(data/'papers.json'),reviews_pin=reviews_pin,primary_audit=pin(out/'primary-source-audit.json'),semantic_paper_wide_completeness_open=True,whole_goal_complete=False);assert not (data/'biomart-source-coverage.json').exists();save(data/'biomart-source-coverage.json',coverage);save(out/'COMPLETED.json',dict(status='passed-unchanged-250-source-952-role-audit-and-seven-existing-biomaRt-roles',coverage=pin(data/'biomart-source-coverage.json'),source_pins={f.relative_to(ROOT).as_posix():pin(f) for f in [Path(__file__).resolve(),ROOT/'tools/literature/audit_loftee_catalog.py',ROOT/'tools/literature/biomart_catalog.py']},frozen_audit_bytes_preserved=True,papers_unchanged=before['papers.json'],reviews_unchanged=reviews_pin,whole_goal_complete=False));print('Rechecked250 primary XMLs/DOIs,2450 observations,952 reviews and7 existing biomaRt roles; no review or frozen audit changed')

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable primary-source/role preservation checks')
    main()
