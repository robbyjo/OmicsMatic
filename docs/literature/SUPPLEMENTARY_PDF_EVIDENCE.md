# Supplementary PDF evidence in the 250-paper catalog

The catalog now includes ConstructASCATFiles and PREASCAT from physical page 7 of the supplement to [P146](https://doi.org/10.1038/s41588-023-01545-1). ConstructASCATFiles extracts coverage and BAF at selected exome SNPs; PREASCAT performs coverage QC/PCA and produces normalized logR and BAF before ASCAT fitting. The paper does not report either tool's version. The inspected repository commits identify the source audit, not the authors' software versions.

Current totals are **250 papers, 453 named tool labels, 492 providers and 2,733 observations**. There are 1,821 reviewed and 912 pending uses. The two additions receive **zero implementation credit**: 171 scoped families remain available (34 native, 137 upstream), and 252 labels remain missing. The full goal remains active; the registry is not a discovery ceiling.

## Warnings and what to do

**ConstructASCATFiles and PREASCAT runtime analyses are SKIP3.** The inspected analysis code has no identified source-wide license or permission. Obtain applicable permission from its rights holders before preparing or validating an original private runtime, or keep these analyses skipped. Bundled SAMtools/IDE notices do not license the authors' own code. Neither tool was installed, executed or redistributed. Their source reviews and paraphrased paper uses can still be cataloged. See the [retained source audit](ascat-source-audit-v2/SUPPLEMENTARY-TOOLS.json).

**The original PDF triggers a parser warning.** pypdf reports a cross-reference table correction. The file remains unchanged; the locator records the warning identity/count along with the PDF and extracted-page hashes. This is a disclosed parser behavior, not a repaired replacement PDF. Page 7 was visually inspected during the preceding source audit. New locators need source review; matching text alone does not establish the meaning of a method or completeness of extraction.

**Source validation needs the exact retained PDFs, XMLs and parser.** Missing or forbidden prerequisites produce exit 3 and skip the dependent source validation. Supply the exact permitted sources and pypdf 6.21.4 where installation is allowed, or retain the skip. Invalid or changed bytes, page hashes, offsets, metadata or diagnostic populations produce exit 2 and no complete validation. Preserve the attempt, investigate the named mismatch and use a new reviewed stage; never refresh historical hashes to force acceptance.

Cell Ranger remains excluded. MaxQuant runtime remains skipped, with MaxQuant/Andromeda/MaxDIA pending. The literature HTMLs remain withheld from Pages. Existing ASCAT execution warnings and qualifications remain in the [ASCAT guide](../ascat-tools.md).

## Locator contract

[EVIDENCE.json](supplement-pdf-admission/EVIDENCE.json) is canonical, excerpt-free PDF evidence. A tool observation uses `evidence_source_kind: supplementary-pdf` and an `evidence_pdf` object. It has **no JATS paragraph index or paragraph hash**.

Each PDF locator records:

- The official HTTPS source URL, article version, complete file byte count and SHA-256.
- The physical, **one-based** page number and total page count, including unnumbered front matter.
- The pypdf version, strict parser setting, plain text extraction mode and absence of text normalization or OCR fallback.
- SHA-256 of the exact extracted page string encoded as UTF-8, and all literal tool-name occurrences as zero-based Python string offsets with exclusive ends.
- Counts and SHA-256 identities of parser warning/error messages. Original diagnostics remain visible during verification.

The official PMC version metadata binds the PDF to the paper DOI, PMCID and article version. Its media URL and MD5 are checked in addition to the retained SHA-256. Long PDF excerpts and raw source files remain private. PDF text extraction is parser-dependent, so a different parser version is a missing exact prerequisite rather than evidence of successful replay.

Existing JATS and supplementary DOCX locators retain their original semantics. The current report selects the PDF renderer for the new kind; it does not display an undefined paragraph index. All prior 2,731 observations, 1,819 reviews, 451 inventory rows, paper identities and scientific qualification files are preserved. The predecessor metadata is archived under [supplement-pdf-admission](supplement-pdf-admission/BASELINE.json); earlier qualification statements remain historical statements about their exact source contexts.

## Verify

Run with Python 3.10+ and the recorded pypdf version. Use an environment where installation is permitted; the verifier does not download or install prerequisites.

```console
python -B tools/literature/supplement_pdf_catalog.py --metadata-only
```

This checks the canonical metadata derivation, fixed cohort and all catalog checksums. It explicitly **does not verify original source bytes**. For source validation, provide the retained XML-cache directory and supplementary directory:

```console
python -B tools/literature/supplement_pdf_catalog.py --xml-cache /path/to/original/xml --supplements /path/to/official-cloud-supplements-1
```

The XML directory must contain the 250 original `PMC*.xml.gz` files. The supplementary directory must retain the original P129 and P146 PDFs plus `P146-v1-METADATA.json`; filenames and hashes are recorded in the [source census](ascat-source-audit-v2/CENSUS.json). A full pass verifies all 250 original XMLs, both retained supplement byte identities, official P146 metadata and both new PDF locators. It does not execute either preprocessing tool or reproduce a paper analysis.

The one-time `--apply` transition is restricted to the recorded ASCAT predecessor. Later discoveries need a new reviewed transition, preserving this one and its historical evidence.
