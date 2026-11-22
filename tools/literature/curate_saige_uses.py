#!/usr/bin/env python3
"""Validate SAIGE,SAIGE-GENE(+) and Meta-SAIGE source roles without exporting excerpts."""
import argparse,gzip,hashlib,json,os,tempfile,xml.etree.ElementTree as ET
from pathlib import Path
from collect_omics_review import paragraphs,tool_matches,publication_record,validate_supporting_evidence

ROOT=Path(__file__).resolve().parents[2]
HERE=Path(__file__).resolve().parent
def sha(path):return hashlib.sha256(Path(path).read_bytes()).hexdigest()
def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',required=True,type=Path);parser.add_argument('--apply',action='store_true');args=parser.parse_args();args.out.mkdir(parents=True,exist_ok=False)
    data=ROOT/'docs/literature/omics-2023-2026';papers={p['paper_id']:p for p in json.loads((data/'papers.json').read_text(encoding='utf-8'))};registry={t['id']:t for t in json.loads((HERE/'tool-registry.json').read_text(encoding='utf-8'))};rows_path=HERE/'saige-use-reviews.json';rows=json.loads(rows_path.read_text(encoding='utf-8'));family={'saige','saige-gene','metasaige'};keys={(r['paper_id'],r['review']['tool_id']) for r in rows};observed={(p['paper_id'],t['tool_id']) for p in papers.values() for t in p['tools'] if t['tool_id'] in family};assert len(rows)==len(keys)==19;assert keys==observed|{('P147','saige')};assert {(r['paper_id'],r['review']['tool_id']) for r in rows if r.get('new_source_role')}=={('P147','saige')}
    path=HERE/'reviewed-uses.json';before=path.read_bytes();original_sha=hashlib.sha256(before).hexdigest();document=json.loads(before);merged=list(document['reviews']);by_key={(r['pmcid'],r['tool_id']):r for r in merged};assert len(by_key)==len(merged);proof=[]
    for row in rows:
        p=papers[row['paper_id']];review=row['review'];tool=review['tool_id'];assert tool in family and p['pmcid']==review['pmcid'] and p['doi']==review['doi'];raw=gzip.decompress((ROOT/'build/literature-review/xml'/(p['pmcid']+'.xml.gz')).read_bytes());assert hashlib.sha256(raw).hexdigest()==p['xml_sha256'];source=paragraphs(ET.fromstring(raw));primary=source[row['primary_paragraph']-1];assert primary['paragraph_index']==row['primary_paragraph'] and hashlib.sha256(primary['text'].encode()).hexdigest()==review['evidence_paragraph_sha256'];validate_supporting_evidence(source,[review],registry)
        found=tool_matches(source,[registry[tool]],{tool:review['evidence_paragraph_sha256']});assert len(found)==1 and found[0]['evidence_paragraph_index']==row['primary_paragraph'],(row['paper_id'],tool,found);checked=publication_record(dict(p,tools=found),[review])['tools'][0]
        for field in ['use_summary','implementation_scope_note','version_reported_near_name']:assert checked[field]==review[field],(row['paper_id'],field)
        key=(review['pmcid'],tool)
        if key in by_key:assert by_key[key]==review,('Existing review changed;inspect separately',key)
        else:merged.append(review);by_key[key]=review
        current=next((t for t in p['tools'] if t['tool_id']==tool),None);proof.append(dict(paper_id=row['paper_id'],tool_id=tool,role=row['observed_role'],primary_paragraph=row['primary_paragraph'],evidence_sha256=review['evidence_paragraph_sha256'],prior_primary=None if current is None else current['evidence_paragraph_index'],supporting_pointers=len(review.get('supporting_evidence',[])),reported_version=review['version_reported_near_name']))
    result=dict(status='passed-source-role-validation',applied=args.apply,source_roles=19,saige_roles=16,saige_gene_roles=2,meta_saige_roles=1,supporting_pointers=26,total_reviews=len(merged),added_reviews=len(merged)-len(document['reviews']),original_reviews_sha256=original_sha,proof=proof,curator_sha256=sha(__file__),review_rows_sha256=sha(rows_path),scope='Excerpt-free source-role audit. Direct GWAS/GRM runs,null-model framework reuse,burden/gene tests,LD and published-summary reuse remain distinct. One direct Results summary-reuse role P147 is added. P141 primary anchors move to explicit Methods while supporting Results preserve actual score/gene uses. Versions/citations and multiple versions per paper are not conflated. Runtime integration/paper-specific reproduction remain pending.')
    if args.apply:
        assert sha(path)==original_sha,'Reviews changed during validation';history=ROOT/'build/literature-review/catalog-history';history.mkdir(exist_ok=True);archive=history/(original_sha+'-reviewed-uses.json.gz')
        if not archive.exists():archive.write_bytes(gzip.compress(before,mtime=0))
        document['reviews']=merged
        with tempfile.NamedTemporaryFile('w',encoding='utf-8',dir=path.parent,delete=False) as stream:temporary=Path(stream.name);stream.write(json.dumps(document,ensure_ascii=False,indent=2)+'\n')
        try:assert sha(path)==original_sha,'Reviews changed before publication';os.replace(temporary,path)
        finally:temporary.unlink(missing_ok=True)
        result['reviews_sha256']=sha(path)
    (args.out/'verification.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8');print(json.dumps({key:result[key] for key in ['status','applied','total_reviews','added_reviews']}))
if __name__=='__main__':main()
