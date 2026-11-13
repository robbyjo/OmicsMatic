#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Audit candidate names and individual primary-source anchors without changing the main catalog."""
from pathlib import Path
from collections import Counter
import argparse,gzip,hashlib,json,re,sys,traceback,xml.etree.ElementTree as ET
HERE=Path(__file__).resolve().parent;ROOT=HERE.parents[2];TOOLS=HERE.parent;sys.path.insert(0,str(TOOLS))
from collect_omics_review import paragraphs
REGISTRY=HERE/'candidate-registry.json';REVIEWS=HERE/'anchor-reviews.json'
COHORT=ROOT/'docs/literature/imagej-catalog-integration/CENSUS.json'
ACTION='Required action: read each named primary context and retain its exact identity, edition, role and uncertainty. Detector matches are candidates, not confirmed software execution or implementation credit. Hardware, ordinary words, data-product references, environment listings, acquisition and presentation roles remain explicit. SKIP3 skips a fresh source audit when exact permitted cached XML is missing; recover the cache only where permitted. FAIL2 admits no audit. This intake does not rewrite the main catalog, replace scientific verifiers or establish whole-paper reproduction; the full original goal remains open.'
CLASSES={'software-analysis','software-presentation','software-acquisition','software-visual-inspection','software-data-io','software-environment-listing','data-product-reference','hardware-not-software','ordinary-word-not-software','software-identity-unresolved','lookup-resource','historical-implementation-reference'}
SCOPES={'complete-primary-paragraph','targeted-named-source-context','previously-read-complete-primary-paragraph-with-pinned-source-recheck'}
def pin(p):return dict(bytes=p.stat().st_size,sha256=hashlib.sha256(p.read_bytes()).hexdigest())
def save(p,v):p.write_text(json.dumps(v,indent=2)+'\n',encoding='utf8',newline='\n')

def scan():
 sourcepaths=[REGISTRY,REVIEWS,HERE/'intake.py',TOOLS/'collect_omics_review.py',ROOT/'docs/literature/omics-2023-2026/papers.json',COHORT];before={p.relative_to(ROOT).as_posix():pin(p) for p in sourcepaths}
 registry=json.loads(REGISTRY.read_bytes());definitions=registry['definitions'];manual=json.loads(REVIEWS.read_bytes());reviews=manual['reviews']
 if registry['status']!='CANDIDATE_REGISTRY_NOT_CATALOG_ADMISSION' or manual['status']!='DRAFT_INDIVIDUAL_ANCHOR_REVIEWS_NOT_MAIN_CATALOG':raise ValueError('Unqualified intake scope')
 ids=[d['id'] for d in definitions]
 if len(ids)!=len(set(ids)):raise ValueError('Duplicate candidate identity')
 byReview={}
 for row in reviews:
  key=(row['paper_id'],row['tool_id'],row['paragraph_index'])
  if key in byReview or row['classification'] not in CLASSES or row['reading_scope'] not in SCOPES or not row['use_summary'].strip() or not row['scope_note'].strip():raise ValueError('Duplicate, unsupported or empty individual source review')
  byReview[key]=row
 papers=json.loads((ROOT/'docs/literature/omics-2023-2026/papers.json').read_bytes());census=[];matches=[];seen=set();contexts=[]
 cohort=json.loads(COHORT.read_bytes())['census']
 if len(papers)!=250 or len(cohort)!=250 or {p['paper_id'] for p in papers}!={'P'+str(i).zfill(3) for i in range(1,251)} or len({p['pmcid'] for p in papers})!=250 or len({p['doi'].lower() for p in papers})!=250:raise ValueError('The complete unique250-paper source cohort is required')
 for paper,frozen in zip(papers,cohort,strict=True):
  if any(paper[k]!=frozen[k] for k in ['paper_id','pmcid','doi','xml_sha256']):raise ValueError('Original frozen primary cohort identity differs')
 for paper in papers:
  raw=gzip.decompress((ROOT/'build/literature-review/xml'/(paper['pmcid']+'.xml.gz')).read_bytes());sha=hashlib.sha256(raw).hexdigest()
  if sha!=paper['xml_sha256']:raise ValueError('Exact primary XML differs: '+paper['paper_id'])
  xml=ET.fromstring(raw);dois=[e.text for e in xml.findall('front/article-meta/article-id') if e.get('pub-id-type')=='doi']
  if paper['doi'].lower() not in [s.lower() for s in dois if s]:raise ValueError('Primary DOI differs')
  ps=paragraphs(xml);found={}
  for d in definitions:
   named=[]
   for paragraph in ps:
    spans=list(re.finditer(d['pattern'],paragraph['match_text'],re.I))
    if not spans:continue
    index=paragraph['paragraph_index'];named.append(index);key=(paper['paper_id'],d['id'],index);hash=hashlib.sha256(paragraph['text'].encode('utf8')).hexdigest();row=dict(paper_id=paper['paper_id'],pmcid=paper['pmcid'],doi=paper['doi'],tool_id=d['id'],paragraph_index=index,section=paragraph['section'],paragraph_sha256=hash,matched_names=[s.group() for s in spans],classification='source-match-awaiting-individual-review')
    if key in byReview:
     review=byReview[key]
     if any(review[k]!=row[k] for k in ['pmcid','doi','paragraph_sha256','section']):raise ValueError('Manual review source identity or anchor differs: '+str(key))
     row.update({k:review[k] for k in ['classification','use_summary','scope_note','version_reported_near_name','reading_scope']});seen.add(key)
    else:
     contexts.append((key,paragraph['text'],paragraph['section']))
    matches.append(row)
   if named:found[d['id']]=named
  census.append(dict(paper_id=paper['paper_id'],pmcid=paper['pmcid'],doi=paper['doi'],xml_sha256=sha,matched_paragraphs_by_candidate=found))
 if seen!=set(byReview):raise ValueError('A reviewed name no longer matches its exact primary paragraph: '+str(set(byReview)-seen))
 pairs={}
 for row in matches:pairs.setdefault((row['paper_id'],row['tool_id']),[]).append(row)
 complete=[dict(paper_id=key[0],tool_id=key[1],named_anchors=len(rows),classifications=sorted({r['classification'] for r in rows})) for key,rows in pairs.items() if all(r['classification']!='source-match-awaiting-individual-review' for r in rows)]
 if any(pin(ROOT/name)!=wanted for name,wanted in before.items()):raise ValueError('Intake code, curation or source metadata changed during the full audit')
 result=dict(status='PASS_PINNED250_CANDIDATE_CENSUS_AND_DRAFT_ANCHOR_IDENTITY',papers=len(papers),candidate_name_definitions=len(ids),new_family_candidates=sum(not d.get('existing_family_id') for d in definitions),existing_family_alias_definitions=sum(bool(d.get('existing_family_id')) for d in definitions),candidate_paper_name_pairs=len(pairs),named_primary_anchors=len(matches),reviewed_primary_anchors=len(seen),unreviewed_primary_anchors=len(matches)-len(seen),pairs_with_every_named_anchor_reviewed=len(complete),classification_counts=dict(sorted(Counter(r['classification'] for r in matches).items())),zero_hit_definitions=sorted(set(ids)-{r['tool_id'] for r in matches}),candidate_definitions=definitions,census=census,matches=matches,pairs_with_complete_named_anchor_review=complete,source_pins=before,main_catalog_modified=False,new_tool_credit=0,whole_paper_semantic_review_complete=False,whole_goal_complete=False)
 return result,contexts

