# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Apply six primary-bound GSEApy roles: append five and explicitly refine one existing review."""
import argparse,copy,gzip,hashlib,json,sys
import xml.etree.ElementTree as ET
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent))
from setup_deseq2_reference import ROOT,pin,save
from curate_shapeit_uses import inverse,serialize
from collect_omics_review import paragraphs,publication_record,validate_supporting_evidence
from catalog_fixed_cohort import verify_fixed_cohort
from scfates_catalog import load_catalog_registry


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);p.add_argument('--apply',action='store_true');a=p.parse_args();out=a.out.resolve();assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/gseapy-runtime');out.mkdir(parents=True);data=ROOT/'docs/literature/omics-2023-2026';paths=dict(papers=data/'papers.json',reviews=ROOT/'tools/literature/reviewed-uses.json');before={k:p.read_bytes() for k,p in paths.items()};papers=json.loads(before['papers']);old=json.loads(before['reviews']);verify_fixed_cohort(ROOT,data,papers);assert len(old['reviews'])==988 and sum(len(p['tools']) for p in papers)==2465;draftpath=ROOT/'build/literature-review/gseapy-runtime/primary-role-drafts-1/gseapy-use-drafts.json';draft=json.loads(draftpath.read_bytes());producer=json.loads(draftpath.with_name('COMPLETED.json').read_bytes());assert pin(draftpath)==producer['draft'] and producer['papers_pin']==pin(paths['papers']) and producer['reviews_pin']==pin(paths['reviews']) and all(pin(ROOT/n)==v for n,v in producer['source_pins'].items());by={r['paper_id']:r for r in draft['reviews']};assert sorted(by)==['P009','P053','P062','P079','P098','P161'];registry={r['id']:r for r in load_catalog_registry(ROOT)};updated=copy.deepcopy(papers);reviewed=copy.deepcopy(old);applied=[];prior_gseapy=[r for r in old['reviews'] if r['tool_id']=='gseapy'];assert len(prior_gseapy)==1 and prior_gseapy[0]['pmcid']==by['P062']['pmcid']
    for paper in updated:
        raw=gzip.decompress((ROOT/'build/literature-review/xml'/(paper['pmcid']+'.xml.gz')).read_bytes());assert hashlib.sha256(raw).hexdigest()==paper['xml_sha256'];xml=ET.fromstring(raw);assert next(e.text for e in xml.findall('front/article-meta/article-id') if e.get('pub-id-type')=='doi').lower()==paper['doi'].lower();ps=paragraphs(xml);named=[p for p in ps if 'gseapy' in p['text'].lower()];assert bool(named)==(paper['paper_id'] in by)
        if not named:continue
        d=by[paper['paper_id']];assert paper['xml_sha256']==d['xml_sha256'];source={r['paragraph_index']:r for r in ps}
        for e in d['primary_evidence']:assert source[e['paragraph_index']]['text']==e['text'] and source[e['paragraph_index']]['section']==e['section'] and hashlib.sha256(e['text'].encode()).hexdigest()==e['sha256']
        anchor=d['primary_evidence'][0];review=dict(pmcid=paper['pmcid'],doi=paper['doi'],tool_id='gseapy',evidence_paragraph_sha256=anchor['sha256'],use_summary=d['use_summary'],implementation_scope_note=d['implementation_scope_note'])
        if d['reported_version'] is not None:review['version_reported_near_name']=d['reported_version']
        if len(d['primary_evidence'])>1:review['supporting_evidence']=[dict(paragraph_index=e['paragraph_index'],section=e['section'],paragraph_sha256=e['sha256']) for e in d['primary_evidence'][1:]]
        validate_supporting_evidence(ps,[review],registry);key=(review['pmcid'],review['tool_id']);matches=[i for i,r in enumerate(reviewed['reviews']) if (r['pmcid'],r['tool_id'])==key]
        if matches:assert paper['paper_id']=='P062' and len(matches)==1;reviewed['reviews'][matches[0]]=review
        else:reviewed['reviews'].append(review)
        cached=json.loads((ROOT/'build/literature-review/extracted'/(paper['pmcid']+'.json')).read_bytes());tool=next(t for t in cached['tools'] if t['tool_id']=='gseapy');assert tool['evidence_paragraph_index']==d['primary_paragraph'];replacement=publication_record(dict(paper,tools=[tool]),[review])['tools'][0];index=next(i for i,t in enumerate(paper['tools']) if t['tool_id']=='gseapy');original=paper['tools'][index];assert original['evidence_paragraph_sha256']==review['evidence_paragraph_sha256'];paper['tools'][index]=replacement;applied.append(dict(paper_id=paper['paper_id'],review=review))
    for prior,current in zip(papers,updated):assert {k:v for k,v in prior.items() if k!='tools'}=={k:v for k,v in current.items() if k!='tools'} and [t for t in prior['tools'] if t['tool_id']!='gseapy']==[t for t in current['tools'] if t['tool_id']!='gseapy']
    assert [r for r in old['reviews'] if r['tool_id']!='gseapy']==[r for r in reviewed['reviews'] if r['tool_id']!='gseapy'];assert len(reviewed['reviews'])==len({(r['pmcid'],r['tool_id']) for r in reviewed['reviews']})==993 and sum(len(r.get('supporting_evidence',[])) for r in reviewed['reviews'])==344;after=dict(papers=serialize(updated,before['papers']),reviews=serialize(reviewed,before['reviews']));row=inverse(before['reviews'],after['reviews']);row['path']='tools/literature/reviewed-uses.json';drafted=dict(status='six-primary-bound-GSEApy-role-reviews',reviews=applied,source_draft=pin(draftpath),whole_paper_reproduced=False,whole_goal_complete=False)
    for key,raw in before.items():(out/('before-'+key+'.json.gz')).write_bytes(gzip.compress(raw,mtime=0))
    save(out/'gseapy-use-reviews.json',drafted);result=dict(status='passed-six-GSEApy-primary-role-reviews',applied=a.apply,prior_reviews=988,unchanged_prior_reviews=987,refined_existing_reviews=1,new_reviews=5,total_reviews=993,supporting_pointers=344,source_records=250,observations=2465,changed_paper_roles=sorted(by),curator_sha256=pin(Path(__file__))['sha256'],source_draft=pin(draftpath),before_reviews_sha256=hashlib.sha256(before['reviews']).hexdigest(),reviews_sha256=hashlib.sha256(after['reviews']).hexdigest(),papers_sha256=hashlib.sha256(after['papers']).hexdigest(),draft_pin=pin(out/'gseapy-use-reviews.json'),whole_paper_reproduced=False,whole_goal_complete=False)
    if a.apply:
        from gseapy_execution_evidence import verify
        assert verify(False)['full_gseapy_adapter_admitted'];destinations=[data/'gseapy-role-coverage.json',data/'gseapy-prior-reviews.json.gz',data/'gseapy-prior-papers.json.gz',data/'gseapy-reviews-shared-source-compatibility.json',ROOT/'tools/literature/gseapy-use-reviews.json'];assert not any(p.exists() for p in destinations) and all(p.read_bytes()==before[k] for k,p in paths.items());destinations[1].write_bytes(gzip.compress(before['reviews'],mtime=0));destinations[2].write_bytes(gzip.compress(before['papers'],mtime=0));result.update(prior_review_archive=pin(destinations[1]),prior_papers_archive=pin(destinations[2]));save(destinations[3],dict(schema_version=1,status='passed-dispatch-catalog-only-source-deltas',tool_id='gseapy',scope='Five appended GSEApy reviews and one explicitly refined existing GSEApy review; all987 other roles and250 paper identities remain exact. No scientific source exception.',paths=[row]));destinations[4].write_bytes((out/'gseapy-use-reviews.json').read_bytes())
        for k,p in paths.items():p.write_bytes(after[k])
        save(destinations[0],result)
    save(out/'COMPLETED.json',result);print('Validated250 XML/DOIs; five new and one refined GSEApy review;993 roles and344 support pointers; applied='+str(a.apply))


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable source/cohort gates')
    main()
