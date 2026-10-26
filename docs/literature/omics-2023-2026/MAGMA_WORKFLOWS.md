# MAGMA gene and gene-set workflows

Unreleased source work for the active 250-paper goal. `magma-tools` adds native Java
strand-aware variant-to-gene mapping and input/result validation, plus a typed
interface to the original MAGMA 1.10 statistical application. Twenty-one paper
contexts are individually reviewed, including annotation windows, LD ancestry,
GWAS sample sizes, gene sets, continuous properties, conditional models and reuse
of gene scores in other software.

**READ WARNINGS AND EFFECTIVE ANALYSIS UNIVERSES.** A successful process does not
establish that every input gene was tested. Empty annotations, unmatched variants,
upstream QC exclusions, missing properties and omitted genes remain explicit.
Inspect `variant-status.tsv`, `gene-status.tsv`, set membership, native logs and
actual `N`/`NGENES` before interpretation. A MAGMA gene Z-score expresses unsigned
association strength; it is not the sign of an allele effect or causal evidence.

**DEPENDENCY UNAVAILABLE OR INSTALLATION FORBIDDEN: SKIP THE ANALYSIS.** External
methods return **3**, name the executable/version/hash/platform cause, and give
recovery in `WARNINGS.md`. Where permitted install the pinned original runtime
with the command below. Otherwise supply it on a permitted supported host and
rerun there. Contract/execution failures return **2**, retain partial artifacts in
`partial/`, and publish no successful `results.tsv`. Correct the exact cause and
use a new output directory. Native annotation needs no external runtime.

## Runtime and verification

