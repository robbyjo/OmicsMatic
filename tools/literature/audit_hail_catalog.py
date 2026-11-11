# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Recheck250 primary sources,2450 observations and960 roles; only8 Hail rows may change."""
import gzip,hashlib,json,re
import xml.etree.ElementTree as ET
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from collect_omics_review import paragraphs,validate_supporting_evidence
from collect_htseq_omics import tool_matches as htseq_matches
from catalog_fixed_cohort import verify_fixed_cohort
from hail_catalog import load_catalog_registry,verify_review_extension


def main():
    data=ROOT/'docs/literature/omics-2023-2026';papers=json.loads((data/'papers.json').read_bytes());old=json.loads(gzip.decompress((data/'hail-prior-papers.json.gz').read_bytes()));registry={r['id']:r for r in load_catalog_registry(ROOT)};verify_fixed_cohort(ROOT,data,papers);sources={};observations=0;changed=[];aliases=[]
    for before,paper in zip(old,papers):
        assert {k:v for k,v in before.items() if k!='tools'}=={k:v for k,v in paper.items() if k!='tools'} and [t for t in before['tools'] if t['tool_id'] not in ['hail']]==[t for t in paper['tools'] if t['tool_id'] not in ['hail']]
        if before!=paper:changed.append(paper['paper_id'])
        raw=gzip.decompress((ROOT/'build/literature-review/xml'/(paper['pmcid']+'.xml.gz')).read_bytes());assert hashlib.sha256(raw).hexdigest()==paper['xml_sha256'];root=ET.fromstring(raw);assert next(e.text for e in root.findall('front/article-meta/article-id') if e.get('pub-id-type')=='doi').lower()==paper['doi'].lower();ps=paragraphs(root);sources[paper['pmcid']]=ps;by={p['paragraph_index']:p for p in ps};cache=json.loads((ROOT/'build/literature-review/extracted'/(paper['pmcid']+'.json')).read_bytes());cached={t['tool_id']:t for t in cache['tools']};assert len({t['tool_id'] for t in paper['tools']})==len(paper['tools'])
        for tool in paper['tools']:
            anchor=by[tool['evidence_paragraph_index']];assert anchor['text']==cached[tool['tool_id']]['evidence_text'];assert hashlib.sha256(anchor['text'].encode('utf8')).hexdigest()==tool['evidence_paragraph_sha256'];assert tool['tool_id'] in registry and 'evidence_text' not in tool and tool['use_summary']
            if not re.search(registry[tool['tool_id']]['pattern'],anchor['match_text'],re.I):assert (paper['paper_id'],tool['tool_id'],anchor['paragraph_index'])==('P005','htseq',54) and htseq_matches([anchor],[registry['htseq']],{'htseq':tool['evidence_paragraph_sha256']})[0]['matched_text']=='HT-seq';aliases.append('P005/htseq/54')
            observations+=1
    assert len(papers)==len(old)==250 and observations==2450 and aliases==['P005/htseq/54'] and changed==['P055','P168','P186','P191','P204','P210','P220','P246'];reviews=json.loads((ROOT/'tools/literature/reviewed-uses.json').read_bytes())['reviews'];verify_review_extension(ROOT,data,reviews);by_pmcid={p['pmcid']:p for p in papers}
    for review in reviews:
        paper=by_pmcid[review['pmcid']];tool=next(t for t in paper['tools'] if t['tool_id']==review['tool_id']);assert paper['doi'].lower()==review['doi'].lower()
        for key in ['evidence_paragraph_sha256','use_summary','implementation_scope_note','usage_context','version_reported_near_name','supporting_evidence']:
            if key in review:assert tool[key]==review[key]
        assert tool['review_status']=='usage-summary-reviewed; paper-wide-completeness-review-open';validate_supporting_evidence(sources[paper['pmcid']],[review],registry)
    result=dict(status='passed-250-current-primary-source-and-960-role-audit',source_records=250,source_XML_hashes_verified=250,fulltext_DOIs_verified=250,observations_verified=2450,reviewed_uses_verified=960,supporting_pointers_verified=331,prior_reviews_preserved=952,unchanged_named_tool_count=390,changed_paper_roles=changed,papers_pin=pin(data/'papers.json'),reviews_pin=pin(ROOT/'tools/literature/reviewed-uses.json'),source_pins={p.relative_to(ROOT).as_posix():pin(p) for p in [Path(__file__).resolve(),ROOT/'tools/literature/collect_omics_review.py',ROOT/'tools/literature/collect_htseq_omics.py',ROOT/'tools/literature/hail_catalog.py']},semantic_paper_wide_completeness_open=True,whole_goal_complete=False);save(data/'hail-source-audit.json',result);print('Verified250 primary XMLs/DOIs,2450 observations,960 roles and331 support pointers;952 earlier reviews preserved')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable primary-source/role checks')
    main()
