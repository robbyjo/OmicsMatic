# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Validate six primary Sniffles reviews; apply only after complete calling validation."""
import argparse,copy,gzip,hashlib,json,re,xml.etree.ElementTree as ET
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from curate_shapeit_uses import inverse,serialize
from collect_omics_review import paragraphs,publication_record,validate_supporting_evidence
from catalog_fixed_cohort import verify_fixed_cohort
from rsubread_catalog import load_catalog_registry


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    for name in ['audit','out']:parser.add_argument('--'+name,type=Path,required=True)
    parser.add_argument('--apply',action='store_true');a=parser.parse_args()
    out=a.out.resolve();assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/sniffles-runtime');out.mkdir(parents=True)
    data=ROOT/'docs/literature/omics-2023-2026';paths=dict(papers=data/'papers.json',reviews=ROOT/'tools/literature/reviewed-uses.json')
    before={key:path.read_bytes() for key,path in paths.items()};papers=json.loads(before['papers']);old=json.loads(before['reviews'])
    verify_fixed_cohort(ROOT,data,papers);assert len(old['reviews'])==1003 and sum(len(paper['tools']) for paper in papers)==2465
    audit=a.audit.resolve();proof=json.loads((audit/'COMPLETED.json').read_bytes())
    assert proof['status']=='passed250-primary-XML-DOI-and2465-anchors-with-six-Sniffles-drafts'
    assert pin(audit/'role-drafts.json')==proof['role_drafts'] and pin(audit/'source-audit.json')==proof['source_audit']
    assert all(pin(ROOT/name)==value for name,value in proof['catalog_before'].items())
    drafts=copy.deepcopy(json.loads((audit/'role-drafts.json').read_bytes())['reviews'])
    by={item['paper_id']:item['review'] for item in drafts};assert set(by)=={'P033','P102','P129','P164','P247','P248'}
    registry={row['id']:row for row in load_catalog_registry(ROOT)};assert len(registry)==438
    updated=copy.deepcopy(papers);reviews=copy.deepcopy(old);applied=[]
    for paper in updated:
        raw=gzip.decompress((ROOT/'build/literature-review/xml'/(paper['pmcid']+'.xml.gz')).read_bytes())
        assert hashlib.sha256(raw).hexdigest()==paper['xml_sha256'];xml=ET.fromstring(raw)
        assert next(node.text for node in xml.findall('front/article-meta/article-id') if node.get('pub-id-type')=='doi').lower()==paper['doi'].lower()
        ps=paragraphs(xml);named=[p for p in ps if re.search(registry['sniffles']['pattern'],p['match_text'],re.I)]
        assert bool(named)==(paper['paper_id'] in by)
        if not named:continue
        review=by[paper['paper_id']];tool=next(t for t in paper['tools'] if t['tool_id']=='sniffles')
        assert review['pmcid']==paper['pmcid'] and review['doi']==paper['doi'] and review['evidence_paragraph_sha256']==tool['evidence_paragraph_sha256']
        assert review['version_reported_near_name'] in ' '.join(p['text'] for p in named)
        if paper['paper_id']=='P102':
            anchor=next(p for p in ps if p['paragraph_index']==tool['evidence_paragraph_index'])
            pointer=review['supporting_evidence'][0]
            assert pointer['paragraph_index']==36 and anchor['paragraph_index']==37
            assert pointer['section']==anchor['section'] and 'same steps as above' in anchor['text']
            pointer.update(evidence_role='method-details',
                named_anchor=dict(paragraph_index=anchor['paragraph_index'],section=anchor['section'],paragraph_sha256=review['evidence_paragraph_sha256']),
                shared_terms=['ZS11','SVs'],
                relation_note='The named Sniffles paragraph explicitly links its SV filters to the preceding whole-genome alignment paragraph. This pointer supplies those stated conditions without inventing field mappings, duplicate definitions or a Boolean expression.')
        validate_supporting_evidence(ps,[review],registry)
        assert not any((item['pmcid'],item['tool_id'])==(review['pmcid'],'sniffles') for item in reviews['reviews'])
        reviews['reviews'].append(review)
        cache=json.loads((ROOT/'build/literature-review/extracted'/(paper['pmcid']+'.json')).read_bytes())
        source=next(t for t in cache['tools'] if t['tool_id']=='sniffles')
        replacement=publication_record(dict(paper,tools=[source]),[review])['tools'][0]
        index=next(index for index,t in enumerate(paper['tools']) if t['tool_id']=='sniffles');paper['tools'][index]=replacement
        applied.append(dict(paper_id=paper['paper_id'],review=review))
    assert len(applied)==6 and len(reviews['reviews'])==1009 and sum(len(item.get('supporting_evidence',[])) for item in reviews['reviews'])==353
    assert reviews['reviews'][:1003]==old['reviews']
    for previous,current in zip(papers,updated,strict=True):
        assert {k:v for k,v in previous.items() if k!='tools'}=={k:v for k,v in current.items() if k!='tools'}
        assert [t['tool_id'] for t in previous['tools']]==[t['tool_id'] for t in current['tools']]
        assert [t for t in previous['tools'] if t['tool_id']!='sniffles']==[t for t in current['tools'] if t['tool_id']!='sniffles']
    after=dict(papers=serialize(updated,before['papers']),reviews=serialize(reviews,before['reviews']))
    draft=dict(status='six-primary-bound-Sniffles-role-reviews',reviews=applied,primary_audit=pin(audit/'source-audit.json'),whole_paper_reproduced=False,whole_goal_complete=False)
    save(out/'sniffles-use-reviews.json',draft)
    for key,raw in before.items():(out/('before-'+key+'.json.gz')).write_bytes(gzip.compress(raw,mtime=0))
    for key,raw in after.items():(out/('candidate-'+key+'.json')).write_bytes(raw)
    report=dict(status='passed-six-Sniffles-primary-role-reviews',applied=a.apply,prior_reviews=1003,unchanged_prior_reviews=1003,new_reviews=6,total_reviews=1009,
        supporting_pointers=353,checked_method_detail_links=1,source_records=250,observations=2465,changed_paper_roles=sorted(by),curator_sha256=pin(Path(__file__))['sha256'],
        before_reviews_sha256=hashlib.sha256(before['reviews']).hexdigest(),reviews_sha256=hashlib.sha256(after['reviews']).hexdigest(),papers_sha256=hashlib.sha256(after['papers']).hexdigest(),
        draft_pin=pin(out/'sniffles-use-reviews.json'),all_prior_reviews_and_non_sniffles_roles_preserved=True,implementation_credit=False,whole_paper_reproduced=False,whole_goal_complete=False)
    if a.apply:
        execution_helper=ROOT/'tools/literature/sniffles_execution_evidence.py'
        assert execution_helper.is_file(),'ACTION: calling validation is unfinished. Preserve this validated candidate; do not apply catalog availability or reviews before the final execution evidence is available.'
        from sniffles_execution_evidence import verify
        execution,_=verify(False);assert execution['declared_calling_workflow_available']
        destinations=[data/'sniffles-role-coverage.json',data/'sniffles-prior-reviews.json.gz',data/'sniffles-prior-papers.json.gz',
            data/'sniffles-reviews-shared-source-compatibility.json',ROOT/'tools/literature/sniffles-use-reviews.json']
        assert not any(path.exists() for path in destinations) and all(path.read_bytes()==before[key] for key,path in paths.items())
        destinations[1].write_bytes(gzip.compress(before['reviews'],mtime=0));destinations[2].write_bytes(gzip.compress(before['papers'],mtime=0))
        report.update(prior_review_archive=pin(destinations[1]),prior_papers_archive=pin(destinations[2]))
        row=inverse(before['reviews'],after['reviews']);row['path']='tools/literature/reviewed-uses.json'
        save(destinations[3],dict(schema_version=1,status='passed-dispatch-catalog-only-source-deltas',tool_id='sniffles',
            scope='Append six primary Sniffles reviews. Preserve all1003 prior reviews and all250 identities/2465 observed memberships. No scientific source exception.',paths=[row]))
        destinations[4].write_bytes((out/'sniffles-use-reviews.json').read_bytes())
        for key,path in paths.items():path.write_bytes(after[key])
        save(destinations[0],report)
    save(out/'COMPLETED.json',report)
    print('Verified250 primary XML/DOIs; candidate1009 reviews/353 pointers; all1003 prior reviews preserved; applied='+str(a.apply),flush=True)


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete primary/cohort and prior-review preservation checks')
    main()
