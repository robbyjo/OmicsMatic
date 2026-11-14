# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Verify all250 exact primary XML sources and all12 named ggpubr anchors."""
import gzip,hashlib,json,re,xml.etree.ElementTree as ET
from collect_omics_review import paragraphs

EXPECTED={'P040':[54],'P054':[93],'P058':[37,61,70],'P087':[57],'P098':[62],'P101':[45],'P195':[21],'P231':[18],'P241':[54,111]}
VERSIONS={'P040':'0.6.0','P054':'0.6.0','P058':'unreported','P087':'unreported','P098':'0.4.0','P101':'0.4.0','P195':'0.4.0','P231':'unreported','P241':'unreported'}
PATTERN=r'(?<![A-Za-z0-9_])ggpubr(?![A-Za-z0-9_])'

def validate(census,papers,intake):
 rows=intake['reviews']
 if census['status']!='PASS_ALL250_PINNED_GGPUBR_PRIMARY_SOURCES' or (census['papers'],census['named_papers'],census['named_paragraphs'])!=(250,9,12):raise ValueError('Incomplete ggpubr primary-source population')
 if len(papers)!=250 or len(census['census'])!=250 or len(census['matches'])!=12 or len(rows)!=9:raise ValueError('Missing complete primary sources or individual readings')
 if [r['paper_id'] for r in rows]!=list(EXPECTED):raise ValueError('Missing or reordered individual paper roles')
 by={p['paper_id']:p for p in papers};actual={}
 for paper,row in zip(papers,census['census'],strict=True):
  if any(paper[k]!=row[k] for k in ['paper_id','pmcid','doi','xml_sha256']):raise ValueError('Exact primary source identity differs')
  if row['named_paragraphs']:actual[row['paper_id']]=row['named_paragraphs']
 if actual!=EXPECTED:raise ValueError('Complete named paragraph population differs')
 anchors={(m['paper_id'],m['paragraph_index']):m for m in census['matches']}
 if len(anchors)!=12 or set(anchors)!={(p,i) for p,indices in EXPECTED.items() for i in indices}:raise ValueError('Duplicate or missing primary anchor')
 for row in rows:
  paper=by[row['paper_id']]
  if any(row[k]!=paper[k] for k in ['pmcid','doi']) or row['primary_xml_sha256']!=paper['xml_sha256']:raise ValueError('Individual source reading identity differs')
  if row['version_reported_near_name']!=VERSIONS[row['paper_id']]:raise ValueError('Reported and unreported editions must remain separate')
  if [a['paragraph_index'] for a in row['supporting_evidence']]!=EXPECTED[row['paper_id']]:raise ValueError('Incomplete named source supports')
  classes={str(i):'named-analysis-or-visualization-role' for i in EXPECTED[row['paper_id']]}
  if row['paper_id']=='P241':classes['111']='resource-link-only'
  if row['named_anchor_classification']!=classes:raise ValueError('A resource link cannot imply another analysis execution')
  for support in row['supporting_evidence']:
   anchor=anchors[(row['paper_id'],support['paragraph_index'])]
   if any(anchor[k]!=paper[k] for k in ['pmcid','doi','xml_sha256']) or any(support[k]!=anchor[k] for k in ['paragraph_sha256','section']):raise ValueError('Primary support identity/hash/section differs')
 return dict(primary_sources=250,individual_paper_role_readings=9,named_primary_paragraphs=12,analysis_or_visualization_anchors=11,resource_link_only_anchors=1,
  source_roles_not_inferred_from_generic_mentions=True,paper_wide_semantic_review_complete=False)

def scan(root,papers,intake):
 census=[];matches=[]
 for paper in papers:
  raw=gzip.decompress((root/'build/literature-review/xml'/(paper['pmcid']+'.xml.gz')).read_bytes());sha=hashlib.sha256(raw).hexdigest()
  if sha!=paper['xml_sha256']:raise ValueError('Exact primary XML differs: '+paper['paper_id'])
  xml=ET.fromstring(raw)
  if paper['doi'].lower() not in [e.text.lower() for e in xml.findall('front/article-meta/article-id') if e.get('pub-id-type')=='doi' and e.text]:raise ValueError('Exact primary DOI differs')
  named=[]
  for p in paragraphs(xml):
   if re.search(PATTERN,p['match_text'],re.I):
    named.append(p['paragraph_index']);matches.append(dict(paper_id=paper['paper_id'],pmcid=paper['pmcid'],doi=paper['doi'],xml_sha256=sha,
     paragraph_index=p['paragraph_index'],section=p['section'],paragraph_sha256=hashlib.sha256(p['text'].encode('utf8')).hexdigest()))
  census.append(dict(paper_id=paper['paper_id'],pmcid=paper['pmcid'],doi=paper['doi'],xml_sha256=sha,named_paragraphs=named))
 result=dict(status='PASS_ALL250_PINNED_GGPUBR_PRIMARY_SOURCES',papers=len(census),named_papers=sum(bool(r['named_paragraphs']) for r in census),named_paragraphs=len(matches),census=census,matches=matches)
 validate(result,papers,intake);return result
