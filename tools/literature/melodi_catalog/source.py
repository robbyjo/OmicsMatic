# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Correct the unrelated Presto identity and read all named companion source roles."""
from pathlib import Path
import gzip,hashlib,json,re,xml.etree.ElementTree as ET
from collect_omics_review import paragraphs

PATTERNS={'presto':r'(?<![A-Za-z0-9_])presto(?![A-Za-z0-9_])',
 'oncoscore':r'(?<![A-Za-z0-9_])OncoScore(?![A-Za-z0-9_])',
 'oncoenrichr':r'(?<![A-Za-z0-9_])oncoEnrichR(?![A-Za-z0-9_])',
 'intogen':r'(?<![A-Za-z0-9_])intOGen(?![A-Za-z0-9_])',
 'haploreg':r'(?<![A-Za-z0-9_])HaploReg(?![A-Za-z0-9_])'}
EXPECTED={'presto':{'P038':[68],'P157':[67],'P169':[15,49]},'oncoscore':{'P169':[15,49]},
 'oncoenrichr':{'P169':[16,50]},'intogen':{'P042':[6,43,47],'P169':[49]},'haploreg':{'P073':[21],'P169':[49],'P192':[54]}}
R_PRESTO_PATTERN=r'(?<![A-Za-z0-9_])(?<!MELODI\s)(?:(?-i:Presto|presto))(?![A-Za-z0-9_])'
MISSING_NOTE=('Source-reviewed software/resource use only; no integration or runtime result is established for this family. '
 'Keep each reported release, data snapshot, analysis and data-reuse role distinct. Supply the exact permitted prerequisites before dependent analysis; '
 'SKIP3 skips unavailable/forbidden requirements and FAIL2 admits no complete result. No author input, setting or output is inferred.')
DEFINITIONS=[
 dict(id='melodi-presto',name='MELODI Presto',pattern=r'(?<![A-Za-z0-9_])MELODI\s+Presto(?![A-Za-z0-9_])',role='Semantic literature-triple enrichment and overlap queries'),
 dict(id='oncoscore',name='OncoScore',pattern=PATTERNS['oncoscore'],role='Text-mining gene associations with cancer'),
 dict(id='oncoenrichr',name='oncoEnrichR',pattern=PATTERNS['oncoenrichr'],role='Cancer-focused gene-set characterization and functional/drug evidence'),
 dict(id='intogen',name='IntOGen',pattern=PATTERNS['intogen'],role='Somatic driver-gene pipeline and distinct driver-cohort resource reuse'),
 dict(id='haploreg',name='HaploReg',pattern=PATTERNS['haploreg'],role='Variant functional annotation, gene-distance lookup and LD resource queries')]
for row in DEFINITIONS:row.update(current_coverage='missing',existing_source=None,coverage_note=MISSING_NOTE)

# Contexts are read independently, including the explicitly different IntOGen pipeline/resource roles.
FACTS=[
 ('P169','melodi-presto',49,[15,49],None,
  'Surveyed semantic subject-predicate-object literature triples in MELODI Presto, accessed 3 April 2024, with the candidate gene list as subject and colorectal cancer as object, complementing OncoScore. This is not the presto single-cell Wilcoxon/AUC R package.',
  dict(access_date='2024-04-03',subject='candidate gene list',object='colorectal cancer',database='Semantic MEDLINE Database',author_server_commit=None,author_database_snapshot=None), 'analysis-method-description'),
 ('P169','oncoscore',49,[15,49],'1.30.0',
  'Ranked candidate target genes by cancer association using literature mining in OncoScore1.30.0; used a score of21.09 as the stated novelty threshold. This analysis is separate from the MELODI Presto literature survey.',
  dict(novelty_threshold=21.09,threshold_comparator=None,exact_gene_query_settings=None),'analysis-method-description'),
 ('P169','oncoenrichr',50,[16,50],'1.4.2.1',
  'Characterized candidate gene sets with oncoEnrichR1.4.2.1, including cancer/drug associations, synthetic lethality, gene fitness and protein interactions. The results discussion draws on Open Targets and DepMap; this is not the separate Enrichr pathway-enrichment step.',
  dict(analysis_domains=['cancer associations','drug associations','synthetic lethality','gene fitness','protein-protein interactions'],exact_functions=None),'analysis-method-description'),
 ('P042','intogen',43,[6,43,47],'2023',
  'Identified protein-coding cancer driver genes with IntOGen v.2023, integrating dNdSCV, OncodriveFML, OncodriveCLUSTL, cBaSE, MutPanning, HotMaps3D and smRegions. Separately annotated candidates against IntOGen cohorts from a prior pan-cancer analysis; that resource reuse does not establish another pipeline execution.',
  dict(pipeline_version_literal='2023',component_methods=['dNdSCV','OncodriveFML','OncodriveCLUSTL','cBaSE','MutPanning','HotMaps3D','smRegions'],component_versions=None,cohort_annotation_is_separate=True,cohort_snapshot=None),'analysis-method-description'),
 ('P169','intogen',49,[49],None,
  'Used the IntOGen driver-gene resource released1February2020, restricted to colorectal COADREAD cohorts, for driver-gene evidence. The source does not establish execution of the IntOGen mutation-analysis pipeline in this paper.',
  dict(resource_release_date='2020-02-01',cohort_restriction='COADREAD',pipeline_execution_established=False),'resource-data-reuse'),
 ('P073','haploreg',21,[21],None,
  'Examined HaploReg functional annotations for rs6751481 and rs74258942, reporting blood-specific promoter and enhancer overlaps respectively. The HaploReg release and full query settings are not reported at this anchor; coloc and SuSiE results in the paragraph are separate methods.',
  dict(variants=['rs6751481','rs74258942'],annotations={'rs6751481':'blood-specific promoter','rs74258942':'blood-specific enhancer'},release=None),'resource-query-description'),
 ('P169','haploreg',49,[49],'4.1',
  'Obtained gene-distance information from HaploReg4.1 for candidate-gene evidence. The exact queried variant list and full lookup settings are not recovered.',
  dict(information='gene distance',exact_query_settings=None),'resource-query-description'),
 ('P192','haploreg',54,[54],'4.2',
  'Used HaploReg4.2 for the reported R-squared=0 LD comparison between TBX15 rs10494217 and rs4659153. The Finns/METSIM R-squared=0.03 comparison and earlier Inuit selection results are distinct; they are not HaploReg outputs from the same population.',
  dict(variants=['rs10494217','rs4659153'],reported_HaploReg_R_squared=0,reported_METSIM_R_squared=0.03,HaploReg_reference_population=None),'resource-query-description')]

