# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Replay250 primary sources and six complete nf-core role reviews without runtime credit."""
import argparse,copy,gzip,hashlib,json,re,sys,xml.etree.ElementTree as ET
from pathlib import Path
import collect_omics_review as collector
from catalog_fixed_cohort import verify_fixed_cohort
from nfcore_review_roles import ROLES
ROOT=Path(__file__).resolve().parents[2]
FOLDER='docs/literature/nfcore-reviewed-source-audit'
PATTERN=re.compile(r'\bnf[\s\-‐‑‒–—−]*core\b|\bnf_core\b|\bnf-co\.re\b|github\.com/nf-core/',re.I)
DRAFT=dict(bytes=21049,sha256='c1c8f7490f051d7fe9d965118216ccb7231cb9b83ead79435f47ceb65ad1d227')
def read(path):return json.loads(Path(path).read_bytes())
def identity(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
def pin(path):return identity(Path(path).read_bytes())
def encoded(value):return(json.dumps(value,ensure_ascii=False,indent=2,allow_nan=False)+'\n').encode()
def require(ok,message):
 if not ok:raise ValueError(message)
def collect(root,cache):
 papers=read(root/'docs/literature/omics-2023-2026/papers.json');verify_fixed_cohort(root,root/'docs/literature/omics-2023-2026',papers);draft_path=root/'docs/literature/nfcore-source-audit/ROLES.json';require(pin(draft_path)==DRAFT,'Retained source draft changed');drafts={x['paper_id']:x for x in read(draft_path)['roles']}
 registry=read(root/'docs/literature/tensorqtl-admission/REGISTRY.json');definition=next(x for x in registry if x['id']=='nfcore');census=dict(schema_version=1,frozen_interval=['2023-10-23','2026-10-23'],pattern=PATTERN.pattern,collector=pin(Path(collector.__file__)),sources={},contexts=[],other_structures=[]);selected={}
 for paper in papers:
  with gzip.open(cache/(paper['pmcid']+'.xml.gz'),'rb')as stream:raw=stream.read(16000001)
  require(len(raw)<=16000000 and hashlib.sha256(raw).hexdigest()==paper['xml_sha256'],'Primary source bytes changed');tree=ET.fromstring(raw);require(any((node.text or'').strip().lower()==paper['doi'].lower()for node in tree.findall('front/article-meta/article-id')),'Primary DOI changed');ps=list(collector.paragraphs(tree));census['sources'][paper['paper_id']]=dict(pmcid=paper['pmcid'],doi=paper['doi'],**identity(raw))
  for p in ps:
   if PATTERN.search(p['match_text']):census['contexts'].append(dict(paper_id=paper['paper_id'],paragraph_index=p['paragraph_index'],section=p['section'],paragraph_sha256=hashlib.sha256(p['text'].encode()).hexdigest()))
  for location in ['front/article-meta/abstract','back','floats-group','body//supplementary-material']:
   for node in tree.findall(location):
    if PATTERN.search(collector.matching_words(node)):census['other_structures'].append(dict(paper_id=paper['paper_id'],location=location,sha256=hashlib.sha256(ET.tostring(node)).hexdigest()))
  if paper['paper_id']in ROLES:selected[paper['paper_id']]=(paper,ps)
 require(len(census['sources'])==250 and len(census['contexts'])==11 and set(x['paper_id']for x in census['contexts'])==set(ROLES),'Expanded source population changed');require(len(census['other_structures'])==4 and all(x['location']=='back'for x in census['other_structures']),'Additional body/abstract methods need review')
 roles=[];reviews=[]
 for pid,spec in ROLES.items():
  paper,ps=selected[pid];by={x['paragraph_index']:x for x in ps};use=next(x for x in paper['tools']if x['tool_id']=='nfcore');draft=copy.deepcopy(drafts[pid]);require(draft['source']['doi']==paper['doi']and draft['source']['xml_sha256']==paper['xml_sha256'],'Draft primary identity differs');require([x['paragraph_index']for x in draft['reviewed_paragraphs']]==spec['paragraphs'],'Incomplete source roles');anchors=[]
  for old in draft['reviewed_paragraphs']:
   p=by[old['paragraph_index']];require(old['paragraph_sha256']==hashlib.sha256(p['text'].encode()).hexdigest()and PATTERN.search(p['match_text']),'Role paragraph changed');anchors.append(dict(paragraph_index=p['paragraph_index'],section=p['section'],paragraph_sha256=old['paragraph_sha256'],matching_text_sha256=hashlib.sha256(p['match_text'].encode()).hexdigest()))
  note=spec['caution']+' Primary-source review alone does not admit a runtime analysis, prove an executed author recipe, certify a complete tool family or reproduce a paper.'
  supporting=[x for x in anchors if x['paragraph_index']!=use['evidence_paragraph_index']];review=dict(pmcid=paper['pmcid'],doi=paper['doi'],tool_id='nfcore',evidence_paragraph_sha256=use['evidence_paragraph_sha256'],use_summary=spec['usage'],implementation_scope_note=note,supporting_evidence=supporting);collector.validate_supporting_evidence(ps,[review],{'nfcore':definition})
  for branch in draft['pipelines']:branch['qualified']=False
  roles.append(dict(paper_id=pid,pmcid=paper['pmcid'],doi=paper['doi'],tool_id='nfcore',primary_anchors=anchors,pipelines=draft['pipelines'],separate_downstream_steps=draft['separate'],use_summary=spec['usage'],implementation_scope_note=note,extractor_near_name_version_preserved=use['version_reported_near_name'],source_review_complete_for_named_contexts=True,source_audit_admits_runtime=False,whole_family_qualified=False,whole_paper_reproduced=False));reviews.append(review)
 require(sum(len(x['pipelines'])for x in roles)==10,'Distinct workflow branches changed')
 return census,dict(schema_version=1,roles=roles,new_source_reviews=6,new_family_credit=0,source_audit_admits_runtime=False,whole_goal_complete=False),dict(schema_version=1,reviews=reviews)
def audit(root,cache):
 values=collect(root,cache);folder=root/FOLDER
 for name,value in zip(['CENSUS.json','ROLES.json','REVIEW-ENTRIES.json'],values):require(read(folder/name)==value,'Reviewed source evidence changed: '+name)
 return dict(status='PASS250_PRIMARY_NFCORE_ROLE_REVIEW',papers=250,named_contexts=11,paper_memberships=6,distinct_pipeline_branches=10,new_source_reviews=6,new_family_credit=0,whole_goal_complete=False)
if __name__=='__main__':
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--root',type=Path,default=ROOT);p.add_argument('--xml-cache',type=Path,required=True);p.add_argument('--write',action='store_true');a=p.parse_args()
 try:
  root=a.root.resolve();cache=a.xml_cache.resolve()
  if a.write:
   values=collect(root,cache);folder=root/FOLDER;folder.mkdir()
   for name,value in zip(['CENSUS.json','ROLES.json','REVIEW-ENTRIES.json'],values):
    with(folder/name).open('xb')as stream:stream.write(encoded(value))
  print(json.dumps(audit(root,cache),indent=2))
 except Exception as error:raise SystemExit('FAIL2: no source review admitted; retain original sources and correct the named cause: '+str(error))
