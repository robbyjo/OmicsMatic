"""Replay the frozen250 SVA/ComBat census without publishing article excerpts."""
import argparse,gzip,hashlib,json,re,sys,xml.etree.ElementTree as ET
from pathlib import Path
import collect_omics_review as collector
from catalog_fixed_cohort import verify_fixed_cohort

ROOT=Path(__file__).resolve().parents[2]
FOLDER='docs/literature/sva-combat-source-audit'
PATTERN=re.compile(r'(?<![A-Za-z0-9])(?:ComBat(?:[-_\s]*seq)?|sva|surrogate variable analysis|svaseq|num[._]sv|fsva|psva)(?![A-Za-z])',re.I)
COMBAT_PATTERN=r'(?<![A-Za-z0-9_])(?:ComBat(?:[-_ ]seq)?)(?![A-Za-z0-9_])'
POSITIVE={('P086','combat'),('P108','combat'),('P108','sva'),('P128','combat'),('P176','sva'),('P185','sva')}
MUTABLE={'review_status','use_summary','implementation_scope_note','evidence_selection','supporting_evidence','source_role_qualification'}

def read(path):return json.loads(Path(path).read_bytes())
def identity(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
def require(ok,message):
 if not ok:raise ValueError(message)

def recovered_observation(paper,paragraphs,definition):
 """Use the unchanged collector with one explicitly corrected name definition."""
 definition=dict(definition,pattern=COMBAT_PATTERN)
 uses=collector.tool_matches(paragraphs,[definition])
 require(len(uses)==1 and uses[0]['evidence_paragraph_index']==36 and uses[0]['matched_text']=='ComBat_seq','Recovered name/paragraph differs')
 return collector.publication_record(dict(paper,tools=uses),reviewed_uses=[])['tools'][0]

def audit(root,cache,private=None):
 census=read(root/FOLDER/'CENSUS.json');document=read(root/FOLDER/'ROLES.json');roles=document['roles']
 papers=read(root/'docs/literature/omics-2023-2026/papers.json');verify_fixed_cohort(root,root/'docs/literature/omics-2023-2026',papers)
 require(census['collector']==identity(Path(collector.__file__).read_bytes()) and census['pattern']==PATTERN.pattern,'Collector or expanded pattern changed')
 require(census['frozen_interval']==['2023-10-23','2026-10-23'] and len(papers)==len(census['XML'])==250,'Frozen source population changed')
 hits=[];other=[];recovered=None;reference=None
 for paper in papers:
  expected=census['XML'][paper['paper_id']]
  require((paper['pmcid'],paper['doi'],paper['xml_sha256'])==(expected['pmcid'],expected['doi'],expected['sha256']),'Source paper identity changed')
  with gzip.open(cache/(paper['pmcid']+'.xml.gz'),'rb')as stream:raw=stream.read(expected['bytes']+1)
  require(identity(raw)=={k:expected[k]for k in ['bytes','sha256']},'Complete primary XML changed')
  tree=ET.fromstring(raw);require(any((n.text or'').strip()==paper['doi']for n in tree.findall('front/article-meta/article-id')),'Primary DOI differs')
  paragraphs=list(collector.paragraphs(tree))
  for paragraph in paragraphs:
   if PATTERN.search(paragraph['match_text']):hits.append(dict(paper_id=paper['paper_id'],pmcid=paper['pmcid'],doi=paper['doi'],paragraph_index=paragraph['paragraph_index'],section=paragraph['section'],paragraph_sha256=hashlib.sha256(paragraph['text'].encode()).hexdigest()))
  for location in ['front/article-meta/abstract','back','floats-group','body//supplementary-material']:
   for node in tree.findall(location):
    if PATTERN.search(collector.matching_words(node)):other.append(dict(paper_id=paper['paper_id'],location=location,sha256=hashlib.sha256(ET.tostring(node)).hexdigest()))
  if paper['paper_id']=='P086':recovered=recovered_observation(paper,paragraphs,census['recovery_definition'])
  if paper['paper_id']=='P128':
   ref=next(n for n in tree.iter('ref')if n.get('id')=='CR109');reference=dict(paper_id='P128',reference_id='CR109',reference_sha256=hashlib.sha256(ET.tostring(ref)).hexdigest())
 require(hits==census['contexts'] and len(hits)==12 and len({h['paper_id']for h in hits})==9,'Full primary context population differs')
 require(other==census['other_structures'] and len(other)==3,'Other primary structure population differs')
 require(len(roles)==6 and {(r['paper_id'],r['tool_id'])for r in roles}==POSITIVE,'Exactly six distinct positive observations required')
 decisions=census['context_decisions'];require(len(decisions)==12 and [(x['paper_id'],x['paragraph_index'])for x in decisions]==[(x['paper_id'],x['paragraph_index'])for x in hits],'A primary context lacks a decision')
 positive={(x['paper_id'],x['paragraph_index'])for x in decisions if x['positive_software_use']}
 require(positive=={('P086',36),('P108',51),('P128',49),('P176',28),('P185',9)},'Positive/negative primary decisions differ')
 for role in roles:
  paper=next(p for p in papers if p['paper_id']==role['paper_id']);raw_use=role['raw_observation_preserved']
  require(role['primary_anchors']==[h for h in hits if h['paper_id']==role['paper_id'] and h['paragraph_index']==raw_use['evidence_paragraph_index']],'Role anchor differs')
  require(not role['exact_author_recipe_qualified'] and not role['whole_paper_reproduced'] and not role['source_audit_admits_runtime'],'Source inspection cannot admit execution')
  if role['paper_id']=='P086':require(raw_use==recovered,'Recovered observation differs from unchanged collector')
  uses=[u for u in paper['tools']if u['tool_id']==role['tool_id']]
  if uses:require(len(uses)==1 and {k:v for k,v in uses[0].items()if k not in MUTABLE}=={k:v for k,v in raw_use.items()if k not in MUTABLE},'Raw observation, version or locator changed')
  else:require(role['paper_id']=='P086','A positive original observation disappeared')
 exclusion=document['excluded_observation'];require(exclusion['paper_id']=='P206' and exclusion['tool_id']=='sva' and exclusion['raw_observation_preserved']['evidence_paragraph_sha256']==next(h['paragraph_sha256']for h in hits if h['paper_id']=='P206'),'False positive exclusion differs')
 require(document['longitudinal_method_reference']==reference,'Longitudinal method reference differs')
 provenance=read(root/FOLDER/'SOURCE-PROVENANCE.json')
 require(provenance['author_code']['license_declaration_found']is False and provenance['author_code']['executed']is False and provenance['author_code']['redistributed']is False,'Unlicensed author code execution/distribution is not admitted')
 require(provenance['longCombat_license_declarations']==dict(description='MIT',license_file='Artistic License 2.0',resolved=False),'Retain upstream license declaration discrepancy')
 if private is not None:
  private=Path(private)
  for row in provenance['upstream_sources']:require(identity((private/'upstream-intake-1'/row['path']).read_bytes())==row['identity'],'Inspected upstream source bytes changed')
  source=(private/'author-source-inspection-1/DESeq2_analysis.R').read_bytes();author=provenance['author_code']
  require(identity(source)==author['source'] and hashlib.sha1(b'blob '+str(len(source)).encode()+b'\0'+source).hexdigest()==author['git_blob'],'Author source SHA256/Git blob changed')
  tree=read(private/'upstream-intake-1/AUTHOR-TREE.json');require(tree['sha']==author['tree'] and not tree.get('truncated',False) and len(tree['tree'])==2 and next(t for t in tree['tree']if t['path']=='DESeq2_analysis.R')['sha']==author['git_blob'],'Complete author tree changed')
  lines=source.splitlines(keepends=True)
  require(all(identity(b''.join(lines[x['start']-1:x['end']]))==x['identity']for x in author['inspected_ranges']),'Inspected author function/design lines changed')
 return dict(status='PASS_FULL250_PRIMARY_SVA_COMBAT_SOURCE_AUDIT',papers=250,main_text_contexts=12,context_papers=9,other_structures=3,positive_source_observations=6,excluded_false_positive_observations=1,recovered_observations=1,private_upstream_sources_verified=private is not None,new_family_credit=0,raw_article_text_published=False,supplementary_method_review_complete=False,whole_goal_complete=False)

if __name__=='__main__':
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--root',type=Path,default=ROOT);p.add_argument('--xml-cache',type=Path,required=True);p.add_argument('--private',type=Path);a=p.parse_args()
 try:print(json.dumps(audit(a.root.resolve(),a.xml_cache.absolute(),a.private),indent=2))
 except Exception as error:
  code=3 if isinstance(error,(FileNotFoundError,PermissionError))else 2
  print(('SKIP3: Source audit skipped; restore the named permitted source evidence. 'if code==3 else'FAIL2: No complete source audit. ')+str(error),file=sys.stderr);raise SystemExit(code)
