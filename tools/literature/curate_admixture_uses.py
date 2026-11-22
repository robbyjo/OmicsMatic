"""Verify ADMIXTURE source roles and two separately named result-handling tools."""
import argparse,gzip,hashlib,json,os,tempfile,xml.etree.ElementTree as ET
from pathlib import Path
from collect_omics_review import paragraphs,tool_matches,publication_record,validate_supporting_evidence

ROOT=Path(__file__).resolve().parents[2]
HERE=Path(__file__).resolve().parent
NEW_TOOLS=[
    dict(id='admixtureplotter',name='AdmixturePlotter',pattern=r'(?<![A-Za-z0-9_])AdmixturePlotter(?![A-Za-z0-9_])',role='ADMIXTURE result handling and ancestry-component presentation',current_coverage='missing',existing_source=None,coverage_note='Explicitly named alongside ADMIXTURE in P002;separate implementation and helper-specific settings remain open.'),
    dict(id='clumpp',name='CLUMPP',pattern=r'(?<![A-Za-z0-9_])CLUMPP(?![A-Za-z0-9_])',role='Replicate cluster-membership matrix analysis',current_coverage='missing',existing_source=None,coverage_note='P180 passes membership coefficients from five ADMIXTURE runs;no equivalent validated CLUMPP workflow is available.')]
def sha(path):return hashlib.sha256(Path(path).read_bytes()).hexdigest()
def atomic(path,value):
    with tempfile.NamedTemporaryFile('w',encoding='utf-8',newline='\n',dir=path.parent,delete=False) as stream:temporary=Path(stream.name);stream.write(json.dumps(value,ensure_ascii=False,indent=2)+'\n')
    try:os.replace(temporary,path)
    finally:temporary.unlink(missing_ok=True)
