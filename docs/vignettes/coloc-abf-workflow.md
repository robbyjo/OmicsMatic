# COLOC ABF file workflow

Use `coloc-abf` for **single-causal-variant colocalization from supplied regional
summary statistics**. It runs natively in Java and needs no R installation.
The calculation preserves the selected coloc 6.0.3 source behavior. The
existing `coloc` command accepts SuSiE signal log-Bayes factors and remains a
separate method.

Start with the [complete synthetic example](../../examples/coloc-abf-workflow/README.md).
Use a JAR built from current source; the published 0.4.0 release predates this
command. The [qualification checkpoint](../literature/COLOC_ABF_WORKFLOW_CHECKPOINT_2026-11-19.md)
records the exact tested candidate and evidence.

## Prepare a request and two TSV files

```json
{
  "schema_version": 1,
  "mode": "RUN",
  "out": "results-new",
  "trait1": {
    "file": "expression.tsv", "type": "quant", "sdY": 1,
    "columns": {"snp": "variant_id", "beta": "effect", "varbeta": "variance"}
  },
  "trait2": {
    "file": "gwas.tsv", "type": "cc",
    "columns": {"snp": "variant_id", "beta": "log_odds", "varbeta": "variance"}
  },
  "priors": {"p1": 0.0001, "p2": 0.0001, "p12": 0.00001}
}
```

```powershell
java -Xmx4g -jar build/libs/omicsmatic-0.4.0.jar coloc-abf --request request.json
```

Paths resolve against the request's directory. The output directory must be
new. Supply **variance (SE squared)** in `varbeta`. This workflow does not
guess column aliases, square an SE column automatically, align alleles or
choose/filter a locus. Prepare and document the appropriate regional inputs,
association model, variant coverage, allele alignment and scale beforehand.

Files are **unquoted UTF-8 TSV**, with an exact unique header and one row per
variant. Quotes are literal characters. IDs/headers are not trimmed or
normalized; blank rows are errors. Duplicate IDs, controls, malformed Unicode,
ragged rows and unmapped required columns are refused. IDs/headers have a
4,094-UTF-16-unit limit and tables a 512-column limit. Extra unmapped annotation
columns are captured and counted in a warning, without entering the calculation.

Numeric cells must be finite literal decimal numbers, with no surrounding
whitespace or missing-value token. Nonzero values that overflow or underflow
the Java double range are refused. Zero variance, invalid frequencies or
probabilities, and nonfinite calculations produce no complete result.

## Choose the input mode explicitly

| Trait/input | Request fields and mapped columns |
| --- | --- |
| Quantitative regression | `type: "quant"`; mapped `beta`/`varbeta`; supplied `sdY`, or mapped `MAF` plus scalar `N` to estimate sdY |
| Case-control regression | `type: "cc"`; mapped `beta`/`varbeta` on the log-odds scale |
| Quantitative P values | `type: "quant"`; mapped `pvalues`/`MAF` and scalar `N`; source approximation assumes unit trait variance |
| Case-control P values | `type: "cc"`; mapped `pvalues`/`MAF`, scalar `N`, and case fraction `s` in (0,1) |

For example, a P-value trait can be:

```json
{
  "file": "gwas-p.tsv", "type": "cc", "N": 12000, "s": 0.25,
  "columns": {"snp": "rsid", "pvalues": "P", "MAF": "frequency"}
}
```

P values must lie in (0,1], frequencies in (0,1), and N/sdY must be positive.
Frequencies above 0.5 are retained, not folded. Per-variant N is unsupported.
When beta/variance and P values are both mapped, the beta/variance pair wins;
optional supplied fields are still validated and precedence is warned. N/s
supplied to case-control regression, or sdY supplied to P-value mode, are
recorded with an explicit unused-setting warning where appropriate.

Optional mapped `position` values must be nonnegative `long` integers. If both
traits have positions, matching uses **ID and position**; mismatches are
excluded with a warning. If only one has positions, matching uses ID alone.
Output IDs follow deterministic Unicode code-point order, matching the
reference's UTF-8 C collation. No significance-based row filtering is applied.

## Priors and source-specific warnings

Default requested p1=p2=1e-4 and p12=1e-5 are recorded explicitly in the captured
effective request. Each prior is in (0,1); p12 may exceed p1 or p2 as allowed
by the selected source. Source n*p>=1 adjustments use original trait sizes
for p1/p2 and the shared size for p12. Quantitative sdY estimation uses all
original trait variants before intersection.

Map `prior_weight` in either trait for optional finite, nonnegative weights.
Weights follow that input's row order and are normalized after intersection.
Both-trait weighting generates an independence warning. All-zero/disjoint
support can produce nonfinite source arithmetic and is refused; it is never
replaced with uniform weights.

**The source's `SNP.PP.H4` is unweighted even when regional H0..H4 use prior
weights.** It is conditional on H4 and differs from regional H4. This behavior
is retained and warned. H3 may become zero at floating-point resolution under
the source log-difference formula. Weak signals (no P<1e-6), estimated sdY,
prior adjustments and negative-infinite individual log-ABFs have explicit
warnings. No alternative H3 formula, probability floor or silent input repair
is introduced. The source's fixed effect-prior behavior and ignored top-level
`effect_priors` override are explained in the earlier
[API component guide](coloc-native-abf.md); its earlier CLI-unfinished status
is superseded by this workflow checkpoint.

