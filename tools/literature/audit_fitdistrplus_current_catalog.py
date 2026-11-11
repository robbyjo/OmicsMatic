# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Audit every current primary source, role and exact prior-prefix derivation after fitdistrplus intake."""
import gzip,hashlib,json,re
import xml.etree.ElementTree as ET
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from collect_omics_review import paragraphs,validate_supporting_evidence
from collect_htseq_omics import tool_matches as htseq_matches
from catalog_fixed_cohort import verify_fixed_cohort
from fitdistrplus_catalog import load_catalog_registry,verify_review_extension


def main():
    data=ROOT/'docs/literature/omics-2023-2026';papers=json.loads((data/'papers.json').read_bytes());rows=load_catalog_registry(ROOT);registry={r['id']:r for r in rows};verify_fixed_cohort(ROOT,data,papers);assert len(registry)==len(rows)==428
    oldpapers=json.loads(gzip.decompress((data/'fitdistrplus-prior-papers.json.gz').read_bytes()));assert len(papers)==len(oldpapers)==250
    sources={};observations=0;aliases=[]
    for old,paper in zip(oldpapers,papers):
        assert {k:v for k,v in old.items() if k!='tools'}=={k:v for k,v in paper.items() if k!='tools'} and old['tools']==[t for t in paper['tools'] if t['tool_id']!='fitdistrplus']
        raw=gzip.decompress((ROOT/'build/literature-review/xml'/(paper['pmcid']+'.xml.gz')).read_bytes());assert hashlib.sha256(raw).hexdigest()==paper['xml_sha256'];root=ET.fromstring(raw);assert next(e.text for e in root.findall('front/article-meta/article-id') if e.get('pub-id-type')=='doi').lower()==paper['doi'].lower()
        ps=paragraphs(root);sources[paper['pmcid']]=ps;by={p['paragraph_index']:p for p in ps};cache=json.loads((ROOT/'build/literature-review/extracted'/(paper['pmcid']+'.json')).read_bytes());cached={t['tool_id']:t for t in cache['tools']};assert len({t['tool_id'] for t in paper['tools']})==len(paper['tools'])
        for tool in paper['tools']:
            anchor=by[tool['evidence_paragraph_index']];assert anchor['text']==cached[tool['tool_id']]['evidence_text'];assert hashlib.sha256(anchor['text'].encode('utf8')).hexdigest()==tool['evidence_paragraph_sha256'];assert tool['tool_id'] in registry and 'evidence_text' not in tool and tool['use_summary']
            if not re.search(registry[tool['tool_id']]['pattern'],anchor['match_text'],re.I):
                assert (paper['paper_id'],tool['tool_id'],anchor['paragraph_index'])==('P005','htseq',54);hits=htseq_matches([anchor],[registry['htseq']],{'htseq':tool['evidence_paragraph_sha256']});assert len(hits)==1 and hits[0]['matched_text']=='HT-seq';aliases.append('P005/htseq/54')
            observations+=1
    assert observations==2450 and aliases==['P005/htseq/54'];reviews=json.loads((ROOT/'tools/literature/reviewed-uses.json').read_bytes())['reviews'];verify_review_extension(ROOT,data,reviews);by_pmcid={p['pmcid']:p for p in papers}
    for review in reviews:
        paper=by_pmcid[review['pmcid']];tool=next(t for t in paper['tools'] if t['tool_id']==review['tool_id']);assert paper['doi'].lower()==review['doi'].lower()
        for key in ['evidence_paragraph_sha256','use_summary','implementation_scope_note','usage_context','version_reported_near_name','supporting_evidence']:
            if key in review:assert tool[key]==review[key]
        assert tool['review_status']=='usage-summary-reviewed; paper-wide-completeness-review-open';validate_supporting_evidence(sources[paper['pmcid']],[review],registry)
    result=dict(status='passed-250-current-primary-source-and-918-role-audit',source_records=250,source_XML_hashes_verified=250,fulltext_DOIs_verified=250,observations_verified=2450,reviewed_uses_verified=918,supporting_pointers_verified=309,prior_reviews_preserved=917,prior_observations_preserved=2449,unchanged_previously_admitted_aliases=aliases,papers_pin=pin(data/'papers.json'),reviews_pin=pin(ROOT/'tools/literature/reviewed-uses.json'),source_pins={p.relative_to(ROOT).as_posix():pin(p) for p in [Path(__file__).resolve(),ROOT/'tools/literature/collect_omics_review.py',ROOT/'tools/literature/collect_htseq_omics.py',ROOT/'tools/literature/fitdistrplus_catalog.py',ROOT/'tools/literature/fitdistrplus-named-software-registry.json',ROOT/'tools/literature/monocle-named-software-registry.json',ROOT/'tools/literature/tool-registry.json']},semantic_paper_wide_completeness_open=True,whole_goal_complete=False)
    save(data/'fitdistrplus-source-audit.json',result);print(json.dumps({k:v for k,v in result.items() if k!='source_pins'}))


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable primary source/role checks')
    main()
