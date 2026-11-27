"""Verify all frozen250 primary MatrixEQTL contexts and distinct version/cohort roles."""
import argparse,gzip,hashlib,json,re,sys,xml.etree.ElementTree as ET
from pathlib import Path
import collect_omics_review as collector
from catalog_fixed_cohort import verify_fixed_cohort
ROOT=Path(__file__).resolve().parents[2];FOLDER='docs/literature/matrixeqtl-source-audit';PAPERS={'P169','P185','P213','P219'}
PATTERN=re.compile('(?<![A-Za-z0-9])Matrix[\\s._-]*(?:eQTL|EQTL)(?![A-Za-z])',re.I)
MUTABLE={'review_status','use_summary','implementation_scope_note','evidence_selection','supporting_evidence','source_role_qualification'}
def read(path):return json.loads(Path(path).read_bytes())
def identity(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
def require(condition,message):
 if not condition:raise ValueError(message)
def audit(root,cache):
 census=read(root/FOLDER/'CENSUS.json');roles=read(root/FOLDER/'ROLES.json')['roles'];papers=read(root/'docs/literature/omics-2023-2026/papers.json');verify_fixed_cohort(root,root/'docs/literature/omics-2023-2026',papers);require(census['collector']==identity(Path(collector.__file__).read_bytes())and census['pattern']==PATTERN.pattern,'Original source parser/pattern changed');require(census['frozen_interval']==['2023-10-23','2026-10-23']and len(papers)==len(census['XML'])==250,'Frozen source cohort changed');hits=[];other=[]
 for paper in papers:
  expected=census['XML'][paper['paper_id']];require((paper['pmcid'],paper['doi'],paper['xml_sha256'])==(expected['pmcid'],expected['doi'],expected['sha256']),'Source identity differs');require(re.fullmatch(r'PMC[0-9]+',paper['pmcid'])and 0<expected['bytes']<=33554432,'Invalid source capacity/identity')
  with gzip.open(cache/(paper['pmcid']+'.xml.gz'),'rb')as stream:raw=stream.read(expected['bytes']+1)
  require(identity(raw)=={k:expected[k]for k in ['bytes','sha256']},'Original XML changed');tree=ET.fromstring(raw);require(any((node.text or'').strip()==paper['doi']for node in tree.findall('front/article-meta/article-id')),'Original DOI differs')
  for paragraph in collector.paragraphs(tree):
   if PATTERN.search(paragraph['match_text']):hits.append(dict(paper_id=paper['paper_id'],pmcid=paper['pmcid'],doi=paper['doi'],paragraph_index=paragraph['paragraph_index'],section=paragraph['section'],paragraph_sha256=hashlib.sha256(paragraph['text'].encode()).hexdigest()))
  for location in ['front/article-meta/abstract','back','floats-group','body//supplementary-material']:
   for element in tree.findall(location):
    if PATTERN.search(collector.matching_words(element)):other.append(dict(paper_id=paper['paper_id'],location=location,sha256=hashlib.sha256(ET.tostring(element)).hexdigest()))
 require(hits==census['contexts']and other==census['other_structures']and len(hits)==5 and{h['paper_id']for h in hits}==PAPERS,'Complete primary context population changed');require(len(roles)==4 and{r['paper_id']for r in roles}==PAPERS,'Incomplete source role population')
 for role in roles:
  paper=next(p for p in papers if p['paper_id']==role['paper_id']);uses=[u for u in paper['tools']if u['tool_id']=='matrixeqtl'];require(len(uses)==1,'Parent observation changed');require({k:v for k,v in uses[0].items()if k not in MUTABLE}=={k:v for k,v in role['raw_observation_preserved'].items()if k not in MUTABLE},'Raw observation/version/locator changed');require(role['primary_anchors']==[h for h in hits if h['paper_id']==role['paper_id']],'Primary context omitted');require(not role['exact_author_recipe_qualified']and not role['whole_paper_reproduced'],'Author reproduction overclaimed')
 by={r['paper_id']:r for r in roles};require({k:r['reported_version']for k,r in by.items()}=={'P169':'2.3','P185':'2.3','P213':None,'P219':'2.3'},'Version-specific roles changed');require(by['P185']['raw_observation_preserved']['version_reported_near_name']is None and by['P213']['reported_version']is None,'Raw or unknown version was silently corrected');require(all(not r['source_audit_admits_runtime']for r in roles),'Source audit alone cannot admit a genotyping runtime; require separate execution qualification')
 return dict(status='PASS_FULL250_PRIMARY_MATRIXEQTL_SOURCE_AUDIT',papers=250,main_text_contexts=5,other_structures=len(other),source_roles=4,new_source_reviews_eligible=4,raw_article_text_published=False,supplementary_method_review_complete=False,whole_goal_complete=False)
if __name__=='__main__':
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--root',type=Path,default=ROOT);parser.add_argument('--xml-cache',type=Path,required=True);args=parser.parse_args()
 try:print(json.dumps(audit(args.root.resolve(),args.xml_cache.absolute()),indent=2))
 except Exception as error:
  code=3 if isinstance(error,(FileNotFoundError,PermissionError))else 2;print(('SKIP3 DEPENDENT SOURCE AUDIT SKIPPED: 'if code==3 else'FAIL2 NO COMPLETE SOURCE AUDIT: ')+str(error)+'. Restore exact permitted XML/parser context and retry.',file=sys.stderr);raise SystemExit(code)
