"""Verify every frozen250 primary FlashPCA context and four distinct source roles."""
import argparse,gzip,hashlib,json,re,sys,xml.etree.ElementTree as ET
from pathlib import Path
import collect_omics_review as collector
from catalog_fixed_cohort import verify_fixed_cohort
ROOT=Path(__file__).resolve().parents[2];FOLDER='docs/literature/flashpca-source-audit';PAPERS={'P023','P115','P118','P240'}
PATTERN=re.compile(r'(?<![A-Za-z0-9])FlashPCA(?:2)?(?![A-Za-z])',re.I)
MUTABLE={'review_status','use_summary','implementation_scope_note','evidence_selection','supporting_evidence','source_role_qualification'}
def read(p):return json.loads(Path(p).read_bytes())
def identity(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
def require(condition,message):
 if not condition:raise ValueError(message)
def audit(root,cache):
 census=read(root/FOLDER/'CENSUS.json');roles=read(root/FOLDER/'ROLES.json')['roles'];papers=read(root/'docs/literature/omics-2023-2026/papers.json');verify_fixed_cohort(root,root/'docs/literature/omics-2023-2026',papers)
 require(census['collector']==identity(Path(collector.__file__).read_bytes())and census['pattern']==PATTERN.pattern,'Frozen collector or pattern changed');require(census['frozen_interval']==['2023-10-23','2026-10-23']and len(papers)==len(census['XML'])==250,'Frozen cohort changed');hits=[];other=[];citations=[]
 for paper in papers:
  expected=census['XML'][paper['paper_id']];require((paper['pmcid'],paper['doi'],paper['xml_sha256'])==(expected['pmcid'],expected['doi'],expected['sha256']),'Source identity changed')
  with gzip.open(cache/(paper['pmcid']+'.xml.gz'),'rb')as stream:raw=stream.read(expected['bytes']+1)
  require(identity(raw)=={k:expected[k]for k in ['bytes','sha256']},'Complete original XML changed');tree=ET.fromstring(raw);require(any((n.text or'').strip()==paper['doi']for n in tree.findall('front/article-meta/article-id')),'Primary DOI differs')
  for paragraph in collector.paragraphs(tree):
   if PATTERN.search(paragraph['match_text']):hits.append(dict(paper_id=paper['paper_id'],pmcid=paper['pmcid'],doi=paper['doi'],paragraph_index=paragraph['paragraph_index'],section=paragraph['section'],paragraph_sha256=hashlib.sha256(paragraph['text'].encode()).hexdigest()))
  for location in ['front/article-meta/abstract','back','floats-group','body//supplementary-material']:
   for node in tree.findall(location):
    if PATTERN.search(collector.matching_words(node)):other.append(dict(paper_id=paper['paper_id'],location=location,sha256=hashlib.sha256(ET.tostring(node)).hexdigest()))
  if paper['paper_id']in PAPERS:
   for ref in tree.iter('ref'):
    text=''.join(ref.itertext()).lower().replace(' ','')
    if'flashpca'in text or'fastprincipalcomponentanalysis'in text:citations.append(dict(paper_id=paper['paper_id'],reference_id=ref.get('id'),reference_sha256=hashlib.sha256(ET.tostring(ref)).hexdigest(),method_family='FlashPCA2 / 2017'if'flashpca2'in text else'FlashPCA / 2014',exact_author_patch_established=False))
 require(hits==census['contexts']and len(hits)==4 and{h['paper_id']for h in hits}==PAPERS,'Complete primary context population differs');require(other==census['other_structures']and citations==census['method_citations']and len(citations)==4,'Reference structure or method-family evidence differs')
 require(len(roles)==4 and{r['paper_id']for r in roles}==PAPERS,'Incomplete primary role population')
 for role in roles:
  paper=next(p for p in papers if p['paper_id']==role['paper_id']);uses=[u for u in paper['tools']if u['tool_id']=='flashpca'];require(len(uses)==1,'Original use population changed')
  require({k:v for k,v in uses[0].items()if k not in MUTABLE}=={k:v for k,v in role['raw_observation_preserved'].items()if k not in MUTABLE},'Raw observation/version/locator changed');require(role['primary_anchors']==[h for h in hits if h['paper_id']==role['paper_id']]and role['method_citation']==next(c for c in citations if c['paper_id']==role['paper_id']),'Primary anchor omitted')
  require(not role['exact_author_recipe_qualified']and not role['whole_paper_reproduced']and not role['source_audit_admits_runtime'],'Source audit alone cannot admit an execution or author recipe')
 require({r['paper_id']:r['reported_version']for r in roles}=={'P023':None,'P115':None,'P118':None,'P240':'2.0'}and all(r['raw_observation_preserved']['version_reported_near_name']is None for r in roles),'Raw/unknown versus primary-reported version changed')
 return dict(status='PASS_FULL250_PRIMARY_FLASHPCA_SOURCE_AUDIT',papers=250,main_text_contexts=4,other_structures=len(other),method_family_citations=4,source_roles=4,raw_article_text_published=False,supplementary_method_review_complete=False,whole_goal_complete=False)
if __name__=='__main__':
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--root',type=Path,default=ROOT);p.add_argument('--xml-cache',type=Path,required=True);a=p.parse_args()
 try:print(json.dumps(audit(a.root.resolve(),a.xml_cache.absolute()),indent=2))
 except Exception as error:
  code=3 if isinstance(error,(FileNotFoundError,PermissionError))else 2;print(('SKIP3: Source audit skipped; restore permitted source evidence. 'if code==3 else'FAIL2: No complete source audit. ')+str(error),file=sys.stderr);raise SystemExit(code)
