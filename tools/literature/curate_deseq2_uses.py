# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Review 47 retained uses plus two named declarations; preserve every unrelated observation."""
import argparse,copy,gzip,hashlib,json,xml.etree.ElementTree as ET
from pathlib import Path
from collect_omics_review import paragraphs,publication_record,validate_supporting_evidence
from collect_reviewed_omics import tool_matches
from curate_shapeit_uses import inverse,serialize
from catalog_fixed_cohort import verify_fixed_cohort
from catalog_source_integrity import reverse_delta
ROOT=Path(__file__).resolve().parents[2];BASE=ROOT/'build/literature-review/deseq2-runtime';HERE=ROOT/'tools/literature';DATA=ROOT/'docs/literature/omics-2023-2026'
def sha(raw):return hashlib.sha256(raw).hexdigest()

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);p.add_argument('--apply',action='store_true');a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(BASE) and not out.exists();out.mkdir()
    paths=dict(papers=DATA/'papers.json',reviews=HERE/'reviewed-uses.json',enrichment=DATA/'catalog-enrichment.json');before={k:p.read_bytes() for k,p in paths.items()};papers=json.loads(before['papers']);old_reviews=json.loads(before['reviews']);drafts=json.loads((HERE/'deseq2-use-reviews.json').read_bytes());registry=json.loads((HERE/'tool-registry.json').read_bytes())
    assert (len(papers),len(registry),len(old_reviews['reviews']),len(drafts))==(250,425,832,49);verify_fixed_cohort(ROOT,DATA,papers)
    old_ds=[r for r in old_reviews['reviews'] if r['tool_id']=='deseq2'];assert len(old_ds)==1 and old_ds[0]['pmcid']=='PMC11525197'
    reviews=copy.deepcopy(old_reviews);by_draft={(d['review']['pmcid'],d['review']['tool_id']):d['review'] for d in drafts};assert len(by_draft)==49
    reviews['reviews']=[by_draft.get((r['pmcid'],r['tool_id']),r) for r in reviews['reviews']];oldkeys={(r['pmcid'],r['tool_id']) for r in old_reviews['reviews']};reviews['reviews'].extend(d['review'] for d in drafts if (d['review']['pmcid'],d['review']['tool_id']) not in oldkeys)
    assert len(reviews['reviews'])==880 and [r for r in reviews['reviews'] if r['tool_id']!='deseq2']==[r for r in old_reviews['reviews'] if r['tool_id']!='deseq2']
    draft_by={r['review']['pmcid']:r for r in drafts};preferred={(r['pmcid'],r['tool_id']):r['evidence_paragraph_sha256'] for r in reviews['reviews']};updated=copy.deepcopy(papers);cache_updates={};source_proof=[];changed=[];added=[]
    for original,paper in zip(papers,updated):
        raw=gzip.decompress((ROOT/'build/literature-review/xml'/(paper['pmcid']+'.xml.gz')).read_bytes());assert sha(raw)==paper['xml_sha256']
        if paper['pmcid'] not in draft_by:assert paper==original;continue
        draft=draft_by[paper['pmcid']];review=draft['review'];ps=paragraphs(ET.fromstring(raw));prefs={tool:h for (pmc,tool),h in preferred.items() if pmc==paper['pmcid']};found=tool_matches(ps,registry,prefs);by={t['tool_id']:t for t in found};old={t['tool_id']:t for t in original['tools']};isnew='deseq2' not in old
        assert set(by)==set(old)|{'deseq2'}
        for name,t in old.items():assert (t['evidence_paragraph_index'],t['evidence_paragraph_sha256'])==(by[name]['evidence_paragraph_index'],sha(by[name]['evidence_text'].encode('utf8'))),(paper['paper_id'],name)
        candidate=by['deseq2'];assert (draft['paper_id'],review['doi'],review['evidence_paragraph_sha256'])==(paper['paper_id'],paper['doi'],sha(candidate['evidence_text'].encode('utf8')));validate_supporting_evidence(ps,[review],{r['id']:r for r in registry});published=publication_record(dict(paper,tools=[candidate]),[review])['tools'][0]
        paper['tools']=sorted([t for t in paper['tools'] if t['tool_id']!='deseq2']+[published],key=lambda t:t['tool_id']);assert [t for t in original['tools'] if t['tool_id']!='deseq2']==[t for t in paper['tools'] if t['tool_id']!='deseq2'];assert {k:v for k,v in original.items() if k!='tools'}=={k:v for k,v in paper.items() if k!='tools'}
        cache=ROOT/'build/literature-review/extracted'/(paper['pmcid']+'.json');old_cache=cache.read_bytes();cached=json.loads(old_cache);assert sum(t['tool_id']=='deseq2' for t in cached['tools'])==(0 if isnew else 1);cached['tools']=sorted([t for t in cached['tools'] if t['tool_id']!='deseq2']+[candidate],key=lambda t:t['tool_id']);cache_updates[cache]=(old_cache,serialize(cached,old_cache));changed.append(paper['paper_id'])
        if isnew:added.append(paper['paper_id'])
        source_proof.append(dict(paper_id=paper['paper_id'],pmcid=paper['pmcid'],doi=paper['doi'],xml_sha256=paper['xml_sha256'],paragraph_index=candidate['evidence_paragraph_index'],paragraph_sha256=review['evidence_paragraph_sha256'],newly_recovered_declaration=isnew,role_unresolved=paper['paper_id']=='P082',supporting_pointers=len(review['supporting_evidence'])))
    assert len(changed)==49 and added==['P082','P088'] and len(cache_updates)==49 and sum(len(p['tools']) for p in updated)==2444 and len({t['tool_id'] for p in updated for t in p['tools']})==387;verify_fixed_cohort(ROOT,DATA,updated)
    prior=json.loads(before['enrichment']);enrichment=dict(previous_catalog_sha256=sha(before['papers']),registry_sha256=sha((HERE/'tool-registry.json').read_bytes()),source_cohort_unchanged=True,paper_count=250,history=prior.get('history',[])+[{k:v for k,v in prior.items() if k!='history'}],changes=[dict(paper_id=pid,reviewed_ids=['deseq2'],newly_recovered_named_declaration=pid in added) for pid in changed],extension_method='All250 retained XML identities checked; unchanged425-row registry and exact original47 DESeq2 anchors preserved. A separate exact reviewed-declaration collector adds only P082/P088 Code availability statements. P082 exact role remains unresolved; publisher supplement establishes P088 normalization before limma-voom. Replace one prior DESeq2 review, append48; preserve831 unrelated reviews and every unrelated paper observation. No implementation credit by curation.')
    after=dict(papers=serialize(updated,before['papers']),reviews=serialize(reviews,before['reviews']),enrichment=serialize(enrichment,before['enrichment']));row=inverse(before['reviews'],after['reviews']);assert reverse_delta(after['reviews'],row)==before['reviews'];bridge=dict(schema_version=1,status='passed-dispatch-catalog-only-source-deltas',tool_id='deseq2',scope='Update one existing DESeq2 review and append48 checked primary roles, preserving831 unrelated reviews. Every prior review-file byte reconstructs. No scientific source waiver or implementation credit.',paths=[row])
    report=dict(schema_version=1,status='passed-49-DESeq2-primary-role-reviews',applied=False,source_papers_XML_hashes_checked=250,selected_papers_reextracted=49,registry_rows=425,distinct_named_tools=387,prior_observations=2442,total_observations=2444,prior_reviews=832,unrelated_reviews_preserved=831,prior_DESeq2_reviews_updated=1,new_reviews=48,total_reviews=880,supporting_pointers=sum(len(r.get('supporting_evidence',[])) for r in reviews['reviews']),DESeq2_supporting_pointers=35,reviewed_roles=49,recovered_named_declarations=added,unresolved_P082_exact_role=True,P088_normalization_not_limma_tests=True,count_unit_discrepancies_preserved=['P086','P108','P226','P249'],source_proof=source_proof,curator_sha256=sha(Path(__file__).read_bytes()),draft_sha256=sha((HERE/'deseq2-use-reviews.json').read_bytes()),implementation_promoted=False,whole_paper_reproduced=False)
    for name,path in paths.items():(out/('before-'+path.name+'.gz')).write_bytes(gzip.compress(before[name],mtime=0))
    for path,(raw,_) in cache_updates.items():(out/('PRIVATE-before-cache-'+path.name+'.gz')).write_bytes(gzip.compress(raw,mtime=0))
    if a.apply:
        target=DATA/'deseq2-use-shared-source-compatibility.json';roles=DATA/'deseq2-role-coverage.json';assert not target.exists() and not roles.exists() and all(p.read_bytes()==before[k] for k,p in paths.items()) and all(p.read_bytes()==v[0] for p,v in cache_updates.items());target.write_text(json.dumps(bridge,indent=2)+'\n',encoding='utf8',newline='\n')
        for k,path in paths.items():path.write_bytes(after[k])
        for path,(_,raw) in cache_updates.items():path.write_bytes(raw)
        report.update(applied=True,papers_sha256=sha(after['papers']),reviews_sha256=sha(after['reviews']));roles.write_text(json.dumps(report,indent=2)+'\n',encoding='utf8',newline='\n')
    (out/'verification.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf8',newline='\n');print(json.dumps({k:v for k,v in report.items() if k!='source_proof'}))

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable exact primary review/cohort checks')
    main()
