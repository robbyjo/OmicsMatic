#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Primary-source census and explicit Sambamba roles across the full fixed corpus."""
from pathlib import Path
import copy,gzip,hashlib,json,sys,xml.etree.ElementTree as ET
sys.dont_write_bytecode=True
ROOT=Path(__file__).resolve().parents[3]
DATA='docs/literature/omics-2023-2026';PAPERS=DATA+'/papers.json';REVIEWS='tools/literature/reviewed-uses.json'
KNOWN={
 'P082':dict(pmcid='PMC11631769',doi='10.1038/s41588-024-01999-x',xml_sha256='c46b71277955179cdc573c3bf2131f9e385491f06ebfc680ddfce7143dc1077e',evidence_paragraph_sha256='ca44d238ba149b628e05398ce1a466404ae438b1e859861e914d481d3a0e16b9',version='0.6.5',section='Code availability',index=64,context='reported-software-list-role-unresolved',role='Declared software; exact Sambamba operation unresolved',supported=False,
    summary='Code availability lists Sambamba 0.6.5; its operation is unreported.'),
 'P110':dict(pmcid='PMC12695648',doi='10.1038/s41588-025-02409-6',xml_sha256='60ec7e72b2a2ec2bef4fe2a9695ce9668fb05548951d3f624c9865080509232b',evidence_paragraph_sha256='b8ee0c2ce8208e736b6d8dae2555d90e7ca85b56630e720797d3b31eb7b9e5be',version='0.6.6',section='Methods / ChIP–seq library preparation and analysis',index=49,context='analysis-method-description',role='Duplicate removal before ChIP-seq coverage and peak analysis',supported=True,
    summary='Removed duplicate ChIP-seq alignments with Sambamba 0.6.6 after fastp trimming and Bowtie2 alignment, before coverage/insert-size visualization and MACS3 peak analysis. Exact subcommand, flags and executable build are unreported.'),
 'P198':dict(pmcid='PMC11568759',doi='10.1016/j.ajhg.2024.09.005',xml_sha256='c00fe16d9e42a3dd58df478207b86b0fbe70bfee6a4d0609b637c4c22080e1af',evidence_paragraph_sha256='8d8785c53a087ff98eba096f3e2f9e4a4f1c475127a0860b99e1c991d4c031fc',version='0.8.2',section='Methods / Single-sperm sequencing',index=15,context='analysis-method-description',role='Duplicate marking before donor-level PAR variant calling',supported=True,
    summary='Marked duplicates with Sambamba 0.8.2 after SAMtools combined each donor’s PAR alignments across haploid cells, before FreeBayes calling. Alignment used hg38 with chrY PAR masked. The doublet-barcode filtering order, full flags and any cell/UMI grouping rule are not established.')
}
def load(path):return json.loads(path.read_bytes())
def digest(raw):return hashlib.sha256(raw).hexdigest()
def census(root=ROOT,papers=None):
    papers=load(root/PAPERS) if papers is None else papers
    if len(papers)!=250:raise ValueError('Complete fixed250-paper corpus required')
    files={};hits=[]
    for paper in papers:
        path=root/'build/literature-review/xml'/(paper['pmcid']+'.xml.gz');raw=gzip.decompress(path.read_bytes())
        if digest(raw)!=paper['xml_sha256']:raise ValueError('Frozen primary XML differs: '+paper['paper_id'])
        files[paper['paper_id']]=dict(pmcid=paper['pmcid'],bytes=len(raw),sha256=digest(raw));paragraphs={};occurrences=0
        for paragraph in ET.fromstring(raw).iter('p'):
            text=' '.join(''.join(paragraph.itertext()).split())
            if 'sambamba' not in text.casefold():continue
            sha=digest(text.encode());paragraphs[sha]=dict(sha256=sha,characters=len(text));occurrences+=1
        if paragraphs:hits.append(dict(paper_id=paper['paper_id'],pmcid=paper['pmcid'],unique_paragraphs=list(paragraphs.values()),raw_paragraph_occurrences=occurrences))
    if {row['paper_id'] for row in hits}!=set(KNOWN):raise ValueError('Additional or missing Sambamba primary mentions need review')
    for row in hits:
        expected=KNOWN[row['paper_id']]
        if files[row['paper_id']]['sha256']!=expected['xml_sha256'] or {p['sha256'] for p in row['unique_paragraphs']}!={expected['evidence_paragraph_sha256']}:
            raise ValueError('Primary role paragraph differs: '+row['paper_id'])
    return dict(status='PASS_COMPLETE_PRIMARY_SAMBAMBA_CENSUS_AND_THREE_SOURCE_ROLES',papers_scanned=250,primary_files=files,hits=hits,newly_detected_paper='P082',
        missed_name_reason='Inline citation62 joined to Sambamba in original itertext; reviewed declaration is retained without inferring an operation.',
        P082_operation_established=False,P082_reported_edition_supported=False,new_scoped_integration_credit=0)
def reviews(root=ROOT):
    papers={p['paper_id']:p for p in load(root/PAPERS)};result=[]
    for paper_id,role in KNOWN.items():
        paper=papers[paper_id]
        if any(paper[key]!=role[key] for key in ['pmcid','doi','xml_sha256']):raise ValueError('Exact primary paper identity required: '+paper_id)
        result.append(dict(paper_id=paper_id,pmcid=role['pmcid'],doi=role['doi'],tool_id='sambamba',xml_sha256=role['xml_sha256'],
            evidence_paragraph_sha256=role['evidence_paragraph_sha256'],version_reported_near_name=role['version'],use_summary=role['summary'],usage_context=role['context'],
            reported_edition_has_validated_project_profile=role['supported'],author_operation_established=paper_id!='P082',author_options_established=False,
            exact_author_executable_build_established=False,whole_paper_reproduced=False))
    return result