def main():
 p=argparse.ArgumentParser(description=__doc__,epilog=ACTION);p.add_argument('--out',required=True,type=Path);a=p.parse_args();out=a.out.resolve();owned=False
 try:
  if not out.is_relative_to(ROOT/'build/literature-review/monocle-runtime'):raise ValueError('Use a NEW owned monocle-runtime output')
  out.mkdir(parents=True,exist_ok=False);owned=True;result,contexts=scan();save(out/'CENSUS.json',result)
  grouped={}
  for key,text,section in contexts:grouped.setdefault((key[0],key[2]),dict(text=text,section=section,tools=[]))['tools'].append(key[1])
  full=[];presentation=[]
  for (paper,index),row in sorted(grouped.items()):
   block=paper+' '+str(index)+' '+','.join(row['tools'])+' ['+row['section']+']\n'
   if set(row['tools'])=={'biorender'}:
    spans=list(re.finditer('BioRender',row['text'],re.I));windows=[]
    for s in spans:
     start=max(0,s.start()-900);end=min(len(row['text']),s.end()+300)
     if windows and start<=windows[-1][1]:windows[-1]=(windows[-1][0],max(windows[-1][1],end))
     else:windows.append((start,end))
    presentation.append(block+'TARGETED CONTEXT ONLY; full paragraph is not represented below\n'+'\n'.join('chars '+str(start)+'..'+str(end)+': '+row['text'][start:end] for start,end in windows)+'\n')
   else:full.append(block+row['text']+'\n')
  (out/'UNREVIEWED_FULL_METHOD_CONTEXTS.txt').write_text('\n'.join(full),encoding='utf8',newline='\n');(out/'UNREVIEWED_BIORENDER_TARGETED_CONTEXTS.txt').write_text('\n'.join(presentation),encoding='utf8',newline='\n');(out/'WARNINGS.md').write_text(ACTION+'\n',encoding='utf8',newline='\n')
  print(json.dumps({k:v for k,v in result.items() if k not in ['candidate_definitions','census','matches','pairs_with_complete_named_anchor_review','source_pins']},indent=2),flush=True);return 0
 except Exception as e:
  code=3 if isinstance(e,OSError) else 2;v=dict(status='SKIP3_PRIMARY_CENSUS_PREREQUISITE_UNAVAILABLE' if code==3 else 'FAIL2_SOFTWARE_FRONTIER_DRAFT_INTAKE',cause=repr(e),traceback=traceback.format_exc(),required_action=ACTION)
  if owned:save(out/'FAILURE.json',v);(out/'WARNINGS_FAILURE.md').write_text(v['status']+'\n'+repr(e)+'\n'+ACTION+'\n',encoding='utf8',newline='\n')
  print(v['status'],repr(e),'\n'+ACTION,flush=True);return code
if __name__=='__main__':raise SystemExit(main())
