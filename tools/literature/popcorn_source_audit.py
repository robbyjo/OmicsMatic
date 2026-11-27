"""Verify complete frozen250 Popcorn primary-text contexts; keep article text private."""
import argparse,gzip,hashlib,json,re,sys,xml.etree.ElementTree as ET
from pathlib import Path
import collect_omics_review as collector
from catalog_fixed_cohort import verify_fixed_cohort
ROOT=Path(__file__).resolve().parents[2];FOLDER='docs/literature/popcorn-source-audit';PAPERS={'P035','P105','P112','P150','P199'}
PATTERN=re.compile(r'(?<![A-Za-z0-9])Popcorn(?![A-Za-z])',re.I)
MUTABLE={'review_status','use_summary','implementation_scope_note','evidence_selection','supporting_evidence','source_role_qualification'}
def read(path):return json.loads(Path(path).read_bytes())
def identity(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
def require(condition,message):
 if not condition:raise ValueError(message)
def audit(root,cache):
 census=read(root/FOLDER/'CENSUS.json');roles=read(root/FOLDER/'ROLES.json')['roles'];papers=read(root/'docs/literature/omics-2023-2026/papers.json');verify_fixed_cohort(root,root/'docs/literature/omics-2023-2026',papers)
 require(census['collector']==identity(Path(collector.__file__).read_bytes())and census['pattern']==PATTERN.pattern,'Original parser/pattern changed');require(census['frozen_interval']==['2023-10-23','2026-10-23']and len(papers)==len(census['XML'])==250,'Frozen source cohort changed');hits=[];other=[]
 for paper in papers:
  expected=census['XML'][paper['paper_id']];require((paper['pmcid'],paper['doi'],paper['xml_sha256'])==(expected['pmcid'],expected['doi'],expected['sha256']),'Source identity differs');require(re.fullmatch(r'PMC[0-9]+',paper['pmcid'])and 0<expected['bytes']<=33554432,'Invalid source capacity/identity')
  with gzip.open(cache/(paper['pmcid']+'.xml.gz'),'rb')as stream:raw=stream.read(expected['bytes']+1)
  require(identity(raw)=={k:expected[k]for k in ['bytes','sha256']},'Original XML changed');tree=ET.fromstring(raw);require(any((node.text or'').strip()==paper['doi']for node in tree.findall('front/article-meta/article-id')),'Original DOI differs')
  for paragraph in collector.paragraphs(tree):
   if PATTERN.search(paragraph['match_text']):hits.append(dict(paper_id=paper['paper_id'],pmcid=paper['pmcid'],doi=paper['doi'],paragraph_index=paragraph['paragraph_index'],section=paragraph['section'],paragraph_sha256=hashlib.sha256(paragraph['text'].encode()).hexdigest()))
  for location in ['front/article-meta/abstract','back','floats-group','body//supplementary-material']:
   for element in tree.findall(location):
    if PATTERN.search(collector.matching_words(element)):other.append(dict(paper_id=paper['paper_id'],location=location,sha256=hashlib.sha256(ET.tostring(element)).hexdigest()))
 require(hits==census['contexts']and other==census['other_structures']and len(hits)==25 and len(other)==1 and{h['paper_id']for h in hits}==PAPERS,'Complete primary Popcorn context population changed');require(len(roles)==5 and{r['paper_id']for r in roles}==PAPERS,'Incomplete source role population')
 for role in roles:
  paper=next(p for p in papers if p['paper_id']==role['paper_id']);uses=[u for u in paper['tools']if u['tool_id']=='popcorn'];expected=role.get('new_observation')or role['raw_observation_preserved']
  require(len(uses)==1 or(role['paper_id']=='P112'and len(uses)==0),'Parent observation differs')
  if uses:require({k:v for k,v in uses[0].items()if k not in MUTABLE}=={k:v for k,v in expected.items()if k not in MUTABLE},'Raw observation/version/locator changed')
  require(role['primary_anchors']==[h for h in hits if h['paper_id']==role['paper_id']],'Primary context omitted');require(not role['exact_author_recipe_qualified']and not role['whole_paper_reproduced'],'Paper reproduction overclaimed')
 by={r['paper_id']:r for r in roles};require(by['P105']['mode']=='unresolved-effect-vs-impact'and by['P150']['mode']=='genetic-effect'and by['P035']['mode']=='genetic-impact','Effect/impact distinction collapsed');require(by['P150']['reported_version']=='1.0'and by['P150']['raw_observation_preserved']['version_reported_near_name']is None,'Reviewed version correction erased source extraction');require(by['P112']['new_observation']['matched_text']=='POPCORN','Uppercase missed observation absent')
 return dict(status='PASS_FULL250_PRIMARY_POPCORN_SOURCE_AUDIT',papers=250,main_text_contexts=25,other_structures=1,source_roles=5,newly_recovered_observations=1,new_source_reviews_eligible=5,raw_article_text_published=False,supplementary_method_review_complete=False,whole_goal_complete=False)
if __name__=='__main__':
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--root',type=Path,default=ROOT);parser.add_argument('--xml-cache',type=Path,required=True);args=parser.parse_args()
 try:print(json.dumps(audit(args.root.resolve(),args.xml_cache.absolute()),indent=2))
 except Exception as error:
  code=3 if isinstance(error,(FileNotFoundError,PermissionError))else 2;print(('SKIP3 DEPENDENT SOURCE AUDIT SKIPPED: 'if code==3 else'FAIL2 NO COMPLETE SOURCE AUDIT: ')+str(error)+'. Restore the exact permitted source XML/parser context and retry.',file=sys.stderr);raise SystemExit(code)
