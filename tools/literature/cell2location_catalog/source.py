# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Bind six individually read source roles to every exact primary identity and all nine anchors."""
import gzip,hashlib,json,re,xml.etree.ElementTree as ET
from collect_omics_review import paragraphs,validate_supporting_evidence
EXPECTED={'P005':[60],'P008':[92],'P070':[38],'P096':[9,53],'P099':[9,40],'P100':[23,45]}
PRIMARY={'P005':60,'P008':92,'P070':38,'P096':53,'P099':40,'P100':45}
CURATION='tools/literature/cell2location-paper-use-reviews.json'
INTAKE='tools/literature/cell2location013/paper-use-intake.json'
PATTERN=r'(?<![A-Za-z0-9_])cell2location(?![A-Za-z0-9_])'

def reviews(root):
 before=json.loads((root/INTAKE).read_bytes())['readings'];rows=json.loads((root/CURATION).read_bytes())['reviews']
 if len(rows)!=6 or [r['paper_id'] for r in rows]!=list(EXPECTED):raise ValueError('Incomplete six individual cell2location source readings')
 for old,row in zip(before,rows):
  if any(row.get(k)!=v for k,v in old.items() if k!='review_scope') or row.get('source_intake_review_scope')!=old['review_scope']:raise ValueError('Primary-source reading changed during curation')
  primary=next(a for a in row['supporting_evidence'] if a['paragraph_index']==PRIMARY[row['paper_id']])
  if (row['evidence_paragraph_index'],row['evidence_paragraph_sha256'])!=(primary['paragraph_index'],primary['paragraph_sha256']):raise ValueError('The methods anchor was replaced by results/figure context')
 return rows

def scan(root,papers):
 census=[]
 for paper in papers:
  raw=gzip.decompress((root/'build/literature-review/xml'/(paper['pmcid']+'.xml.gz')).read_bytes())
  if hashlib.sha256(raw).hexdigest()!=paper['xml_sha256']:raise ValueError('Exact primary XML differs: '+paper['paper_id'])
  xml=ET.fromstring(raw)
  if paper['doi'].lower() not in [e.text.lower() for e in xml.findall('front/article-meta/article-id') if e.get('pub-id-type')=='doi' and e.text]:raise ValueError('Primary DOI differs')
  named=[p for p in paragraphs(xml) if re.search(PATTERN,p['match_text'],re.I)]
  if [p['paragraph_index'] for p in named]!=EXPECTED.get(paper['paper_id'],[]):raise ValueError('Complete named anchor population differs: '+paper['paper_id'])
  census.append(dict(paper_id=paper['paper_id'],pmcid=paper['pmcid'],doi=paper['doi'],xml_sha256=paper['xml_sha256'],named_anchors=[dict(paragraph_index=p['paragraph_index'],section=p['section'],paragraph_sha256=hashlib.sha256(p['text'].encode()).hexdigest()) for p in named]))
 validate(census,papers,reviews(root),root);return census

def validate(census,papers,rows,root=None):
 if len(papers)!=250 or len(census)!=250 or len(rows)!=6 or sum(len(r['named_anchors']) for r in census)!=9:raise ValueError('Incomplete250-source/six-reading/nine-anchor census')
 for paper,row in zip(papers,census):
  if any(paper[k]!=row[k] for k in ['paper_id','pmcid','doi','xml_sha256']):raise ValueError('Fixed primary-source identity differs')
  if [a['paragraph_index'] for a in row['named_anchors']]!=EXPECTED.get(paper['paper_id'],[]):raise ValueError('Named source anchors differ')
 by={r['paper_id']:r for r in census}
 for row in rows:
  paper=by[row['paper_id']]
  if row['tool_id']!='cell2location' or row['supporting_evidence']!=paper['named_anchors'] or row['primary_xml_sha256']!=paper['xml_sha256']:raise ValueError('Primary reading/support identity differs')
  if row['version_reported_near_name']!=('0.7a0' if row['paper_id']=='P096' else '0.1.3' if row['paper_id']=='P100' else None):raise ValueError('Reported/unknown author edition changed')
  if root is not None:
   raw=gzip.decompress((root/'build/literature-review/xml'/(row['pmcid']+'.xml.gz')).read_bytes());validate_supporting_evidence(paragraphs(ET.fromstring(raw)),[row],{'cell2location':{'pattern':PATTERN}})
 return dict(primary_sources=250,individually_reviewed_uses=6,named_anchors=9,methods_anchors=6,results_or_figure_contexts=3,reported_historical_edition_unqualified=True)
