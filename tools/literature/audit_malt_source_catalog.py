# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Audit all250 current primary sources plus separately typed DOCX evidence."""
from pathlib import Path
import argparse,json,re,traceback
from setup_deseq2_reference import ROOT,pin,save
from malt_primary_sources import primary_paragraphs,supplement,pointer,sha,PMCID
from malt_source_registry import ENTRIES,IDS
from malt_source_catalog import verify_extension,load_catalog_registry
from collect_omics_review import validate_supporting_evidence
from collect_htseq_omics import tool_matches
from catalog_fixed_cohort import verify_fixed_cohort


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',required=True,type=Path);out=p.parse_args().out.resolve()
    assert out.is_relative_to(ROOT/'build/literature-review/nfcore-runtime') and not out.exists();out.mkdir(parents=True)
    state=dict(status='FAIL2_MALT_FAMILY_CURRENT_SOURCE_AUDIT',whole_goal_complete=False)
    try:
        data=ROOT/'docs/literature/omics-2023-2026';proof=verify_extension(ROOT);papers=json.loads((data/'papers.json').read_bytes());reviews=json.loads((ROOT/'tools/literature/reviewed-uses.json').read_bytes())['reviews'];verify_fixed_cohort(ROOT,data,papers)
        assert len(papers)==len({p['doi'].lower() for p in papers})==len({p['pmcid'] for p in papers})==250
        registry={r['id']:r for r in load_catalog_registry(ROOT)};assert len(registry)==443 and len(reviews)==1052;sp=supplement();sources={};observations=0;main=0;supp=0;census=[];aliases=[];prior_census=[]
        for number,paper in enumerate(papers,1):
            assert paper['paper_id']==f'P{number:03d}' and '2023-10-23'<=paper['first_publication_date']<='2026-10-23' and paper['journal_abbreviation'] in {'Nature','Nat Genet','Am J Hum Genet'}
            ps=primary_paragraphs(paper);sources[paper['pmcid']]=ps;by={r['paragraph_index']:r for r in ps};cached={r['tool_id']:r for r in json.loads((ROOT/'build/literature-review/extracted'/(paper['pmcid']+'.json')).read_bytes())['tools']}
            assert len({t['tool_id'] for t in paper['tools']})==len(paper['tools'])
            selected={t['tool_id']:t for t in paper['tools'] if t['tool_id'] in {'snprelate','genesis','mtag','scdrs'}}
            fresh={t['tool_id']:t for t in tool_matches(ps,[registry[k] for k in ['snprelate','genesis','mtag','scdrs']],{k:v['evidence_paragraph_sha256'] for k,v in selected.items()})};assert set(fresh)==set(selected)
            prior_census.extend((paper['paper_id'],k) for k in fresh)
            census.extend((paper['paper_id'],t['tool_id']) for t in tool_matches(ps,ENTRIES))
            for tool in paper['tools']:
                assert tool['tool_id'] in registry and 'evidence_text' not in tool and 'evidence_sentence' not in tool
                candidate=fresh[tool['tool_id']] if tool['tool_id'] in selected else cached[tool['tool_id']]
                if tool.get('evidence_source_kind')=='supplementary-docx':
                    assert (paper['pmcid'],tool['tool_id'],tool['evidence_paragraph_index'])==(PMCID,'maltextract',246)
                    anchor=sp[246];assert tool['supplementary_primary_source']==pointer(anchor) and tool['evidence_source_url']==anchor['source_url'] and tool['evidence_document_sha256']==anchor['document_sha256'];text=anchor['text'];supp+=1
                else:
                    assert tool.get('evidence_source_kind','article-jats')=='article-jats';anchor=by[tool['evidence_paragraph_index']];text=anchor['text'];main+=1
                    if not re.search(registry[tool['tool_id']]['pattern'],anchor['match_text'],re.I):
                        assert (paper['paper_id'],tool['tool_id'],anchor['paragraph_index'])==('P005','htseq',54)
                        assert tool_matches([anchor],[registry['htseq']],{'htseq':tool['evidence_paragraph_sha256']})[0]['matched_text']=='HT-seq';aliases.append(('P005','htseq',54))
                assert candidate['evidence_text']==text and sha(text.encode())==tool['evidence_paragraph_sha256'] and candidate['evidence_paragraph_index']==tool['evidence_paragraph_index'] and tool['use_summary']
                observations+=1
        assert observations==2479 and main==2478 and supp==1 and aliases==[('P005','htseq',54)] and sorted(census)==[('P002','hops'),('P002','malt')]
        by_pmc={p['pmcid']:p for p in papers};secondary=0
        for review in reviews:
            paper=by_pmc[review['pmcid']];assert review['doi'].lower()==paper['doi'].lower();tool=next(t for t in paper['tools'] if t['tool_id']==review['tool_id'])
            assert tool['review_status']=='usage-summary-reviewed; paper-wide-completeness-review-open'
            for name in ['evidence_paragraph_sha256','use_summary','implementation_scope_note','usage_context','version_reported_near_name','supporting_evidence','supplementary_evidence','supplementary_primary_source','evidence_source_kind']:
                if name in review:assert tool[name]==review[name]
            validate_supporting_evidence(sources[paper['pmcid']],[review],registry)
            for source in review.get('supplementary_evidence',[]):
                assert (paper['pmcid'],review['tool_id'])==(PMCID,'malt') and source==pointer(sp[source['paragraph_index']]);secondary+=1
        assert secondary==4 and sum(len(r.get('supporting_evidence',[])) for r in reviews)==377
        assert len(prior_census)==20 and {k:sum(t==k for _,t in prior_census) for k in ['snprelate','genesis','mtag','scdrs']}==dict(snprelate=4,genesis=5,mtag=5,scdrs=6)
        for r in registry.values():
            if r['existing_source']:assert (ROOT/r['existing_source']).is_file()
        state.update(status='PASS250_PRIMARY_ARTICLES_2478_JATS_OBSERVATIONS_ONE_TYPED_DOCX_OBSERVATION_AND1052_REVIEWS',source_papers=250,observations=2479,main_article_observations=2478,supplementary_docx_observations=1,reviewed_uses=1052,main_article_supporting_pointers=377,supplementary_secondary_pointers=secondary,prior_observations_unchanged=2476,prior_reviews_unchanged=1049,new_named_tools=3,new_whole_tool_credit=0,derived_registry_entries=443,complete_main_software_census=census,paper_wide_semantic_review_open=True,source_pins={n:pin(ROOT/n) for n in ['tools/literature/audit_malt_source_catalog.py','tools/literature/malt_primary_sources.py','tools/literature/malt_source_catalog.py','tools/literature/malt_source_registry.py','tools/literature/collect_omics_review.py','tools/literature/collect_htseq_omics.py']},papers=pin(data/'papers.json'),reviews=pin(ROOT/'tools/literature/reviewed-uses.json'))
    except Exception as e:state.update(cause=repr(e),traceback=traceback.format_exc())
    save(out/'COMPLETED.json',state);(out/'WARNINGS.md').write_text('# '+state['status']+'\n\nRequired action: retain complete primary-source inputs and resolve any named pointer, hash, schema or prerequisite failure before a NEW audit. Missing/invalid evidence does not admit a tool or biological result. Semantic completeness and full implementation remain open.\n',encoding='utf8');print(state['status'],state.get('cause',''),flush=True);return 0 if state['status'].startswith('PASS') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete source-kind or provenance checks')
    raise SystemExit(main())
