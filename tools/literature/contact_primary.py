"""Verify all 250 exact primary XMLs and the eight contactomics source-role summaries."""
from pathlib import Path
import argparse,gzip,json,re,sys,xml.etree.ElementTree as ET
import collect_omics_review as collector
from gemma_primary import encoded,pin,require

ROOT=Path(__file__).resolve().parents[2]
FOLDER='docs/literature/contact-insulation-source-audit'
PATTERNS={k:re.compile(v,re.I)for k,v in dict(allcools=r'(?<![A-Za-z0-9_])all[\W_]*cools(?![A-Za-z0-9_])',cooltools=r'(?<![A-Za-z0-9_])cool[\W_]*tools(?![A-Za-z0-9_])').items()}
EXPECTED={'allcools':{'P047','P053','P060','P061'},'cooltools':{'P003','P053','P169','P228'}}
MUTABLE={'review_status','use_summary','implementation_scope_note','evidence_selection','supporting_evidence','source_role_qualification'}

def audit(root,cache):
    root,cache=Path(root),Path(cache);census=json.loads((root/FOLDER/'CENSUS.json').read_bytes())
    roles=json.loads((root/FOLDER/'ROLES.json').read_bytes())['roles']
    papers=json.loads((root/'docs/literature/omics-2023-2026/papers.json').read_bytes())
    require(pin(Path(collector.__file__).read_bytes())==census['collector'],'Running paragraph collector differs from the selected source parser')
    require(len(papers)==len(census['XML'])==250 and [p['paper_id']for p in papers]==list(census['XML']),'Changed frozen primary cohort')
    require(census['patterns']=={k:p.pattern for k,p in PATTERNS.items()},'Changed full-cohort name census')
    hits={tool:[]for tool in PATTERNS};other={tool:[]for tool in PATTERNS};anchors={}
    for paper in papers:
        expected=census['XML'][paper['paper_id']]
        require(paper['pmcid']==expected['pmcid']and paper['doi']==expected['doi']and paper['xml_sha256']==expected['sha256'],'Changed paper/XML identity')
        raw=gzip.decompress((cache/(paper['pmcid']+'.xml.gz')).read_bytes())
        require(pin(raw)=={k:expected[k]for k in ('bytes','sha256')},'Changed exact primary XML; preserve historical hashes')
        tree=ET.fromstring(raw)
        for paragraph in collector.paragraphs(tree):
            item=dict(paper_id=paper['paper_id'],doi=paper['doi'],pmcid=paper['pmcid'],xml_sha256=paper['xml_sha256'],paragraph_index=paragraph['paragraph_index'],section=paragraph['section'],paragraph_sha256=pin(paragraph['text'].encode())['sha256'])
            anchors[paper['paper_id'],paragraph['paragraph_index']]=item
            for tool,pattern in PATTERNS.items():
                if pattern.search(paragraph['match_text']):hits[tool].append(item)
        for location in ('front/article-meta/abstract','back','floats-group','body//supplementary-material'):
            for element in tree.findall(location):
                text=collector.matching_words(element)
                for tool,pattern in PATTERNS.items():
                    if pattern.search(text):other[tool].append(dict(paper_id=paper['paper_id'],location=location))
    require(hits==census['anchors']and other==census['other_structures'],'Changed full primary name census or nonbody pointers')
    require(len(hits['allcools'])==18 and len(hits['cooltools'])==6,'Changed direct context population')
    require({tool:{row['paper_id']for row in rows}for tool,rows in hits.items()}==EXPECTED,'Changed positive paper set')
    require(len(roles)==len({(r['paper_id'],r['tool_id'])for r in roles})==8,'Different or duplicate reviewed role set')
    for tool,ids in EXPECTED.items():require({r['paper_id']for r in roles if r['tool_id']==tool}==ids,'Source roles lost or added a paper')
    claimed=set()
    for role in roles:
        require(role['tool_id']in EXPECTED and role['whole_paper_reproduced']is False and not role['implementation_available'],'Primary review expanded into runtime admission')
        paper=next(p for p in papers if p['paper_id']==role['paper_id']);require(paper['pmcid']==role['pmcid']and paper['doi']==role['doi'],'Role paper identity differs')
        uses=[u for u in paper['tools']if u['tool_id']==role['tool_id']];require(len(uses)==1,'Missing/duplicate existing tool observation')
        raw=role['raw_catalog_observation_preserved']
        require(all(uses[0].get(k)==value for k,value in raw.items()if k not in MUTABLE),'Raw source/near-name version was modified')
        require(role['remaining_scope']and role['use_summary']and role['source_role_review_status']=='MANUAL_FULL_DIRECT_CONTEXT_REVIEW_OTHER_STRUCTURE_POINTERS_RETAINED','Incomplete semantic review')
        for anchor in role['primary_anchors']:
            require(anchors.get((anchor['paper_id'],anchor['paragraph_index']))==anchor,'Role anchor differs from complete primary XML')
            claimed.add((role['tool_id'],anchor['paper_id'],anchor['paragraph_index']))
    require(claimed=={(tool,a['paper_id'],a['paragraph_index'])for tool,rows in hits.items()for a in rows},'A direct matching source paragraph is unaccounted for')
    by_pair={(r['paper_id'],r['tool_id']):r for r in roles}
    require(by_pair['P003','cooltools']['literal_package_version_reported_elsewhere']is None and by_pair['P003','cooltools']['component_version_exceptions']==dict(dots='0.5.1',other_cooltools_roles=None),'Unreported insulation edition was replaced by dots edition')
    require(by_pair['P053','cooltools']['literal_package_version_reported_elsewhere']=='0.5.1'and by_pair['P169','cooltools']['literal_package_version_reported_elsewhere']=='0.5.4','Changed reported cooltools editions')
    require(by_pair['P228','cooltools']['distinct_uses']==['eigs-cis compartment analysis'],'Surrounding TAD operations were assigned to cooltools')
    require('methylpy'in by_pair['P053','allcools']['use_summary']and'250bp/PCC>0.3'in by_pair['P061','allcools']['remaining_scope'],'Distinct methylation callers/recipes were conflated')
    return dict(status='PASS_FULL250_CONTACTOMICS_SOURCE_ROLE_CENSUS',papers=250,direct_contexts=24,reviewed_roles=8,unique_role_papers=7,other_structure_pointers=2,new_source_role_reviews=7,existing_source_review_expanded=1,new_papers_or_observations=0,whole_paper_reproduced=False,whole_goal_complete=False)

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--root',type=Path,default=ROOT);parser.add_argument('--cache',type=Path,required=True);args=parser.parse_args()
    try:print(json.dumps(audit(args.root,args.cache),sort_keys=True));return 0
    except Exception as error:
        code=3 if isinstance(error,(FileNotFoundError,PermissionError))else 2
        print(('SKIP3 DEPENDENT PRIMARY AUDIT SKIPPED: 'if code==3 else'FAIL2 NO PRIMARY AUDIT ADMITTED: ')+str(error)+'. Required action: restore the exact permitted complete XMLs and selected parser/source context. Preserve old hashes and attempts; do not replace a missing paper or infer an author version.',file=sys.stderr);return code

if __name__=='__main__':raise SystemExit(main())
