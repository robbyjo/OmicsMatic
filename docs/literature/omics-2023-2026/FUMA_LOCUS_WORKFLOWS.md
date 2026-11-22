# Native supplied-reference FUMA locus workflow

**Checkpoint scope:** this guide retains its earlier candidate and validation. The current combined candidate adds optional raw gene-score annotations and typed orchestration; read [FUMA_TYPED_WORKFLOW.md](FUMA_TYPED_WORKFLOW.md), [FUMA_GENE_SCORE_ANNOTATIONS.md](FUMA_GENE_SCORE_ANNOTATIONS.md) and `fuma-workflow-validation.json`. Broader FUMA remains missing.

**Checkpoint scope:** this guide retains its earlier validated candidate and formats. The current combined command also supports supplied chromatin interactions; read [FUMA_CHROMATIN_WORKFLOWS.md](FUMA_CHROMATIN_WORKFLOWS.md) and `fuma-chromatin-validation.json`. Broader FUMA remains missing.

**Available in a development candidate; broader FUMA remains unfinished.**
`fuma-tools --method loci` selects LD-linked candidates, independent variants and
lead SNPs, then constructs complete genomic-risk-locus components. It uses explicit
local GWAS, frequency and LD tables and requires only Java. It does not estimate
LD, choose a reference population/build, perform liftover or run annotation,
ANNOVAR, MAGMA, expression or a hosted FUMA service.

Use the exact candidate/checksum in [validation metadata](fuma-locus-validation.json).
The [runnable synthetic example](../../../examples/fuma/loci/README.md) includes an
actual retained original partition that fails locus invariants, alongside the
expected corrected native result. From the repository root:

```text
java -jar JAR fuma-tools --locus-options-file examples/fuma/loci/options.json --out build/fuma-loci-example-NEW
```

Every output directory must be new. The JSON contains string-valued options;
direct options override it and both the carrier hash and final choices are
recorded. An ASCII carrier path supports Unicode input/output paths on Windows
Java 21. Relative resource paths resolve against the working directory.

## Inputs and scientific choices

Supply plain UTF-8 TSV with these exact headers. Extra GWAS columns are preserved
in `input-gwas.tsv`; the locus method uses the required fields below.

| Input | Required headers | Meaning |
|---|---|---|
| `--gwas` | `chr,pos,non_effect_allele,effect_allele,rsID,p` | Unique standardized GWAS identities and supplied decimal P values. |
| `--reference` | `chr,pos,rsID,A1,A2,MAF` | Explicit reference variants and minor-allele frequencies in [0,0.5]. |
| `--ld` | `chr,pos1,pos2,r2` | Undirected within-chromosome pairwise r-squared in [0,1]. |
| Optional `--regions` | `chr,start,end` | Inclusive regions controlling GWAS eligibility. |
| Optional `--predefined` | `uid` | First-stage seeds identified by `chr:pos:alphabetically-sorted-alleles`. |

Chromosomes are positive integers and positions are positive, one-based integers.
No chromosome-label conversion, allele complementation, assembly conversion or
ancestry inference is performed. Matching uses the exact coordinate and unordered
allele pair; supplied GWAS effect-allele orientation is retained. Reference-only
candidates use A1 as effect allele, matching the inspected backend.

The original LD lookup addresses variants by coordinate. A reference with multiple
variants at one coordinate fails explicitly; resolve the allele/LD ambiguity in a
documented derived resource. Duplicate/conflicting GWAS or rsID identities, missing
LD coordinates and conflicting pair values also fail. Self LD is implicitly one;
omit self-pair rows. Equal reverse pair rows are accepted, conflicting values fail.

Declare `--reference-id ASSEMBLY_POPULATION_RELEASE` and `--ld-floor VALUE`.
All unlisted within-chromosome pairs must be strictly below that floor, and the
floor must not exceed either requested LD threshold. A zero floor requires every
within-chromosome pair, including zero LD. The software checks supplied structure;
it cannot prove the omitted LD values or reference ancestry/build from a sparse
table. An empty LD table with a positive floor is a substantive coverage assertion.

Choose every analysis setting explicitly:

```text
--lead-p 5e-8 --candidate-p 0.05 --maf 0
--independent-r2 0.6 --lead-r2 0.1
--merge-distance-bp 250000 --merge-boundary strict
--include-reference true --mhc none --add-independent true
--ties error --unmatched-reference error --empty error
```

P comparisons are strict; MAF and r-squared comparisons are inclusive. A first-stage
index collects qualifying GWAS/reference candidates at the independent-r2 threshold;
already collected variants are skipped as automatic indices. Independent variants
are then clumped at lead-r2. Direct lead memberships can overlap: a shared
independent variant need not be artificially assigned to just one lead group.

Predefined seeds bypass their own P cutoff and automatic first-stage independence
selection. They still require reference/MAF/MHC/region eligibility and can be
reclumped at the second stage. They need not be genome-wide significant or final
lead SNPs. `--add-independent false` uses only the predefined seed list. Run warnings
identify admitted/filtered seeds and their significance exception.

`--ties error` refuses an actual choice among equal-P, unclaimed candidates in a
stage. It does not reject an unused automatic pool or variants already collected
by predefined/earlier indices. `position` explicitly resolves ties by ascending
coordinate. Old NumPy quicksort can select different index IDs; that difference
is measured and retained, rather than presented as historical equivalence.

