# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Check every exact primary XML and all twelve named rstatix paragraphs."""
import gzip,hashlib,json,re,xml.etree.ElementTree as ET
from collect_omics_review import paragraphs,validate_supporting_evidence

CURATION='tools/literature/rstatix-paper-use-reviews.json'
INTAKE='tools/literature/rstatix072/paper-use-intake.json'
QUALIFIED_CENSUS='docs/literature/rstatix072-development-checkpoint/CENSUS.json'
PATTERN=r'(?<![A-Za-z0-9_])rstatix(?![A-Za-z0-9_])'
EXPECTED={'P032':[56],'P040':[54],'P054':[58,65,69,73,75,84],'P087':[65],'P098':[43],'P241':[54,113]}

def reviews(root):
 original=json.loads((root/INTAKE).read_bytes())['reviews'];rows=json.loads((root/CURATION).read_bytes())['reviews']
 if len(rows)!=6 or [r['paper_id'] for r in rows]!=list(EXPECTED):raise ValueError('Incomplete individual rstatix source reviews')
 for before,row in zip(original,rows,strict=True):
  if any(row.get(k)!=v for k,v in before.items()):raise ValueError('Qualified source reading changed')
  first=before['supporting_evidence'][0]
  if (row['tool_id'],row['evidence_paragraph_index'],row['evidence_paragraph_sha256'])!=('rstatix',first['paragraph_index'],first['paragraph_sha256']):raise ValueError('Primary review anchor differs')
 return rows

def scan(root,papers):
 census=[];matches=[]
 for paper in papers:
  raw=gzip.decompress((root/'build/literature-review/xml'/(paper['pmcid']+'.xml.gz')).read_bytes());sha=hashlib.sha256(raw).hexdigest()
  if sha!=paper['xml_sha256']:raise ValueError('Exact primary XML differs: '+paper['paper_id'])
  xml=ET.fromstring(raw)
  if paper['doi'].lower() not in [e.text.lower() for e in xml.findall('front/article-meta/article-id') if e.get('pub-id-type')=='doi' and e.text]:raise ValueError('Primary DOI differs')
  named=[]
  for p in paragraphs(xml):
   if re.search(PATTERN,p['match_text'],re.I):
    named.append(p['paragraph_index']);matches.append(dict(paper_id=paper['paper_id'],pmcid=paper['pmcid'],doi=paper['doi'],xml_sha256=sha,
     paragraph_index=p['paragraph_index'],section=p['section'],paragraph_sha256=hashlib.sha256(p['text'].encode('utf8')).hexdigest()))
  census.append(dict(paper_id=paper['paper_id'],pmcid=paper['pmcid'],doi=paper['doi'],xml_sha256=sha,named_paragraphs=named))
 result=dict(status='PASS_ALL250_PINNED_RSTATIX_PRIMARY_SOURCES',papers=len(census),named_papers=sum(bool(r['named_paragraphs']) for r in census),named_paragraphs=len(matches),census=census,matches=matches)
 validate(result,papers,reviews(root),root);return result

def validate(census,papers,rows,root=None):
 if census['status']!='PASS_ALL250_PINNED_RSTATIX_PRIMARY_SOURCES' or (census['papers'],census['named_papers'],census['named_paragraphs'])!=(250,6,12):raise ValueError('Incomplete rstatix census')
 if len(papers)!=250 or len(census['census'])!=250 or len(census['matches'])!=12:raise ValueError('Incomplete primary population')
 if len(rows)!=6 or [r['paper_id'] for r in rows]!=list(EXPECTED):raise ValueError('Incomplete source-role readings')
 actual={}
 for p,r in zip(papers,census['census'],strict=True):
  if any(p[k]!=r[k] for k in ['paper_id','pmcid','doi','xml_sha256']):raise ValueError('Census identity differs')
  if r['named_paragraphs']:actual[p['paper_id']]=r['named_paragraphs']
 if actual!=EXPECTED:raise ValueError('Named primary paragraph population differs')
 named={(m['paper_id'],m['paragraph_index']):m for m in census['matches']}
 if len(named)!=12 or set(named)!={(p,i) for p,indices in EXPECTED.items() for i in indices}:raise ValueError('Duplicate or missing named primary anchor')
 by={p['paper_id']:p for p in papers}
 for m in census['matches']:
  if any(m[k]!=by[m['paper_id']][k] for k in ['pmcid','doi','xml_sha256']):raise ValueError('Named anchor identity differs')
 for row in rows:
  paper=by[row['paper_id']]
  if row['tool_id']!='rstatix' or any(row[k]!=paper[k] for k in ['pmcid','doi']) or row['primary_xml_sha256']!=paper['xml_sha256']:raise ValueError('Individual source identity differs')
  if [a['paragraph_index'] for a in row['supporting_evidence']]!=EXPECTED[row['paper_id']]:raise ValueError('Missing or reordered complete source supports')
  classes={str(i):'analysis-role' for i in EXPECTED[row['paper_id']]}
  if row['paper_id']=='P241':classes['113']='resource-link-only'
  if row['named_anchor_classification']!=classes:raise ValueError('A resource link cannot imply another analysis execution')
  for a in row['supporting_evidence']:
   anchor=named[(row['paper_id'],a['paragraph_index'])]
   if any(a[k]!=anchor[k] for k in ['paragraph_sha256','section']):raise ValueError('Stale source support')
  anchor=named[(row['paper_id'],row['evidence_paragraph_index'])]
  if row['evidence_paragraph_sha256']!=anchor['paragraph_sha256'] or classes[str(row['evidence_paragraph_index'])]!='analysis-role':raise ValueError('Primary review must anchor analysis')
  if root is not None:
   raw=gzip.decompress((root/'build/literature-review/xml'/(paper['pmcid']+'.xml.gz')).read_bytes())
   validate_supporting_evidence(paragraphs(ET.fromstring(raw)),[row],{'rstatix':{'pattern':PATTERN}})
 if root is not None and census!=json.loads((root/QUALIFIED_CENSUS).read_bytes()):raise ValueError('Fresh source census differs from qualified complete readings')
 return dict(primary_sources=250,reviewed_paper_family_uses=6,named_primary_paragraphs=12,analysis_anchors=11,resource_link_anchors=1)
