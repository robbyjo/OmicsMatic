#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Verify the frozen 250 primary identities and enumerate exact LDSC source pointers.

This is a source census, not individual role admission or paper reproduction.
"""
import argparse,gzip,hashlib,json,re,sys,unicodedata,xml.etree.ElementTree as ET
from pathlib import Path
ROOT=Path(__file__).resolve().parents[3]
sys.path.insert(0,str(ROOT/'tools/literature'))
from collect_omics_review import paragraphs
from catalog_fixed_cohort import verify_fixed_cohort

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--xml-cache',required=True,type=Path);p.add_argument('--out',required=True,type=Path);a=p.parse_args();a.out.mkdir(parents=True,exist_ok=False)
 catalog=ROOT/'docs/literature/omics-2023-2026';papers=json.loads((catalog/'papers.json').read_bytes());verify_fixed_cohort(ROOT,catalog,papers)
 row=next(x for x in json.loads((catalog/'tool-inventory.json').read_bytes()) if x['id']=='ldsc');pattern=re.compile(row['pattern'],re.I)
 sources=[];uses=[];raw_candidates=[]
 for paper in papers:
  raw=gzip.decompress((a.xml_cache/(paper['pmcid']+'.xml.gz')).read_bytes());assert hashlib.sha256(raw).hexdigest()==paper['xml_sha256'];sources.append(dict(paper_id=paper['paper_id'],pmcid=paper['pmcid'],xml_sha256=paper['xml_sha256']))
  rows=paragraphs(ET.fromstring(raw));by_index={p['paragraph_index']:p for p in rows};pointers=[]
  for paragraph in rows:
   if pattern.search(unicodedata.normalize('NFKC',paragraph['text'])):pointers.append(dict(paragraph_index=paragraph['paragraph_index'],paragraph_sha256=hashlib.sha256(paragraph['text'].encode()).hexdigest(),section=paragraph['section']))
  canonical=[use for use in paper['tools'] if use['tool_id']=='ldsc'];assert len(canonical)<=1
  if canonical:
   use=canonical[0];primary=by_index[use['evidence_paragraph_index']];assert hashlib.sha256(primary['text'].encode()).hexdigest()==use['evidence_paragraph_sha256']
   pinned=[dict(paragraph_index=use['evidence_paragraph_index'],paragraph_sha256=use['evidence_paragraph_sha256'],section=primary['section'])]
   for supporting in use.get('supporting_evidence',[]):
    source=by_index[supporting['paragraph_index']];assert hashlib.sha256(source['text'].encode()).hexdigest()==supporting['paragraph_sha256'];pinned.append(dict(paragraph_index=supporting['paragraph_index'],paragraph_sha256=supporting['paragraph_sha256'],section=source['section']))
   uses.append(dict(paper_id=paper['paper_id'],pmcid=paper['pmcid'],doi=paper['doi'],xml_sha256=paper['xml_sha256'],canonical_source_pointers=pinned,additional_raw_body_pointers=pointers,existing_catalog_use_summary=use.get('use_summary'),individual_role_review_complete=False,univariate_hsq_paper_reproduced=False))
  elif pointers:raw_candidates.append(dict(paper_id=paper['paper_id'],pmcid=paper['pmcid'],doi=paper['doi'],source_pointers=pointers,status='RAW_BODY_MATCH_NEEDS_SEMANTIC_REVIEW_NOT_ADDED_TO_CATALOG'))
 assert len(uses)==41 and {x['paper_id'] for x in uses}==set(row['papers'])
 targeted={'P072':dict(classification='actual-univariate-heritability-context',paragraphs=[23],summary='PESCA-related Methods describe estimating European/East-Asian genome-wide heritability with LD-score regression; author reference/QC/version settings remain distinct.'),'P092':dict(classification='actual-stratified-role-catalog-omission',paragraphs=[58],summary='Methods use sLDSC baseline-LD2.2 with European reference/HapMap3 regression SNPs, MHC exclusion, supplied peak annotations and additional conditioning. The current catalog omits this family role; canonical correction remains pending. The new univariate method does not supply this role.'),'P151':dict(classification='comparison-only-not-executed',paragraphs=[49],summary='RELI is presented as an alternative to stratified LD-score regression; this paragraph does not establish LDSC execution. Correct canonical role attribution in a later source-bound transition.'),'P177':dict(classification='discussion-alternative-not-executed',paragraphs=[38],summary='Discussion lists LD-score regression among possible correlation methods, while the study used a simple Z-score method. This is not an execution omission.')}
 papers_by_id={x['paper_id']:x for x in papers};reviews=[]
 for paper_id,review in targeted.items():
  paper=papers_by_id[paper_id];raw=gzip.decompress((a.xml_cache/(paper['pmcid']+'.xml.gz')).read_bytes());by_index={x['paragraph_index']:x for x in paragraphs(ET.fromstring(raw))}
  reviews.append(dict(paper_id=paper_id,pmcid=paper['pmcid'],classification=review['classification'],summary=review['summary'],source_pointers=[dict(paragraph_index=i,paragraph_sha256=hashlib.sha256(by_index[i]['text'].encode()).hexdigest(),section=by_index[i]['section']) for i in review['paragraphs']],canonical_transition_applied=False))
 report=dict(status='PASS250_PRIMARY_IDENTITIES_AND41_CANONICAL_LDSC_PAPER_POINTERS',primary_sources=sources,ldsc_papers=uses,additional_body_match_candidates=raw_candidates,targeted_semantic_reviews=reviews,scope='Canonical source-pointer census with four targeted semantic reviews, not a recall-complete family extraction or canonical correction. Native prepared unpartitioned Hsq numerical component. Census does not establish that every mention uses Hsq or resolve genetic correlation, stratification, LD-score generation, historical editions or whole author pipelines.',new_catalog_integrations=0,whole_goal_complete=False)
 (a.out/'SOURCE_CENSUS.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf8');print('PASS250 primary hashes;41 LDSC paper identities with exact paragraph pointers;no role/badge inflation')

if __name__=='__main__':main()
