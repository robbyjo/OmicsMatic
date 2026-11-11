# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Bind final Monocle generation/role reviews and separately recovered named software."""
import argparse
import gzip
import hashlib
import json
from pathlib import Path
import xml.etree.ElementTree as ET
from setup_deseq2_reference import ROOT,pin,save
from collect_omics_review import paragraphs,validate_supporting_evidence

FACTS={
 ('P024','py-monocle'):(
  'Computed pseudotime using py-monocle for the Monocle3/Palantir comparison, with 200 eligible roots and selection by Braak-stage correlation.',
  'The paper calls this a Python wrapper; upstream describes a separate Python implementation with graph-handling changes. Exact paper commit and settings are unstated. The inspected Community-Use License permits noncommercial use only. No runtime installation or analysis has run; implementation awaits the user license preference. R/native Monocle controls are not py-monocle controls.',
  'software-comparison-with-selected-root'),
 ('P043','cicero'):(
  'Predicted cis-co-accessible cCRE pairs separately across 275 subclasses with Cicero for Monocle3, mm10, 500-kb windows and default parameters, followed by shuffled-background score fitting and FDR filtering.',
  'Samples at most 5000 nuclei per subclass; the paper reports the Hypendymal_NN subclass failed. Normal background fitting uses fitdistrplus, BH FDR<0.001 and GENCODE mm10v23 TSS±1kb distal/proximal grouping. The source says filtered out only if scores were significantly larger; preserve that wording rather than infer a reversed retention rule. Exact Cicero/Monocle commits, seeds and background implementation remain unknown. Source capture of Cicero-M3 1.3.9 is not paper reproduction.',
  'co-accessibility-and-background-filtering'),
 ('P153','cicero'):(
  'Benchmarked Cicero predictions after subsetting candidate pairs by peak accessibility for individual cell types.',
  'This Methods paragraph establishes prediction subsetting, not a new Cicero fitting run or gene-expression filtering for Cicero. Other predictors use detected-gene filters; the explicit Cicero exception uses accessibility only. Exact prediction release, Cicero patch and generation settings remain unresolved.',
  'published-prediction-comparator')}


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args();base=ROOT/'build/literature-review/monocle-runtime';out=a.out.resolve();assert out.is_relative_to(base) and not out.exists();out.mkdir()
    drafts=json.loads((ROOT/'tools/literature/monocle-use-review-drafts.json').read_bytes());papers={p['paper_id']:p for p in json.loads((ROOT/'docs/literature/omics-2023-2026/papers.json').read_bytes())}
    for draft in drafts:
        paper=papers[draft['paper_id']];raw=gzip.decompress((ROOT/'build/literature-review/xml'/(paper['pmcid']+'.xml.gz')).read_bytes());assert hashlib.sha256(raw).hexdigest()==paper['xml_sha256'];ps=paragraphs(ET.fromstring(raw));byid={r['paragraph_index']:r for r in ps};review=draft['review']
        review['supporting_evidence']=[dict(paragraph_index=r['paragraph_index'],section=r['section'],paragraph_sha256=hashlib.sha256(byid[r['paragraph_index']]['text'].encode('utf8')).hexdigest()) for r in draft.pop('additional_named_context_paragraphs')]
        if draft['paper_id']=='P024':
            review['use_summary']='Compared pseudotime from py-monocle, described in the paper as a Monocle 3 Python wrapper, with neural-network predictions and Palantir in microglia; tried 200 eligible starting cells and retained the highest Braak-stage correlation.'
            review['implementation_scope_note']=FACTS['P024','py-monocle'][1]+' Preserve specified starting-cell eligibility and selection; these are not default root selection or independent predictive validation.'
        if draft['paper_id']=='P099':
            review['use_summary']='Used Monocle 2 differentialGeneTest for gene/exon and accessibility comparisons, age/AD analyses with sex covariates, subtype fraction likelihood-ratio tests, and human AD/control expression comparisons; populations and significance/effect/expression filters differ by analysis.'
            review['implementation_scope_note']='Named supporting Methods identify subclusters, exon inputs, maximum-5000 sampling, sex covariates, RT-well or mouse replicate counts, accessibility filters and human comparisons. Exact patch, family, full formulas and normalization remain unresolved. The validated 2.26.0 development adapter preserves original differential tests, size factors and dispersions, without reconstructing unstated choices or the paper patch. Original free-NB/binomial family failures remain unadmitted. TPM thresholds do not establish raw-count eligibility; do not treat cells as mouse replicates, drop failed genes or substitute Monocle3 tests.'
        if draft['paper_id'] in ['P051','P117']:review['implementation_scope_note']+=' Official DESCRIPTION history now supplies a version-matching source candidate; the paper commit is not established and source capture does not validate execution.'
        if draft['paper_id']=='P043':review['implementation_scope_note']+=' Preserve the reported failed Hypendymal_NN subclass and the source filtering wording without reversing its meaning.'
    recovery=json.loads((base/'named-recovery-intake-1/COMPLETED.json').read_bytes());new=[]
    for record in recovery['records']:
        pid,tid=record['paper_id'],record['tool_id'];x=json.loads((base/'named-recovery-intake-1'/(pid+'-'+tid+'.json')).read_bytes());o=x['observation'];summary,scope,context=FACTS[pid,tid]
        review=dict(pmcid=x['pmcid'],doi=x['doi'],tool_id=tid,evidence_paragraph_sha256=hashlib.sha256(o['evidence_text'].encode('utf8')).hexdigest(),use_summary=summary,implementation_scope_note=scope,version_reported_near_name=None,usage_context=context,supporting_evidence=[])
        if pid=='P153':
            row=next(row for row in x['named_paragraphs'] if row['paragraph_index']==11);review['supporting_evidence']=[dict(paragraph_index=11,section=row['section'],paragraph_sha256=hashlib.sha256(row['text'].encode('utf8')).hexdigest())]
        new.append(dict(paper_id=pid,primary_paragraph=o['evidence_paragraph_index'],review=review))
    drafts.extend(sorted(new,key=lambda d:(d['paper_id'],d['review']['tool_id'])));assert len(drafts)==12 and sum(len(d['review']['supporting_evidence']) for d in drafts)==7
    original=json.loads((ROOT/'tools/literature/tool-registry.json').read_bytes());registry={t['id']:t for t in [*original,*recovery['registry']]};assert len(registry)==427
    for draft in drafts:
        paper=papers[draft['paper_id']];ps=paragraphs(ET.fromstring(gzip.decompress((ROOT/'build/literature-review/xml'/(paper['pmcid']+'.xml.gz')).read_bytes())));validate_supporting_evidence(ps,[draft['review']],registry)
    final=ROOT/'tools/literature/monocle-catalog-use-reviews.json';overlay=ROOT/'tools/literature/monocle-named-software-registry.json';assert not final.exists() and not overlay.exists();save(final,drafts);save(overlay,recovery['registry'])
    save(out/'COMPLETED.json',dict(status='prepared-twelve-primary-bound-reviews-and-two-distinct-registry-additions',drafts=pin(final),registry=pin(overlay),source_pin=pin(Path(__file__)),supporting_pointers=7,published_catalog_changed=False));print('Prepared12 reviews,9 Monocle and3 distinct software observations,7 support pointers')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable primary context checks')
    main()
