# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Refine three and append three Rsubread roles after a complete primary-source census and original-runtime proof."""
import argparse,copy,gzip,hashlib,json,re,xml.etree.ElementTree as ET
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from curate_shapeit_uses import inverse,serialize
from collect_omics_review import paragraphs,publication_record,validate_supporting_evidence
from catalog_fixed_cohort import verify_fixed_cohort
from hifiasm_catalog import load_catalog_registry


def main():
    p=argparse.ArgumentParser(description=__doc__)
    for name in ['audit','out']:p.add_argument('--'+name,type=Path,required=True)
    p.add_argument('--apply',action='store_true');a=p.parse_args();out=a.out.resolve();assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/rsubread-runtime');out.mkdir(parents=True)
    data=ROOT/'docs/literature/omics-2023-2026';paths=dict(papers=data/'papers.json',reviews=ROOT/'tools/literature/reviewed-uses.json');before={k:p.read_bytes() for k,p in paths.items()};papers=json.loads(before['papers']);old=json.loads(before['reviews']);verify_fixed_cohort(ROOT,data,papers)
    assert len(old['reviews'])==1000 and sum(len(q['tools']) for q in papers)==2465
    audit=a.audit.resolve();proof=json.loads((audit/'COMPLETED.json').read_bytes());assert proof['status']=='passed250-primary-XML-DOI-and2465-anchors-with-six-Rsubread-role-drafts-only' and pin(audit/'role-drafts.json')==proof['role_drafts']
    for name,value in proof['catalog_before'].items():assert pin(ROOT/name)==value
    drafts=copy.deepcopy(json.loads((audit/'role-drafts.json').read_bytes())['reviews']);assert sorted(r['paper_id'] for r in drafts)==['P013','P029','P133','P139','P172','P223']
    author=ROOT/'docs/literature/rsubread-result-development/AUTHOR-CODE-AUDIT.json';code=json.loads(author.read_bytes());assert code['status']=='passed-read-only-P133-linked-author-code-audit' and not code['scripts_executed'] and not code['atac']['checkFragLength_reported'] and not code['atac']['countReadPairs_reported']
    p133=next(d['review'] for d in drafts if d['paper_id']=='P133')
    p133['implementation_scope_note']='The article spells the RNA function featurecount; preserve that statement separately from the package API. The linked author ATAC script at commit3aeb503348e52b1f0b88d47839427380250c0ea5 explicitly calls Rsubread::featureCounts on a SAF-style data.frame with paired-end input, no multi-mapping, maxFragLength=100 and autosort=TRUE, leaving checkFragLength and countReadPairs unspecified. It later renames columns separately; no package edition is established. The inspected RNA figure/revision script contains no Rsubread counting call, which does not resolve all author materials. RNA article settings specify exon/gene_id meta-features, no multi-overlap/multi-mapping, unstranded single-end data, juncCounts=TRUE and genome=NULL. Do not silently enable ATAC length filtering or infer a paper runtime. STAR, shared-peak preparation, normalization and DESeq2 remain separate.'
    by={r['paper_id']:r['review'] for r in drafts};registry={r['id']:r for r in load_catalog_registry(ROOT)};updated=copy.deepcopy(papers);reviews=copy.deepcopy(old);applied=[]
    for paper in updated:
        raw=gzip.decompress((ROOT/'build/literature-review/xml'/(paper['pmcid']+'.xml.gz')).read_bytes());assert hashlib.sha256(raw).hexdigest()==paper['xml_sha256'];xml=ET.fromstring(raw);assert next(q.text for q in xml.findall('front/article-meta/article-id') if q.get('pub-id-type')=='doi').lower()==paper['doi'].lower();ps=paragraphs(xml)
        named=[p for p in ps if re.search(registry['rsubread']['pattern'],p['match_text'],re.I)];assert bool(named)==(paper['paper_id'] in by)
        if not named:continue
        review=by[paper['paper_id']];tool=next(t for t in paper['tools'] if t['tool_id']=='rsubread');assert review['pmcid']==paper['pmcid'] and review['doi']==paper['doi'] and review['evidence_paragraph_sha256']==tool['evidence_paragraph_sha256'];validate_supporting_evidence(ps,[review],registry)
        indices=[i for i,r in enumerate(reviews['reviews']) if (r['pmcid'],r['tool_id'])==(review['pmcid'],'rsubread')]
        if indices:assert paper['paper_id'] in ['P013','P029','P223'] and len(indices)==1;reviews['reviews'][indices[0]]=review
        else:assert paper['paper_id'] in ['P133','P139','P172'];reviews['reviews'].append(review)
        cache=json.loads((ROOT/'build/literature-review/extracted'/(paper['pmcid']+'.json')).read_bytes());original=next(t for t in cache['tools'] if t['tool_id']=='rsubread');replacement=publication_record(dict(paper,tools=[original]),[review])['tools'][0];index=next(i for i,t in enumerate(paper['tools']) if t['tool_id']=='rsubread');paper['tools'][index]=replacement;applied.append(dict(paper_id=paper['paper_id'],review=review))
    for prior,current in zip(papers,updated,strict=True):assert {k:v for k,v in prior.items() if k!='tools'}=={k:v for k,v in current.items() if k!='tools'} and [t for t in prior['tools'] if t['tool_id']!='rsubread']==[t for t in current['tools'] if t['tool_id']!='rsubread']
    assert len(reviews['reviews'])==1003 and sum(len(r.get('supporting_evidence',[])) for r in reviews['reviews'])==349
    assert [r for r in old['reviews'] if r['tool_id']!='rsubread']==[r for r in reviews['reviews'] if r['tool_id']!='rsubread']
    after=dict(papers=serialize(updated,before['papers']),reviews=serialize(reviews,before['reviews']));draft=dict(status='six-primary-bound-Rsubread-role-reviews',reviews=applied,primary_audit=pin(audit/'source-audit.json'),author_code_audit=pin(author),whole_paper_reproduced=False,whole_goal_complete=False);save(out/'rsubread-use-reviews.json',draft)
    for key,raw in before.items():(out/('before-'+key+'.json.gz')).write_bytes(gzip.compress(raw,mtime=0))
    report=dict(status='passed-six-Rsubread-primary-role-reviews',applied=a.apply,prior_reviews=1000,unchanged_prior_reviews=997,refined_existing_reviews=3,new_reviews=3,total_reviews=1003,supporting_pointers=349,source_records=250,observations=2465,changed_paper_roles=sorted(by),curator_sha256=pin(Path(__file__).resolve())['sha256'],before_reviews_sha256=hashlib.sha256(before['reviews']).hexdigest(),reviews_sha256=hashlib.sha256(after['reviews']).hexdigest(),papers_sha256=hashlib.sha256(after['papers']).hexdigest(),draft_pin=pin(out/'rsubread-use-reviews.json'),all_non_rsubread_reviews_preserved=True,whole_paper_reproduced=False,whole_goal_complete=False)
    if a.apply:
        from rsubread_execution_evidence import verify
        execution,_=verify(False);assert execution['declared_counting_workflow_available'];dest=[data/'rsubread-role-coverage.json',data/'rsubread-prior-reviews.json.gz',data/'rsubread-prior-papers.json.gz',data/'rsubread-reviews-shared-source-compatibility.json',ROOT/'tools/literature/rsubread-use-reviews.json'];assert not any(p.exists() for p in dest) and all(p.read_bytes()==before[k] for k,p in paths.items())
        dest[1].write_bytes(gzip.compress(before['reviews'],mtime=0));dest[2].write_bytes(gzip.compress(before['papers'],mtime=0));report.update(prior_review_archive=pin(dest[1]),prior_papers_archive=pin(dest[2]));row=inverse(before['reviews'],after['reviews']);row['path']='tools/literature/reviewed-uses.json'
        save(dest[3],dict(schema_version=1,status='passed-dispatch-catalog-only-source-deltas',tool_id='rsubread',scope='Three appended and three explicitly refined primary Rsubread reviews; all997 other roles and all250 paper identities/observed memberships remain exact. No scientific source exception.',paths=[row]));dest[4].write_bytes((out/'rsubread-use-reviews.json').read_bytes())
        for k,p in paths.items():p.write_bytes(after[k])
        save(dest[0],report)
    save(out/'COMPLETED.json',report);print('Validated250 primary XML/DOIs; three new and three refined Rsubread reviews;1003 roles and349 pointers; applied='+str(a.apply))


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete primary/source and unrelated-review preservation checks')
    main()
