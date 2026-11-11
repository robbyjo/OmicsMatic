# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Prepare a complete250-paper SNPRelate/GENESIS role extension without applying it or granting implementation credit."""
import argparse
import copy
import gzip
import hashlib
import json
import traceback
import xml.etree.ElementTree as ET
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from diamond_catalog import load_catalog_registry
from collect_omics_review import paragraphs,tool_matches,publication_record,validate_supporting_evidence
BASE=ROOT/'build/literature-review/snprelate-runtime'


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args()
    out=a.out.resolve();assert not out.exists() and out.is_relative_to(BASE);out.mkdir(parents=True)
    state=dict(status='FAIL2_SNPRELATE_GENESIS_CATALOG_CANDIDATE',catalog_applied=False,new_catalog_credit=False,whole_goal_complete=False)
    try:
        data=ROOT/'docs/literature/omics-2023-2026';paper_file=data/'papers.json';review_file=ROOT/'tools/literature/reviewed-uses.json'
        before=dict(papers=paper_file.read_bytes(),reviews=review_file.read_bytes());papers=json.loads(before['papers']);old=json.loads(before['reviews'])
        assert len(papers)==250 and len(old['reviews'])==1029
        registry=load_catalog_registry(ROOT);extra_file=ROOT/'tools/literature/snprelate-named-software-registry.json';extra=json.loads(extra_file.read_bytes())
        assert len(registry)==438 and {r['id'] for r in extra}=={'snprelate','genesis'} and not ({r['id'] for r in registry}&{r['id'] for r in extra})
        registry+=extra;definitions={r['id']:r for r in extra};assert len(registry)==440
        supplement=data/'snprelate-genesis-primary-roles-v2.json';roles=json.loads(supplement.read_bytes())
        assert roles['status']=='reviewed-citation-normalized-primary-role-supplement-registry-admission-pending' and roles['cohort_papers']==250
        grouped={}
        for role in roles['roles']:
            if role['classification']=='software-use':grouped.setdefault((role['paper_id'],role['tool']),[]).append(role)
        assert len(grouped)==9
        primary={key:sorted(values,key=lambda r:r['paragraph_index'])[0] for key,values in grouped.items()}
        candidates=copy.deepcopy(papers);reviews=copy.deepcopy(old);drafts=[];anchors={}
        for paper in candidates:
            path=ROOT/'build/literature-review/xml'/(paper['pmcid']+'.xml.gz');raw=gzip.decompress(path.read_bytes())
            assert hashlib.sha256(raw).hexdigest()==paper['xml_sha256'];ps=paragraphs(ET.fromstring(raw))
            source_mentions=tool_matches(ps,extra,{tool:primary[(paper['paper_id'],tool)]['paragraph_sha256'] for tool in definitions if (paper['paper_id'],tool) in primary})
            actual={r['tool_id'] for r in source_mentions};expected={tool for pid,tool in grouped if pid==paper['paper_id']}
            assert actual==expected,('Unreviewed new package mention or generic-word false positive',paper['paper_id'],actual,expected)
            assert not any(r['tool_id'] in definitions for r in paper['tools'])
            for observation in source_mentions:
                tool=observation['tool_id'];key=(paper['paper_id'],tool);rows=sorted(grouped[key],key=lambda r:r['paragraph_index']);first=primary[key]
                assert hashlib.sha256(observation['evidence_text'].encode('utf8')).hexdigest()==first['paragraph_sha256'] and observation['evidence_paragraph_index']==first['paragraph_index']
                scope=('Typed original SNPRelate1.46.0 execution is under validation and earns no new implementation credit here. '
                    'The paper-specific host, version, family context, LD estimator/window/start/seed, GRM estimator and input population must be explicit. '
                    'P130 reports1.30.1; a1.46.0 run does not reproduce that historical version. ' if tool=='snprelate' else
                    'GENESIS workflows remain unimplemented. Native/SNPRelate KING coefficients and ordinary PCA do not implement GENESIS PC-AiR, '
                    'PC-Relate or relatedness-adjusted model fitting. Require the paper version and explicit function/model settings before a permitted original runtime execution. ')
                scope+=' '.join(r['unresolved_scope'] for r in rows if r['unresolved_scope'])
                review=dict(pmcid=paper['pmcid'],doi=paper['doi'],tool_id=tool,evidence_paragraph_sha256=first['paragraph_sha256'],
                    use_summary=' '.join(r['reviewed_role'] for r in rows),implementation_scope_note=scope,version_reported_near_name=first['reported_version'])
                supporting=[dict(paragraph_index=r['paragraph_index'],section=r['section'],paragraph_sha256=r['paragraph_sha256']) for r in rows[1:]]
                if supporting:review['supporting_evidence']=supporting
                validate_supporting_evidence(ps,[review],definitions)
                published=publication_record(dict(paper,tools=[observation]),[review])['tools'][0]
                assert published['review_status'].startswith('usage-summary-reviewed') and published['use_summary']==review['use_summary']
                paper['tools'].append(published);reviews['reviews'].append(review)
                drafts.append(dict(paper_id=paper['paper_id'],review=review,prior_review=None,action='new-primary-bound-package-review'))
                anchors[paper['paper_id']+':'+tool]=dict(pmcid=paper['pmcid'],doi=paper['doi'],xml_sha256=paper['xml_sha256'],
                    primary_paragraph=first['paragraph_index'],primary_paragraph_sha256=first['paragraph_sha256'])
        assert len(reviews['reviews'])==len({(r['pmcid'],r['tool_id']) for r in reviews['reviews']})==1038
        assert reviews['reviews'][:1029]==old['reviews'] and sum(len(r.get('supporting_evidence',[])) for r in reviews['reviews'])==369
        assert sum(len(p['tools']) for p in papers)==2466 and sum(len(p['tools']) for p in candidates)==2475
        assert len({r['tool_id'] for p in candidates for r in p['tools']})==402
        for before_paper,after_paper in zip(papers,candidates,strict=True):
            assert {k:v for k,v in before_paper.items() if k!='tools'}=={k:v for k,v in after_paper.items() if k!='tools'}
            assert before_paper['tools']==[r for r in after_paper['tools'] if r['tool_id'] not in definitions]
        for key,raw in before.items():(out/('prior-'+key+'.json.gz')).write_bytes(gzip.compress(raw,mtime=0))
        save(out/'candidate-papers.json',candidates);save(out/'candidate-reviews.json',reviews);save(out/'candidate-registry.json',registry)
        save(out/'review-draft.json',dict(status='nine-primary-bound-SNPRelate-GENESIS-package-reviews',reviews=drafts))
        state.update(status='PASS250_PRIMARY_SNPRELATE_GENESIS_CATALOG_CANDIDATE_ONLY',source_records=250,new_package_definitions=2,
            prior_definitions=438,candidate_definitions=440,prior_observed_tools=400,candidate_observed_tools=402,prior_observations=2466,candidate_observations=2475,
            prior_reviews_preserved=1029,new_reviews=9,candidate_reviews=1038,candidate_supporting_pointers=369,
            source_anchors=anchors,registry_extension=pin(extra_file),primary_role_supplement=pin(supplement),producer=pin(Path(__file__)),
            candidate_files={f.name:pin(f) for f in out.iterdir() if f.is_file()},article_prose_redistributed=False,
            scope='Prepared full-cohort catalog correction only. No production catalog files, predecessor scientific sources or integration counts are changed.')
        assert paper_file.read_bytes()==before['papers'] and review_file.read_bytes()==before['reviews']
    except Exception as error:
        state['error']=repr(error);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error','')[:2400],flush=True)
    return 0 if state['status'].startswith('PASS250_') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete cohort/old-observation preservation or source-role checks')
    raise SystemExit(main())