## Inspect the complete output

| File | Meaning |
| --- | --- |
| `COMPLETED.json` | Written last, after complete publication and internal verification; links the manifest hash |
| `analysis-status.json`, `WARNINGS.md` | Admission status, all warnings and required recovery actions |
| `summary.tsv`, `results.json` | Variant counts, H0..H4, effective priors, scale estimates and structured warnings |
| `variants.tsv` | Exact merged IDs/positions, source P-value-mode P/MAF/N, per-trait V/z/shrinkage/log-ABF, combined log-ABF and source conditional SNP posterior |
| `matched-weights.tsv` | Supplied weights in merged order; `.` denotes no supplied weight |
| `source-request.json`, `request.json`, `inputs/` | Original request bytes, explicit portable effective settings and unmodified captured input files |
| `producer.json`, `manifest.json` | Selected producer-class hashes, JVM/OS metadata and complete file inventory |
| `replay-request.json`, `replay-parent.json` | Present on replay; retain the control request and verified parent-manifest identity |

In tables, `.` marks an inapplicable input/scale/position, not a repaired
scientific value. `-Infinity` log-ABFs may appear only when the source-compatible
final posterior remains finite. All warning records are saved; standard error
shows the first 20 with the full count and file location.

Default limits are 256,000,000 combined input bytes, 1,000,000 variants per
trait and 1,000,000,000 output/diagnostic bytes. Set `maximum_input_bytes`
(1..1,000,000,000), `maximum_variants` (1..1,000,000), and
`maximum_output_bytes` (1..4,000,000,000) explicitly when needed. These are
refusal boundaries, not scale or memory guarantees. The synthetic regional
qualification used 12,000/10,000 input variants and 8,000 shared variants.
Metadata JSON is bounded at 2 MB. A suitable heap is still required; capacity,
heap or complete-population failures retain partial evidence and admit no
complete result.

## Verify or replay without original input paths

```powershell
java -jar build/libs/omicsmatic-0.4.0.jar coloc-abf --verify results-new
```

VERIFY checks every file/hash, original versus effective settings, selected
producer classes, status/scope/recovery text, then reparses captured data and
recomputes the complete native result. It compares all published numerical
columns, IDs, counts and warnings. Numerical comparison permits relative
2e-12 or 16 ULP error; an expected zero and a nonzero value are distinct.
Thus tiny positive probabilities cannot be silently replaced by zero.
Changed/extra/missing files or resealed incorrect calculations fail. SHA-256
provides integrity, not a signature or protection against coordinated fabricated
inputs and results. Restore the exact original producer classes to verify an
older profile; do not refresh its hashes.

To replay, save a new request:

```json
{"schema_version":1,"mode":"REPLAY","analysis":"results-new","out":"replayed-new"}
```

Run it with `--request`. REPLAY first verifies the parent, preserves all its
scientific settings, and reruns native computation from its captured inputs
into a new directory. Original external file paths and R are not required.
The parent is unchanged. A VERIFY JSON request is also supported:
`{"schema_version":1,"mode":"VERIFY","analysis":"results-new"}`.

**Exit 3 / SKIP3:** a named exact prerequisite is missing/unreadable; the
dependent analysis is skipped. Restore it, or leave that analysis skipped.

**Exit 2 / FAIL2:** invalid scientific data, nonfinite output, changed evidence,
capacity/heap, schema or other error; no complete result is admitted. Retain
raw inputs, partials and diagnostics, fix the stated cause and retry a new
output. Failures before a valid fresh output can be opened are reported on
standard error. A failed/skipped/partial bundle cannot pass VERIFY.

Thirty original-package reference cases and 32 workflow controls passed on
Windows Java 21/25 and Linux Java 21. Packaged RUN/VERIFY/REPLAY passed on each;
Linux also verified and replayed a Windows bundle. These checks do not establish
historical-edition parity, multi-causal fitting, source-paper reproduction,
biological validity or whole-tool completion. Cumulative COLOC catalog admission
and broader source-role coverage remain separate work.

<!-- SCIENTIFIC-CITATIONS:START -->
## Scientific citations

These are the primary sources for the methods used in this workflow. Cite the relevant paper as well as OmicsMatic when reporting results.

- [Claudia Giambartolomei et al. (2014) — Bayesian test for colocalisation between pairs of genetic association studies using summary statistics](../CITATIONS.md#giambartolomei-coloc-2014) — [PMID: 24830394](https://pubmed.ncbi.nlm.nih.gov/24830394/) · [PMCID: PMC4022491](https://pmc.ncbi.nlm.nih.gov/articles/PMC4022491/)

[Search the complete scientific bibliography](https://robbyjo.github.io/OmicsMatic/citations.html).
<!-- SCIENTIFIC-CITATIONS:END -->
