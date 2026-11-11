# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Recheck250 original XMLs/DOIs,2457 observations and980 roles; preserve all966 predecessors and all unrelated rows."""
import gzip,hashlib,json,re
from pathlib import Path
import xml.etree.ElementTree as ET
from setup_deseq2_reference import ROOT,pin,save
from collect_omics_review import paragraphs,tool_matches,validate_supporting_evidence
from collect_htseq_omics import tool_matches as htseq_matches
from catalog_fixed_cohort import verify_fixed_cohort
from cellphonedb_catalog import load_catalog_registry,verify_review_extension


def main():
    data=ROOT/'docs/literature/omics-2023-2026';papers=json.loads((data/'papers.json').read_bytes());old=json.loads(gzip.decompress((data/'cellphonedb-prior-papers.json.gz').read_bytes()));registry={r['id']:r for r in load_catalog_registry(ROOT)};extra=json.loads((ROOT/'tools/literature/cellphonedb-named-software-registry.json').read_bytes());extra_ids={r['id'] for r in extra};affected=extra_ids|{'cellphonedb'};verify_fixed_cohort(ROOT,data,papers);sources={};observations=0;changed=[];aliases=[];new_matches=[]
    for before,paper in zip(old,papers):
        assert {k:v for k,v in before.items() if k!='tools'}=={k:v for k,v in paper.items() if k!='tools'} and [t for t in before['tools'] if t['tool_id'] not in affected]==[t for t in paper['tools'] if t['tool_id'] not in affected]
        if before!=paper:changed.append(paper['paper_id'])
        raw=gzip.decompress((ROOT/'build/literature-review/xml'/(paper['pmcid']+'.xml.gz')).read_bytes());assert hashlib.sha256(raw).hexdigest()==paper['xml_sha256'];xml=ET.fromstring(raw);assert next(e.text for e in xml.findall('front/article-meta/article-id') if e.get('pub-id-type')=='doi').lower()==paper['doi'].lower();ps=paragraphs(xml);sources[paper['pmcid']]=ps;by={p['paragraph_index']:p for p in ps};cache=json.loads((ROOT/'build/literature-review/extracted'/(paper['pmcid']+'.json')).read_bytes());cached={t['tool_id']:t for t in cache['tools']};assert len({t['tool_id'] for t in paper['tools']})==len(paper['tools']);matches=tool_matches(ps,extra,{});new_matches.extend((paper['paper_id'],m['tool_id']) for m in matches);assert {m['tool_id'] for m in matches}=={t['tool_id'] for t in paper['tools'] if t['tool_id'] in extra_ids}
        for tool in paper['tools']:
            anchor=by[tool['evidence_paragraph_index']];assert anchor['text']==cached[tool['tool_id']]['evidence_text'];assert hashlib.sha256(anchor['text'].encode()).hexdigest()==tool['evidence_paragraph_sha256'];assert tool['tool_id'] in registry and 'evidence_text' not in tool and tool['use_summary']
            if not re.search(registry[tool['tool_id']]['pattern'],anchor['match_text'],re.I):assert (paper['paper_id'],tool['tool_id'],anchor['paragraph_index'])==('P005','htseq',54) and htseq_matches([anchor],[registry['htseq']],{'htseq':tool['evidence_paragraph_sha256']})[0]['matched_text']=='HT-seq';aliases.append('P005/htseq/54')
            observations+=1
    assert len(papers)==len(old)==250 and observations==2457 and len(new_matches)==7 and aliases==['P005/htseq/54'] and changed==['P008','P010','P070','P094','P100','P160','P161','P167','P169'];reviews=json.loads((ROOT/'tools/literature/reviewed-uses.json').read_bytes())['reviews'];verify_review_extension(ROOT,data,reviews);by_pmcid={p['pmcid']:p for p in papers}
    for review in reviews:
        paper=by_pmcid[review['pmcid']];tool=next(t for t in paper['tools'] if t['tool_id']==review['tool_id']);assert paper['doi'].lower()==review['doi'].lower()
        for key in ['evidence_paragraph_sha256','use_summary','implementation_scope_note','usage_context','version_reported_near_name','supporting_evidence']:
            if key in review:assert tool[key]==review[key]
        assert tool['review_status']=='usage-summary-reviewed; paper-wide-completeness-review-open';validate_supporting_evidence(sources[paper['pmcid']],[review],registry)
    followups=json.loads((ROOT/'tools/literature/cellphonedb-related-software-followups.json').read_bytes());assert followups['status']=='primary-named-software-followups-open' and len(followups['candidates'])==5
    for candidate in followups['candidates']:
        p=by_pmcid[candidate['pmcid']];anchor=next(r for r in sources[candidate['pmcid']] if r['paragraph_index']==candidate['paragraph_index']);assert candidate['xml_sha256']==p['xml_sha256'] and candidate['paragraph_sha256']==hashlib.sha256(anchor['text'].encode()).hexdigest() and candidate['name'].lower() in anchor['text'].lower() and not candidate['registry_applied'] and not candidate['implementation_credit']
    result=dict(status='passed-250-current-primary-source-and-980-role-audit',source_records=250,source_XML_hashes_verified=250,fulltext_DOIs_verified=250,observations_verified=2457,reviewed_uses_verified=980,supporting_pointers_verified=340,prior_reviews_preserved=966,new_named_observations=7,cataloged_named_tool_count=395,registry_definitions=433,changed_paper_roles=changed,papers_pin=pin(data/'papers.json'),reviews_pin=pin(ROOT/'tools/literature/reviewed-uses.json'),source_pins={p.relative_to(ROOT).as_posix():pin(p) for p in [Path(__file__).resolve(),ROOT/'tools/literature/collect_omics_review.py',ROOT/'tools/literature/collect_htseq_omics.py',ROOT/'tools/literature/cellphonedb_catalog.py',ROOT/'tools/literature/cellphonedb-related-software-followups.json']},semantic_paper_wide_completeness_open=True,named_software_followups_open=5,whole_goal_complete=False);save(data/'cellphonedb-source-audit.json',result);print('Verified250 primary XML/DOIs,2457 observations,980 roles and340 supporting pointers;966 earlier reviews preserved; five named followups remain open')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable primary-source/role checks')
    main()