def audit(root,with_reviews=True):
 papers=json.loads((root/'docs/literature/omics-2023-2026/papers.json').read_bytes())
 if len(papers)!=250 or len({p['pmcid'] for p in papers})!=250:raise ValueError('All250 distinct primary sources required')
 census=[];texts={};anchors={}
 for p in papers:
  raw=gzip.decompress((root/'build/literature-review/xml'/(p['pmcid']+'.xml.gz')).read_bytes())
  if hashlib.sha256(raw).hexdigest()!=p['xml_sha256']:raise ValueError('Exact primary XML differs: '+p['paper_id'])
  xml=ET.fromstring(raw)
  if p['doi'].lower() not in [n.text.lower() for n in xml.findall('front/article-meta/article-id') if n.get('pub-id-type')=='doi' and n.text]:raise ValueError('Primary DOI differs')
  ps=paragraphs(xml);texts[p['paper_id']]={q['paragraph_index']:q for q in ps}
  row={k:p[k] for k in ['paper_id','pmcid','doi','xml_sha256','fulltext_url','license']};row['anchors']={}
  for name,pattern in PATTERNS.items():
   hits=[q for q in ps if re.search(pattern,q['match_text'],re.I)]
   row['anchors'][name]=[dict(paragraph_index=q['paragraph_index'],section=q['section'],paragraph_sha256=hashlib.sha256(q['text'].encode()).hexdigest()) for q in hits]
   if [q['paragraph_index'] for q in hits]!=EXPECTED[name].get(p['paper_id'],[]):raise ValueError('Complete named source population differs: '+p['paper_id']+'/'+name)
  census.append(row)
 reviews=[]
 for paper,tool,index,support,version,summary,settings,context in FACTS:
  p=next(p for p in papers if p['paper_id']==paper);source=texts[paper][index]
  evidence=[dict(paragraph_index=i,section=texts[paper][i]['section'],paragraph_sha256=hashlib.sha256(texts[paper][i]['text'].encode()).hexdigest()) for i in support]
  reviews.append(dict(paper_id=paper,pmcid=p['pmcid'],doi=p['doi'],tool_id=tool,primary_xml_sha256=p['xml_sha256'],
   evidence_paragraph_index=index,evidence_paragraph_sha256=hashlib.sha256(source['text'].encode()).hexdigest(),
   version_reported_near_name=version,usage_context=context,use_summary=summary,reported_settings=settings,
   supporting_evidence=evidence,implementation_scope_note=MISSING_NOTE,
   uncertainties=['Exact executed source/deployed server, complete author inputs and unreported caller settings remain unknown.']))
 # The ambiguous standalone name only refers to a plasmid kit/funding in other papers.
 if 'PRESTO-Tango plasmid kit' not in texts['P038'][68]['text'] or 'JST PRESTO' not in texts['P157'][67]['text']:raise ValueError('Preserved nonsoftware counterexamples differ')
 if 'MELODI Presto (accessed 3 April 2024)' not in texts['P169'][49]['text']:raise ValueError('Literal service identity/access date differs')
 return dict(status='PASS_ALL250_PRIMARY_MELODI_PRESTO_IDENTITY_AND_COMPANION_ROLE_REVIEW',papers=250,census=census,
  nonsoftware_Presto_counterexamples={'P038':dict(paragraph_index=68,kind='plasmid kit'),'P157':dict(paragraph_index=67,kind='funding program')},
  unrelated_R_presto_is_not_P169_software=True,primary_texts_retained_privately_not_relicensed_as_code=True,
  reviews=reviews if with_reviews else [],whole_paper_completeness_established=False,new_implementation_credit=0,whole_goal_complete=False)
