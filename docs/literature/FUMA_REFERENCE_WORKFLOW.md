# Native FUMA hardcall reference preparation and replay

**Development candidate; broader FUMA integration remains incomplete.**
This scoped workflow builds autosomal diploid allele frequencies and unphased
allele-count Pearson LD from explicitly selected PLINK BED/BIM/FAM inputs.
It captures selected hardcalls for every requested variant, replays the entire
family without producer paths or external software, and hands the verified
reference into native loci. It does not reconstruct a hosted or historical paper
reference, infer ancestry/assembly, download a panel, or generate expression/DEG
resources. FUMA's catalog status remains missing.

The library API is `FumaReferencePreparation`; the underlying numerical core and
original controls are described in [FUMA_LD_CORE.md](FUMA_LD_CORE.md).
The candidate, source pins, test results and retained failed attempts are recorded
in [fuma-reference-workflow/VALIDATION.json](fuma-reference-workflow/VALIDATION.json).
The canonical release executable is preserved; use the identified candidate.

## Run the complete synthetic example

From the repository root in PowerShell, with a Java21 JDK on PATH:

```powershell
$proof = Get-Content docs/literature/fuma-reference-workflow/VALIDATION.json -Raw | ConvertFrom-Json
$jar = $proof.artifact.path
javac --release 17 -Xlint:all -Werror -d build/fuma-reference-example examples/fuma-reference/CreateInputs.java
java -cp build/fuma-reference-example CreateInputs build/fuma-reference-example-run-1
Copy-Item examples/fuma-reference/workflow.json build/fuma-reference-example-run-1/workflow.json
java -Xmx128m -jar $jar fuma-tools --method workflow --workflow-options-file build/fuma-reference-example-run-1/workflow.json --out build/fuma-reference-example-run-1/workflow-1
java -Xmx128m -jar $jar fuma-tools --method replay-reference --bundle build/fuma-reference-example-run-1/workflow-1/reference-preparation --out build/fuma-reference-example-run-1/replay-1
java -Xmx128m -jar $jar fuma-tools --locus-options-file examples/fuma-reference/locus-options.json --gwas build/fuma-reference-example-run-1/input/gwas.tsv --reference-bundle build/fuma-reference-example-run-1/replay-1 --expected-reference-assembly synthetic-GRCh37 --out build/fuma-reference-example-run-1/loci-1
```

Check `$LASTEXITCODE` after each command. Every destination must be new; choose
another run suffix to repeat. Expected output is documented in the
[example README](../../examples/fuma-reference/README.md). Preserve the complete
bundle when moving to another machine; copying only `ld.tsv` loses its coverage
contract. Captured selected genotypes are individual-level data: move them only
where the study's data-access permissions allow.

## Prepare a real, explicitly declared panel

```powershell
java -Xmx1g -jar $jar fuma-tools --method prepare-reference --bfile data/panel --variants data/variant-selection.tsv --samples data/sample-selection.tsv --sample-policy founders --assembly GRCh37 --population DECLARED_POPULATION --release DECLARED_RELEASE --minimum-maf 0 --ld-floor 0.05 --window-bp 1000000 --window-index-gap 99998 --out output/reference-1
```

Variant selection is an exact UTF-8 TSV with header `id`, one BIM ID per row.
Sample selection has header `fid<TAB>iid`, one exact case-sensitive FID/IID pair
per row. Duplicate/unknown IDs fail. Selection retains FAM order, irrespective
of selection-file order. `founders` intersects the selection with recorded FAM
flags (both parent IDs zero); `selected` uses every explicitly selected sample.
No ancestry, kinship, relatedness or ploidy inference is performed. The caller
must establish that the selected samples represent the intended panel.

Admission requires at least two selected samples, resolved distinct BIM alleles,
canonical numeric chromosomes1..22, positive int-range positions, unique variant
IDs and one variant per coordinate. X/Y/MT, noncanonical labels and ambiguous
multiallelic positions fail; explicitly prepare appropriate autosomal inputs.
No allele complement, imputation, dosage uncertainty or haplotype model is added.

