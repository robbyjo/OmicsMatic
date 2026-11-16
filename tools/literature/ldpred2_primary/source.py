#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Independent full-corpus LDpred2 census and five scoped primary-source reviews."""
from pathlib import Path
import argparse,gzip,hashlib,json,xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parents[3]
KNOWN={
 'P074':dict(pmcid='PMC12807872',doi='10.1038/s41588-025-02417-6',anchor='77512206aa757dd65fefcd697732fc84f514e9d9f472090b964c92570ecf11bc',support=['c872f7134f46bab0b316ad27a35350756869f42eb37498c1041d531ab9e6fac6','002257e696ef36f9d35543759534a5477765d35d67c2b0d8ce1f35a3b2b0a4a7'],method='grid',version='1.12.2',context='analysis-method-description',
  summary='Constructed aortic-stenosis PRSs from GWAS excluding the UKB/MGBB evaluation cohorts and HapMap3 variants. Used population-matched 1000 Genomes LD, with European LD for the multi-ancestry GWAS. Tested heritability multipliers 0.7/1/1.4, 17 logarithmic causal fractions from 1e-4 to 1, and sparse/dense models before separate MGBB score selection.',
  scope='bigsnpr 1.12.2 and bigstatsr 1.6.1 are reported in Code availability. The multiplier grid requires the actual estimated heritability, which is unstated here: never silently use 1.4 as that heritability. LD construction, phenotype tuning and validation are separate; full author settings/build/seed remain unestablished.'),
 'P109':dict(pmcid='PMC11631756',doi='10.1038/s41588-024-01967-5',anchor='9b2142a1344bf72d4aadd3667fcab5e351cbd7ee0ece00f5be278f4a676cb09f',support=['29c458d45cd6cf51bfd6a74419906f67733199a94a6adc2bd337d083a8517117'],method='auto-supported-by-figure',version='1.12.4',context='analysis-method-description',
  summary='Calculated polygenic scores for grape traits using LDpred2, reported as version 1.12.4. Clustered genomic variations before a 70/20/10 training/tuning/test split; the figure explicitly labels LDpred2-auto phenotype prediction.',
  scope='The selected official bigsnpr development source reports 1.12.4; the exact author commit/binary and complete auto options are unknown. The project implements the prepared-input numerical workflow, not variant clustering, split construction, phenotype fitting or the full grape analysis.'),
 'P125':dict(pmcid='PMC12695632',doi='10.1038/s41588-025-02396-8',anchor='fd1de0f3932a40a899816ca63078d3779a10cc6d787ca7098b52223d393dd334',support=[],method='auto',version=None,context='analysis-method-description',
  summary='Computed LDpred2-auto PRSs through bigsnpr and custom R scripts, using author-provided European-ancestry UK Biobank LD matrices for about 1.1 million HapMap3 SNPs.',
  scope='Exact package version, custom-script controls, seed and author binary remain unresolved. Synthetic population pair-LD scale controls do not reproduce the biological UKB LD resource or establish the author environment. Do not silently infer the selected project edition or defaults as the paper settings.'),
 'P128':dict(pmcid='PMC13553296',doi='10.1038/s41588-026-02722-8',anchor='b2ce9d3e449bc1570ade5a2c89eeb0c8ffcc8f2bd4e4128a26ed67715af061de',support=['ba6c879ccb51baa4f7cf9478c9a39f8005e543e3e7fe714d47a5cd51db63f482','fa8e26b4d8ae138d3f34b9b9e89df360690d06a84977224b13a93f27e711642e'],method='unresolved-inf-grid-or-auto',version=None,context='analysis-method-description',
  summary='Used LDPred2 among methods for multi-ancestry Alzheimer-disease PRSs, comparing meta-analysis-derived, weighted and unweighted PRS combinations. Selected scores in ADSP and separately trained an APOE-region-excluded score with ADGC-derived summation weights.',
  scope='Version, inf/grid/auto choice and LD controls are unstated. Do not infer a project method/default. Ancestry-specific scoring, weight training, score selection, the reported APOE window and independent cohort validation are separate unqualified steps.'),
 'P205':dict(pmcid='PMC13504350',doi='10.1016/j.ajhg.2026.05.006',anchor='2152dd1f57811a4634655e5d2ab7d923239edebfd0284211857ea9cbe7a8a848',support=['0ea677346bc26044d50d5e9197b916997c82c711616a6e6da07ec464942efb11'],method='unresolved-inf-grid-or-auto',version=None,context='benchmark-or-comparison',
  summary='Adjusted effects with LDpred2 for real-GWAS conventional PRS comparators; PRS-CS was used separately for simulated-GWAS comparators. Fine-tuning data selected single-GWAS scores and fitted their linear combination.',
  scope='Version, grid/auto choice and LD panel are unreported here. Preserve real versus simulated inputs and independent tuning/evaluation partitions. The estimator adapter does not implement downstream score selection, ancestry-specific fine-tuning or combination fitting, and does not implement LDpred1.')
}
def digest(raw):return hashlib.sha256(raw).hexdigest()
def encoded(value):return (json.dumps(value,ensure_ascii=False,indent=2)+'\n').encode()
def census(root=ROOT):
    root=Path(root).resolve();papers=json.loads((root/'docs/literature/omics-2023-2026/papers.json').read_bytes());assert len(papers)==250
    prior=json.loads((root/'docs/literature/ldpred2-native-development/PRIMARY-ROLES.json').read_bytes());expected={p['paper_id']:{v['sha256'] for v in p['paragraphs']} for p in prior['papers']}
    primary={};hits=[];reviews=[];bridges=[]
    for paper in papers:
        raw=gzip.decompress((root/'build/literature-review/xml'/(paper['pmcid']+'.xml.gz')).read_bytes());assert digest(raw)==paper['xml_sha256'],paper['paper_id']
        primary[paper['paper_id']]=dict(pmcid=paper['pmcid'],bytes=len(raw),xml_sha256=digest(raw));article=ET.fromstring(raw);parents={c:p for p in article.iter() for c in p};found={};raw_hashes=set()
        for index,node in enumerate(article.iter('p')):
            original=''.join(node.itertext());text=' '.join(original.split())
            if 'ldpred' not in text.casefold():continue
            raw_hash=digest(original.encode());sha=digest(text.encode());raw_hashes.add(raw_hash);sections=[];q=node
            while q in parents:
                q=parents[q]
                if q.tag=='sec':
                    title=q.find('title')
                    if title is not None:sections.append(''.join(title.itertext()))
                elif q.tag=='fig':sections.append('Figure '+str(q.get('id')))
            record=found.setdefault(sha,dict(paragraph_sha256=sha,raw_itertext_sha256=raw_hash,characters=len(text),locations=[]))
            record['locations'].append(dict(xml_all_p_index=index,section=' / '.join(reversed(sections))))
            if raw_hash!=sha:bridges.append(dict(paper_id=paper['paper_id'],raw_itertext_sha256=raw_hash,unicode_whitespace_normalized_sha256=sha,xml_all_p_index=index,source_bytes_changed=False))
        if not found:continue
        pid=paper['paper_id'];assert pid in KNOWN,'Additional LDpred mention needs source review: '+pid
        assert raw_hashes==expected[pid],('Raw primary paragraph population differs',pid)
        role=KNOWN[pid];assert paper['pmcid']==role['pmcid'] and paper['doi']==role['doi']
        assert set([role['anchor'],*role['support']])<=set(found),pid
        hits.append(dict(paper_id=pid,pmcid=paper['pmcid'],paragraphs=list(found.values())))
        reviews.append(dict(paper_id=pid,pmcid=paper['pmcid'],doi=paper['doi'],xml_sha256=paper['xml_sha256'],tool_id='ldpred',evidence_paragraph_sha256=role['anchor'],source_normalization='Unicode-whitespace collapse over XML p itertext; raw hashes retained separately',use_summary=role['summary'],implementation_scope_note=role['scope'],usage_context=role['context'],version_reported_near_name=role['version'],method_reported=role['method'],exact_author_binary_build_established=False,author_options_established=False,whole_paper_reproduced=False,supporting_evidence=[found[s] for s in role['support']]))
    assert {p['paper_id'] for p in hits}==set(KNOWN)
    return dict(status='PASS_ALL_250_LDPRED2_PRIMARY_XML_AND_FIVE_SCOPED_REVIEWS',papers_scanned=250,primary_files=primary,papers=hits,normalization_bridges=bridges,older_raw_itertext_hashes_preserved=True,reviews=reviews,new_catalog_credit=0,whole_goal_complete=False)
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.absolute();out.mkdir(parents=True,exist_ok=False)
    try:
        result=census();(out/'CENSUS.json').write_bytes(encoded(result));(out/'REVIEWS.json').write_bytes(encoded(result['reviews']));print(result['status']);return 0
    except Exception as error:
        (out/'FAILURE.json').write_bytes(encoded(dict(cause=repr(error),required_action='Retain the exact primary XML cache and old source records. Resolve the named new/missing role before admission; never refresh old digests.')));print('FAIL2_LDPRED2_ROLES_UNREVIEWED',repr(error));return 2
if __name__=='__main__':raise SystemExit(main())
