#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Verify250 frozen primary sources and the reviewed Dunn/Dunnett method crosswalk.

Publishes paraphrases and exact source pointers, never long paper excerpts.
It does not promote Prism or a whole-paper workflow to implemented status.
"""
import argparse
import gzip
import hashlib
import json
import re
import sys
import unicodedata
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT=Path(__file__).resolve().parents[3]
sys.path.insert(0,str(ROOT/'tools/literature'))
from collect_omics_review import paragraphs
from catalog_fixed_cohort import verify_fixed_cohort

DUNN={
 'P006':('method-menu-ambiguous-model','Lists Dunn among several alternatives after an ordinary ANOVA description; the paragraph does not establish a valid independent-rank model, exact panels or correction family. Preserve this source ambiguity.'),
 'P018':('nonparametric-model-settings-unresolved','Uses Dunn for nonparametric multiple comparisons; exact independent/repeated design and comparison family require explicit source inputs.'),
 'P048':('kruskal-wallis-dunn','Uses Kruskal-Wallis followed by Dunn for specified tumor/myeloid, expression and functional comparisons. Other t-tests, factorial ANOVA/Sidak, regressions and survival analyses remain separate.'),
 'P087':('kruskal-wallis-dunn','A figure specifies Kruskal-Wallis/Dunn for macrophage-related measurements. Other R Wilcoxon/BH, multcomp Dunnett, Seurat and spatial analyses are separate roles.'),
 'P122':('kruskal-wallis-dunn-bonferroni','Specifies Dunn with Bonferroni after Kruskal-Wallis for the locus-proximity figure. The adjacent bootstrap analyses do not establish that bootstrap draws are independent test units; original units and comparison family remain required.'),
 'P167':('kruskal-wallis-dunn-holm','Uses Kruskal-Wallis for cellular/neighborhood compositions and Dunn with Holm adjustment for intergroup comparisons. Exact observational units and selected contrasts remain caller inputs.'),
 'P196':('kruskal-wallis-dunn-with-outer-correction','Uses Kruskal-Wallis/Dunn for non-normal multi-genotype data and separately describes further Bonferroni correction across tests. The independent rank family and any outer family must be declared separately.'),
 'P217':('kruskal-wallis-dunn-all-conditions','Specifies Kruskal-Wallis/Dunn comparisons among all conditions for an expression-related experiment. The exact correction choice and observations are not recovered from this mention.'),
 'P035':('excluded-author-name','Dunn appears in an author/contributor name, not as a statistical method; excluded from method-use count.')}
DUNNETT={
 'P006':'Menu of alternative tests; exact panel/model remains unresolved.',
 'P026':'Ordinary one-way ANOVA followed by Dunnett; native Dunnett implementation remains pending.',
 'P029':'One-way ANOVA/Dunnett for qPCR-related comparisons against control; native Dunnett remains pending.',
 'P031':'One-way ANOVA/Dunnett in organoid experiments; author observations/control family remain required.',
 'P034':'Repeated-measures ANOVA with Geisser-Greenhouse correction and Dunnett; an ordinary independent model would not supply this role.',
 'P051':'One-way ANOVA/Dunnett in neutrophil/functional comparisons; exact author groups and controls remain required.',
 'P084':'Ordinary one-way ANOVA/Dunnett across more than two groups; other paired-donor tests are separate.',
 'P087':'One-way ANOVA/control comparisons, including an explicit multcomp1.4-29 R role; other nonparametric tests are separate.',
 'P217':'Brown-Forsythe/Welch ANOVA and Dunnett against control; unequal-variance/precise Dunnett variant remains unresolved.'}

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--xml-cache',required=True,type=Path);p.add_argument('--out',required=True,type=Path);a=p.parse_args();a.out.mkdir(parents=True,exist_ok=False)
 catalog=ROOT/'docs/literature/omics-2023-2026';papers=json.loads((catalog/'papers.json').read_bytes());verify_fixed_cohort(ROOT,catalog,papers)
 sources=[];matches=[]
 for paper in papers:
  raw=gzip.decompress((a.xml_cache/(paper['pmcid']+'.xml.gz')).read_bytes());assert hashlib.sha256(raw).hexdigest()==paper['xml_sha256'],paper['paper_id'];sources.append(dict(paper_id=paper['paper_id'],pmcid=paper['pmcid'],xml_sha256=paper['xml_sha256']))
  for row in paragraphs(ET.fromstring(raw)):
   text=unicodedata.normalize('NFKC',row['text']);names=[name for name,pattern in [('dunn',r'\bDunn(?:[\u2019\u0027]s)?\b'),('dunnett',r'\bDunnett(?:[\u2019\u0027]s)?\b'),('prism',r'\b(?:GraphPad|Prism)\b')] if re.search(pattern,text,re.I)]
   if names:matches.append(dict(paper_id=paper['paper_id'],pmcid=paper['pmcid'],doi=paper['doi'],xml_sha256=paper['xml_sha256'],paragraph_index=row['paragraph_index'],paragraph_sha256=hashlib.sha256(row['text'].encode()).hexdigest(),names=names))
 assert {x['paper_id'] for x in matches if 'dunn' in x['names']}==set(DUNN)
 assert {x['paper_id'] for x in matches if 'dunnett' in x['names']}==set(DUNNETT)
 prism_ids={x['paper_id'] for x in matches if 'prism' in x['names']};inventory=json.loads((catalog/'tool-inventory.json').read_bytes());prism=next(t for t in inventory if t['id']=='graphpad_prism');assert len(prism_ids)==47 and prism_ids-set(prism['papers'])=={'P215'} and set(prism['papers'])<=prism_ids
 reviewed=[]
 for paper,(classification,summary) in DUNN.items():reviewed.append(dict(paper_id=paper,classification=classification,use_summary=summary,source_pointers=[x for x in matches if x['paper_id']==paper and 'dunn' in x['names']],native_independent_dunn_component_available=paper not in ['P035','P006'],whole_paper_reproduced=False))
 report=dict(status='PASS250_PRIMARY_HASHES_AND_REVIEWED_COMMON_STATISTICAL_ROLES',primary_sources=sources,dunn_name_papers=9,dunn_method_papers=8,dunn_roles=reviewed,dunnett_roles=[dict(paper_id=paper,use_summary=summary,source_pointers=[x for x in matches if x['paper_id']==paper and 'dunnett' in x['names']],implemented=False) for paper,summary in DUNNETT.items()],prism_named_papers=47,admitted_catalog_prism_papers=46,catalog_source_gap=dict(paper_id='P215',status='NEWLY_IDENTIFIED_SOURCE_CATALOG_OMISSION_PENDING_CANONICAL_CURATION',use_summary='Primary Methods repeatedly names Prism, including two-tailed unpaired t-tests on biological-replicate means. Bare Prism is missed by the existing family expression.',source_pointers=[x for x in matches if x['paper_id']=='P215' and 'prism' in x['names']]),scope='Source-bound statistical-method review. Exact author inputs/designs, historical defaults and full vendor workflow coverage remain unqualified. Main inventory/observations have not been promoted or silently regenerated.',new_catalog_integrations=0)
 (a.out/'SOURCE_REVIEW.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf8',newline='\n');print('PASS250 source hashes;8 Dunn method papers;1 excluded author-name hit;9 separately reviewed Dunnett papers;P215 Prism catalog omission recorded')

if __name__=='__main__':main()