Every requested variant receives an audit decision: no observed calls, fewer
than two observed calls, zero MAF, constant observed calls, below minimum MAF,
or retained. MAF is inclusive at the requested minimum, with MAF0 always excluded.
All-heterozygous calls can have MAF0.5 and zero variance; they are excluded under
the explicit constant-call rule. Every requested row remains in genotype capture.

**Windows use retained genomic ranks after these exclusions**, within chromosome.
Both bp distance and index gap are inclusive. A PLINK count window of `k`
corresponds to gap`k-1`; the example's99998 is a declared protocol choice, not an
inferred default for a paper. Store every defined eligible pair with r²>=floor.
Assess and count all remaining eligible pairs as below-floor. Outside-window
pairs are uncomputed protocol exclusions and establish no biological independence.
An undefined eligible pair fails the entire request, even if its marginal rows
were nonconstant. Inspect paired-call missingness; do not recode NaN as zero,
silently omit the pair, or widen exclusions solely to obtain a successful run.

The [PLINK LD documentation](https://www.cog-genomics.org/plink/1.9/ld) and
[FUMA reference tutorial](https://fuma.ctglab.nl/tutorial) describe original tool
and reference protocols. This workflow's explicit filtering/window order must
be checked against the exact paper/panel being reproduced.

## Replay, handoff, budgets and failures

`reference-bundle.json` binds eight fixed payload files: variants, selected samples,
the full source-sample selection audit, packed genotypes, variant decisions,
reference MAF, stored LD and the complete assessed-pair audit. SHA-256 binds
bytes; it is not an authentication signature or proof of biological provenance.
Replay validates selections, sizes, hashes and the captured format, recomputes
all decisions/eligible pairs, compares every payload byte and checks complete
pair accounting. Marginal exclusions are replayed too. Native results are
deterministic across the tested JVMs; this is not an upstream bit-for-bit claim.

`loci --reference-bundle DIRECTORY` performs the same complete replay before
admission. It carries identity, floor and both windows; manual raw protocol
overrides are rejected. Optional `--expected-reference-assembly` checks the
declaration without liftover/relabeling. The typed `reference_prepare` workflow
stage supplies its bundle to `loci` automatically, requires assembly equal to
workflow build, and skips dependent loci/mapping when preparation fails.
An existing bundle can also be passed directly to the typed loci section.

Raw locus input remains supported. Supply both `--window-bp` and
`--window-index-gap` to declare bounded raw coverage. Omitting both preserves
the original unbounded sparse assertion. Raw files alone cannot prove omitted
pair estimates; the bundle workflow provides complete replayable evidence.

Defaults are100000 samples,100000 requested variants,2000000 eligible pairs,
100000000 pair-mask word operations,256MiB estimated owned storage,256MiB total
payload and16MiB metadata. Work includes marginal self checks and all eligible
pairs. A conservative storage estimate admits the complete retained-window cache
before the pair pass and must fit half the heap. Metadata has additional smaller
heap/budget sublimits. These are bounded local analyses, not an admission claim
for an entire biobank or all genome-wide variant pairs. Explicitly reduce inputs
or raise permitted `--max-*` limits/heap; no successful prefix is returned.
Library callers must also bound their own sink storage and treat emitted rows
as provisional until the method returns.

**Exit2 means failed: no reference or partial LD prefix is admitted.**
Read `WARNINGS.md` and `session.json`; partial audits are retained in `partial/`
(empty for failure before output). Correct the named data/selection/missingness,
integrity, cancellation or capacity cause and rerun the complete request to a new
directory. Existing destinations are preserved. A failed workflow preparation
causes dependent stages to SKIP3 with a named upstream cause; the overall workflow
reports FAIL2. These native commands need no external installation. Other upstream
analyses still follow their own SKIP3 installation instructions when dependencies
are missing/forbidden. Cell Ranger remains excluded.
