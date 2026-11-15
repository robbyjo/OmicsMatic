#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Identity-checked full-corpus SingleR and related named-software census."""
from pathlib import Path
import argparse,gzip,hashlib,json,re,sys,xml.etree.ElementTree as ET
sys.dont_write_bytecode=True
ROOT=Path(__file__).resolve().parents[3]
sys.path.insert(0,str(ROOT/'tools/literature'))
from collect_omics_review import paragraphs

PATTERNS={'singler':r'(?<![A-Za-z0-9_])SingleR(?![A-Za-z0-9_])',
 'celldex':r'(?<![A-Za-z0-9_])celldex(?![A-Za-z0-9_])',
 'bd-rhapsody':r'(?<![A-Za-z0-9_])Rhapsody(?![A-Za-z0-9_])',
 'spotlight':r'(?<![A-Za-z0-9_])SPOTlight(?![A-Za-z0-9_])',
 'immgen-reference':r'(?<![A-Za-z0-9_])ImmGen(?![A-Za-z0-9_])'}
EXPECTED={'P051':[81,93,119],'P082':[45,64,71],'P192':[16,18],'P233':[8]}

def audit():
    papers=json.loads((ROOT/'docs/literature/omics-2023-2026/papers.json').read_bytes())
    if len(papers)!=250 or len({p['pmcid'] for p in papers})!=250:raise ValueError('All 250 distinct original primary sources are required')
    census=[];anchors={};texts={}
    for paper in papers:
        path=ROOT/'build/literature-review/xml'/(paper['pmcid']+'.xml.gz');raw=gzip.decompress(path.read_bytes())
        if hashlib.sha256(raw).hexdigest()!=paper['xml_sha256']:raise ValueError('Primary XML differs: '+paper['paper_id'])
        xml=ET.fromstring(raw)
        ids=[n.text.lower() for n in xml.findall('front/article-meta/article-id') if n.get('pub-id-type')=='doi' and n.text]
        if paper['doi'].lower() not in ids:raise ValueError('Primary DOI differs: '+paper['paper_id'])
        ps=paragraphs(xml);row={k:paper[k] for k in ['paper_id','pmcid','doi','xml_sha256','fulltext_url','license']};row['anchors']={}
        for family,pattern in PATTERNS.items():
            selected=[p for p in ps if re.search(pattern,p['match_text'],re.I)]
            row['anchors'][family]=[dict(paragraph_index=p['paragraph_index'],section=p['section'],paragraph_sha256=hashlib.sha256(p['text'].encode()).hexdigest()) for p in selected]
        singler=row['anchors']['singler']
        if [p['paragraph_index'] for p in singler]!=EXPECTED.get(paper['paper_id'],[]):raise ValueError('SingleR named anchor population differs: '+paper['paper_id'])
        if singler:
            anchors[paper['paper_id']]=singler
            texts[paper['paper_id']]={p['paragraph_index']:p['text'] for p in ps if p['paragraph_index'] in EXPECTED[paper['paper_id']]}
        census.append(row)
    checks=[('P051',93,'SingleR', '2.8.0'),('P051',119,'SingleR','2.6.0'),('P082',64,'SingleR','2.2.0'),
      ('P082',71,'SingleR','2.2.0'),('P192',16,'SingleR','1.2.4'),('P192',18,'SingleR','1.8.1'),('P233',8,'SingleR','2.41')]
    for paper,index,name,version in checks:
        if not re.search(re.escape(name)+r'.{0,18}'+re.escape(version),texts[paper][index]):raise ValueError('Source-specific literal release differs')
    if 'SingleR (2.41)' not in texts['P233'][8]:raise ValueError('Preserve the ambiguous literal spelling without normalization')
    return dict(status='PASS_COMPLETE_250_PRIMARY_SOURCE_CENSUS_NOT_IMPLEMENTATION_ADMISSION',papers=250,
      singleR_papers=4,singleR_named_anchors=9,census=census,source_specific_version_anchors=checks,
      primary_singleR_anchors=anchors,primary_anchor_texts=texts,
      registry_or_observation_metadata_changed=False,author_reference_objects_inferred=False,
      literal_2_41_resolved=False,new_family_credit=0,whole_goal_complete=False)

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args()
    result=audit()
    # Public audit exports use source links, original article terms and exact
    # paragraph identities. Complete original primary texts stay in the
    # unchanged private XML cache; no journal paragraph is relicensed as code.
    result.pop('primary_anchor_texts')
    result['complete_primary_text_preserved_in_original_cache']=True
    result['primary_paragraph_text_in_this_export']=False
    with a.out.open('x',encoding='utf-8',newline='\n') as s:s.write(json.dumps(result,ensure_ascii=False,indent=2)+'\n')
    print(result['status'])

if __name__=='__main__':main()
