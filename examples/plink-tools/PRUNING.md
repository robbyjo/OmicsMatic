# Original PLINK LD pruning

**Development feature. Released 0.3.7 and the earlier QC candidate do not
contain `plink-tools --task prune`. Use the verified pruning candidate below.**

**Review console warnings, any `WARNINGS.md`, and the reference/decision tables
before using a retained variant list.** An unavailable, changed, unsupported or
forbidden original runtime skips this analysis with exit **3**. The admitted
profile is Windows x86_64. Where setup is permitted, run:

```powershell
python tools/literature/install_plink.py --home build/omics-plink-official --allow-install --allow-download
```

Installation and download permissions are separate. An existing verified home
can be reused without either flag or network access. A changed or unmanaged
home is preserved; select a new private home. If setup or execution is forbidden,
skip the analysis. Analysis commands never install software.

## Runnable synthetic examples

```powershell
$pruneJar = 'build/literature-review/plink-prune-candidate-1/jlinalg-0.3.7.jar'
java -jar $pruneJar plink-tools --options-file examples/plink-tools/pruning/plink1-count-legacy.json
java -jar $pruneJar plink-tools --options-file examples/plink-tools/pruning/plink2-count-legacy.json
```

Run from the repository root. Use a new output directory for each attempt;
direct options override a recipe's values. Recipes are UTF-8 JSON objects with
string values. For Windows Unicode paths, place the paths inside a UTF-8 recipe
whose filename is ASCII. Paths resolve against the calling directory.

The fixture contains 128 synthetic samples and 91 variants. Its sample table
includes 125 people, of whom 123 are recorded founders. Deliberately copied,
reversed and partly missing genotype rows at near/distant positions create
known pruning contrasts. It contains no patient data or biological linkage
realism. Coordinates are arbitrary and the genome build is unspecified.

**The example's windows, shifts, r-squared threshold and call-count minimum are
small synthetic test choices, not research-cohort recommendations or inferred
paper parameters.** Additional recipes exercise kilobase windows and PLINK2's
backward pair order:

| Recipe | Original mode |
|---|---|
| `plink1-count-legacy.json` | 20-variant window, shift 5, PLINK1 native order |
| `plink1-kb-legacy.json` | 50-kb window, shift 1, PLINK1 native order |
| `plink2-count-legacy.json` | 20-variant window, shift 5, explicit order 1 |
| `plink2-kb-legacy.json` | 50-kb window, shift 1, explicit order 1 |
| `plink2-count-backward.json` | 20-variant window, shift 5, explicit order 2 |
| `plink2-kb-backward.json` | 50-kb window, shift 1, explicit order 2 |

## Declare the reference and pruning choices

The exact sample TSV header is `fid`, `iid`, `include`, `group`, separated by
tabs. Name every source FAM pair exactly once. `include` is `yes` or `no` and
`group` is `control`, `case` or `unassigned`, matching the QC table format.

Only initially included samples with **both recorded FAM parent IDs equal to
`0`** enter the pruning reference. Group labels are preserved and unused;
cases are not implicitly removed. Recorded founder status does not establish
biological unrelatedness. The reference ledger is not a final-cohort sample
exclusion mask.

| Required option | Supported contract |
|---|---|
| `--engine plink1\|plink2` | Select a pinned original engine. |
| `--chromosome-scope autosomes` | Initially exclude nonautosomes. Canonical human chromosome codes and contiguous blocks are required. |
| `--unresolved-alleles error\|exclude` | Decide how missing allele labels (`0`/`.`) are handled before pruning. |
| `--reference-population founders` | Explicitly use the recorded-founder subset of included samples. |
| `--window-unit variants\|kb`, `--window-size INTEGER` | Choose variant-count or kilobase units. Count windows require at least 2 variants; kb windows require a positive integer. |
| `--step-size INTEGER` | Positive shift in variant count. PLINK2 requires 1 for kb windows. |
| `--r2 FRACTION` | This subset accepts finite thresholds strictly between 0 and 1. |
| `--pair-order legacy\|backward` | PLINK1 requires `legacy`. PLINK2 explicitly maps these choices to original orders 1 and 2. |
| `--missing-genotypes error\|upstream` | Prohibit reference missing calls or retain the original program's hard-call handling, with warnings. No native imputation is added. |
| `--minimum-reference-calls INTEGER` | Declare a marginal per-variant minimum of at least 3 called reference samples. |
| `--genome-build GRCh37\|GRCh38\|unspecified` | Record assembly provenance; this workflow does not validate it. |

The admitted PLINK1 profile requires at least 3 selected reference founders;
PLINK2 requires at least 50. A `--bad-ld` override is not inferred or exposed.
Every submitted variant must have enough called reference samples and variable
hard-call dosage. A failing marker produces diagnostics and stops the analysis;
no additional QC filter is selected automatically. Resolve such markers with a
reviewed QC workflow and rerun to a new directory.

Within each chromosome, admitted bp positions must be positive and sorted.
Input is never reordered. A variant-count window still uses the supplied
within-chromosome order. Review a named resource bound before increasing it;
the shared sample, variant, cell-inspection, metadata, line, input-byte and
report-byte limits are listed in the combined `plink-tools --help` output and
the [QC guide](QC.md).

This command runs original **`--indep-pairwise` on unphased hard calls**. It
does not expose VIF, phased-haplotype or dosage-aware pruning. Original pruning
is greedy and windowed; it does not guarantee that every pair in the remaining
genome has r-squared below the threshold. Pair checking and missing-call behavior
can differ across versions. Review the original
[PLINK1 documentation](https://www.cog-genomics.org/plink/1.9/ld) and
[PLINK2 documentation](https://www.cog-genomics.org/plink/2.0/ld) when selecting
the explicit parameters. An unspecified paper parameter remains unspecified.

## Outputs, warnings and recovery

| Output | Use |
|---|---|
| `prune.in`, `prune.out` | Retained/removed original variant IDs in input BIM order. They form a complete disjoint partition of the submitted variant family. Initial scope/allele exclusions are separate. |
| `sample-reference.tsv` | Every source FID/IID pair, include flag, recorded-founder status, unused group label and reference membership. |
| `variant-reference.tsv` | Exact marginal called/missing counts, reference size, dosage range and admission status. These checks do not establish reportability of every pair. |
| `variant-decisions.tsv` | Every source variant's metadata, initial exclusion or original pruning decision. |
| `session.yaml`, `manifest.yaml`, `upstream/` | Effective options, source hashes, original runtime identity, commands/exits, and unchanged raw reports/masks. |

**No filtered BED/BIM/FAM is produced.** The lists do not change the current
genotype files. Use the retained list in an explicitly declared later variant
selection; do not assume a downstream analysis is pruned merely because these
files exist. This command computes no PCs, kinship, clumps or association tests.

| Exit | Required action |
|---|---|
| **0** | Review warnings, reference membership, call diagnostics, initial exclusions and original reports before downstream use. |
| **2** | Read the named input/scientific/process cause, correct it and rerun to a new output directory. Failed canonical lists/decisions are quarantined under `partial/`; do not treat them as results. |
| **3** | Admit/install the original runtime where permitted, or skip this analysis. Never substitute an unverified binary or reinterpret an absent result. |

The separate packaged validation record identifies the tested candidate and
scope. Validation of this subset does not establish whole-PLINK coverage,
historical-paper replay, clinical validity or availability in released 0.3.7.
Broader tool integration and the paper-wide audit remain active.
