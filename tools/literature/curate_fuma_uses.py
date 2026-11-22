"""Verify fourteen FUMA source roles; revise seven prior wrapper-only reviews."""
import argparse,gzip,hashlib,json,os,tempfile,xml.etree.ElementTree as ET
from pathlib import Path
from collect_omics_review import paragraphs,tool_matches,publication_record,validate_supporting_evidence

ROOT=Path(__file__).resolve().parents[2]
HERE=Path(__file__).resolve().parent
def sha(path):return hashlib.sha256(Path(path).read_bytes()).hexdigest()
def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True);parser.add_argument('--apply',action='store_true');args=parser.parse_args();args.out.mkdir(parents=True,exist_ok=False)
    data=ROOT/'docs/literature/omics-2023-2026';papers={p['paper_id']:p for p in json.loads((data/'papers.json').read_text(encoding='utf-8'))};registry_path=HERE/'tool-registry.json';registry=json.loads(registry_path.read_text(encoding='utf-8'));by_tool={t['id']:t for t in registry};assert sha(registry_path)=='d42966377e8efb908d2b125e8b9fff05b97330289eff6689a287333071271cd4'
    review_path=HERE/'reviewed-uses.json';before=review_path.read_bytes();document=json.loads(before);old=document['reviews'];assert hashlib.sha256(before).hexdigest()=='e4d7db7b6f735fefa4c4b5853e0bf5f2728dbde85d91e7732dad5e033fa6d435' and len(old)==638
    rows=json.loads((HERE/'fuma-use-reviews.json').read_text(encoding='utf-8'));keys={r['paper_id'] for r in rows};observed={p['paper_id'] for p in papers.values() if any(t['tool_id']=='fuma' for t in p['tools'])};assert len(rows)==14 and keys==observed
    proof=[];sources={};replacement={r['review']['pmcid']:r['review'] for r in rows};assert len(replacement)==14
    for row in rows:
        paper=papers[row['paper_id']];review=row['review'];assert paper['pmcid']==review['pmcid'] and paper['doi']==review['doi']
        raw=gzip.decompress((ROOT/'build/literature-review/xml'/(paper['pmcid']+'.xml.gz')).read_bytes());assert hashlib.sha256(raw).hexdigest()==paper['xml_sha256'];ps=paragraphs(ET.fromstring(raw));primary=ps[row['primary_paragraph']-1];assert hashlib.sha256(primary['text'].encode()).hexdigest()==review['evidence_paragraph_sha256'];validate_supporting_evidence(ps,[review],by_tool)
        found=tool_matches(ps,[by_tool['fuma']],{'fuma':review['evidence_paragraph_sha256']});assert len(found)==1 and found[0]['evidence_paragraph_index']==row['primary_paragraph'];checked=publication_record(dict(paper,tools=found),[review])['tools'][0]
        for field in ['use_summary','implementation_scope_note','version_reported_near_name']:assert checked[field]==review[field]
        prior=next(t for t in paper['tools'] if t['tool_id']=='fuma');sources[paper['paper_id']]=paper['xml_sha256'];proof.append(dict(paper_id=paper['paper_id'],primary_paragraph=row['primary_paragraph'],prior_primary=prior['evidence_paragraph_index'],paragraph_sha256=review['evidence_paragraph_sha256'],reported_version=review['version_reported_near_name'],observed_role=row['observed_role'],supporting_pointers=len(review['supporting_evidence'])))
    prior=[r for r in old if r['tool_id']=='fuma'];assert len(prior)==7 and all(r['pmcid'] in replacement for r in prior)
    merged=[replacement[r['pmcid']] if r['tool_id']=='fuma' else r for r in old];existing={r['pmcid'] for r in prior};merged += [r['review'] for r in rows if r['review']['pmcid'] not in existing]
    assert len(merged)==645 and [r for r in merged if r['tool_id']!='fuma']==[r for r in old if r['tool_id']!='fuma'] and sum(len(r.get('supporting_evidence',[])) for r in merged)==187
    report=dict(status='passed-fuma-source-role-validation',applied=args.apply,reviewed_roles=14,revised_prior_roles=7,new_roles=7,supporting_pointers=16,total_reviews=645,total_supporting_pointers=187,preserved_other_reviews=631,registry_sha256=sha(registry_path),previous_reviews_sha256=hashlib.sha256(before).hexdigest(),review_rows_sha256=sha(HERE/'fuma-use-reviews.json'),curator_sha256=sha(__file__),source_XML_hashes=sources,proof=proof,scope='Excerpt-free individually reviewed roles. Locus thresholds, LD references, MAGMA, GENE2FUNC and separately performed follow-up remain distinct. Source releases do not certify historical runtime or full-paper equivalence.')
    if args.apply:
        assert sha(review_path)==report['previous_reviews_sha256'];history=ROOT/'build/literature-review/catalog-history';history.mkdir(exist_ok=True);path=history/(report['previous_reviews_sha256']+'-reviewed-uses.json.gz')
        if not path.exists():path.write_bytes(gzip.compress(before,mtime=0))
        document['reviews']=merged
        with tempfile.NamedTemporaryFile('w',encoding='utf-8',newline='\n',dir=review_path.parent,delete=False) as stream:temporary=Path(stream.name);stream.write(json.dumps(document,ensure_ascii=False,indent=2)+'\n')
        try:os.replace(temporary,review_path)
        finally:temporary.unlink(missing_ok=True)
        report['reviews_sha256']=sha(review_path)
    (args.out/'verification.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8');print(json.dumps({k:report[k] for k in ['status','applied','reviewed_roles','revised_prior_roles','new_roles','total_reviews','total_supporting_pointers']}))
if __name__=='__main__':main()
