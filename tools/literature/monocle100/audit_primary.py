"""Fresh all-250 XML/DOI audit for the nine retained Monocle source roles; no catalog mutation."""
from pathlib import Path
import argparse,gzip,hashlib,json,re,sys,xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parents[3]
sys.path.insert(0,str(ROOT/'tools/literature'))
from collect_omics_review import paragraphs
from catalog_fixed_cohort import verify_fixed_cohort
from twosamplemr_catalog.model import registry
from setup import require,pin,save

EXPECTED=['P021','P024','P043','P051','P085','P099','P117','P134','P233']
def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--cache',type=Path,required=True);parser.add_argument('--out',type=Path,required=True);args=parser.parse_args()
    out=args.out.resolve();require(out.is_relative_to(ROOT/'build') and out!=ROOT/'build','Use NEW workspace build output');out.mkdir(parents=True,exist_ok=False)
    data=ROOT/'docs/literature/omics-2023-2026';papers=json.loads((data/'papers.json').read_bytes());verify_fixed_cohort(ROOT,data,papers)
    require(len(papers)==250 and len({p['doi'].lower() for p in papers})==250,'Different frozen source population')
    pattern=next(row['pattern'] for row in registry() if row['id']=='monocle');records=[];matches=[]
    for paper in papers:
        raw=gzip.decompress((args.cache/(paper['pmcid']+'.xml.gz')).read_bytes());require(hashlib.sha256(raw).hexdigest()==paper['xml_sha256'],'Changed source XML: '+paper['paper_id'])
        xml=ET.fromstring(raw);doi=next(node.text for node in xml.findall('front/article-meta/article-id') if node.get('pub-id-type')=='doi');require(doi.lower()==paper['doi'].lower(),'Changed source DOI')
        ps=paragraphs(xml);by_index={row['paragraph_index']:row for row in ps};hits=[row for row in ps if re.search(pattern,row['match_text'],re.I)]
        if hits:matches.append(dict(paper_id=paper['paper_id'],paragraphs=[dict(index=row['paragraph_index'],sha256=hashlib.sha256(row['text'].encode()).hexdigest()) for row in hits]))
        uses=[row for row in paper['tools'] if row['tool_id']=='monocle']
        if uses:
            require(len(uses)==1,'Duplicate Monocle observation');use=uses[0];anchor=by_index[use['evidence_paragraph_index']]
            require(hashlib.sha256(anchor['text'].encode()).hexdigest()==use['evidence_paragraph_sha256'],'Changed original source pointer')
            for support in use.get('supporting_evidence',[]):
                index=support.get('paragraph_index',support.get('evidence_paragraph_index'));wanted=support.get('paragraph_sha256',support.get('evidence_paragraph_sha256'))
                require(hashlib.sha256(by_index[index]['text'].encode()).hexdigest()==wanted,'Changed supporting source pointer')
            records.append(dict(paper_id=paper['paper_id'],pmcid=paper['pmcid'],doi=paper['doi'],xml_sha256=paper['xml_sha256'],paragraph_index=use['evidence_paragraph_index'],paragraph_sha256=use['evidence_paragraph_sha256'],reported_edition=use['version_reported_near_name'],current_review=use))
    require([row['paper_id'] for row in records]==EXPECTED,'Different retained Monocle role population')
    require([row['paper_id'] for row in matches]==EXPECTED,'Unexpected additional named source: review before excluding it')
    result=dict(status='PASS_FRESH250_MONOCLE9_XML_DOI_AND_SOURCE_POINTERS',papers=250,roles=records,all_named_match_pointers=matches,source_profile='Monocle3 1.0.0 004c096f3a3bdaf1e6709a65fb36f40ba775f1ab',P021_embedding_label_retained='UMAT',P021_current_author_commit_is_not_proven_paper_commit=True,whole_Monocle_or_author_pipeline_qualified=False,new_catalog_credit=0,whole_goal_complete=False,source_inputs={str(path.relative_to(ROOT).as_posix()):pin(path) for path in [Path(__file__),ROOT/'tools/literature/collect_omics_review.py',data/'papers.json',ROOT/'tools/literature/reviewed-uses.json']})
    save(out/'RESULT.json',result);print('PASS fresh250 XML/DOI audit and nine retained Monocle source roles')

if __name__=='__main__':
    require(__debug__,'Use Python without -O');main()