The authors provide MAGMA 1.10 for Windows x86_64. Their current terms prohibit
redistribution and modification of its source and binaries. JLinAlg downloads the
unmodified official archive into an ignored private build directory; the program,
source and manual are not bundled in the executable JAR or repository. The authors
request mailing-list subscription; JLinAlg sends no email. See the
[official MAGMA page](https://cncr.nl/research/magma/) for terms and citation.

```powershell
python tools/literature/install_magma.py --allow-download
$homeMagma = 'build/omics-magma'
$jar = 'build/literature-review/validated-cli/jlinalg-0.3.7.jar'
java -jar $jar magma-tools --help
```

The exact archive SHA-256 is
`953a2a88eb3a7047b2963712a5fce5d74ab82ca63c9ac32bc3184b3055020507`;
the original executable SHA-256 is
`e05e3d963215a973064852ec33640118d65745884cc800a5119ca7cffe6cb859`.
The installer verifies all four original members and the actual version. Reuse
needs no download flag. Unmanaged/changed homes, corrupt cached archives and prior
attempts are preserved; choose a new `--environment` after correcting the cause.
Other operating-system profiles are not validated by this installer.

The old `ctg.cncr.nl` program URL redirected to HTML during this run; it was retained
as a failed archive candidate and was never installed/executed. The current download
comes from the university storage link on the authors' page. Do not infer an archive
from a `.zip` URL suffix. Installation/version checks are separate from scientific
validation.

Scientific fixtures use independently generated synthetic genotypes: 160 samples,
995 variants, 202 genes (one empty), planted pathway/property and raw phenotype
signals. Direct calls to the original program freeze 804 gene results across
mean/top/multi and raw PC regression, plus competitive/self-contained gene sets
and continuous properties. Native and CLI tests check exact current results,
independent singleton P-values, conditional/joint models, phenotype coding,
coordinate/build errors and runtime skips. See `magma-adapter-validation.json` and
`magma-packaged-validation.json` for the completed artifact identities.

The examples use the locally validated source JAR. This work is not in the public
v0.3.7 release. Use the artifact and manifest from the same completed validation.

## Native annotation and exact coordinates

Prepare headered TSV files, without automatic assembly/ID/`chr`-prefix conversion:

| File | Required columns |
| --- | --- |
| Genes | `gene_id`, `chromosome`, `start`, `end`, `strand` |
| SNPs | `variant_id`, `chromosome`, `position` |
| Summary GWAS | `variant_id`, `chromosome`, `position`, `p`, `n` |

Coordinates are positive, **one-based closed**. Human chromosome codes are 1–22,
X/Y or 23/24 (the latter explicitly canonicalize to X/Y). Nonhuman mode permits
numeric chromosomes through 2,000,000 and X/Y/W/Z. IDs must be unique tokens;
no whitespace/control characters or leading comment marker. Use `+`/`-` strands.
Unknown `.` is allowed only for symmetric windows or explicit `--ignore-strand true`.

```powershell
java -jar $jar magma-tools --method annotate-native --build GRCh38 `
  --genes genes.tsv --snps snps.tsv --upstream 35000 --downstream 10000 `
  --out build/magma-annotation
```

Windows use integer bases and follow gene strand. Set `--variant-format bim` to
read a six-column unheadered PLINK BIM directly. Use `--method annotate --home
$homeMagma` for original upstream annotation, followed by a complete independent
Java membership/window audit. Every input gene, including empty ones, is retained
in the status table. Empty genes are omitted from the statistical annotation.

Expanded lower bounds clip to MAGMA's **zero sentinel**; this does not turn the
interval into BED. Expanded bounds remain closed and are checked for integer
overflow. The mapper indexes SNP positions by chromosome and retains exact input
variant order. It does not read variant alleles or infer expression/functional links.

The sealed bundle contains canonical genes/SNPs, `genes.annot`, status/results and
`magma-bundle.json` hashes. Later steps reject changed members or a different build
label. Coordinate checks can detect contradictions between inputs; a user-supplied
build or ancestry label alone does not prove the underlying resource identity.
P024's reported `g1000_eur.bim`/`NCBI38.gene.loc` pairing needs actual coordinate
verification. Strand handling of legacy four-column gene-location files also needs
reconstruction before claiming historical asymmetric-window parity.

## Gene association from GWAS or raw phenotypes

```powershell
java -jar $jar magma-tools --method gene --build GRCh38 --home $homeMagma `
  --annotation build/magma-annotation --reference-panel reference/eur-panel `
  --ancestry EUR --input-mode summary --pvalues gwas.tsv --model mean --seed 42 `
  --out build/magma-genes
```

The original `.bed/.bim/.fam` family is required. Native checks cover SNP-major
magic/size, BIM IDs/coordinates and exact FID/IID uniqueness; original bytes are
hashed before/after. The ancestry-matched reference estimates LD. It is not the GWAS
sample-size source. A generic EUR panel does not reproduce a custom mixed-ancestry
panel (P072) or a different high-coverage reference edition (P125).

Supply finite **0 < P ≤ 1** and integer total/per-variant GWAS sample sizes. Repair
underflowed zero/missing probabilities explicitly; no guessed floor is imposed.
Duplicate SNPs and same-ID coordinate differences fail. Unmatched reference SNPs
fail unless `--unmatched-variants exclude` is explicit; every row/status is retained.
For uniform N, omit the `n` column and supply `--sample-size TOTAL_N`. A conflicting
per-variant column is rejected. Binary GWAS N is total sample size; effective N,
sex-specific N and panel sample counts are not substituted automatically.

`--model mean|top|multi` selects the current original SNP-wise model. `multi` is
an aggregate test, not an alias for `mean`; even identical singleton component
P-values need not produce an identical aggregate P-value. Preserve the model-specific
columns. The SNP-wise mean method changed in MAGMA 1.08; no parity with older
algorithms or complete historical pipelines is inferred from a shared model name.

Raw analysis uses `--input-mode raw --phenotype-kind quantitative|binary-zero-one|
binary-one-two`; it reads explicitly declared FAM phenotypes. `NA`/`-9` are the
adapter's declared missing values. Choose `--model linreg --prune .999` for the
PC/F-test model with 99.9% variance retention, or the exposed SNP-wise/multi models.
Summary P-values cannot request raw PC regression. P130's reported PC model must be
reconciled with its actual raw design before treating it as a P-only computation.

**RAW ZERO CODING MATTERS.** MAGMA's binary detection treats zero as missing.
The adapter writes an audited phenotype table: declared binary 0/1 maps to 1/2;
declared 1/2 is preserved. Explicitly quantitative values restricted to 0/1/2 are
shifted by +3, which preserves linear association and prevents binary/missing-zero
inference. Original FAM files remain unchanged; mapping, observed/missing counts
and warnings are retained. Binary raw models are MAGMA's linear/PC or SNP-wise
procedures, not an inferred logistic/Firth test.

`--snp-min-maf`, `--snp-min-mac`, `--snp-max-missing` and optional `--permutations`
expose QC/permutation choices. Review upstream rare-variant defaults and sample/SNP
exclusions. Validation fixtures are autosomal; other chromosome/ploidy/design cases
need their own scientific checks. Every gene has a status, including those omitted
by annotation/reference/QC. Original `.genes.out` and `.genes.raw` outputs remain;
Java audits identities, coordinates, numeric results and raw gene correlations.

## Gene sets, properties, conditional and joint models

Set files are **row-based set name followed by gene IDs**, without a header or GMT
description column. Gene-property files are TSV with `GENE` first and unique numeric
property columns; explicit missing values use `NA`. Set/property names must be distinct.

```powershell
java -jar $jar magma-tools --method sets --build GRCh38 --home $homeMagma `
  --gene-bundle build/magma-genes --sets pathways.annot --self-contained true `
  --out build/magma-pathways

java -jar $jar magma-tools --method gsa --build GRCh38 --home $homeMagma `
  --gene-bundle build/magma-genes --sets pathways.annot --properties expression.tsv `
  --condition SHARED_PATHWAY --analyse Brain --property-direction positive `
  --out build/magma-conditional
```

Use `properties` for properties alone and `gsa` for combined input. Competitive
background is the original gene-analysis universe after explicit property/missingness
choices. Unknown set members remain in `set-membership-status.tsv` and are ignored
upstream with a warning. A set needs at least two matched genes and outside-background
genes. Missing property genes/values fail by default; `--missing-genes drop` and
`--missing-values drop|mean|median --max-missing 0..0.2` make the alternative explicit.
These choices can change the background or remove variables; inspect native logs
and effective `NGENES`.

Default directions are positive for sets and two-sided for properties. `--correct
all|none` controls technical corrections; default outlier truncation remains upstream.
`--condition` and `--analyse` accept quoted comma-separated variable names. `--joint`
accepts a file with one whitespace-separated model per line; unknown variables or
overlap between joint and conditioning variables fail. Self-contained results are
kept in a separate table. Raw P-values are not automatically adjusted across sets,
traits, windows or repeated models; use the exact reviewed correction family.

FUMA web annotation/clumping and prepared expression, MAGMA.Celltyping, EWCE,
GSA-MiXeR, PoPS, scDRS, MHC selection and orthologue conversion remain separate
workflows. Averaging MAGMA/EWCE adjusted P-values (P035) is a reported procedure,
not an asserted FDR-control guarantee. Read each source-linked scope note before
using a tool-level coverage badge as evidence of complete paper reproduction.

Native APIs are `GeneWindowMapping` and `MagmaTables`. Synthetic fixtures and
direct-call provenance are in `src/test/resources/magma`; no upstream program,
manual, source or private data are redistributed there.

To reproduce the independent reference files without the Java adapter, run
`python tools/literature/reproduce_magma_references.py --home build/omics-magma
--out build/magma-direct-reference-new`. The output directory must not exist.
The script checks all eight frozen files and preserves the source fixture bytes.
