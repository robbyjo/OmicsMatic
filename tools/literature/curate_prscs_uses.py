#!/usr/bin/env python3
"""Validate twelve PRS-CS roles;add eleven source-checked reviews and preserve the existing one."""
import argparse
import gzip
import hashlib
import json
import os
from pathlib import Path
import tempfile
import xml.etree.ElementTree as ET
from collect_omics_review import paragraphs,tool_matches,publication_record,validate_supporting_evidence

ROOT=Path(__file__).resolve().parents[2]
ROWS=json.loads(r'''[
  {
    "paper_id": "P024",
    "primary_paragraph": 60,
    "review": {
      "pmcid": "PMC13600946",
      "doi": "10.1038/s41586-025-09573-z",
      "tool_id": "prscs",
      "evidence_paragraph_sha256": "a3a5d336d4e7b2423790a2882dfeb2363fc415f292a5ba856b92cdf506300258",
      "use_summary": "Inferred Alzheimer disease SNP weights with PRS-CS-auto using a developers' 1000 Genomes LD panel and reported a=1,b=0.5,1000 iterations,500 burn-in and thinning5; PLINK2 then computed individual scores.",
      "implementation_scope_note": "The exact source build and LD-panel population are unstated. Scores including or excluding APOE and their sensitivity comparison are downstream choices; PRS-CS inference does not perform that evaluation.",
      "version_reported_near_name": ""
    }
  },
  {
    "paper_id": "P074",
    "primary_paragraph": 53,
    "review": {
      "pmcid": "PMC12807872",
      "doi": "10.1038/s41588-025-02417-6",
      "tool_id": "prscs",
      "evidence_paragraph_sha256": "77512206aa757dd65fefcd697732fc84f514e9d9f472090b964c92570ecf11bc",
      "use_summary": "Inferred ankylosing-spondylitis PRS weights from UKB/MGBB-excluded GWAS with matched-population 1000 Genomes LD panels; the multi-ancestry GWAS used a European panel. PRS-CS evaluated phi=1e-6,1e-4,1e-2,1 with a=1,b=0.5 before separate MGBB tuning.",
      "implementation_scope_note": "The reported date label is retained without inferring an unprovided historical checksum. LDpred2's heritability,sparsity and causal-fraction grid is separate. Selecting the best predictive PRS requires the stated nonoverlapping tuning data and evaluation design.",
      "version_reported_near_name": "2021-Jun-4",
      "supporting_evidence": [
        {
          "paragraph_index": 54,
          "section": "Methods / Development of an AS PRS",
          "paragraph_sha256": "c872f7134f46bab0b316ad27a35350756869f42eb37498c1041d531ab9e6fac6"
        },
        {
          "paragraph_index": 68,
          "section": "Code availability",
          "paragraph_sha256": "002257e696ef36f9d35543759534a5477765d35d67c2b0d8ce1f35a3b2b0a4a7"
        }
      ]
    }
  },
  {
    "paper_id": "P076",
    "primary_paragraph": 11,
    "review": {
      "pmcid": "PMC10786716",
      "doi": "10.1038/s41588-023-01583-9",
      "tool_id": "prscs",
      "evidence_paragraph_sha256": "64a206036a5a1506064a0b3982cb20bea2ff97fac498c92d8d2ecf534e236246",
      "use_summary": "Benchmarked PRS-CS-mult by fitting separate single-population PRS-CS models and learning a linear combination of their scores using training and evaluation partitions.",
      "implementation_scope_note": "Separate single-population models plus learned combination weights are distinct from PRS-CSx joint inference. The native adapter can fit the constituent models; combination training and benchmark performance require their own explicitly designed data splits.",
      "version_reported_near_name": "1.0.0",
      "usage_context": "benchmark-or-comparison",
      "supporting_evidence": [
        {
          "paragraph_index": 72,
          "section": "Code availability",
          "paragraph_sha256": "0e422572c1e718b67565d1037a8f88a8affd75dc256673e5ac4a26e981e179e0"
        }
      ]
    }
  },
  {
    "paper_id": "P090",
    "primary_paragraph": 40,
    "review": {
      "pmcid": "PMC11631752",
      "doi": "10.1038/s41588-024-01952-y",
      "tool_id": "prscs",
      "evidence_paragraph_sha256": "bcdc0db06ac40e35cb8aa29991574eb07548e414661c051f352181d25e546448",
      "use_summary": "Inferred dilated-cardiomyopathy PRS weights with PRS-CS-auto and a European 1000 Genomes phase3 LD panel after UKB-excluded GWAS/MTAG analysis; PLINK1.9 applied the resulting SNP weights.",
      "implementation_scope_note": "The paper reports version1.0 without an exact source checksum. Association/MTAG,marker QC and clinical evaluation are separate operations; the adapter does not infer their settings or remove training-target overlap automatically.",
      "version_reported_near_name": "1.0"
    }
  },
  {
    "paper_id": "P105",
    "primary_paragraph": 57,
    "review": {
      "pmcid": "PMC10703676",
      "doi": "10.1038/s41588-023-01569-7",
      "tool_id": "prscs",
      "evidence_paragraph_sha256": "4ebb423f0707161b8c3fafcdcfc705aaca23fbffbd77113804037cadb68b768f",
      "use_summary": "Inferred SNP weights with PRS-CS-auto and an East Asian 1000 Genomes HapMap3 reference,using TMM-50K GWAS for scores applied to BioBank Japan.",
      "implementation_scope_note": "The source reports the2021-Jun-4 PRS-CS date and Python3.8.8; neither establishes a complete archived environment or exact panel checksum. The privately tested historical tag uses a separately declared compatible Python runtime and does not claim to reproduce the study environment.",
      "version_reported_near_name": "2021-Jun-4"
    }
  },
  {
    "paper_id": "P114",
    "primary_paragraph": 45,
    "review": {
      "pmcid": "PMC13649597",
      "doi": "10.1038/s41588-026-02732-6",
      "tool_id": "prscs",
      "evidence_paragraph_sha256": "41c4e85ebfde638860aafeba33fdf10eda8231e585f3ca0c73953f98b28b329f",
      "use_summary": "Inferred schizophrenia PRS weights from East Asian GWAS with an East Asian 1000 Genomes phase3 LD panel and phi=0.01 after excluding MHC and strand-ambiguous variants; PLINK2 computed chromosome scores.",
      "implementation_scope_note": "The exact software build and remaining sampling options are unstated. Summing chromosome scores,standardization and downstream phenotype analyses are separate; the adapter requires explicit marker and sampling policies.",
      "version_reported_near_name": ""
    }
  },
  {
    "paper_id": "P120",
    "primary_paragraph": 31,
    "review": {
      "pmcid": "PMC12695664",
      "doi": "10.1038/s41588-025-02410-z",
      "tool_id": "prscs",
      "evidence_paragraph_sha256": "edfdbdd09795b82783bcb547c7a0217eea9c54a73d24452f2c7f2caf27e8d48a",
      "use_summary": "Inferred hypothyroidism PRS weights from the GWAS meta-analysis using PRS-CS with a UK Biobank LD reference panel before UKB/GESUS phenotype evaluation.",
      "implementation_scope_note": "The cited paragraph does not specify the PRS-CS version,global-shrinkage choice or panel population. Those remain caller declarations; downstream regression and pROC evaluation are separate and are not inferred from the named LD source.",
      "version_reported_near_name": ""
    }
  },
  {
    "paper_id": "P126",
    "primary_paragraph": 52,
    "review": {
      "pmcid": "PMC11549050",
      "doi": "10.1038/s41588-024-01947-9",
      "tool_id": "prscs",
      "evidence_paragraph_sha256": "f39bdb1bbd0d1a64476919e0c2aa4bb2c7cbaae33490650520fe68f905f9f3ca",
      "use_summary": "Inferred type2-diabetes PRS weights with PRS-CS-auto from a European GWAS meta-analysis excluding UKB,using a European UK Biobank LD panel,for later rare-gene carrier stratification.",
      "implementation_scope_note": "PRS-CS fits the common-variant score here; it does not fit rare-variant burden effects or establish MODY pathogenicity. Carrier definitions,stratification and downstream associations require their separately documented methods.",
      "version_reported_near_name": ""
    }
  },
  {
    "paper_id": "P128",
    "primary_paragraph": 40,
    "review": {
      "pmcid": "PMC13553296",
      "doi": "10.1038/s41588-026-02722-8",
      "tool_id": "prscs",
      "evidence_paragraph_sha256": "12915ed2c1a092fe7ae9a2655452b2ef90e886f7265fc218c070b729cfd34e84",
      "use_summary": "Inferred separate population-specific Alzheimer disease PRS weights using ancestry-matched 1000 Genomes HapMap3 LD panels; PRSice2 applied scores without clumping before ADGC combination-weight learning and ADSP evaluation.",
      "implementation_scope_note": "This is separate PRS-CS inference followed by scoring,linear combination and normalization,not joint PRS-CSx inference. Reported defaults do not identify an exact phi/source build; training weights and evaluation normalization remain distinct supplied operations.",
      "version_reported_near_name": "",
      "supporting_evidence": [
        {
          "paragraph_index": 42,
          "section": "Methods / Training of PRS summation weights using ADGC data",
          "paragraph_sha256": "fa8e26b4d8ae138d3f34b9b9e89df360690d06a84977224b13a93f27e711642e"
        }
      ]
    }
  },
  {
    "paper_id": "P141",
    "primary_paragraph": 55,
    "review": {
      "pmcid": "PMC13553284",
      "doi": "10.1038/s41588-026-02705-9",
      "tool_id": "prscs",
      "evidence_paragraph_sha256": "2c4020bf555354e571be311f67c99fb37d6f4bd796e068131469ac67dea637df",
      "use_summary": "Used PRS-CS as an alternative method for the common-variant PRS component when comparing and combining it with separately estimated rare-variant scores.",
      "implementation_scope_note": "This does not assign rare-variant effect estimation or burden testing to PRS-CS. SBayesRC,rare-effect fitting and composite-score evaluation are separate; the paragraph leaves the PRS-CS build,panel and sampling parameters unstated.",
      "version_reported_near_name": ""
    }
  },
  {
    "paper_id": "P150",
    "primary_paragraph": 61,
    "review": {
      "pmcid": "PMC11549047",
      "doi": "10.1038/s41588-024-01933-1",
      "tool_id": "prscs",
      "evidence_paragraph_sha256": "2f87476465169f1a2689944763c6f6bc8b7abc13bf563fb5b28d16e595f4d04c",
      "use_summary": "Inferred PRS weights for metabolic-syndrome components and latent factors from UKB-excluded GWAS using phi=0.01,a European 1000 Genomes phase3 LD panel and effective N for binary phenotypes; reported a=1,b=0.5,1000/500/5 sampling settings preceded PLINK2 scoring.",
      "implementation_scope_note": "Effective N is a supplied study quantity,not silently substituted for total sample size. Genomic SEM,individual genotype scoring and predictive/clinical evaluation are separate; the exact PRS-CS source build is unstated.",
      "version_reported_near_name": ""
    }
  },
  {
    "paper_id": "P205",
    "primary_paragraph": 36,
    "review": {
      "pmcid": "PMC13504350",
      "doi": "10.1016/j.ajhg.2026.05.006",
      "tool_id": "prscs",
      "evidence_paragraph_sha256": "2152dd1f57811a4634655e5d2ab7d923239edebfd0284211857ea9cbe7a8a848",
      "use_summary": "Used PRS-CS to adjust SNP effects for simulated GWAS data in the conventional multi-population PRS comparator pipeline, followed by independent tuning and linear combination of single-GWAS scores.",
      "implementation_scope_note": "LDpred2 is used for real GWAS data in the described comparator. Preserve simulation versus empirical inputs and the tuning/evaluation partitions. The source gives no PRS-CS version or exact hyperparameters here; downstream tuning is separate from Bayesian effect estimation.",
      "usage_context": "benchmark-or-comparison"
    }
  }
]''' )


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, required=True, help='New report directory; no source excerpts are exported')
    parser.add_argument('--apply', action='store_true', help='Add validated reviews to reviewed-uses.json')
    args = parser.parse_args()
    args.out.mkdir(parents=True, exist_ok=False)
    data = ROOT/'docs/literature/omics-2023-2026'
    papers = {p['paper_id']: p for p in json.loads((data/'papers.json').read_text(encoding='utf-8'))}
    registry = {r['id']: r for r in json.loads(Path(__file__).with_name('tool-registry.json').read_text())}
    path = Path(__file__).with_name('reviewed-uses.json')
    before = path.read_bytes(); original_hash = hashlib.sha256(before).hexdigest()
    document = json.loads(before); merged = list(document['reviews']); by_key = {(r['pmcid'],r['tool_id']): r for r in merged}
    assert len(by_key) == len(merged)
    proof = []
    for row in ROWS:
        paper = papers[row['paper_id']]; review = row['review']
        assert paper['doi'] == review['doi'] and paper['pmcid'] == review['pmcid']
        raw = gzip.decompress((ROOT/'build/literature-review/xml'/(paper['pmcid']+'.xml.gz')).read_bytes())
        assert hashlib.sha256(raw).hexdigest() == paper['xml_sha256']
        sources = paragraphs(ET.fromstring(raw)); primary = sources[row['primary_paragraph']-1]
        assert primary['paragraph_index'] == row['primary_paragraph']
        assert hashlib.sha256(primary['text'].encode()).hexdigest() == review['evidence_paragraph_sha256']
        validate_supporting_evidence(sources, [review], registry)
        tools = tool_matches(sources, [registry['prscs']], {'prscs': review['evidence_paragraph_sha256']})
        assert len(tools) == 1 and tools[0]['evidence_paragraph_index'] == row['primary_paragraph']
        check = dict(paper, tools=tools)
        if 'version_reported_near_name' in review:
            assert publication_record(check, [review])['tools'][0]['version_reported_near_name'] == review['version_reported_near_name']
        key = (review['pmcid'], review['tool_id'])
        if key in by_key:
            assert by_key[key] == review, ('Existing review changed; inspect before replacing', key)
        else:
            merged.append(review); by_key[key] = review
        proof.append(dict(paper_id=row['paper_id'], primary_paragraph=row['primary_paragraph'], evidence_sha256=review['evidence_paragraph_sha256'], supporting_pointers=len(review.get('supporting_evidence', []))))
    assert len(proof) == 12
    result = dict(status='passed-source-role-validation', applied=args.apply, reviewed_prscs_roles=12, total_reviews=len(merged), added_reviews=len(merged)-len(document['reviews']), original_reviews_sha256=original_hash, proof=proof, curator_sha256=sha(__file__), scope='Hash-verified PRS-CS roles;separate fixed/auto shrinkage,panel/sampling/score/combination/tuning operations;one existing simulated-data comparator review is preserved; semantic paper completeness, historical cohort reconstruction and software validation are separate.')
    if args.apply:
        assert sha(path) == original_hash, 'Review file changed during validation'
        history = ROOT/'build/literature-review/catalog-history'; history.mkdir(exist_ok=True)
        archive = history/(original_hash+'-reviewed-uses.json.gz')
        if not archive.exists():
            archive.write_bytes(gzip.compress(before, mtime=0))
        document['reviews'] = merged
        with tempfile.NamedTemporaryFile('w', encoding='utf-8', dir=path.parent, delete=False) as stream:
            temporary = Path(stream.name); stream.write(json.dumps(document, ensure_ascii=False, indent=2)+'\n')
        try:
            assert sha(path) == original_hash, 'Review file changed before publication'
            os.replace(temporary, path)
        finally:
            temporary.unlink(missing_ok=True)
        result['reviews_sha256'] = sha(path)
    (args.out/'verification.json').write_text(json.dumps(result, indent=2)+'\n')
    print(json.dumps({k: result[k] for k in ['status','applied','total_reviews','added_reviews']}))


if __name__ == '__main__':
    main()
