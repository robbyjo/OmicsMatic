#!/usr/bin/env python3
"""Validate nine Beagle paper roles against cached primary sources; optionally apply them."""
import argparse
import gzip
import hashlib
import json
import os
from pathlib import Path
import tempfile
import xml.etree.ElementTree as ET
from collect_omics_review import paragraphs, tool_matches, publication_record, validate_supporting_evidence

ROOT = Path(__file__).resolve().parents[2]
ROWS = json.loads(r'''[
  {
    "paper_id": "P024",
    "primary_paragraph": 66,
    "review": {
      "pmcid": "PMC13600946",
      "doi": "10.1038/s41586-025-09573-z",
      "tool_id": "beagle",
      "evidence_paragraph_sha256": "7b675badc807893bd5cbd3a2c12c509f2be1ebbee8eb457b5372e53ad1f0e082",
      "use_summary": "Estimated haplotypes across the selected MAPT/17q21.31 genotypes after PCA and three-cluster haplotype grouping; the source reports one burn-in and 23 phasing iterations.",
      "implementation_scope_note": "Reported convergence counts are not a complete command-line specification. PCA, k-means and the later logistic model are separate operations; the reference panel and other Beagle settings are not stated.",
      "version_reported_near_name": "5.4"
    }
  },
  {
    "paper_id": "P101",
    "primary_paragraph": 41,
    "review": {
      "pmcid": "PMC10703678",
      "doi": "10.1038/s41588-023-01571-z",
      "tool_id": "beagle",
      "evidence_paragraph_sha256": "7acf216e86768e323cec803a0c69b3638ee7c58960a97e01418e6dd121e83288",
      "use_summary": "Imputed missing SNP calls before the millet SNP GWAS on 43 phenotypes; SNP QC used MAF at least 0.05 and missingness at most 0.1.",
      "implementation_scope_note": "The paragraph assigns Beagle imputation to SNPs, not the separate PAV analysis. No reference panel or full Beagle options are specified; EMMAX association and kinship construction are separate programs.",
      "version_reported_near_name": "4.1"
    }
  },
  {
    "paper_id": "P102",
    "primary_paragraph": 40,
    "review": {
      "pmcid": "PMC11549052",
      "doi": "10.1038/s41588-024-01957-7",
      "tool_id": "beagle",
      "evidence_paragraph_sha256": "89dbb95679d724542261dbc3f55553abc52c71d50a3d31dc09dcb7cb2f6f4c8c",
      "use_summary": "Imputed and phased variants selected at MAF above 0.05 within each of two Brassica napus subpopulations before GEMMA GWAS.",
      "implementation_scope_note": "Input ploidy, reference panel and detailed Beagle settings are not supplied. The species name does not establish a polyploid HMM or validate every structural-variant encoding; GEMMA PCs/kinship/Wald association are separate.",
      "version_reported_near_name": "5.1"
    }
  },
  {
    "paper_id": "P149",
    "primary_paragraph": 42,
    "review": {
      "pmcid": "PMC10632135",
      "doi": "10.1038/s41588-023-01538-0",
      "tool_id": "beagle",
      "evidence_paragraph_sha256": "a2e33d82822c37ab9a96fb4f61211047b4972e6fd2efd5d902b2e4a298077967",
      "use_summary": "Imputed FinnGen Release 6 array genotypes with the Finnish SISu v3 whole-genome reference panel.",
      "implementation_scope_note": "This is population-specific reference imputation, not ordinary missing-call completion without a panel. The paper does not identify an exact Beagle 4.1 build; the panel access/build and full imputation protocol remain explicit user inputs.",
      "version_reported_near_name": "4.1"
    }
  },
  {
    "paper_id": "P171",
    "primary_paragraph": 52,
    "review": {
      "pmcid": "PMC13447107",
      "doi": "10.1038/s41588-026-02678-9",
      "tool_id": "beagle",
      "evidence_paragraph_sha256": "7fa6c4098d2d204956135f0569f0d61124b1d1dd5ad3dbec4e197e4f4b53a890",
      "use_summary": "Imputed the filtered wheat VCFs using the reported Beagle 5.4 defaults after retaining biallelic SVs and excluding variants with more than 20 percent missing calls.",
      "implementation_scope_note": "The source does not provide the complete input genotype encoding,genetic map or reference panel. Do not infer a tetraploid inference model from the organism name or claim a diploid synthetic test reproduces the wheat workflow.",
      "version_reported_near_name": "5.4"
    }
  },
  {
    "paper_id": "P179",
    "primary_paragraph": 4,
    "review": {
      "pmcid": "PMC10806739",
      "doi": "10.1016/j.ajhg.2023.11.008",
      "tool_id": "beagle",
      "evidence_paragraph_sha256": "1166fabc9f0e3abcb17cf6de13b1289489ed396900d2033772bb4cc28aadbcd8",
      "use_summary": "Phased the two family VCF datasets with the 1000 Genomes reference panel before a separate hap-ibd IBD analysis.",
      "implementation_scope_note": "Reference-assisted phasing and hap-ibd segment detection are distinct. The text does not resolve whether reference-only markers were imputed or describe all Beagle options; family data alone do not imply a pedigree-aware phasing model.",
      "version_reported_near_name": "5.4"
    }
  },
  {
    "paper_id": "P186",
    "primary_paragraph": 9,
    "review": {
      "pmcid": "PMC11639076",
      "doi": "10.1016/j.ajhg.2024.10.018",
      "tool_id": "beagle",
      "evidence_paragraph_sha256": "096ca4c897d1a0b6c1582bc4f607e80d0fb5c3bf3dbaa17541df197053212c43",
      "use_summary": "Imputed Eagle 2.3.5-prephased genotypes with the SISu 4.2 reference panel using the explicitly reported Beagle 08Jun17.d8b build.",
      "implementation_scope_note": "Eagle prephasing, reference construction and post-imputation INFO filtering are separate. The reported INFO<0.7 exclusion is not automatically interchangeable with a Beagle DR2 threshold; preserve the actual field/protocol and exact archive build.",
      "version_reported_near_name": "4.1 (08Jun17.d8b)"
    }
  },
  {
    "paper_id": "P209",
    "primary_paragraph": 36,
    "review": {
      "pmcid": "PMC12668784",
      "doi": "10.1016/j.ajhg.2025.09.004",
      "tool_id": "beagle",
      "evidence_paragraph_sha256": "22d5eab4dc0500960d853e5531f14aa67558081b348c1c2a767780578924b5ba",
      "use_summary": "Separately phased the Indian British and Black British UKBB subsets with Beagle 5.4; other TOPMed and White British datasets reused earlier Beagle 5.2 phasing.",
      "implementation_scope_note": "Primary evidence is the authors' own subgroup phasing paragraph. Supporting paragraphs describe reused 5.2 data, not another new 5.2 run in this study. Relatedness filtering and later IBD/selection analyses are separate.",
      "version_reported_near_name": "5.4",
      "supporting_evidence": [
        {
          "paragraph_index": 31,
          "section": "Material and methods / Pre-processing genetic data",
          "paragraph_sha256": "1567247cced984b805b0b6a76d087b0a388e16899de56edab4ee4d3cf33c62b2"
        },
        {
          "paragraph_index": 37,
          "section": "Material and methods / Pre-processing genetic data / UKBB",
          "paragraph_sha256": "f14315261a3d1cbdd42b295e727febcc52c8b87af40723e572da9dc3b35fe42c"
        }
      ]
    }
  },
  {
    "paper_id": "P248",
    "primary_paragraph": 54,
    "review": {
      "pmcid": "PMC13447089",
      "doi": "10.1038/s41588-026-02682-z",
      "tool_id": "beagle",
      "evidence_paragraph_sha256": "1db475f20cbbc1f967e2abb699ab97cad10afdee1e827eca86edef3bb4eba476",
      "use_summary": "Imputed missing SNP genotypes before cucumber GEMMA GWAS with the explicitly reported 21Jan17.6cc Beagle build.",
      "implementation_scope_note": "The original downloaded 21Jan17.6cc JAR identifies itself as Beagle 4.1. The source assigns imputation to SNPs and a missingness exclusion to SVs; neither reference panel nor all imputation options are specified.",
      "version_reported_near_name": "21Jan17.6cc"
    }
  }
]''')


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
        tools = tool_matches(sources, [registry['beagle']], {'beagle': review['evidence_paragraph_sha256']})
        assert len(tools) == 1 and tools[0]['evidence_paragraph_index'] == row['primary_paragraph']
        check = dict(paper, tools=tools)
        assert publication_record(check, [review])['tools'][0]['version_reported_near_name'] == review['version_reported_near_name']
        key = (review['pmcid'], review['tool_id'])
        if key in by_key:
            assert by_key[key] == review, ('Existing review changed; inspect before replacing', key)
        else:
            merged.append(review); by_key[key] = review
        proof.append(dict(paper_id=row['paper_id'], primary_paragraph=row['primary_paragraph'], evidence_sha256=review['evidence_paragraph_sha256'], supporting_pointers=len(review.get('supporting_evidence', []))))
    assert len(proof) == 9
    result = dict(status='passed-source-role-validation', applied=args.apply, reviewed_beagle_roles=9, total_reviews=len(merged), added_reviews=len(merged)-len(document['reviews']), original_reviews_sha256=original_hash, proof=proof, curator_sha256=sha(__file__), scope='Hash-verified paraphrased Beagle roles and actual-work/reused-input distinction; semantic paper completeness, historical cohort reconstruction and software validation are separate.')
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