`--mhc none` keeps all variants. To exclude an explicit interval in the declared
assembly, use `--mhc 6:START:END`; endpoints are excluded. This filters candidate
variants, not every base between outer locus bounds. Likewise, predefined regions
filter GWAS eligibility, while reference-only LD partners can extend outside them.
Original GWAS rows outside those regions can re-enter as reference-only candidates;
their original P remains available separately, and this reclassification is warned.

## Complete loci and retained original differences

Each lead group's block spans the union of its independent variants' candidate
blocks. Native loci are complete interval components, merging all overlapping or
nearby blocks transitively while preserving every independent, lead and candidate
membership. This corrects observed backward-merge defects in the pinned source.

`strict` uses gap < merge-distance, matching the inspected source code; physical
overlap still merges at distance zero. `inclusive` explicitly chooses gap <=
merge-distance. Parts of the tutorial label the distance inclusive while its code
and other descriptions use strict distance. Distances here are base pairs:
250000 means 250 kb.

The source also reports newly added candidates in its independent-SNP count
columns. When blocks share candidates, those incremental counts differ from full
block sizes; a forced correlated seed can even report zero new candidates.
`independent.tsv` preserves both interpretations with distinct column names.

Native P parsing retains the full Java double and original decimal string.
Legacy pandas can round an input across a strict threshold, and Python 2's displayed
P strings use limited significant digits. Rows within four double ULPs of a cutoff,
zero P, actual position ties and zero-MAF candidates produce review warnings.
Read the [measured source/numerical findings](FUMA_NUMERICAL_FINDINGS.md).

The method and its implementation differences are tied to the inspected
[official getLD source](https://github.com/vufuma/FUMA-webapp/blob/bdfbf06b5dd24037ec3c7497d773fad3c2656150/scripts/getld/getLD.py)
and [FUMA tutorial](https://fuma.ctglab.nl/tutorial). These synthetic findings do
not establish that any catalogued paper's reported loci were affected.

## Outputs, warnings and recovery

Read `WARNINGS.md`, `analysis-status.json` and `session.json` for every run.

| Output | Use |
|---|---|
| `input-gwas.tsv` | Retains supplied GWAS columns and decimal strings. |
| `gwas-disposition.tsv` | Original P, parsed P and row eligibility/selection. |
| `candidates.tsv` | Exact identities/orientation, reference MAF, original/filtered GWAS roles, maximum LD association and locus. |
| `independent.tsv` | Complete block counts, distinct legacy-new counts, predefined status and processing order. |
| `ld-memberships.tsv` | Every retained independent-to-candidate link, including implicit self links. |
| `leads.tsv` | Second-stage lead indices and their direct independent memberships. |
| `loci.tsv` | Complete component bounds, top lead, counts and all independent/lead memberships. |

The manifest records input/output hashes and the session records the exact
reference identity, protocol, parser and component rule. Review the LD coverage
declaration before using locus counts or assigning genes to the resulting bounds.

Exit 0 means completed with review warnings; explicit `--empty retain` can produce
a completed empty result. Exit 2 means failed: no canonical partial locus table is
admitted. Correct the named cause and rerun to a new directory. Retained partial
audits are under `partial/`. Existing destinations are never overwritten.

For unmatched variants, inspect assembly, coordinates and alleles in the
disposition ledger; correct the reference or explicitly choose
`--unmatched-reference exclude`. For no remaining indices, inspect all eligibility
filters and seed choices before explicitly retaining an empty analysis. For LD
floor/coverage errors, supply adequate pair data; do not silently assume missing
LD is zero. For ambiguous coordinates, preserve the original resource and resolve
its allele identity explicitly.

Default caps: 256 MiB per file, one million GWAS rows, two million reference rows,
two million pair rows and 100 million visited operations. The help lists explicit
overrides. Sparse panels still require memory for reference/adjacency maps; choose
an appropriate JVM heap and resource budget. No parameter, P value, reference
population or LD definition is retuned after failure.

The native analysis installs nothing. Only private reference validation uses
end-of-life Python 2/pandas/pytabix and original BGZF/tabix utilities. The broader
annotation, mapping, MAGMA orchestration and expression workflows remain unfinished.
Skip a dependent analysis whose required software/data cannot be supplied or
installed under the environment's permissions, and state its cause and required
recovery prominently. Cell Ranger remains excluded at the user's request.

## Portable reference replay

The checked-in [reference fixtures](../../../tools/literature/fixtures/fuma-loci/README.md)
and standard-library Python checker validate the packaged command without an
original FUMA runtime or network access:

```text
python tools/literature/verify_fuma_loci.py --jar JAR --java JAVA --out NEW_DIRECTORY
```

This replays 43 frozen synthetic original-function/independent-graph expectations;
it does not re-execute the original Python 2 functions. The actual original runs
are separately retained with source/resource hashes. The public checker passed
43 protocols on each of Windows and Linux Java 21, with 301 byte-identical table
groups. Missing Java is tested to skip verification with exit 3, a named cause and
required recovery; it never installs software. Failed comparisons return 2 and
retain diagnostics. Do not regenerate expected data from native output snapshots.
