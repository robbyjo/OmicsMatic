"""Verify the full frozen ASCAT source census and supplement identities without execution."""
from pathlib import Path
import argparse,gzip,hashlib,json,re,sys,xml.etree.ElementTree as ET
import collect_omics_review as collector

ROOT=Path(__file__).resolve().parents[2]
FOLDER='docs/literature/ascat-source-audit-v2'
PATTERN=re.compile(r'(?<![a-z0-9_])ascat(?:[._-]?ngs)?(?![a-z0-9_])',re.I)
PAPERS={'P111','P129','P143','P146'}
MUTABLE={'review_status','use_summary','implementation_scope_note','evidence_selection','supporting_evidence','source_role_qualification'}

def require(value,message):
 if not value:raise ValueError(message)

def pin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())

def read(path):return json.loads(Path(path).read_bytes())

def audit(root,cache,supplements):
 root,cache,supplements=map(Path,(root,cache,supplements));census=read(root/FOLDER/'CENSUS.json');roles=read(root/FOLDER/'ROLES.json')['roles'];papers=read(root/'docs/literature/omics-2023-2026/papers.json')
 require(pin(Path(collector.__file__).read_bytes())==census['collector'],'Original paragraph collector changed; restore its source context')
 require(census['frozen_interval']==['2023-10-23','2026-10-23']and census['pattern']==PATTERN.pattern,'Frozen interval or source pattern changed')
 require(len(papers)==len(census['XML'])==250 and[p['paper_id']for p in papers]==list(census['XML']),'Complete source cohort identity/order changed')
 hits=[];other=[];anchors={}
 for paper in papers:
  expected=census['XML'][paper['paper_id']];require((paper['pmcid'],paper['doi'],paper['xml_sha256'])==(expected['pmcid'],expected['doi'],expected['sha256']),'Original paper/XML identity changed');require(re.fullmatch(r'PMC[0-9]+',paper['pmcid'])and 0<expected['bytes']<=33554432,'Invalid original XML identity/capacity')
  with gzip.open(cache/(paper['pmcid']+'.xml.gz'),'rb')as stream:raw=stream.read(expected['bytes']+1)
  require(pin(raw)=={k:expected[k]for k in('bytes','sha256')},'Missing/changed/truncated original XML');tree=ET.fromstring(raw)
  require(any((el.text or'').strip()==paper['doi']for el in tree.findall('front/article-meta/article-id')),'Original source DOI differs')
  for paragraph in collector.paragraphs(tree):
   anchor=dict(paper_id=paper['paper_id'],pmcid=paper['pmcid'],doi=paper['doi'],paragraph_index=paragraph['paragraph_index'],section=paragraph['section'],paragraph_sha256=pin(paragraph['text'].encode())['sha256']);anchors[paper['paper_id'],paragraph['paragraph_index']]=anchor
   if PATTERN.search(paragraph['match_text']):hits.append(anchor)
  for location in('front/article-meta/abstract','back','floats-group','body//supplementary-material'):
   for element in tree.findall(location):
    if PATTERN.search(collector.matching_words(element)):other.append(dict(paper_id=paper['paper_id'],location=location,sha256=pin(ET.tostring(element))['sha256']))
 require(hits==census['anchors']and other==census['other_structures']and len(hits)==8 and not other and{h['paper_id']for h in hits}==PAPERS,'Complete ASCAT/ascatNgs source mention population differs')
 require(len(roles)==4 and{r['paper_id']for r in roles}==PAPERS,'Incomplete source-role population');claimed=set()
 for role in roles:
  require(role['tool_id']=='ascat'and role['implementation_available']is False and role['implementation_status']=='PENDING_RUNTIME_QUALIFICATION_NO_CATALOG_CREDIT'and role['whole_paper_reproduced']is False,'Source review was expanded into runtime credit')
  paper=next(p for p in papers if p['paper_id']==role['paper_id']);uses=[u for u in paper['tools']if u['tool_id']=='ascat'];require(len(uses)==1 and(paper['doi'],paper['pmcid'])==(role['doi'],role['pmcid']),'Source role/observation identity differs')
  require(all(uses[0].get(k)==v for k,v in role['raw_catalog_observation_preserved'].items()if k not in MUTABLE),'Raw source/version observation changed')
  for anchor in role['primary_anchors']:require(anchors.get((paper['paper_id'],anchor['paragraph_index']))==anchor,'Primary source paragraph changed');claimed.add((paper['paper_id'],anchor['paragraph_index']))
 require(claimed=={(h['paper_id'],h['paragraph_index'])for h in hits},'A complete direct source context was omitted')
 by_id={r['paper_id']:r for r in roles};require({k:r['literal_package_version_reported_elsewhere']for k,r in by_id.items()}==dict(P111='3.2.0',P129='2.5.2',P143=None,P146=None),'Reported versions were inferred or altered')
 require(by_id['P146']['segmentation_penalty']==100 and by_id['P146']['ascatNgs_internal_version']=='4.5.0'and by_id['P146']['goodness_equality_boundary_unresolved'],'Exome settings, internal WGS wrapper or equality ambiguity lost')
 for item in census['supplements']:
  require(item['paper_id']in{'P129','P146'}and len(item['supplements'])==1,'Unexpected original supplement population');source=item['supplements'][0];path=supplements/source['filename'];raw=path.read_bytes();require(pin(raw)==source['pdf']and hashlib.md5(raw).hexdigest()==source['official_MD5'],'Original official supplement bytes/MD5 differ');require(source['license_code']=='CC BY'and source['version']==1,'Original supplement metadata differs')
 additions=read(root/FOLDER/'SUPPLEMENTARY-TOOLS.json');require(len(additions['tools'])==2 and{r['name']for r in additions['tools']}=={'ConstructASCATFiles','PREASCAT'},'New supplement-named tools omitted')
 for tool in additions['tools']:require(tool['paper_id']=='P146'and tool['supplement_PDF_page']==7 and tool['version_reported']is None and tool['runtime_analysis_status']=='SKIP3_DEPENDENT_PREPROCESSING_ANALYSIS_SKIPPED'and all(tool[k]is False for k in('installed','executed','redistributed','implementation_available','paper_to_inspected_commit_equivalence','primary_registry_admitted')),'Unpermitted preprocessing was represented as executed/implemented')
 return dict(status='PASS_FULL250_ASCAT_MAIN_AND_OFFICIAL_SUPPLEMENT_SOURCE_AUDIT',papers=250,direct_contexts=8,reviewed_roles=4,new_source_reviews_eligible=4,official_supplements=2,new_supplementary_named_tools=2,primary_registry_expansion_pending=True,runtime_admission=False,whole_goal_complete=False)

def main():
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--root',type=Path,default=ROOT);parser.add_argument('--cache',type=Path,required=True);parser.add_argument('--supplements',type=Path,required=True);args=parser.parse_args()
 try:print(json.dumps(audit(args.root,args.cache,args.supplements),sort_keys=True));return 0
 except Exception as error:
  code=3 if isinstance(error,(FileNotFoundError,PermissionError))else 2;print(('SKIP3 DEPENDENT ASCAT SOURCE AUDIT SKIPPED: 'if code==3 else'FAIL2 NO COMPLETE ASCAT SOURCE AUDIT: ')+str(error)+'. Required action: restore exact permitted XML/PDF bytes and matching parser/source context. Preserve original cohort, historical failures and old seals; do not substitute papers or infer author settings.',file=sys.stderr);return code
if __name__=='__main__':raise SystemExit(main())