def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True);parser.add_argument('--apply',action='store_true');args=parser.parse_args();args.out.mkdir(parents=True,exist_ok=False)
    data=ROOT/'docs/literature/omics-2023-2026';papers={p['paper_id']:p for p in json.loads((data/'papers.json').read_text(encoding='utf-8'))};registry_path=HERE/'tool-registry.json';registry_bytes=registry_path.read_bytes();registry=json.loads(registry_bytes);old_ids={t['id'] for t in registry};added=[t for t in NEW_TOOLS if t['id'] not in old_ids];assert hashlib.sha256(registry_bytes).hexdigest()=='3de36e33fee93a86d1dfd846bdf31a09a89da19e755b0db95ee4109b30b77350' and len(registry)==415 and len(added)==2,'This intake requires the preserved pre-expansion registry;inspect changed state before reapplying';registry+=added;by_tool={t['id']:t for t in registry}
    review_path=HERE/'reviewed-uses.json';before=review_path.read_bytes();document=json.loads(before);old=list(document['reviews']);rows=json.loads((HERE/'admixture-use-reviews.json').read_text(encoding='utf-8'));assert len(rows)==15 and len(old)==623
    keys={(r['paper_id'],r['review']['tool_id']) for r in rows};observed={(p['paper_id'],t['tool_id']) for p in papers.values() for t in p['tools'] if t['tool_id']=='admixture'};assert len(keys)==15 and observed=={(p,t) for p,t in keys if t=='admixture'};assert keys-observed=={('P002','admixtureplotter'),('P180','clumpp')};existing={(r['pmcid'],r['tool_id']) for r in old};assert all((r['review']['pmcid'],r['review']['tool_id']) not in existing for r in rows)
    proof=[];sources={}
    for row in rows:
        paper=papers[row['paper_id']];review=row['review'];tool=review['tool_id'];assert paper['pmcid']==review['pmcid'] and paper['doi']==review['doi']
        raw=gzip.decompress((ROOT/'build/literature-review/xml'/(paper['pmcid']+'.xml.gz')).read_bytes());assert hashlib.sha256(raw).hexdigest()==paper['xml_sha256'];ps=paragraphs(ET.fromstring(raw));primary=ps[row['primary_paragraph']-1];assert hashlib.sha256(primary['text'].encode()).hexdigest()==review['evidence_paragraph_sha256'];validate_supporting_evidence(ps,[review],by_tool)
        found=tool_matches(ps,[by_tool[tool]],{tool:review['evidence_paragraph_sha256']});assert len(found)==1 and found[0]['evidence_paragraph_index']==row['primary_paragraph'];checked=publication_record(dict(paper,tools=found),[review])['tools'][0]
        for field in ['use_summary','implementation_scope_note','version_reported_near_name']:assert checked[field]==review[field]
        sources[paper['paper_id']]=paper['xml_sha256'];prior=next((t for t in paper['tools'] if t['tool_id']==tool),None);proof.append(dict(paper_id=paper['paper_id'],tool_id=tool,primary_paragraph=row['primary_paragraph'],prior_primary=None if prior is None else prior['evidence_paragraph_index'],paragraph_sha256=review['evidence_paragraph_sha256'],reported_version=review['version_reported_near_name'],observed_role=row['observed_role'],supporting_pointers=len(review.get('supporting_evidence',[]))))
    p=papers['P175'];raw=gzip.decompress((ROOT/'build/literature-review/xml'/(p['pmcid']+'.xml.gz')).read_bytes());assert hashlib.sha256(raw).hexdigest()==p['xml_sha256'];prior=paragraphs(ET.fromstring(raw))[38];assert 'Lo et al.' in prior['text'] and 'ADMIXTURE' in prior['text'];assert not any(t['tool_id']=='admixture' for t in p['tools'])
    exclusion=dict(paper_id='P175',paragraph_index=39,paragraph_sha256=hashlib.sha256(prior['text'].encode()).hexdigest(),status='excluded-prior-study-reference;no-direct-ADMIXTURE-use-in-this-paper',reason='Discussion summarizes Lo et al. rather than reporting a new ADMIXTURE execution in this study.')
    merged=old+[r['review'] for r in rows];assert len(merged)==638 and merged[:623]==old and sum(len(r.get('supporting_evidence',[])) for r in merged)==171
    report=dict(status='passed-admixture-family-source-role-validation',applied=args.apply,reviewed_roles=15,admixture_roles=13,admixtureplotter_roles=1,clumpp_roles=1,added_named_tools=2,supporting_pointers=18,total_reviews=638,total_supporting_pointers=171,preserved_prior_reviews=623,previous_registry_sha256=hashlib.sha256(registry_bytes).hexdigest(),previous_reviews_sha256=hashlib.sha256(before).hexdigest(),review_rows_sha256=sha(HERE/'admixture-use-reviews.json'),curator_sha256=sha(__file__),source_XML_hashes=sources,excluded_prior_study_reference=exclusion,proof=proof,scope='Excerpt-free individually reviewed source roles. Direct fits,supervision,pseudohaploid coding,CV/bootstrap/replicates,crop contexts,prior-group reuse andseparate result helpers remain distinct. No historical/full-paper/runtime equivalence is asserted.')
    if args.apply:
        assert sha(registry_path)==report['previous_registry_sha256'] and sha(review_path)==report['previous_reviews_sha256'];history=ROOT/'build/literature-review/catalog-history';history.mkdir(exist_ok=True)
        for contents,suffix in [(registry_bytes,'-registry.json.gz'),(before,'-reviewed-uses.json.gz')]:
            path=history/(hashlib.sha256(contents).hexdigest()+suffix)
            if not path.exists():path.write_bytes(gzip.compress(contents,mtime=0))
        document['reviews']=merged;atomic(review_path,document);atomic(registry_path,registry);report.update(registry_sha256=sha(registry_path),reviews_sha256=sha(review_path))
    (args.out/'verification.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8');print(json.dumps({k:report[k] for k in ['status','applied','reviewed_roles','added_named_tools','total_reviews','total_supporting_pointers']}))
if __name__=='__main__':main()
