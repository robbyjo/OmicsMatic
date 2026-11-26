"""Read-only full250 DiffBind paper-role audit; no installs or scientific analyses."""
from pathlib import Path
import argparse,gzip,json,re,sys,xml.etree.ElementTree as ET
import collect_omics_review as collector
from gemma_primary import pin,require
ROOT=Path(__file__).resolve().parents[2]
FOLDER='docs/literature/diffbind-source-audit'
PATTERN=re.compile(r'(?<![a-z0-9_])diff[\s-]*bind(?![a-z0-9_])',re.I)
PAIRS={'P020','P046','P155','P163'}
MUTABLE={'review_status','use_summary','implementation_scope_note','evidence_selection','supporting_evidence','source_role_qualification'}

def audit(root,cache):
 root,cache=Path(root),Path(cache);census=json.loads((root/FOLDER/'CENSUS.json').read_bytes());roles=json.loads((root/FOLDER/'ROLES.json').read_bytes())['roles'];papers=json.loads((root/'docs/literature/omics-2023-2026/papers.json').read_bytes())
 require(pin(Path(collector.__file__).read_bytes())==census['collector'],'Paragraph collector changed; restore the matching source context')
 require(census['frozen_interval']==['2023-10-23','2026-10-23']and census['pattern']==PATTERN.pattern,'Changed frozen cohort interval or alias pattern')
 require(len(papers)==len(census['XML'])==250 and[p['paper_id']for p in papers]==list(census['XML']),'Changed full cohort identity/order')
 hits=[];other=[];all_anchors={};references={}
 for paper in papers:
  expected=census['XML'][paper['paper_id']];require(paper['pmcid']==expected['pmcid']and paper['doi']==expected['doi']and paper['xml_sha256']==expected['sha256']and re.fullmatch(r'PMC[0-9]+',paper['pmcid']),'Changed original paper/XML identity');require(0<expected['bytes']<=33554432,'Complete XML capacity exceeded')
  with gzip.open(cache/(paper['pmcid']+'.xml.gz'),'rb')as stream:raw=stream.read(expected['bytes']+1)
  require(pin(raw)=={k:expected[k]for k in('bytes','sha256')},'Missing, changed, extra or truncated original XML bytes');tree=ET.fromstring(raw)
  require(any((element.text or'').strip()==paper['doi']for element in tree.findall('front/article-meta/article-id')),'Original DOI differs')
  for q in collector.paragraphs(tree):
   anchor=dict(paper_id=paper['paper_id'],doi=paper['doi'],pmcid=paper['pmcid'],xml_sha256=paper['xml_sha256'],paragraph_index=q['paragraph_index'],section=q['section'],paragraph_sha256=pin(q['text'].encode())['sha256']);all_anchors[paper['paper_id'],q['paragraph_index']]=anchor
   if PATTERN.search(q['match_text']):hits.append(anchor)
  for location in('front/article-meta/abstract','back','floats-group','body//supplementary-material'):
   for element in tree.findall(location):
    if PATTERN.search(collector.matching_words(element)):other.append(dict(paper_id=paper['paper_id'],location=location,sha256=pin(ET.tostring(element))['sha256']))
  if paper['paper_id']=='P155':
   refs=[element for element in tree.findall('.//ref')if element.get('id')=='CR42'];require(len(refs)==1,'Changed P155 package-reference population');references['P155']=dict(paper_id='P155',reference_id='CR42',label='42',sha256=pin(ET.tostring(refs[0]))['sha256'])
 require(hits==census['anchors']and other==census['other_structures'],'Complete source contexts or non-body pointers changed')
 require(len(hits)==5 and len(other)==2 and{r['paper_id']for r in hits}==PAIRS,'Changed complete DiffBind mention population')
 require(len(roles)==4 and{r['paper_id']for r in roles}==PAIRS,'Missing or duplicated reviewed role');claimed=set();by_id={r['paper_id']:r for r in roles}
 for role in roles:
  require(role['tool_id']=='diffbind'and role['whole_paper_reproduced']is False and role['implementation_available']is False and role['implementation_status']=='PENDING_RUNTIME_QUALIFICATION_NO_CATALOG_CREDIT','Source review expanded into runtime admission')
  require(role['source_role_review_status']=='MANUAL_FULL_DIRECT_CONTEXT_REVIEW_OTHER_STRUCTURE_POINTERS_RETAINED'and role['use_summary']and role['remaining_scope'],'Incomplete role review')
  paper=next(p for p in papers if p['paper_id']==role['paper_id']);require((paper['doi'],paper['pmcid'])==(role['doi'],role['pmcid']),'Role identity differs');uses=[u for u in paper['tools']if u['tool_id']=='diffbind'];require(len(uses)==1,'Changed existing observation population')
  require(all(uses[0].get(k)==value for k,value in role['raw_catalog_observation_preserved'].items()if k not in MUTABLE),'Raw version/source fields changed')
  for anchor in role['primary_anchors']:require(all_anchors.get((paper['paper_id'],anchor['paragraph_index']))==anchor,'Primary paragraph anchor changed');claimed.add((paper['paper_id'],anchor['paragraph_index']))
  for anchor in role['other_method_anchors']:require(all_anchors.get((paper['paper_id'],anchor['paragraph_index']))==anchor,'Cross-section design anchor changed')
 require(claimed=={(r['paper_id'],r['paragraph_index'])for r in hits},'A complete matching context was omitted')
 require({k:v['literal_package_version_reported_elsewhere']for k,v in by_id.items()}==dict(P020='3.0.15',P046='3.4.11',P155=None,P163='3.12.0'),'Reported versions were inferred or altered')
 require(by_id['P020']['inference_engine_reported']is None and by_id['P155']['inference_engine_reported']is None and by_id['P046']['inference_engine_reported']==by_id['P163']['inference_engine_reported']=='DESeq2','An unspecified engine was replaced by a package default')
 require(by_id['P163']['single_sample_custom_script_is_separate']and by_id['P046']['formal_design_referred_to_other_section'],'Single-sample or cross-section design scope changed')
 require({(role['paper_id'],anchor['paragraph_index'])for role in roles for anchor in role['other_method_anchors']}=={('P046',30)}and by_id['P155']['method_reference']==references['P155'],'The design source or unversioned package citation was lost')
 require(sum(r['source_review_previously_present']for r in roles)==1 and by_id['P163']['source_review_previously_present'],'Changed source-review delta')
 return dict(status='PASS_FULL250_DIFFBIND_SOURCE_ROLE_CENSUS',papers=250,direct_contexts=5,reviewed_roles=4,other_structure_pointers=2,new_source_reviews_eligible=3,existing_source_review_expanded=1,new_papers_or_observations=0,runtime_admission=False,whole_goal_complete=False)

def main():
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--root',type=Path,default=ROOT);parser.add_argument('--cache',type=Path,required=True);args=parser.parse_args()
 try:print(json.dumps(audit(args.root,args.cache),sort_keys=True));return 0
 except Exception as error:
  code=3 if isinstance(error,(FileNotFoundError,PermissionError))else 2;print(('SKIP3 DEPENDENT DIFFBIND SOURCE AUDIT SKIPPED: 'if code==3 else'FAIL2 NO DIFFBIND SOURCE AUDIT ADMITTED: ')+str(error)+'. Required action: restore the exact permitted250XMLs and matching parser/source context; retain historical hashes and failed attempts. Do not substitute papers or infer author settings.',file=sys.stderr);return code
if __name__=='__main__':raise SystemExit(main())
