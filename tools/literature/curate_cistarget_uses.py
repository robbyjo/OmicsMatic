# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Apply seven primary-bound cisTarget reviews, one explicit supersession and one new observation."""
import argparse,copy,gzip,hashlib,json,re,xml.etree.ElementTree as ET
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from curate_shapeit_uses import inverse,serialize
from collect_omics_review import paragraphs,tool_matches,publication_record,validate_supporting_evidence
from catalog_fixed_cohort import verify_fixed_cohort
from cistarget_catalog import load_catalog_registry


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);p.add_argument('--apply',action='store_true');a=p.parse_args();base=ROOT/'build/literature-review/cistarget-runtime';out=a.out.resolve()
    assert not out.exists() and out.is_relative_to(base);out.mkdir(parents=True);data=ROOT/'docs/literature/omics-2023-2026';paths=dict(papers=data/'papers.json',reviews=ROOT/'tools/literature/reviewed-uses.json');before={k:p.read_bytes() for k,p in paths.items()}
    papers=json.loads(before['papers']);old=json.loads(before['reviews']);verify_fixed_cohort(ROOT,data,papers)
    assert len(old['reviews'])==1012 and sum(len(p['tools']) for p in papers)==2465
    audit=base/'primary-roles-3';audit_record=json.loads((audit/'COMPLETED.json').read_bytes());assert audit_record['source_roles']==7 and all(pin(ROOT/n)==v for n,v in audit_record['catalog_before'].items())
    assert pin(audit/'role-drafts.json')==audit_record['role_drafts'] and pin(audit/'pending-observation.json')==audit_record['pending_observation']
    drafts=copy.deepcopy(json.loads((audit/'role-drafts.json').read_bytes())['reviews']);pending=json.loads((audit/'pending-observation.json').read_bytes());assert not pending['applied'] and pending['paper_id']=='P145'
    by={p['paper_id']:p for p in papers};source_paper=by['P145'];anchor=pending['primary_anchor'];review=dict(pmcid=source_paper['pmcid'],doi=source_paper['doi'],tool_id='cistarget',
        evidence_paragraph_sha256=hashlib.sha256(anchor['text'].encode()).hexdigest(),use_summary=pending['use_summary'],implementation_scope_note=pending['implementation_scope_note'],version_reported_near_name=None,
        supporting_evidence=[dict(paragraph_index=64,section=pending['supporting_resource_paragraphs'][0]['section'],paragraph_sha256=hashlib.sha256(pending['supporting_resource_paragraphs'][0]['text'].encode()).hexdigest())])
    drafts.append(dict(paper_id='P145',role_kind=pending['role_kind'],review=review,prior_review=None,action='new-primary-Methods-observation'))
    assert {r['paper_id'] for r in drafts}=={'P047','P058','P061','P065','P094','P100','P145'}
    registry={r['id']:r for r in load_catalog_registry(ROOT)};by_draft={r['paper_id']:r for r in drafts};updated=copy.deepcopy(papers);reviewed=copy.deepcopy(old);caches=[];changed=[];new_observations=[];source_anchors={};supersessions=[]
    for paper in updated:
        raw=gzip.decompress((ROOT/'build/literature-review/xml'/(paper['pmcid']+'.xml.gz')).read_bytes());assert hashlib.sha256(raw).hexdigest()==paper['xml_sha256']
        xml=ET.fromstring(raw);assert next(n.text for n in xml.findall('front/article-meta/article-id') if n.get('pub-id-type')=='doi').lower()==paper['doi'].lower();ps=paragraphs(xml)
        matches=[p for p in ps if re.search(registry['cistarget']['pattern'],p['match_text'],re.I)];assert bool(matches)==(paper['paper_id'] in by_draft)
        if not matches:continue
        item=by_draft[paper['paper_id']];review=item['review'];assert review['pmcid']==paper['pmcid'] and review['doi']==paper['doi'];validate_supporting_evidence(ps,[review],registry)
        extracted=tool_matches(ps,[registry['cistarget']],{'cistarget':review['evidence_paragraph_sha256']});assert len(extracted)==1
        if paper['paper_id']=='P145':assert extracted[0]['evidence_paragraph_index']==53 and extracted[0]['matched_text'].lower()=='cis-target'
        publication=publication_record(dict(paper,tools=extracted),[review])['tools'][0]
        existing=[i for i,r in enumerate(reviewed['reviews']) if (r['pmcid'],r['tool_id'])==(paper['pmcid'],'cistarget')]
        if existing:
            assert paper['paper_id']=='P061' and len(existing)==1 and item['prior_review']==reviewed['reviews'][existing[0]]
            supersessions.append(dict(paper_id='P061',review_index=existing[0],before=item['prior_review'],after=review,reason='Enrich the earlier generic AUC-only scope note with the source-specific positive/negative DMR sets, strict NES and leading-edge role; retain the complete prior review archive.'))
            reviewed['reviews'][existing[0]]=review
        else:assert item['prior_review'] is None;reviewed['reviews'].append(review)
        cache=ROOT/'build/literature-review/extracted'/(paper['pmcid']+'.json');cache_raw=cache.read_bytes();cached=json.loads(cache_raw)
        paper_index=[i for i,t in enumerate(paper['tools']) if t['tool_id']=='cistarget'];cache_index=[i for i,t in enumerate(cached['tools']) if t['tool_id']=='cistarget'];assert len(paper_index)==len(cache_index)<=1
        if paper_index:paper['tools'][paper_index[0]]=publication;cached['tools'][cache_index[0]]=extracted[0]
        else:
            assert paper['paper_id']=='P145';paper['tools'].append(publication);cached['tools'].append(extracted[0]);new_observations.append(dict(paper_id='P145',tool_id='cistarget',primary_paragraph=53,primary_sha256=review['evidence_paragraph_sha256'],resource_paragraph=64))
        caches.append((cache,cache_raw,serialize(cached,cache_raw)));changed.append(paper['paper_id']);source_anchors[paper['paper_id']]=dict(pmcid=paper['pmcid'],doi=paper['doi'],xml_sha256=paper['xml_sha256'],primary_paragraph=extracted[0]['evidence_paragraph_index'],primary_sha256=review['evidence_paragraph_sha256'])
    assert len(changed)==7 and len(new_observations)==len(supersessions)==1
    assert len(reviewed['reviews'])==len({(r['pmcid'],r['tool_id']) for r in reviewed['reviews']})==1018 and sum(len(r.get('supporting_evidence',[])) for r in reviewed['reviews'])==356
    assert sum(len(p['tools']) for p in updated)==2466 and len({t['tool_id'] for p in updated for t in p['tools']})==400
    for prior,current in zip(papers,updated,strict=True):
        assert {k:v for k,v in prior.items() if k!='tools'}=={k:v for k,v in current.items() if k!='tools'}
        assert [t for t in prior['tools'] if t['tool_id']!='cistarget']==[t for t in current['tools'] if t['tool_id']!='cistarget']
        assert set(t['tool_id'] for t in prior['tools'])<=set(t['tool_id'] for t in current['tools'])
    assert [r for r in reviewed['reviews'] if not(r['pmcid']=='PMC10719113' and r['tool_id']=='cistarget')][:1011]==[r for r in old['reviews'] if not(r['pmcid']=='PMC10719113' and r['tool_id']=='cistarget')]
    after=dict(papers=serialize(updated,before['papers']),reviews=serialize(reviewed,before['reviews']))
    save(out/'cistarget-use-reviews.json',dict(status='seven-primary-bound-cisTarget-role-reviews',reviews=drafts,whole_paper_reproduced=False,whole_goal_complete=False))
    save(out/'cistarget-review-supersession.json',dict(status='one-explicit-source-bound-cisTarget-review-enrichment',supersessions=supersessions,source_anchors=source_anchors,prior_review_preserved=True))
    save(out/'cistarget-new-observation.json',dict(status='one-primary-bound-cis-target-spelling-observation-added',observations=new_observations,previous_observations=2465,new_observations=2466,previous_definitions=438,new_definitions=438,standalone_execution_inferred=False))
    for name,raw in before.items():(out/('before-'+name+'.json.gz')).write_bytes(gzip.compress(raw,mtime=0))
    for name,raw in after.items():(out/('candidate-'+name+'.json')).write_bytes(raw)
    report=dict(status='passed-seven-cisTarget-primary-role-reviews-and-one-spelling-observation',applied=a.apply,prior_reviews=1012,unchanged_prior_reviews=1011,superseded_prior_reviews=1,new_reviews=6,total_reviews=1018,supporting_pointers=356,
        source_records=250,observations=2466,previous_observations=2465,changed_paper_roles=changed,curator_sha256=pin(Path(__file__))['sha256'],
        before_reviews_sha256=hashlib.sha256(before['reviews']).hexdigest(),reviews_sha256=hashlib.sha256(after['reviews']).hexdigest(),papers_sha256=hashlib.sha256(after['papers']).hexdigest(),
        draft_pin=pin(out/'cistarget-use-reviews.json'),supersession_pin=pin(out/'cistarget-review-supersession.json'),new_observation_pin=pin(out/'cistarget-new-observation.json'),registry_spelling=pin(ROOT/'tools/literature/cistarget-registry-spelling.json'),
        all_non_cistarget_source_roles_preserved=True,whole_paper_reproduced=False,whole_goal_complete=False)
    if a.apply:
        root=json.loads((base/'catalog-root-validation-2/COMPLETED.json').read_bytes());assert root['status']=='PASS108_COMPLETE_CISTARGET_ROOT_FLOWS_12_PREDECESSOR_FLOWS_AND72_DISPATCH_CONTRACTS'
        destinations=[data/n for n in ['cistarget-role-coverage.json','cistarget-prior-reviews.json.gz','cistarget-prior-papers.json.gz','cistarget-reviews-shared-source-compatibility.json','cistarget-review-supersession.json','cistarget-new-observation.json']]+[ROOT/'tools/literature/cistarget-use-reviews.json']
        assert not any(p.exists() for p in destinations) and all(p.read_bytes()==before[k] for k,p in paths.items()) and all(p.read_bytes()==raw for p,raw,_ in caches)
        destinations[1].write_bytes(gzip.compress(before['reviews'],mtime=0));destinations[2].write_bytes(gzip.compress(before['papers'],mtime=0));report.update(prior_review_archive=pin(destinations[1]),prior_papers_archive=pin(destinations[2]))
        row=inverse(before['reviews'],after['reviews']);row['path']='tools/literature/reviewed-uses.json'
        save(destinations[3],dict(schema_version=1,status='passed-dispatch-catalog-only-source-deltas',tool_id='cistarget',scope='Six appended primary-bound reviews and one explicit P061 review supersession, preserving all1011 other prior reviews. The archived1012-review document reconstructs exactly. One P145 spelling-variant Methods observation is added; no scientific-source exception.',paths=[row]))
        for path,name in [(destinations[4],'cistarget-review-supersession.json'),(destinations[5],'cistarget-new-observation.json'),(destinations[6],'cistarget-use-reviews.json')]:path.write_bytes((out/name).read_bytes())
        for key,path in paths.items():path.write_bytes(after[key])
        for path,_,raw in caches:path.write_bytes(raw)
        save(destinations[0],report)
    save(out/'COMPLETED.json',report);print('Validated250 primary records;1018 reviews/356 pointers/2466 observations; applied='+str(a.apply))


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable primary evidence and prior-population preservation checks')
    main()
