# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Bind five scVelo roles and one separately named R wrapper to all250 exact sources."""
import copy,gzip,hashlib,json,re,xml.etree.ElementTree as ET
from collect_omics_review import paragraphs,validate_supporting_evidence,tool_matches
CURATION='tools/literature/scvelo-paper-use-reviews.json'
INTAKE='tools/literature/scvelo024025/paper-use-intake.json'
DEFINITION='tools/literature/scvelo_catalog/new-wrapper-definition.json'
EXTRACTION='tools/literature/scvelo_catalog/new-wrapper-extraction.json'
EXPECTED={'P051':[85],'P070':[42],'P117':[57],'P161':[66],'P228':[13,31]}
PRIMARY={'P051':85,'P070':42,'P117':57,'P161':66,'P228':13}
PATTERNS={'scvelo':r'(?<![A-Za-z0-9_])scvelo(?![A-Za-z0-9_])','velociraptor':r'(?<![A-Za-z0-9_])velociraptor(?![A-Za-z0-9_])'}

def reviews(root):
 rows=json.loads((root/CURATION).read_bytes())['reviews'];intake=json.loads((root/INTAKE).read_bytes())['readings']
 if len(rows)!=6 or [(r['paper_id'],r['tool_id']) for r in rows]!=[(n,'scvelo') for n in EXPECTED]+[('P228','velociraptor')]:raise ValueError('Complete five-scVelo/one-wrapper primary readings required')
 if len(intake)!=5:raise ValueError('Original five source intake readings differ')
 for old,row in zip(intake,rows[:5]):
  if row['source_intake_snapshot']!=old or any(row[k]!=old[k] for k in ['paper_id','pmcid','doi','tool_id','use_summary','version_reported_near_name','reported_settings','uncertainties','supporting_evidence']):raise ValueError('An original source reading/uncertainty was rewritten')
 if rows[1]['version_reported_near_name']!='0.24' or rows[2]['version_reported_near_name']!='0.2.4' or rows[3]['version_reported_near_name']!='0.2.5' or any(rows[i]['version_reported_near_name'] is not None for i in [0,4,5]):raise ValueError('Reported or unknown author edition was substituted')
 if rows[-1]['reported_settings']!={'interface':'R package velociraptor','wrapper_edition':None,'Python_backend_edition':None,'velocity_mode':None}:raise ValueError('Wrapper author settings were inferred')
 return rows

def scan(root,papers):
 census=[]
 for paper in papers:
  raw=gzip.decompress((root/'build/literature-review/xml'/(paper['pmcid']+'.xml.gz')).read_bytes())
  if hashlib.sha256(raw).hexdigest()!=paper['xml_sha256']:raise ValueError('Exact primary XML identity differs')
  xml=ET.fromstring(raw);ps=paragraphs(xml)
  if paper['doi'].lower() not in [x.text.lower() for x in xml.findall('front/article-meta/article-id') if x.get('pub-id-type')=='doi' and x.text]:raise ValueError('Primary DOI differs')
  row={k:paper[k] for k in ['paper_id','pmcid','doi','xml_sha256']}
  for name,pattern in PATTERNS.items():row[name+'_anchors']=[dict(paragraph_index=p['paragraph_index'],section=p['section'],paragraph_sha256=hashlib.sha256(p['text'].encode()).hexdigest()) for p in ps if re.search(pattern,p['match_text'],re.I)]
  census.append(row)
 validate(census,papers,reviews(root),root);return census

def validate(census,papers,rows,root=None):
 if (len(census),len(papers),len(rows))!=(250,250,6):raise ValueError('Incomplete primary population')
 by={}
 for paper,row in zip(papers,census):
  if any(paper[k]!=row[k] for k in ['paper_id','pmcid','doi','xml_sha256']):raise ValueError('Fixed250-source identities differ')
  if [a['paragraph_index'] for a in row['scvelo_anchors']]!=EXPECTED.get(paper['paper_id'],[]) or [a['paragraph_index'] for a in row['velociraptor_anchors']]!=([13] if paper['paper_id']=='P228' else []):raise ValueError('Complete named family-anchor population differs')
  by[paper['paper_id']]=row
 for row in rows:
  paper=by[row['paper_id']];anchors=paper[row['tool_id']+'_anchors'];primary=PRIMARY[row['paper_id']]
  if row['supporting_evidence']!=anchors or row['primary_xml_sha256']!=paper['xml_sha256'] or row['evidence_paragraph_index']!=primary or row['evidence_paragraph_sha256']!=next(a['paragraph_sha256'] for a in anchors if a['paragraph_index']==primary):raise ValueError('Exact primary role/anchor differs')
  if root is not None:
   xml=ET.fromstring(gzip.decompress((root/'build/literature-review/xml'/(row['pmcid']+'.xml.gz')).read_bytes()));ps=paragraphs(xml);registry={name:dict(pattern=p) for name,p in PATTERNS.items()};validate_supporting_evidence(ps,[row],registry)
   if row['tool_id']=='velociraptor':
    validate_supporting_evidence(ps,[dict(tool_id='scvelo',supporting_evidence=row['related_underlying_scvelo_results'])],registry)
    definition=json.loads((root/DEFINITION).read_bytes());found=tool_matches(ps,[definition],{'velociraptor':row['evidence_paragraph_sha256']})
    if len(found)!=1:raise ValueError('Missing or duplicated named R-wrapper method extraction')
    found=copy.deepcopy(found[0]);found.pop('evidence_text');found.pop('evidence_sentence')
    if found!=json.loads((root/EXTRACTION).read_bytes()):raise ValueError('Original wrapper method extraction differs')
 return dict(primary_sources=250,individual_scvelo_roles=5,new_wrapper_roles=1,named_family_anchors=7,distinct_named_paragraphs=6,reported_0_24_remains_unqualified=True,R_wrapper_execution_qualified=False)
