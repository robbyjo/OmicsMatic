# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Pinned primary-name census and complete individual ImageJ review anchors."""
import gzip,hashlib,json,re,xml.etree.ElementTree as ET
from collect_omics_review import paragraphs,validate_supporting_evidence
from imagej_catalog.model import CURATION,EVIDENCE,BLEACH,reviews
def definitions(root):
 old=json.loads((root/'tools/literature/p040-named-software-registry.json').read_bytes());return [next(r for r in old if r['id']=='fiji_imagej'),BLEACH]
def scan(root,papers):
 defs=definitions(root);rows=[];matches=[];curated={r['paper_id']:r for r in reviews(root)}
 for paper in papers:
  raw=gzip.decompress((root/'build/literature-review/xml'/(paper['pmcid']+'.xml.gz')).read_bytes());sha=hashlib.sha256(raw).hexdigest()
  if sha!=paper['xml_sha256']:raise ValueError('Exact primary XML differs: '+paper['paper_id'])
  xml=ET.fromstring(raw);dois=[e.text for e in xml.findall('front/article-meta/article-id') if e.get('pub-id-type')=='doi']
  if paper['doi'].lower() not in [s.lower() for s in dois if s]:raise ValueError('Primary DOI differs')
  ps=paragraphs(xml);found={}
  for definition in defs:
   indices=[]
   for p in ps:
    spans=list(re.finditer(definition['pattern'],p['match_text'],re.I))
    if spans:indices.append(p['paragraph_index']);matches.append(dict(paper_id=paper['paper_id'],pmcid=paper['pmcid'],doi=paper['doi'],tool_id=definition['id'],paragraph_index=p['paragraph_index'],section=p['section'],paragraph_sha256=hashlib.sha256(p['text'].encode('utf8')).hexdigest(),matched_names=[m.group() for m in spans]))
   if indices:found[definition['id']]=indices
  rows.append(dict(paper_id=paper['paper_id'],pmcid=paper['pmcid'],doi=paper['doi'],xml_sha256=sha,primary_paragraphs=len(ps),matched_paragraphs_by_tool=found))
  if paper['paper_id'] in curated:validate_supporting_evidence(ps,[curated[paper['paper_id']]],{r['id']:r for r in defs})
 result=dict(status='PASS_ALL250_PINNED_IMAGEJ_AND_BLEACH_PRIMARY_SOURCES',papers=250,name_pairs=sum(len(r['matched_paragraphs_by_tool']) for r in rows),matched_paragraphs=len(matches),census=rows,matches=matches)
 validate(result,papers,reviews(root));return result
def validate(census,papers,rs):
 if census['status']!='PASS_ALL250_PINNED_IMAGEJ_AND_BLEACH_PRIMARY_SOURCES' or (census['papers'],census['name_pairs'],census['matched_paragraphs'])!=(250,52,85) or len(census['census'])!=250 or len(census['matches'])!=85:raise ValueError('Incomplete source population')
 for p,row in zip(papers,census['census'],strict=True):
  if any(p[k]!=row[k] for k in ['paper_id','pmcid','doi','xml_sha256']):raise ValueError('Census paper identity differs')
 by={p['paper_id']:p for p in papers};named={(m['paper_id'],m['tool_id'],m['paragraph_index'],m['paragraph_sha256'],m['section']) for m in census['matches']};seen=set()
 for review in rs:
  id=review['paper_id'];p=by[id]
  if id in seen or review['tool_id']!='fiji_imagej' or any(review[k]!=p[k] for k in ['pmcid','doi']) or review['evidence_paragraph_sha256']!=review['supporting_evidence'][0]['paragraph_sha256']:raise ValueError('Duplicate or unanchored individual review')
  actual={(id,'fiji_imagej',a['paragraph_index'],a['paragraph_sha256'],a['section']) for a in review['supporting_evidence']};expected={a for a in named if a[:2]==(id,'fiji_imagej')}
  if actual!=expected:raise ValueError('Individual review does not cover every named primary paragraph')
  seen.add(id)
 if seen!={m['paper_id'] for m in census['matches'] if m['tool_id']=='fiji_imagej'} or sum(len(r['supporting_evidence']) for r in rs)!=84:raise ValueError('Incomplete ImageJ review population')
 bleach=[m for m in census['matches'] if m['tool_id']=='bleach_correction']
 if len(bleach)!=1 or (bleach[0]['paper_id'],bleach[0]['paragraph_index'])!=('P026',51):raise ValueError('Unexpected Bleach Corrector source population')
 return dict(primary_sources=250,imagej_reviewed_papers=51,imagej_named_primary_paragraphs=84,bleach_papers=1,bleach_named_primary_paragraphs=1)
